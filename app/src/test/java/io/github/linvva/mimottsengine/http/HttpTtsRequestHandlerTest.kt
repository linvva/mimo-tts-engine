package io.github.linvva.mimottsengine.http

import io.github.linvva.mimottsengine.data.TtsSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class HttpTtsRequestHandlerTest {
    // Fake encoded payloads exercise transport; real MediaCodec round trips are device tests.
    private val frame = adtsFrame(byteArrayOf(1, 2, 3))

    @Test fun firstChunkArrivesBeforeCompletionAndDuplicateRequestsShareGeneration() = runBlocking {
        val finish = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        Fixture(aac = { _, emit -> calls.incrementAndGet(); emit(frame); finish.await(); emit(frame) }).use { server ->
            server.connect("/tts?text=same&stream=1").use { a ->
                val first = a.getInputStream()
                assertTrue(first.headers().contains("Transfer-Encoding: chunked"))
                assertArrayEquals(frame, first.chunk())
                assertFalse(finish.isCompleted)
                server.connect("/tts?text=same&stream=1").use { b ->
                    val second = b.getInputStream()
                    second.headers()
                    assertArrayEquals(frame, second.chunk())
                    finish.complete(Unit)
                    assertArrayEquals(frame, first.chunk())
                    assertArrayEquals(frame, second.chunk())
                    assertEquals(0, first.chunk().size)
                    assertEquals(0, second.chunk().size)
                }
            }
            assertEquals(1, calls.get())
            assertTrue(server.exchange("/tts?text=same&stream=1").endsWith("0\r\n\r\n"))
            assertEquals(1, calls.get())
        }
    }

    @Test fun wavAndStreamShareTwoPermitsWhileHealthRemainsAvailable() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val entered = Channel<Unit>(Channel.UNLIMITED)
        val active = AtomicInteger()
        val peak = AtomicInteger()
        suspend fun synthesize() {
            val count = active.incrementAndGet()
            peak.updateAndGet { maxOf(it, count) }
            entered.send(Unit)
            try { release.await() } finally { active.decrementAndGet() }
        }
        Fixture(wav = { synthesize(); byteArrayOf(1) }, aac = { _, emit -> synthesize(); emit(frame) }).use { server ->
            val requests = (0..5).map { i -> async(Dispatchers.IO) { server.exchange("/tts?text=$i&stream=${i % 2}") } }
            withTimeout(2000) { repeat(2) { entered.receive() } }
            assertTrue(server.exchange("/health").endsWith("OK"))
            delay(50)
            assertEquals(2, active.get())
            assertEquals(2, peak.get())
            release.complete(Unit)
            withTimeout(3000) { requests.awaitAll().forEach { assertTrue(it.startsWith("HTTP/1.1 200")) } }
            assertEquals(2, peak.get())
            assertEquals(0, active.get())
        }
    }

    @Test fun streamFailureClosesWithoutSuccessfulEndChunk() = runBlocking {
        val failUpstream = CompletableDeferred<Unit>()
        Fixture(aac = { _, emit -> emit(frame); failUpstream.await(); error("fake stream failure") }).use { server ->
            server.connect("/tts?text=test&stream=1").use { socket ->
                val input = socket.getInputStream()
                assertTrue(input.headers().startsWith("HTTP/1.1 200"))
                assertArrayEquals(frame, input.chunk())
                failUpstream.complete(Unit)
                assertEquals(-1, input.read())
            }
        }
    }

    @Test fun encoderUnavailableIsReportedBeforeSuccessAndDoesNotRetryAsWav() = runBlocking {
        val calls = AtomicInteger()
        Fixture(wav = { fail("Must not retry a paid request"); byteArrayOf() }, aac = { _, _ ->
            calls.incrementAndGet(); throw HttpTtsException(503, "请使用 WAV 兼容配置")
        }).use { server ->
            val response = server.exchange("/tts?text=test&stream=1")
            assertTrue(response.startsWith("HTTP/1.1 503"))
            assertFalse(response.contains("200 OK"))
            assertTrue(response.contains("WAV"))
            assertEquals(1, calls.get())
        }
    }

    @Test fun sourceSpeedAndChineseVoiceOverrideAppSpeedButMissingSpeedIsNatural() = runBlocking {
        val inputs = Channel<HttpTtsInput>(Channel.UNLIMITED)
        Fixture(wav = { inputs.send(it); byteArrayOf(1) }).use { server ->
            assertTrue(server.exchange("/tts?text=test&speed=15&voice=%E8%8C%89%E8%8E%89").startsWith("HTTP/1.1 200"))
            val specified = inputs.receive()
            assertEquals(1.5f, specified.settings.speed)
            assertEquals("茉莉", specified.settings.voice)
            server.exchange("/tts?text=test")
            assertEquals(1f, inputs.receive().settings.speed)
            assertTrue(server.exchange("/tts?text=test&speed=NaN").startsWith("HTTP/1.1 400"))
            assertTrue(inputs.tryReceive().isFailure)
        }
    }

    @Test fun detectedDisconnectCancelsLastConsumerAndStopClosesConnections() = runBlocking {
        val stopped = CompletableDeferred<Unit>()
        val server = Fixture(aac = { _, emit ->
            try { while (true) { emit(frame); delay(10) } } finally { stopped.complete(Unit) }
        })
        try {
            val socket = server.connect("/tts?text=disconnect&stream=1")
            socket.getInputStream().headers()
            socket.getInputStream().chunk()
            socket.setSoLinger(true, 0)
            socket.close()
            withTimeout(2000) { stopped.await() }
            val pending = server.connect("/tts?text=stop&stream=1")
            pending.getInputStream().headers()
            server.close()
            pending.use { assertFalse(it.getInputStream().readBytes().toString(Charsets.ISO_8859_1).endsWith("0\r\n\r\n")) }
        } finally { server.close() }
    }

    @Test fun deadlineIncludesQueuedRequestsAndReleasesAllProducers() = runBlocking {
        val calls = AtomicInteger()
        val active = AtomicInteger()
        Fixture(timeoutMs = 250, aac = { _, _ ->
            calls.incrementAndGet(); active.incrementAndGet()
            try { awaitCancellation() } finally { active.decrementAndGet() }
        }).use { server ->
            val requests = (0..3).map { i -> async(Dispatchers.IO) { server.exchange("/tts?text=$i&stream=1") } }
            withTimeout(2000) { requests.awaitAll() }
            withTimeout(2000) { while (active.get() != 0) delay(5) }
            // All requests expire even if still queued; no task is left holding a permit.
            assertTrue(calls.get() in 2..4)
            assertTrue(server.exchange("/tts?text=wav").startsWith("HTTP/1.1 200"))
        }
    }

    private class Fixture(
        timeoutMs: Long = 3000,
        wav: suspend (HttpTtsInput) -> ByteArray = { byteArrayOf(1) },
        aac: suspend (HttpTtsInput, (ByteArray) -> Unit) -> Unit = { _, emit -> emit(byteArrayOf(1)) },
    ) : AutoCloseable {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val directory = Files.createTempDirectory("http-tts").toFile()
        private val server = ServerSocket(0)
        private val handler = HttpTtsRequestHandler(scope, directory,
            settings = { TtsSettings(apiKey = "test-only", speed = 2f) }, wav = wav, aac = aac, timeoutMs = timeoutMs)
        init {
            scope.launch {
                try {
                    while (isActive) {
                        val client = server.accept()
                        launch { handler.handle(client) }.invokeOnCompletion { client.close() }
                    }
                } catch (_: java.net.SocketException) { }
            }
        }
        fun connect(target: String): Socket = Socket("127.0.0.1", server.localPort).apply {
            soTimeout = 4000
            getOutputStream().write("GET $target HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
            getOutputStream().flush()
        }
        fun exchange(target: String): String = connect(target).use { it.getInputStream().readBytes().toString(Charsets.UTF_8) }
        override fun close() {
            server.close(); scope.cancel(); handler.close()
            runBlocking { withTimeout(2000) { scope.coroutineContext[Job]!!.join() } }
            directory.deleteRecursively()
        }
    }

    private fun InputStream.line(): String = buildString {
        while (true) {
            val byte = read()
            check(byte >= 0) { "Unexpected EOF" }
            if (byte == 10) break
            if (byte != 13) append(byte.toChar())
        }
    }
    private fun InputStream.headers(): String = buildString {
        while (true) { val line = line(); if (line.isEmpty()) break; append(line).append('\n') }
    }
    private fun InputStream.chunk(): ByteArray {
        val size = line().toInt(16)
        val data = ByteArray(size)
        var offset = 0
        while (offset < size) { val n = read(data, offset, size - offset); check(n > 0); offset += n }
        check(line().isEmpty())
        return data
    }
}
