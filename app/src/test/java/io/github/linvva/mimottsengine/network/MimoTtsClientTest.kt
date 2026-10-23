package io.github.linvva.mimottsengine.network

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.linvva.mimottsengine.data.TtsSettings
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MimoTtsClientTest {
    private val settings = TtsSettings(apiKey = "test-only", voice = "茉莉", speed = 1.5f)
    private val audio = "data: {\"choices\":[{\"delta\":{\"audio\":{\"data\":\"AQIDBA==\"}}}]}\n\n"

    @Test fun novelSymbolParagraphsReturnConfiguredSilenceWithoutUpstream() = runBlocking {
        val requests = AtomicInteger()
        Upstream { exchange ->
            requests.incrementAndGet()
            exchange.sse()
            exchange.responseBody.write((audio + "data: [DONE]\n\n").toByteArray())
        }.use { upstream ->
            val client = upstream.client()
            // The full-width indentation and quoted ellipsis match lines 34080 and 34088.
            listOf("\u3000\u3000……", "\u3000\u3000“……”").forEach { text ->
                listOf(0, 300, 10000).forEach { durationMs ->
                    val pcm = ByteArrayOutputStream()
                    val events = mutableListOf<MimoTtsEvent>()
                    client.synthesize(text, settings.copy(symbolPauseMs = durationMs),
                        onEvent = events::add, requireCompleteStream = true, onAudio = pcm::write)
                    assertArrayEquals(ByteArray((durationMs * 24).coerceAtLeast(1) * 2), pcm.toByteArray())
                    assertTrue(events.isEmpty())
                }
            }
            assertEquals(0, requests.get())
        }
    }

    @Test fun disablingSymbolPauseAndReadableShortTextStillUseUpstream() = runBlocking {
        val requests = AtomicInteger()
        Upstream { exchange ->
            requests.incrementAndGet()
            exchange.sse()
            exchange.responseBody.write((audio + "data: [DONE]\n\n").toByteArray())
        }.use { upstream ->
            listOf("\u3000\u3000“……”" to settings.copy(symbolPauseEnabled = false), "嗯。" to settings)
                .forEach { (text, current) ->
                    val pcm = ByteArrayOutputStream()
                    upstream.client().synthesize(text, current, requireCompleteStream = true, onAudio = pcm::write)
                    assertArrayEquals(byteArrayOf(1, 2, 3, 4), pcm.toByteArray())
                }
            assertEquals(2, requests.get())
        }
    }

    @Test fun cancellingLocalAudioCallbackStopsSynthesisWithoutUpstream() = runBlocking {
        val requests = AtomicInteger()
        Upstream { requests.incrementAndGet() }.use { upstream ->
            val synthesis = async {
                upstream.client().synthesize("……", settings) {
                    coroutineContext.cancel()
                }
            }
            try {
                synthesis.await()
                fail("Cancelled synthesis must not complete successfully")
            } catch (_: CancellationException) { }
            assertEquals(0, requests.get())
        }
    }

    @Test fun emitsPcmBeforeUpstreamCompletesAndPreservesVoiceAndPromptSpeed() = runBlocking {
        val release = CountDownLatch(1)
        val first = CompletableDeferred<Unit>()
        val requestBody = CompletableDeferred<String>()
        Upstream { exchange ->
            requestBody.complete(exchange.requestBody.readBytes().toString(Charsets.UTF_8))
            exchange.sse()
            exchange.responseBody.write(audio.toByteArray()); exchange.responseBody.flush()
            release.await(3, TimeUnit.SECONDS)
            exchange.responseBody.write("data: [DONE]\n\n".toByteArray())
        }.use { upstream ->
            val result = async {
                val chunks = mutableListOf<Byte>()
                upstream.client().synthesize("测试文字", settings, requireCompleteStream = true) {
                    chunks.addAll(it.toList()); first.complete(Unit)
                }
                chunks
            }
            try {
                withTimeout(2000) { first.await() }
                assertFalse(result.isCompleted)
                val request = Json.parseToJsonElement(requestBody.await()).jsonObject
                assertEquals("茉莉", request.getValue("audio").jsonObject.getValue("voice").jsonPrimitive.content)
                assertEquals("pcm16", request.getValue("audio").jsonObject.getValue("format").jsonPrimitive.content)
                assertTrue(request.getValue("messages").jsonArray[0].jsonObject.getValue("content").jsonPrimitive.content.contains("150%"))
                release.countDown()
                assertEquals(listOf<Byte>(1, 2, 3, 4), withTimeout(2000) { result.await() })
            } finally { release.countDown() }
        }
    }

    @Test fun truncatedSseAndExplicitUpstreamErrorAreFailures() = runBlocking {
        listOf(audio, audio + "data: {\"error\":{\"message\":\"fake\"}}\n\n", "data: [DONE]\n\n").forEach { body ->
            Upstream { it.sse(); it.responseBody.write(body.toByteArray()) }.use { upstream ->
                try {
                    upstream.client().synthesize("test", settings, requireCompleteStream = true) {}
                    fail("Truncated or empty audio must not finish successfully")
                } catch (_: MimoTtsException) { }
            }
        }
    }

    @Test fun cancellationInterruptsBlockingReadAndBlockingHeaders() = runBlocking {
        listOf(false, true).forEach { sendHeaders ->
            val entered = CompletableDeferred<Unit>()
            val release = CountDownLatch(1)
            Upstream { exchange ->
                if (sendHeaders) { exchange.sse(); exchange.responseBody.write(audio.toByteArray()); exchange.responseBody.flush() }
                entered.complete(Unit)
                release.await(3, TimeUnit.SECONDS)
            }.use { upstream ->
                val job = launch { upstream.client().synthesize("test", settings, requireCompleteStream = true) {} }
                try {
                    withTimeout(2000) { entered.await() }
                    withTimeout(1000) { job.cancelAndJoin() }
                    assertTrue(job.isCancelled)
                } finally { release.countDown() }
            }
        }
    }

    @Test fun idleUpstreamTimesOutWithoutAutomaticRetry() = runBlocking {
        val release = CountDownLatch(1)
        val requests = java.util.concurrent.atomic.AtomicInteger()
        Upstream { exchange ->
            requests.incrementAndGet(); exchange.sse()
            exchange.responseBody.write(audio.toByteArray()); exchange.responseBody.flush()
            release.await(3, TimeUnit.SECONDS)
        }.use { upstream ->
            try {
                val client = upstream.client(OkHttpClient.Builder().readTimeout(100, TimeUnit.MILLISECONDS)
                    .retryOnConnectionFailure(false).build())
                try { client.synthesize("test", settings, requireCompleteStream = true) {}; fail("Expected idle timeout") }
                catch (_: SocketTimeoutException) { }
                assertEquals(1, requests.get())
            } finally { release.countDown() }
        }
    }

    private class Upstream(private val handle: (HttpExchange) -> Unit) : AutoCloseable {
        private val executor = Executors.newCachedThreadPool()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            this.executor = this@Upstream.executor
            createContext("/tts") { exchange ->
                try { handle(exchange) } finally { exchange.close() }
            }
            start()
        }
        fun client(http: OkHttpClient = OkHttpClient()): MimoTtsClient = MimoTtsClient(
            httpClient = http, apiUrl = "http://127.0.0.1:${server.address.port}/tts")
        override fun close() { server.stop(0); executor.shutdownNow() }
    }

    private fun HttpExchange.sse() {
        responseHeaders.add("Content-Type", "text/event-stream")
        sendResponseHeaders(200, 0)
    }
}
