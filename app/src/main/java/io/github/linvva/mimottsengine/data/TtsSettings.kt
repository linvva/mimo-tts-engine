package io.github.linvva.mimottsengine.data

data class TtsSettings(
    val apiKey: String = "",
    val voice: String = VoicePresets.first().id,
    val speed: Float = 1.0f,
    val stylePrompt: String = DEFAULT_STYLE_PROMPT,
) {
    val isReady: Boolean
        get() = apiKey.isNotBlank()
}

data class VoicePreset(
    val id: String,
    val label: String,
    val description: String,
)

val VoicePresets = listOf(
    VoicePreset("mimo_default", "MiMo-默认", "中国集群默认冰糖，其他集群默认 Mia"),
    VoicePreset("冰糖", "冰糖", "中文 · 女声"),
    VoicePreset("茉莉", "茉莉", "中文 · 女声"),
    VoicePreset("苏打", "苏打", "中文 · 男声"),
    VoicePreset("白桦", "白桦", "中文 · 男声"),
    VoicePreset("Mia", "Mia", "English · Female"),
    VoicePreset("Chloe", "Chloe", "English · Female"),
    VoicePreset("Milo", "Milo", "English · Male"),
    VoicePreset("Dean", "Dean", "English · Male"),
)

const val DEFAULT_STYLE_PROMPT =
    "用自然、清晰、适合长时间听书的中文语气朗读。不要解释，不要添加内容。"
