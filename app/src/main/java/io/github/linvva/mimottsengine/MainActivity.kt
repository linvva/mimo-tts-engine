package io.github.linvva.mimottsengine

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import io.github.linvva.mimottsengine.data.SettingsRepository
import io.github.linvva.mimottsengine.http.LocalTtsHttpService
import io.github.linvva.mimottsengine.network.MimoTtsClient
import io.github.linvva.mimottsengine.tts.TtsAudioConfig
import io.github.linvva.mimottsengine.ui.MimoTtsApp
import io.github.linvva.mimottsengine.ui.theme.MimoTtsTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var settingsRepository: SettingsRepository
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mimoTtsClient = MimoTtsClient()
    private var testJob: Job? = null
    private var testAudioTrack: AudioTrack? = null
    private var permissionStateVersion by mutableIntStateOf(0)
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            permissionStateVersion++
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settingsRepository = SettingsRepository(this)

        setContent {
            MimoTtsTheme {
                MimoTtsApp(
                    settingsRepository = settingsRepository,
                    onTestSpeak = { text, onResult -> speakTest(text, onResult) },
                    isNotificationPermissionGranted = { isNotificationPermissionGranted() },
                    isIgnoringBatteryOptimizations = { isIgnoringBatteryOptimizations() },
                    isLocalHttpServiceRunning = { LocalTtsHttpService.isRunning },
                    localHttpServiceError = { LocalTtsHttpService.lastError },
                    permissionStateVersion = permissionStateVersion,
                    onRequestNotificationPermission = { requestNotificationPermission() },
                    onRequestBatteryOptimizationExemption = { requestBatteryOptimizationExemption() },
                    onOpenAppDetails = { openAppDetails() },
                    onStartLocalHttpService = { startLocalHttpService() },
                    onStopLocalHttpService = { stopLocalHttpService() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionStateVersion++
    }

    private fun isNotificationPermissionGranted(): Boolean {
        return checkSelfPermission(
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(PowerManager::class.java)
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestNotificationPermission() {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun requestBatteryOptimizationExemption() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(intent) }
            .onFailure { openAppDetails() }
    }

    private fun openAppDetails() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun startLocalHttpService() {
        val intent = LocalTtsHttpService.startIntent(this)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }.onFailure {
            LocalTtsHttpService.reportStartError(it)
        }
        refreshServiceStateSoon()
    }

    private fun stopLocalHttpService() {
        startService(LocalTtsHttpService.stopIntent(this))
        refreshServiceStateSoon()
    }

    private fun refreshServiceStateSoon() {
        permissionStateVersion++
        activityScope.launch {
            delay(500)
            permissionStateVersion++
        }
    }

    private fun speakTest(text: String, onResult: (String, Boolean) -> Unit) {
        testJob?.cancel()
        testAudioTrack?.release()
        onResult("正在请求 Mimo TTS...", true)

        testJob = activityScope.launch {
            var audioTrack: AudioTrack? = null

            try {
                val settings = settingsRepository.settings.first()
                val track = createTestAudioTrack()
                audioTrack = track
                testAudioTrack = track

                track.play()
                mimoTtsClient.synthesize(text, settings) { bytes ->
                    track.write(bytes, 0, bytes.size)
                }
                onResult("测试朗读完成。", false)
            } catch (_: CancellationException) {
                onResult("测试朗读已取消。", false)
            } catch (error: Exception) {
                onResult(error.message ?: "测试朗读失败，请检查 API Key 和网络。", false)
            } finally {
                audioTrack?.stopSafely()
                audioTrack?.release()
                if (testAudioTrack == audioTrack) {
                    testAudioTrack = null
                }
                testJob = null
            }
        }
    }

    private fun createTestAudioTrack(): AudioTrack {
        val minBufferSize = AudioTrack.getMinBufferSize(
            TtsAudioConfig.SAMPLE_RATE_HZ,
            TtsAudioConfig.OUTPUT_CHANNEL_MASK,
            TtsAudioConfig.ENCODING,
        ).coerceAtLeast(TtsAudioConfig.SAMPLE_RATE_HZ)

        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(TtsAudioConfig.SAMPLE_RATE_HZ)
                    .setChannelMask(TtsAudioConfig.OUTPUT_CHANNEL_MASK)
                    .setEncoding(TtsAudioConfig.ENCODING)
                    .build(),
            )
            .setBufferSizeInBytes(minBufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun AudioTrack.stopSafely() {
        runCatching {
            if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                stop()
            } else {
                flush()
            }
        }
    }

    override fun onDestroy() {
        testJob?.cancel()
        testAudioTrack?.release()
        activityScope.cancel()
        super.onDestroy()
    }
}
