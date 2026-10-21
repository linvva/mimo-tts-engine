package io.github.linvva.mimottsengine.http

import io.github.linvva.mimottsengine.data.TtsSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.Socket
import java.net.SocketTimeoutException

internal class HttpTtsRequestHandler(
    scope: CoroutineScope,
    cacheDirectory: File,
    private val settings: suspend () -> TtsSettings,
    private val wav: suspend (HttpTtsInput) -> ByteArray,
    aac: suspend (HttpTtsInput, (ByteArray) -> Unit) -> Unit,
    private val log: (String) -> Unit = {},
    private val timeoutMs: Long = HTTP_TTS_TIMEOUT_MS,
) : AutoCloseable {
    private val quota = Semaphore(HTTP_TTS_CONCURRENCY)
    private val streams = SharedTtsStreams(scope, cacheDirectory) { input, emit ->
        timedSynthesis("AAC") {
            aac(input, emit)
        }
    }

    suspend fun handle(socket: Socket) = withContext(Dispatchers.IO) {
        socket.use {
            val response = HttpTtsResponse(socket.getOutputStream())
            try {
                withTimeout(timeoutMs) {
                    coroutineScope {
                        val connection = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                            try { awaitCancellation() } finally { runCatching { socket.close() } }
                        }
                        try {
                            socket.soTimeout = 10_000
                            val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                            val line = reader.readLine().orEmpty().split(' ')
                            while (!reader.readLine().isNullOrEmpty()) { /* Drain GET headers. */ }
                            if (line.size < 2 || line[0] != "GET") throw HttpTtsException(400, "只支持 GET 请求")
                            val request = HttpTtsRequest.parse(line[1])
                            when (request.path) {
                                "/health" -> response.text(200, "OK")
                                "/tts" -> serve(request, response)
                                else -> response.text(404, "接口不存在")
                            }
                        } catch (error: TimeoutCancellationException) {
                            throw error
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            currentCoroutineContext().ensureActive()
                            log("HTTP request failed: ${error.javaClass.simpleName}")
                            if (!response.started) {
                                val status = when (error) {
                                    is HttpTtsException -> error.statusCode
                                    is SocketTimeoutException -> 504
                                    else -> 502
                                }
                                runCatching { response.text(status, error.message ?: "本地 HTTP 合成失败") }
                            }
                        } finally {
                            connection.cancel()
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                log("HTTP request timed out")
            }
        }
    }

    private suspend fun serve(request: HttpTtsRequest, response: HttpTtsResponse) {
        val text = request.query["text"]?.trim().orEmpty()
        if (text.isEmpty()) throw HttpTtsException(400, "缺少 text 参数")
        val speed = request.speed
        val streaming = request.streaming
        val current = settings()
        if (current.apiKey.isBlank()) throw HttpTtsException(401, "请先配置 Mimo API Key")
        val input = HttpTtsInput(text, current.copy(
            voice = request.query["voice"]?.takeIf { it.isNotBlank() } ?: current.voice,
            speed = speed,
        ))
        val started = System.nanoTime()
        var sent = 0L
        log("HTTP TTS: length=${text.length}, voice=${input.settings.voice}, speed=$speed, stream=$streaming")
        if (streaming) {
            streams.consume(input) { chunk ->
                response.audioChunk(chunk)
                if (sent == 0L) log("HTTP first AAC: ${elapsedMs(started)}ms")
                sent += chunk.size
            }
            response.finishAudio()
        } else {
            val bytes = timedSynthesis("WAV") { wav(input) }
            response.bytes(200, "audio/wav", bytes)
            sent = bytes.size.toLong()
        }
        log("HTTP complete: total=${elapsedMs(started)}ms, bytes=$sent")
    }

    private suspend fun <T> timedSynthesis(format: String, block: suspend () -> T): T = withTimeout(timeoutMs) {
        val queued = System.nanoTime()
        quota.withPermit {
            log("HTTP $format synthesis queued=${elapsedMs(queued)}ms")
            try {
                block()
            } catch (error: CancellationException) {
                log("HTTP $format synthesis cancelled")
                throw error
            }
        }
    }

    override fun close() = streams.close()

    private fun elapsedMs(started: Long) = (System.nanoTime() - started) / 1_000_000
}
