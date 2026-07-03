package io.github.linvva.mimottsengine.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.util.Log
import io.github.linvva.mimottsengine.MainActivity
import io.github.linvva.mimottsengine.R
import io.github.linvva.mimottsengine.data.SettingsRepository
import io.github.linvva.mimottsengine.network.MimoTtsClient
import io.github.linvva.mimottsengine.network.MimoTtsEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.Locale

class MimoTtsService : TextToSpeechService() {
    private val client = MimoTtsClient()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settingsRepository: SettingsRepository
    private var currentJob: Job? = null
    private var keepAliveReleaseJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        serviceScope.launch {
            val settings = withContext(Dispatchers.IO) { settingsRepository.settings.first() }
            val startedAt = SystemClock.elapsedRealtime()
            client.warmUp(settings.apiKey)
            Log.i(TAG, "Mimo connection warm-up finished in ${SystemClock.elapsedRealtime() - startedAt}ms")
        }
    }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        val job = Job()
        currentJob = job
        beginKeepAlive()
        val text = request.charSequenceText.toString()
        val requestStartMs = SystemClock.elapsedRealtime()
        var firstAudioMs: Long? = null
        var totalBytes = 0L
        var stopped = false
        Log.i(
            TAG,
            "Synthesize request: length=${text.length}, language=${request.language}, country=${request.country}, requestRate=${request.speechRate}",
        )

        runBlocking {
            try {
                withContext(Dispatchers.IO + job) {
                    val settings = settingsRepository.settings.first()
                    val effectiveSettings = settings.copy(
                        speed = (settings.speed * request.speechRateFactor()).coerceIn(MIN_SPEED, MAX_SPEED),
                    )
                    Log.i(TAG, "Effective speech speed: ${effectiveSettings.speed}")
                    val startResult = callback.start(
                        TtsAudioConfig.SAMPLE_RATE_HZ,
                        TtsAudioConfig.ENCODING,
                        TtsAudioConfig.CHANNEL_COUNT,
                    )
                    if (startResult != TextToSpeech.SUCCESS) {
                        Log.w(TAG, "SynthesisCallback.start failed: $startResult")
                        return@withContext
                    }
                    client.synthesize(
                        text = text,
                        settings = effectiveSettings,
                        onEvent = { event -> logMimoEvent(event) },
                    ) { bytes ->
                        if (firstAudioMs == null) {
                            val firstChunkMs = SystemClock.elapsedRealtime()
                            firstAudioMs = firstChunkMs
                            Log.i(TAG, "First audio chunk after ${firstChunkMs - requestStartMs}ms")
                        }
                        totalBytes += bytes.size
                        if (!sendAudio(callback, bytes)) {
                            throw CancellationException("TTS callback rejected audio")
                        }
                    }
                    callback.done()
                    val totalMs = SystemClock.elapsedRealtime() - requestStartMs
                    val audioMs = totalBytes / (TtsAudioConfig.SAMPLE_RATE_HZ * TtsAudioConfig.CHANNEL_COUNT * BYTES_PER_SAMPLE / 1000)
                    Log.i(TAG, "Synthesize done: total=${totalMs}ms, audio=${audioMs}ms, bytes=$totalBytes")
                }
            } catch (_: CancellationException) {
                // onStop() cancels the active request; no further audio should be pushed.
                stopped = true
                Log.i(TAG, "Synthesize cancelled")
            } catch (error: Exception) {
                Log.e(TAG, "Synthesize failed", error)
                callback.error(TextToSpeech.ERROR_SYNTHESIS)
            } finally {
                if (currentJob == job) {
                    currentJob = null
                }
                if (!stopped) {
                    scheduleKeepAliveRelease()
                }
            }
        }
    }

    private fun sendAudio(callback: SynthesisCallback, bytes: ByteArray): Boolean {
        val maxBufferSize = callback.getMaxBufferSize().coerceAtLeast(1)
        var offset = 0
        while (offset < bytes.size) {
            val length = minOf(maxBufferSize, bytes.size - offset)
            val result = callback.audioAvailable(bytes, offset, length)
            if (result != TextToSpeech.SUCCESS) {
                Log.w(TAG, "audioAvailable failed: $result")
                return false
            }
            offset += length
        }
        return true
    }

    private fun logMimoEvent(event: MimoTtsEvent) {
        when (event) {
            MimoTtsEvent.RequestStarted -> Log.i(TAG, "Mimo request started")
            is MimoTtsEvent.ResponseHeaders -> Log.i(TAG, "Mimo response headers after ${event.elapsedMs}ms")
            is MimoTtsEvent.FirstSse -> Log.i(TAG, "Mimo first SSE after ${event.elapsedMs}ms")
            is MimoTtsEvent.FirstAudio -> Log.i(TAG, "Mimo first audio after ${event.elapsedMs}ms, bytes=${event.bytes}")
        }
    }

    override fun onStop() {
        currentJob?.cancel()
        currentJob = null
        releaseKeepAliveNow()
    }

    override fun onDestroy() {
        currentJob?.cancel()
        releaseKeepAliveNow()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        return when (lang) {
            Locale.CHINESE.iso3LanguageCode -> TextToSpeech.LANG_COUNTRY_AVAILABLE
            Locale.ENGLISH.iso3LanguageCode -> TextToSpeech.LANG_AVAILABLE
            else -> TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        return onIsLanguageAvailable(lang, country, variant)
    }

    override fun onGetLanguage(): Array<String> {
        return arrayOf(Locale.CHINESE.iso3LanguageCode, Locale.CHINA.iso3CountryCode, "")
    }

    private companion object {
        private const val TAG = "MimoTtsService"
        private const val BYTES_PER_SAMPLE = 2
        private const val KEEP_ALIVE_NOTIFICATION_ID = 1001
        private const val KEEP_ALIVE_RELEASE_DELAY_MS = 30_000L
        private const val NOTIFICATION_CHANNEL_ID = "mimo_tts_playback"
        private const val MIN_SPEED = 0.6f
        private const val MAX_SPEED = 1.4f
    }

    private fun beginKeepAlive() {
        keepAliveReleaseJob?.cancel()
        keepAliveReleaseJob = null
        ensureNotificationChannel()
        runCatching {
            startForeground(KEEP_ALIVE_NOTIFICATION_ID, createKeepAliveNotification())
        }.onFailure { error ->
            Log.w(TAG, "startForeground failed", error)
        }
        acquireWakeLock()
    }

    private fun scheduleKeepAliveRelease() {
        keepAliveReleaseJob?.cancel()
        keepAliveReleaseJob = serviceScope.launch {
            delay(KEEP_ALIVE_RELEASE_DELAY_MS)
            releaseKeepAliveNow()
        }
    }

    private fun releaseKeepAliveNow() {
        keepAliveReleaseJob?.cancel()
        keepAliveReleaseJob = null
        releaseWakeLock()
        runCatching {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }.onFailure { error ->
            Log.w(TAG, "stopForeground failed", error)
        }
    }

    private fun acquireWakeLock() {
        val existingWakeLock = wakeLock
        if (existingWakeLock?.isHeld == true) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:TtsSynthesis").apply {
            setReferenceCounted(false)
            acquire(KEEP_ALIVE_RELEASE_DELAY_MS + 60_000L)
        }
        Log.i(TAG, "Wake lock acquired")
    }

    private fun releaseWakeLock() {
        val currentWakeLock = wakeLock
        if (currentWakeLock?.isHeld == true) {
            currentWakeLock.release()
            Log.i(TAG, "Wake lock released")
        }
        wakeLock = null
    }

    private fun ensureNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.keep_alive_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.keep_alive_channel_description)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createKeepAliveNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_tts)
            .setContentTitle(getString(R.string.keep_alive_notification_title))
            .setContentText(getString(R.string.keep_alive_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }
}

private val Locale.iso3LanguageCode: String
    get() = getISO3Language()

private val Locale.iso3CountryCode: String
    get() = getISO3Country()

private fun SynthesisRequest.speechRateFactor(): Float {
    return (speechRate / 100f).coerceIn(0.5f, 2.0f)
}
