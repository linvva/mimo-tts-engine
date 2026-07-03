package io.github.linvva.mimottsengine.tts

import android.media.AudioFormat

object TtsAudioConfig {
    const val SAMPLE_RATE_HZ = 24_000
    const val CHANNEL_COUNT = 1
    const val OUTPUT_CHANNEL_MASK = AudioFormat.CHANNEL_OUT_MONO
    const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
}
