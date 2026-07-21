package com.coolercontrol

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service: owns the notification, responds instantly to buttons.
 */
@SuppressLint("MissingPermission")
class CoolerService : Service() {

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> start()
            "STOP"  -> { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
            CoolerNotification.ACTION_CYCLE_POWER  -> action("POWER")
            CoolerNotification.ACTION_CYCLE_LIGHT  -> action("LIGHT")
            CoolerNotification.ACTION_TOGGLE_LIGHT -> action("TOGGLE")
        }
        return START_STICKY
    }

    private fun start() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.createNotificationChannel(
            android.app.NotificationChannel(
                CoolerNotification.CHANNEL_ID, "散热器状态",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
        )
        pushNotif()
        startForeground(CoolerNotification.NOTIF_ID, buildNotif())
    }

    private fun action(type: String) {
        when (type) {
            "POWER"  -> CoolerGlobal.cyclePower()
            "LIGHT"  -> CoolerGlobal.cycleLight()
            "TOGGLE" -> CoolerGlobal.toggleLight()
        }
        pushNotif() // update notification with new state
    }

    fun pushNotif() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(CoolerNotification.NOTIF_ID, buildNotif())
    }

    private fun buildNotif(): android.app.Notification {
        val openPend = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val g = CoolerGlobal
        val content = "🌡️${g.currentPowerLabel}  🔥${g.currentPowerLabel}  💡${g.currentLightLabel}"

        return NotificationCompat.Builder(this, CoolerNotification.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("❄️ 冰封散热背夹")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setOngoing(true)
            .setContentIntent(openPend)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .apply {
                addAction(0, "🔄 切换功率",
                    PendingIntent.getService(this@CoolerService, 1,
                        Intent(this@CoolerService, CoolerService::class.java)
                            .apply { action = CoolerNotification.ACTION_CYCLE_POWER },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                addAction(0, "💡 切换灯效",
                    PendingIntent.getService(this@CoolerService, 2,
                        Intent(this@CoolerService, CoolerService::class.java)
                            .apply { action = CoolerNotification.ACTION_CYCLE_LIGHT },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                val toggleLabel = if (g.currentLightLabel == "⬛ 关闭") "💡 开灯" else "💡 关灯"
                addAction(0, toggleLabel,
                    PendingIntent.getService(this@CoolerService, 3,
                        Intent(this@CoolerService, CoolerService::class.java)
                            .apply { action = CoolerNotification.ACTION_TOGGLE_LIGHT },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            .build()
    }
}
