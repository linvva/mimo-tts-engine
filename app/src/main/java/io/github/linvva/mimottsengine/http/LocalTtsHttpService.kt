package io.github.linvva.mimottsengine.http

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import io.github.linvva.mimottsengine.MainActivity
import io.github.linvva.mimottsengine.R
import io.github.linvva.mimottsengine.data.SettingsRepository
import io.github.linvva.mimottsengine.network.MimoTtsClient
import io.github.linvva.mimottsengine.tts.symbolSilencePcm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.io.File
import java.net.ServerSocket

class LocalTtsHttpService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settingsRepository: SettingsRepository
    private val client = MimoTtsClient.forLocalHttp()
    private lateinit var requests: HttpTtsRequestHandler
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        requests = HttpTtsRequestHandler(
            scope = serviceScope,
            cacheDirectory = File(cacheDir, "http-tts-streams"),
            settings = { settingsRepository.settings.first() },
            wav = { input -> client.synthesizeWav(input.text, input.settings, speedOverride = input.settings.speed) },
            aac = { input, emit ->
                val context = currentCoroutineContext()
                val encoder = try {
                    AacEncoder(checkActive = { context.ensureActive() }, onFrame = emit)
                } catch (_: Exception) {
                    throw HttpTtsException(503, "流式编码不可用，请复制 WAV 兼容配置")
                }
                encoder.use {
                    client.synthesize(
                        input.text,
                        input.settings,
                        onEvent = { event ->
                            if (event is io.github.linvva.mimottsengine.network.MimoTtsEvent.FirstAudio) {
                                Log.i(TAG, "HTTP first PCM: ${event.elapsedMs}ms")
                            }
                        },
                        requireCompleteStream = true,
                        onAudio = { bytes -> encoder.write(bytes) },
                    )
                    encoder.finish()
                }
            },
            silentAac = { durationMs, emit ->
                val context = currentCoroutineContext()
                val encoder = try {
                    AacEncoder(checkActive = { context.ensureActive() }, onFrame = emit)
                } catch (_: Exception) {
                    throw HttpTtsException(503, "流式编码不可用，请复制 WAV 兼容配置")
                }
                encoder.use {
                    encoder.write(symbolSilencePcm(durationMs))
                    encoder.finish()
                }
            },
            log = { message -> Log.i(TAG, message) },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            mutableState.value = mutableState.value.copy(error = null)
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
            }
            startForeground(NOTIFICATION_ID, createNotification(), foregroundType)
            acquireWakeLock()
            startServerIfNeeded()
            return START_STICKY
        } catch (error: Exception) {
            reportStartError(error)
            stopSelf()
            return START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        requests.close()
        serverSocket?.closeSafely()
        serverSocket = null
        wakeLock?.releaseSafely()
        wakeLock = null
        mutableState.value = mutableState.value.copy(isRunning = false, isStarting = false)
        Log.i(TAG, "Local HTTP service stopped")
        super.onDestroy()
    }

    private fun startServerIfNeeded() {
        if (serverJob != null) return
        mutableState.value = State(isStarting = true)

        serverJob = serviceScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ServerSocket(PORT, 8, InetAddress.getByName(HOST)).use { socket ->
                        withContext(Dispatchers.Main.immediate) {
                            serverSocket = socket
                            mutableState.value = State(isRunning = true)
                            Log.i(TAG, "Local HTTP server listening on $BASE_URL")
                        }
                        while (!socket.isClosed) {
                            val clientSocket = socket.accept()
                            launch {
                                try {
                                    requests.handle(clientSocket)
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    Log.w(TAG, "HTTP connection failed: ${error.javaClass.simpleName}")
                                }
                            }.invokeOnCompletion { runCatching { clientSocket.close() } }
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ensureActive()
                reportStartError(error)
                stopSelf()
            }
        }
    }

    private fun createNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mimo 本地朗读服务",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "本地 HTTP 在线朗读接口运行状态"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_tts)
            .setContentTitle("Mimo 本地朗读服务运行中")
            .setContentText("Legado 可通过 127.0.0.1:8765 请求在线朗读")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MimoTtsEngine:LocalHttp")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun ServerSocket.closeSafely() {
        runCatching { close() }
    }

    private fun PowerManager.WakeLock.releaseSafely() {
        runCatching {
            if (isHeld) release()
        }
    }

    data class State(
        val isRunning: Boolean = false,
        val isStarting: Boolean = false,
        val error: Throwable? = null,
    )

    companion object {
        const val HOST = "127.0.0.1"
        const val PORT = 8765
        const val BASE_URL = "http://127.0.0.1:8765"
        const val ACTION_STOP = "io.github.linvva.mimottsengine.http.STOP"
        private const val CHANNEL_ID = "mimo_local_tts_http_v2"
        private const val NOTIFICATION_ID = 2001
        private const val TAG = "LocalTtsHttpService"

        private val mutableState = MutableStateFlow(State())
        val state = mutableState.asStateFlow()

        val isRunning: Boolean
            get() = state.value.isRunning

        val lastError: String?
            get() = state.value.error?.let { it.message ?: "本地 HTTP 服务启动失败" }

        fun start(context: Context) {
            if (state.value.isRunning || state.value.isStarting) return
            mutableState.value = State(isStarting = true)
            try {
                checkNotNull(context.startForegroundService(startIntent(context))) {
                    "系统未允许启动本地 HTTP 服务"
                }
            } catch (error: Exception) {
                reportStartError(error)
            }
        }

        fun reportStartError(error: Throwable) {
            Log.e(TAG, "Local HTTP service start failed", error)
            mutableState.value = State(error = error)
        }

        fun startIntent(context: Context): Intent {
            return Intent(context, LocalTtsHttpService::class.java)
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, LocalTtsHttpService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }
}
