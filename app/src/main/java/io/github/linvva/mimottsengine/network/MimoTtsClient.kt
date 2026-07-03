package io.github.linvva.mimottsengine.network

import android.os.SystemClock
import android.util.Base64
import io.github.linvva.mimottsengine.data.TtsSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class MimoTtsClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
) {
    suspend fun warmUp(apiKey: String) = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext

        val request = Request.Builder()
            .url(API_URL)
            .header("api-key", apiKey)
            .head()
            .build()

        runCatching {
            httpClient.newCall(request).execute().close()
        }
    }

    suspend fun synthesize(
        text: String,
        settings: TtsSettings,
        onEvent: (MimoTtsEvent) -> Unit = {},
        onAudio: suspend (ByteArray) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (settings.apiKey.isBlank()) {
            throw MimoTtsException("请先配置 Mimo API Key")
        }
        if (text.isBlank()) {
            return@withContext
        }

        val body = json.encodeToString(
            ChatCompletionRequest(
                messages = listOf(
                    Message(role = "user", content = settings.promptWithSpeed()),
                    Message(role = "assistant", content = text),
                ),
                audio = AudioOptions(voice = settings.voice),
            ),
        )

        val request = Request.Builder()
            .url(API_URL)
            .header("api-key", settings.apiKey)
            .header("Accept", "text/event-stream")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val call = httpClient.newCall(request)
        val requestStartMs = SystemClock.elapsedRealtime()
        onEvent(MimoTtsEvent.RequestStarted)
        coroutineContext.ensureActive()
        coroutineContext.job.invokeOnCompletion {
            if (it is CancellationException) call.cancel()
        }

        call.execute().use { response ->
            onEvent(MimoTtsEvent.ResponseHeaders(SystemClock.elapsedRealtime() - requestStartMs))
            if (!response.isSuccessful) {
                val errorBody = response.body.string().take(300)
                val detail = errorBody.ifBlank { response.message }
                throw MimoTtsException("Mimo API 请求失败：HTTP ${response.code} $detail")
            }

            val source = response.body.source()

            var sawFirstSse = false
            var sawFirstAudio = false
            while (!source.exhausted()) {
                coroutineContext.ensureActive()
                val line = source.readUtf8Line() ?: break
                val data = line.removePrefix("data:").trim()
                if (!line.startsWith("data:") || data.isBlank() || data == "[DONE]") {
                    continue
                }
                if (!sawFirstSse) {
                    sawFirstSse = true
                    onEvent(MimoTtsEvent.FirstSse(SystemClock.elapsedRealtime() - requestStartMs))
                }

                val audio = parseStreamingAudioData(data) ?: continue
                val bytes = Base64.decode(audio, Base64.DEFAULT)
                if (!sawFirstAudio) {
                    sawFirstAudio = true
                    onEvent(MimoTtsEvent.FirstAudio(SystemClock.elapsedRealtime() - requestStartMs, bytes.size))
                }
                onAudio(bytes)
            }
        }
    }

    suspend fun synthesizeWav(
        text: String,
        settings: TtsSettings,
        speedOverride: Float? = null,
    ): ByteArray = withContext(Dispatchers.IO) {
        if (settings.apiKey.isBlank()) {
            throw MimoTtsException("请先配置 Mimo API Key")
        }
        if (text.isBlank()) {
            throw MimoTtsException("朗读文本不能为空")
        }

        val body = json.encodeToString(
            ChatCompletionRequest(
                messages = listOf(
                    Message(role = "user", content = settings.promptWithOptionalSpeed(speedOverride)),
                    Message(role = "assistant", content = text),
                ),
                audio = AudioOptions(format = "wav", voice = settings.voice),
                stream = false,
            ),
        )

        val request = Request.Builder()
            .url(API_URL)
            .header("api-key", settings.apiKey)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val call = httpClient.newCall(request)
        coroutineContext.job.invokeOnCompletion {
            if (it is CancellationException) call.cancel()
        }

        call.execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body.string().take(300)
                val detail = errorBody.ifBlank { response.message }
                throw MimoTtsException("Mimo API 请求失败：HTTP ${response.code} $detail")
            }

            val responseBody = response.body.string()
            val audio = parseNonStreamingAudioData(responseBody)
                ?: throw MimoTtsException("Mimo API 响应中没有音频数据")
            Base64.decode(audio, Base64.DEFAULT)
        }
    }

    private fun parseStreamingAudioData(data: String): String? {
        val root = runCatching { json.parseToJsonElement(data) }
            .getOrElse { throw MimoTtsException("SSE JSON 解析失败", it) }
            as? JsonObject
            ?: return null

        return (root["choices"] as? JsonArray)
            ?.firstOrNull()
            .asObjectOrNull()
            ?.get("delta")
            .asObjectOrNull()
            ?.get("audio")
            .asObjectOrNull()
            ?.get("data")
            .asPrimitiveOrNull()
            ?.contentOrNull
    }

    private fun parseNonStreamingAudioData(data: String): String? {
        val root = runCatching { json.parseToJsonElement(data) }
            .getOrElse { throw MimoTtsException("Mimo JSON 解析失败", it) }
            as? JsonObject
            ?: return null

        val choice = (root["choices"] as? JsonArray)
            ?.firstOrNull()
            .asObjectOrNull()

        return choice
            ?.get("message")
            .asObjectOrNull()
            ?.get("audio")
            .asObjectOrNull()
            ?.get("data")
            .asPrimitiveOrNull()
            ?.contentOrNull
            ?: choice
                ?.get("delta")
                .asObjectOrNull()
                ?.get("audio")
                .asObjectOrNull()
                ?.get("data")
                .asPrimitiveOrNull()
                ?.contentOrNull
    }

    private fun Any?.asObjectOrNull(): JsonObject? = this as? JsonObject

    private fun Any?.asPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

    private fun TtsSettings.promptWithSpeed(): String {
        return "$stylePrompt\n${speedInstruction()}"
    }

    private fun TtsSettings.promptWithOptionalSpeed(speedOverride: Float?): String {
        return if (speedOverride == null) {
            stylePrompt
        } else {
            "$stylePrompt\n${speedInstruction(speedOverride)}"
        }
    }

    private fun TtsSettings.speedInstruction(): String {
        return speedInstruction(speed)
    }

    private fun speedInstruction(speed: Float): String {
        val percent = (speed * 100).toInt()
        return when {
            speed <= 0.7f -> "语速要求必须优先满足：明显慢读，目标约为正常语速的 $percent%。句内放慢，句末停顿更充分，但不要拖长音。"
            speed < 0.95f -> "语速要求必须优先满足：稍慢朗读，目标约为正常语速的 $percent%。保持稳定停顿。"
            speed <= 1.05f -> "语速要求：自然标准语速，目标约为正常语速的 $percent%。"
            speed < 1.25f -> "语速要求必须优先满足：稍快朗读，目标约为正常语速的 $percent%。减少不必要停顿，但保持吐字清晰。"
            else -> "语速要求必须优先满足：明显快读，目标约为正常语速的 $percent%。显著减少停顿，但不要含糊。"
        }
    }

    @Serializable
    private data class ChatCompletionRequest(
        val model: String = "mimo-v2.5-tts",
        val messages: List<Message>,
        val audio: AudioOptions,
        val stream: Boolean? = true,
    )

    @Serializable
    private data class Message(
        val role: String,
        val content: String,
    )

    @Serializable
    private data class AudioOptions(
        val format: String = "pcm16",
        val voice: String,
    )

    companion object {
        private const val API_URL = "https://api.xiaomimimo.com/v1/chat/completions"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
    }
}

sealed interface MimoTtsEvent {
    data object RequestStarted : MimoTtsEvent
    data class ResponseHeaders(val elapsedMs: Long) : MimoTtsEvent
    data class FirstSse(val elapsedMs: Long) : MimoTtsEvent
    data class FirstAudio(val elapsedMs: Long, val bytes: Int) : MimoTtsEvent
}

class MimoTtsException(message: String, cause: Throwable? = null) : IOException(message, cause)
