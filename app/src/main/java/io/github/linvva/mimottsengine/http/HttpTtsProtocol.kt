package io.github.linvva.mimottsengine.http

import java.io.IOException
import java.io.OutputStream
import java.net.URLDecoder

internal const val HTTP_TTS_TIMEOUT_MS = 180_000L
internal const val HTTP_TTS_CONCURRENCY = 2

internal class HttpTtsException(val statusCode: Int, message: String) : IOException(message)

internal data class HttpTtsRequest(val path: String, val query: Map<String, String>) {
    val speed: Float
        get() {
            val raw = query["speed"] ?: return 1f
            val value = raw.toFloatOrNull()
            if (value == null || !value.isFinite() || value !in 5f..85f) {
                throw HttpTtsException(400, "speed 必须是 5～85 的有限数值")
            }
            return value / 10f
        }

    val streaming: Boolean
        get() = when (query["stream"]) {
            null, "0" -> false
            "1" -> true
            else -> throw HttpTtsException(400, "stream 必须为 0 或 1")
        }

    companion object {
        fun parse(target: String): HttpTtsRequest {
            try {
                val query = target.substringAfter('?', "").split('&')
                    .filter { it.isNotBlank() }
                    .associate { item ->
                        URLDecoder.decode(item.substringBefore('='), "UTF-8") to
                            URLDecoder.decode(item.substringAfter('=', ""), "UTF-8")
                    }
                return HttpTtsRequest(target.substringBefore('?'), query)
            } catch (_: IllegalArgumentException) {
                throw HttpTtsException(400, "URL 参数编码无效")
            }
        }
    }
}

internal class HttpTtsResponse(private val output: OutputStream) {
    var started = false
        private set

    fun text(code: Int, message: String) = bytes(code, "text/plain; charset=utf-8", message.toByteArray())

    fun bytes(code: Int, contentType: String, bytes: ByteArray) {
        start(code, contentType, "Content-Length: ${bytes.size}\r\n")
        output.write(bytes)
        output.flush()
    }

    fun audioChunk(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        if (!started) start(200, "audio/aac", "Transfer-Encoding: chunked\r\n")
        output.write("${bytes.size.toString(16)}\r\n".toByteArray(Charsets.US_ASCII))
        output.write(bytes)
        output.write("\r\n".toByteArray(Charsets.US_ASCII))
        output.flush()
    }

    fun finishAudio() {
        check(started)
        output.write("0\r\n\r\n".toByteArray(Charsets.US_ASCII))
        output.flush()
    }

    private fun start(code: Int, contentType: String, framing: String) {
        check(!started)
        started = true
        val reason = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            504 -> "Gateway Timeout"
            else -> "Internal Server Error"
        }
        output.write(("HTTP/1.1 $code $reason\r\nContent-Type: $contentType\r\n" +
            framing + "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
    }
}
