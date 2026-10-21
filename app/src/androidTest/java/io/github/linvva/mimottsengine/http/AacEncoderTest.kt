package io.github.linvva.mimottsengine.http

import android.media.MediaCodec
import android.media.MediaFormat
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/** Requires a device: verifies actual AAC encoding/decoding, including EOS for short sentences. */
class AacEncoderTest {
    @Test(timeout = 20_000) fun encodedFramesArePlayableBeforeEndAndShortTailIsDrained() {
        listOf(300, 48_123).forEach { samples ->
            val deadline = System.nanoTime() + 8_000_000_000L
            fun checkDeadline() { check(System.nanoTime() < deadline) { "MediaCodec timed out" } }
            val pcm = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
                repeat(samples) { putShort((sin(it * 2 * PI * 440 / 24_000) * 10_000).toInt().toShort()) }
            }.array()
            val frames = mutableListOf<ByteArray>()
            AacEncoder(::checkDeadline, frames::add).use { encoder ->
                // Odd-sized blocks also verify PCM16 sample alignment between SSE chunks.
                pcm.asList().chunked(1501).forEach { encoder.write(it.toByteArray()) }
                if (samples > 24_000) assertTrue("No frame emitted before synthesis ended", frames.isNotEmpty())
                encoder.finish()
            }
            assertTrue(frames.isNotEmpty())
            assertTrue(decode(frames, ::checkDeadline) >= samples)
        }
    }

    private fun decode(frames: List<ByteArray>, checkDeadline: () -> Unit): Int {
        val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 24_000, 1).apply {
            // AudioSpecificConfig: AAC-LC, frequency index 6 (24 kHz), mono.
            setByteBuffer("csd-0", ByteBuffer.wrap(byteArrayOf(0x13, 0x08)))
        }
        var samples = 0
        var nonZero = false
        var ended = false
        val info = MediaCodec.BufferInfo()
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            fun drain() {
                while (true) {
                    checkDeadline()
                    val index = codec.dequeueOutputBuffer(info, 10_000)
                    if (index == MediaCodec.INFO_TRY_AGAIN_LATER) return
                    if (index < 0) continue
                    try {
                        if (info.size > 0) {
                            val bytes = checkNotNull(codec.getOutputBuffer(index)).duplicate().order(ByteOrder.LITTLE_ENDIAN)
                            bytes.position(info.offset); bytes.limit(info.offset + info.size)
                            while (bytes.remaining() >= 2) {
                                if (bytes.short.toInt() != 0) nonZero = true
                                samples++
                            }
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) ended = true
                    } finally { codec.releaseOutputBuffer(index, false) }
                    if (ended) return
                }
            }
            repeat(frames.size + 1) { frameIndex ->
                var index: Int
                do { checkDeadline(); drain(); index = codec.dequeueInputBuffer(10_000) } while (index < 0)
                val eos = frameIndex == frames.size
                val data = if (eos) byteArrayOf() else frames[frameIndex].copyOfRange(7, frames[frameIndex].size)
                codec.getInputBuffer(index)!!.apply { clear(); put(data) }
                codec.queueInputBuffer(index, 0, data.size, frameIndex * 1024L * 1_000_000 / 24_000,
                    if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
            }
            while (!ended) drain()
            assertTrue("Decoded audio is silent", nonZero)
            return samples
        } finally { codec.stop(); codec.release() }
    }
}
