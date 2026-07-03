package io.github.linvva.mimottsengine.http

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import io.github.linvva.mimottsengine.MainActivity
import io.github.linvva.mimottsengine.R
import io.github.linvva.mimottsengine.data.SettingsRepository
import io.github.linvva.mimottsengine.network.MimoTtsClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class LocalTtsHttpService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var settingsRepository: SettingsRepository
    private val client = MimoTtsClient()
    private var serverSocket: ServerSocket? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            lastError = null
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())
        acquireWakeLock()
        startServerIfNeeded()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        serverSocket?.closeSafely()
        serverSocket = null
        wakeLock?.releaseSafely()
        wakeLock = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startServerIfNeeded() {
        if (serverSocket != null) return

        serviceScope.launch {
            runCatching {
                ServerSocket(PORT, 8, InetAddress.getByName(HOST)).use { socket ->
                    serverSocket = socket
                    isRunning = true
                    lastError = null
                    while (!socket.isClosed) {
                        val clientSocket = socket.accept()
                        launch {
                            handleClient(clientSocket)
                        }
                    }
                }
            }.onFailure { error ->
                if (error !is CancellationException && error !is IOException) {
                    lastError = error.message ?: "本地 HTTP 服务启动失败"
                } else if (serverSocket == null) {
                    lastError = error.message ?: "本地 HTTP 服务启动失败"
                }
                isRunning = false
                stopSelf()
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { clientSocket ->
            runCatching {
                val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                val requestLine = reader.readLine().orEmpty()
                while (true) {
                    val headerLine = reader.readLine()
                    if (headerLine.isNullOrEmpty()) break
                    // Drain request headers. This server only supports simple GET requests.
                }

                val parts = requestLine.split(" ")
                if (parts.size < 2 || parts[0] != "GET") {
                    clientSocket.writeTextResponse(400, "只支持 GET 请求")
                    return
                }

                val request = ParsedRequest.from(parts[1])
                when (request.path) {
                    "/health" -> clientSocket.writeTextResponse(200, "OK")
                    "/tts" -> handleTtsRequest(clientSocket, request.query)
                    else -> clientSocket.writeTextResponse(404, "接口不存在")
                }
            }.onFailure { error ->
                runCatching {
                    if (error is LocalHttpException) {
                        socket.writeTextResponse(error.statusCode, error.message)
                    } else {
                        socket.writeTextResponse(500, error.message ?: "本地 HTTP 服务错误")
                    }
                }
            }
        }
    }

    private fun handleTtsRequest(socket: Socket, query: Map<String, String>) {
        val text = query["text"]?.trim().orEmpty()
        if (text.isBlank()) {
            socket.writeTextResponse(400, "缺少 text 参数")
            return
        }

        val wav = runBlocking {
            val currentSettings = settingsRepository.settings.first()
            if (currentSettings.apiKey.isBlank()) {
                throw LocalHttpException(401, "请先配置 Mimo API Key")
            }
            val httpSpeed = query["speed"]?.toLegadoSpeed()
            val requestSettings = currentSettings.copy(
                voice = query["voice"]?.takeIf { it.isNotBlank() } ?: currentSettings.voice,
            )
            Log.i(
                TAG,
                "HTTP TTS request: length=${text.length}, voice=${requestSettings.voice}, speed=${httpSpeed ?: "default"}",
            )
            client.synthesizeWav(text, requestSettings, speedOverride = httpSpeed)
        }

        socket.writeBytesResponse(200, "audio/wav", wav)
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

    private fun Socket.writeTextResponse(code: Int, text: String) {
        writeBytesResponse(code, "text/plain; charset=utf-8", text.toByteArray(StandardCharsets.UTF_8))
    }

    private fun Socket.writeBytesResponse(code: Int, contentType: String, bytes: ByteArray) {
        val reason = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            500 -> "Internal Server Error"
            503 -> "Service Unavailable"
            else -> "Error"
        }
        val headers = buildString {
            append("HTTP/1.1 $code $reason\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray(StandardCharsets.UTF_8)

        getOutputStream().use { output ->
            output.write(headers)
            output.write(bytes)
            output.flush()
        }
    }

    private fun String.toLegadoSpeed(): Float? {
        val value = toFloatOrNull() ?: return null
        return (0.6f + ((value.coerceIn(5f, 50f) - 5f) / 45f) * 0.8f)
    }

    private fun ServerSocket.closeSafely() {
        runCatching { close() }
    }

    private fun PowerManager.WakeLock.releaseSafely() {
        runCatching {
            if (isHeld) release()
        }
    }

    private data class ParsedRequest(
        val path: String,
        val query: Map<String, String>,
    ) {
        companion object {
            fun from(target: String): ParsedRequest {
                val path = target.substringBefore("?")
                val queryString = target.substringAfter("?", "")
                val query = queryString
                    .split("&")
                    .filter { it.isNotBlank() }
                    .mapNotNull { item ->
                        val key = item.substringBefore("=", "").decodeUrl()
                        if (key.isBlank()) return@mapNotNull null
                        key to item.substringAfter("=", "").decodeUrl()
                    }
                    .toMap()
                return ParsedRequest(path, query)
            }

            private fun String.decodeUrl(): String {
                return URLDecoder.decode(this, StandardCharsets.UTF_8.name())
            }
        }
    }

    private class LocalHttpException(
        val statusCode: Int,
        override val message: String,
    ) : IOException(message)

    companion object {
        const val HOST = "127.0.0.1"
        const val PORT = 8765
        const val BASE_URL = "http://127.0.0.1:8765"
        const val ACTION_STOP = "io.github.linvva.mimottsengine.http.STOP"
        private const val CHANNEL_ID = "mimo_local_tts_http_v2"
        private const val NOTIFICATION_ID = 2001
        private const val TAG = "LocalTtsHttpService"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var lastError: String? = null
            private set

        fun reportStartError(error: Throwable) {
            lastError = error.message ?: "本地 HTTP 服务启动失败"
            isRunning = false
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
