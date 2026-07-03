package io.github.linvva.mimottsengine.http

import android.os.Build
import android.service.quicksettings.TileService

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
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(LocalTtsHttpService.startIntent(this))
                } else {
                    startService(LocalTtsHttpService.startIntent(this))
                }
            }.onFailure {
                LocalTtsHttpService.reportStartError(it)
            }
        }
        qsTile?.subtitle = "正在切换"
        qsTile?.updateTile()
        scheduleTileRefresh()
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
