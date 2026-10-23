package io.github.linvva.mimottsengine.tts

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal fun isSymbolOnlyText(text: String): Boolean = text.isNotBlank() && text.codePoints().noneMatch { codePoint ->
    val type = Character.getType(codePoint)
    Character.isLetterOrDigit(codePoint) ||
        type == Character.LETTER_NUMBER.toInt() || type == Character.OTHER_NUMBER.toInt()
}

internal fun symbolSilencePcm(durationMs: Int): ByteArray =
    // Keep one sample for zero pause so players receive decodable audio rather than an empty body.
    ByteArray((TtsAudioConfig.SAMPLE_RATE_HZ * durationMs / 1000).coerceAtLeast(1) * 2)

internal fun symbolSilenceWav(durationMs: Int): ByteArray {
    val pcm = symbolSilencePcm(durationMs)
    return ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray(Charsets.US_ASCII))
        putInt(36 + pcm.size)
        put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        putInt(16)
        putShort(1) // PCM
        putShort(1) // Mono
        putInt(TtsAudioConfig.SAMPLE_RATE_HZ)
        putInt(TtsAudioConfig.SAMPLE_RATE_HZ * 2)
        putShort(2)
        putShort(16)
        put("data".toByteArray(Charsets.US_ASCII))
        putInt(pcm.size)
        put(pcm)
    }.array()
}
