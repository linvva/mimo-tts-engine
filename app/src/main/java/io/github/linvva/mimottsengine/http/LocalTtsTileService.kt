package io.github.linvva.mimottsengine.http

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.TileService
import io.github.linvva.mimottsengine.MainActivity

class LocalTtsTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (LocalTtsHttpService.isRunning) {
            startService(LocalTtsHttpService.stopIntent(this))
        } else {
            startHttpService()
        }
        qsTile?.subtitle = "正在切换"
        qsTile?.updateTile()
        scheduleTileRefresh()
    }

    private fun startHttpService() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(LocalTtsHttpService.startIntent(this))
            } else {
                startService(LocalTtsHttpService.startIntent(this))
            }
        }.onFailure { error ->
            LocalTtsHttpService.reportStartError(error)
            startAppToStartHttpService()
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

    private fun updateTile() {
        qsTile?.apply {
            label = "Mimo HTTP"
            subtitle = if (LocalTtsHttpService.isRunning) {
                "运行中"
            } else {
                LocalTtsHttpService.lastError ?: "未运行"
            }
            state = if (LocalTtsHttpService.isRunning) {
                android.service.quicksettings.Tile.STATE_ACTIVE
            } else {
                android.service.quicksettings.Tile.STATE_INACTIVE
            }
            updateTile()
        }
    }

    private fun scheduleTileRefresh() {
        android.os.Handler(mainLooper).postDelayed(
            { updateTile() },
            600L,
        )
    }
}
