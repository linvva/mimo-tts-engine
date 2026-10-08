package io.github.linvva.mimottsengine.http

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.TileService
import io.github.linvva.mimottsengine.MainActivity
import io.github.linvva.mimottsengine.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class LocalTtsTileService : TileService() {
    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateJob: Job? = null
    private var startRequested = false

    override fun onStartListening() {
        super.onStartListening()
        stateJob?.cancel()
        stateJob = tileScope.launch {
            LocalTtsHttpService.state.collect { state ->
                updateTile(state)
                handleStartResult(state)
            }
        }
    }

    override fun onStopListening() {
        stateJob?.cancel()
        stateJob = null
        startRequested = false
        super.onStopListening()
    }

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        if (LocalTtsHttpService.state.value.isStarting) return
        if (LocalTtsHttpService.isRunning) {
            startService(LocalTtsHttpService.stopIntent(this))
            qsTile?.subtitle = "正在停止"
            qsTile?.updateTile()
        } else {
            startHttpService()
        }
    }

    private fun startHttpService() {
        startRequested = true
        LocalTtsHttpService.start(this)
        updateTile(LocalTtsHttpService.state.value)
        handleStartResult(LocalTtsHttpService.state.value)
    }

    private fun handleStartResult(state: LocalTtsHttpService.State) {
        if (!startRequested) return
        if (state.isRunning) {
            startRequested = false
        } else if (state.error != null) {
            startRequested = false
            // ForegroundServiceStartNotAllowedException also extends IllegalStateException.
            if (state.error is IllegalStateException) {
                runCatching { startAppToStartHttpService() }
                    .onFailure { LocalTtsHttpService.reportStartError(it) }
            }
        }
    }

    private fun startAppToStartHttpService() {
        val intent = MainActivity.startLocalHttpIntent(this)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile(serviceState: LocalTtsHttpService.State) {
        qsTile?.apply {
            label = getString(R.string.local_http_tile_label)
            subtitle = when {
                serviceState.isStarting -> "正在启动"
                serviceState.isRunning -> "运行中"
                serviceState.error != null -> serviceState.error.message ?: "启动失败"
                else -> "未运行"
            }
            state = when {
                serviceState.isStarting -> android.service.quicksettings.Tile.STATE_UNAVAILABLE
                serviceState.isRunning -> android.service.quicksettings.Tile.STATE_ACTIVE
                else -> android.service.quicksettings.Tile.STATE_INACTIVE
            }
            updateTile()
        }
    }
}
