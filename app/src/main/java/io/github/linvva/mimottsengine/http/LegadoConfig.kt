package io.github.linvva.mimottsengine.http

import io.github.linvva.mimottsengine.data.VoicePresets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val configJson = Json { prettyPrint = true }

internal fun legadoConfig(
    defaultVoice: String,
    debug: Boolean,
    streaming: Boolean = true,
    updatedAt: Long = System.currentTimeMillis(),
): String {
    val voiceLiteral = JsonPrimitive(defaultVoice).toString()
    val url = "http://127.0.0.1:8765/tts?text={{java.encodeURI(speakText)}}" +
        "&speed={{speakSpeed}}&voice={{java.encodeURI(currentToneID || $voiceLiteral)}}" +
        "&stream=${if (streaming) 1 else 0}"
    val speakers = buildJsonArray {
        VoicePresets.forEach { voice ->
            add(buildJsonObject {
                put("speakerName", voice.label)
                put("toneID", voice.id)
            })
        }
    }
    return configJson.encodeToString(buildJsonObject {
        put("id", if (debug) 87650002L else 87650001L)
        put("name", if (debug) "Mimo 本地 TTS (Debug)" else "Mimo 本地 TTS")
        put("url", url)
        put("contentType", "audio/.*")
        put("speed", 5)
        put("synthesisThreadCount", HTTP_TTS_CONCURRENCY)
        put("speakersJson", speakers.toString())
        put("lastUpdateTime", updatedAt)
    })
}
