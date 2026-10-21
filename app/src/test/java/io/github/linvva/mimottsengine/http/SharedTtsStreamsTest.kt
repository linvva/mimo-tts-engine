package io.github.linvva.mimottsengine.http

import io.github.linvva.mimottsengine.data.TtsSettings
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class SharedTtsStreamsTest {
    private val input = HttpTtsInput("同一段文字", TtsSettings(apiKey = "test-only", voice = "茉莉"))

    @Test fun overlappingConsumersAndCompletedReplayShareOneProducer() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val directory = Files.createTempDirectory("shared-tts").toFile()
        val calls = AtomicInteger()
        val first = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val store = SharedTtsStreams(scope, directory) { _, emit ->
            calls.incrementAndGet()
            emit(byteArrayOf(1, 2))
            finish.await()
            emit(byteArrayOf(3, 4))
        }
        try {
            val a = async { mutableListOf<Byte>().also { bytes -> store.consume(input) { bytes.addAll(it.toList()); first.complete(Unit) } } }
            withTimeout(2000) { first.await() }
            val b = async { mutableListOf<Byte>().also { bytes -> store.consume(input) { bytes.addAll(it.toList()); second.complete(Unit) } } }
            withTimeout(2000) { second.await() }
            a.cancelAndJoin() // The remaining consumer must keep the producer alive.
            finish.complete(Unit)
            assertEquals(listOf<Byte>(1, 2, 3, 4), withTimeout(2000) { b.await() })
            val replay = mutableListOf<Byte>()
            store.consume(input) { replay.addAll(it.toList()) }
            assertEquals(listOf<Byte>(1, 2, 3, 4), replay)
            assertEquals(1, calls.get())
        } finally {
            store.close(); scope.cancel()
            withTimeout(2000) { while (directory.listFiles().orEmpty().isNotEmpty()) delay(10) }
            directory.deleteRecursively()
        }
    }

    @Test fun failureIsNotReplayedAndCredentialOrPromptChangesDoNotShare() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val directory = Files.createTempDirectory("failed-tts").toFile()
        val calls = AtomicInteger()
        val store = SharedTtsStreams(scope, directory) { _, emit ->
            if (calls.incrementAndGet() == 1) error("fake upstream failure")
            emit(byteArrayOf(1))
        }
        try {
            try { store.consume(input) {}; fail("Expected upstream failure") } catch (_: IllegalStateException) { }
            store.consume(input) {}
            store.consume(input.copy(settings = input.settings.copy(apiKey = "another-test-key"))) {}
            store.consume(input.copy(settings = input.settings.copy(stylePrompt = "另一个提示词"))) {}
            store.consume(input.copy(settings = input.settings.copy(voice = "冰糖"))) {}
            store.consume(input.copy(settings = input.settings.copy(speed = 1.5f))) {}
            assertEquals(6, calls.get())
        } finally { store.close(); scope.cancel(); directory.deleteRecursively() }
    }

    @Test fun completedCacheExpiresAndEvictsOldestOverBudget() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val directory = Files.createTempDirectory("cache-tts").toFile()
        val calls = AtomicInteger()
        val store = SharedTtsStreams(scope, directory, retentionMs = 150, maxCacheBytes = 3) { _, emit ->
            calls.incrementAndGet(); emit(byteArrayOf(1, 2))
        }
        try {
            store.consume(input) {}
            store.consume(input.copy(text = "second")) {}
            store.consume(input) {} // Two two-byte entries exceed the three-byte budget.
            assertEquals(3, calls.get())
            withTimeout(2000) { while (directory.listFiles().orEmpty().isNotEmpty()) delay(10) }
            store.consume(input) {}
            assertEquals(4, calls.get())
        } finally { store.close(); scope.cancel(); directory.deleteRecursively() }
    }

    @Test fun lastConsumerCancellationStopsProducerAndRemovesTemporaryFile() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val directory = Files.createTempDirectory("cancel-tts").toFile()
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val store = SharedTtsStreams(scope, directory) { _, _ ->
            started.complete(Unit)
            try { awaitCancellation() } finally { stopped.complete(Unit) }
        }
        try {
            val consumer = launch { store.consume(input) {} }
            withTimeout(2000) { started.await() }
            consumer.cancelAndJoin()
            withTimeout(2000) {
                stopped.await()
                while (directory.listFiles().orEmpty().isNotEmpty()) delay(10)
            }
        } finally { store.close(); scope.cancel(); directory.deleteRecursively() }
    }
}
