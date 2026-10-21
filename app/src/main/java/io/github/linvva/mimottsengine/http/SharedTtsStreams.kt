package io.github.linvva.mimottsengine.http

import io.github.linvva.mimottsengine.data.TtsSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile

internal data class HttpTtsInput(val text: String, val settings: TtsSettings)

/** One producer per input; subscribers independently read the growing private file. */
internal class SharedTtsStreams(
    private val scope: CoroutineScope,
    private val directory: File,
    private val retentionMs: Long = 60_000,
    private val maxCacheBytes: Long = 32L * 1024 * 1024,
    private val produce: suspend (HttpTtsInput, (ByteArray) -> Unit) -> Unit,
) : AutoCloseable {
    private data class Progress(val size: Long = 0, val done: Boolean = false, val error: Throwable? = null)
    private class Entry(val file: File) {
        val progress = MutableStateFlow(Progress())
        lateinit var job: Job
        var readers = 0
        var completedAt: Long? = null
        var removed = false
    }

    private val lock = Any()
    private val entries = mutableMapOf<HttpTtsInput, Entry>()
    private var closed = false

    init {
        check(directory.mkdirs() || directory.isDirectory)
        directory.listFiles()?.filter { it.name.startsWith("stream-") }?.forEach { it.delete() }
    }

    suspend fun consume(input: HttpTtsInput, onChunk: (ByteArray) -> Unit) {
        val entry = synchronized(lock) {
            check(!closed) { "HTTP 服务已停止" }
            prune()
            entries.getOrPut(input) {
                Entry(File.createTempFile("stream-", ".aac", directory)).also { created ->
                    created.job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                        generate(input, created)
                    }
                    created.job.invokeOnCompletion { error ->
                        synchronized(lock) {
                            // A lazy producer can be cancelled before its body ever starts.
                            if (!created.progress.value.done) {
                                created.progress.value = created.progress.value.copy(
                                    done = true,
                                    error = error ?: CancellationException("HTTP synthesis stopped"),
                                )
                                remove(input, created)
                            }
                            deleteIfUnused(created)
                        }
                    }
                }
            }.also { it.readers++ }
        }
        try {
            entry.job.start()
            RandomAccessFile(entry.file, "r").use { file ->
                var position = 0L
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val progress = entry.progress.first { it.size > position || it.done }
                    progress.error?.let { throw it }
                    while (position < progress.size) {
                        currentCoroutineContext().ensureActive()
                        val size = minOf(buffer.size.toLong(), progress.size - position).toInt()
                        file.readFully(buffer, 0, size)
                        onChunk(buffer.copyOf(size))
                        position += size
                    }
                    if (progress.done) break
                }
            }
        } finally {
            synchronized(lock) {
                entry.readers--
                if (entry.readers == 0 && !entry.progress.value.done) {
                    remove(input, entry)
                    entry.job.cancel(CancellationException("No HTTP audio consumers"))
                }
                prune()
                deleteIfUnused(entry)
            }
        }
    }

    private suspend fun generate(input: HttpTtsInput, entry: Entry) {
        try {
            entry.file.outputStream().use { output ->
                produce(input) { bytes ->
                    if (bytes.isNotEmpty()) {
                        output.write(bytes)
                        entry.progress.value = Progress(entry.progress.value.size + bytes.size)
                    }
                }
            }
            if (entry.progress.value.size == 0L) throw HttpTtsException(502, "MiMo 没有返回音频数据")
            synchronized(lock) {
                entry.completedAt = nowMs()
                entry.progress.value = entry.progress.value.copy(done = true)
                prune()
            }
            scope.launch {
                delay(retentionMs)
                synchronized(lock) { prune() }
            }
        } catch (error: Throwable) {
            synchronized(lock) {
                entry.progress.value = entry.progress.value.copy(done = true, error = error)
                remove(input, entry)
            }
        } finally {
            synchronized(lock) { deleteIfUnused(entry) }
        }
    }

    // Called under lock; active readers keep their file until their own finally block.
    private fun prune() {
        val completed = entries.entries.filter { it.value.completedAt != null }
            .sortedBy { it.value.completedAt }
        var bytes = completed.sumOf { it.value.progress.value.size }
        val now = nowMs()
        completed.forEach { (input, entry) ->
            if (now - checkNotNull(entry.completedAt) >= retentionMs || bytes > maxCacheBytes) {
                bytes -= entry.progress.value.size
                remove(input, entry)
            }
        }
    }

    private fun remove(input: HttpTtsInput, entry: Entry) {
        if (entries[input] === entry) entries.remove(input)
        entry.removed = true
        deleteIfUnused(entry)
    }

    private fun deleteIfUnused(entry: Entry) {
        if (entry.removed && entry.readers == 0 && entry.progress.value.done) entry.file.delete()
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            entries.toList().forEach { (input, entry) ->
                remove(input, entry)
                entry.job.cancel()
            }
        }
    }

    private fun nowMs(): Long = System.nanoTime() / 1_000_000
}
