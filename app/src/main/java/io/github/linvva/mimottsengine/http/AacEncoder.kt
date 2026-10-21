package io.github.linvva.mimottsengine.http

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat

internal class AacEncoder(
    private val checkActive: () -> Unit,
    private val onFrame: (ByteArray) -> Unit,
) : AutoCloseable {
    private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val info = MediaCodec.BufferInfo()
    private var inputBytes = 0L
    private var trailingByte: Byte? = null
    private var outputEnded = false

    init {
        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 24_000, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        } catch (error: Exception) {
            codec.release()
            throw error
        }
    }

    fun write(bytes: ByteArray) {
        val pcm = trailingByte?.let { byteArrayOf(it) + bytes } ?: bytes
        val alignedLength = pcm.size and -2
        trailingByte = if (alignedLength < pcm.size) pcm.last() else null
        var offset = 0
        while (offset < alignedLength) {
            checkActive()
            drain()
            val index = codec.dequeueInputBuffer(10_000)
            if (index < 0) continue
            val input = checkNotNull(codec.getInputBuffer(index))
            input.clear()
            val length = minOf(input.remaining() and -2, alignedLength - offset)
            check(length > 0)
            input.put(pcm, offset, length)
            codec.queueInputBuffer(index, 0, length, inputBytes * 1_000_000 / 48_000, 0)
            inputBytes += length
            offset += length
        }
        drain()
    }

    fun finish() {
        check(trailingByte == null) { "PCM16 音频包含不完整采样" }
        var queued = false
        while (!queued) {
            checkActive()
            drain()
            val index = codec.dequeueInputBuffer(10_000)
            if (index >= 0) {
                codec.queueInputBuffer(index, 0, 0, inputBytes * 1_000_000 / 48_000, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                queued = true
            }
        }
        while (!outputEnded) {
            checkActive()
            drain(wait = true)
        }
    }

    private fun drain(wait: Boolean = false) {
        while (true) {
            checkActive()
            val index = codec.dequeueOutputBuffer(info, if (wait) 10_000 else 0)
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) return
            if (index < 0) continue
            try {
                if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                    val output = checkNotNull(codec.getOutputBuffer(index))
                    output.position(info.offset)
                    output.limit(info.offset + info.size)
                    val payload = ByteArray(info.size)
                    output.get(payload)
                    onFrame(adtsFrame(payload))
                }
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEnded = true
            } finally {
                codec.releaseOutputBuffer(index, false)
            }
            if (outputEnded) return
        }
    }

    override fun close() {
        try { codec.stop() } finally { codec.release() }
    }
}
