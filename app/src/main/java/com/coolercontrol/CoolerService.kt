package com.coolercontrol

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

@SuppressLint("MissingPermission")
class CoolerService : Service() {

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> start()
            "STOP"  -> { isRunning = false; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            CoolerNotification.ACTION_CYCLE_POWER  -> bg { CoolerGlobal.cyclePower(); refreshNotif(this) }
            CoolerNotification.ACTION_CYCLE_LIGHT  -> bg { CoolerGlobal.cycleLight(); refreshNotif(this) }
            CoolerNotification.ACTION_TOGGLE_LIGHT -> bg { CoolerGlobal.toggleLight(); refreshNotif(this) }
        }
        return START_STICKY
    }

    private fun start() {
        isRunning = true
        appContext = this
        createChannel()
        startForeground(CoolerNotification.NOTIF_ID, buildNotif())
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }

    companion object {
        private var isRunning = false
        private var appContext: Context? = null

        /** Update notification from anywhere (ViewModel, etc.) */
        fun refreshNotif(ctx: Context? = appContext) {
            val c = ctx ?: return
            val nm = c.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(CoolerNotification.NOTIF_ID, buildNotif(c))
        }

        /** Run BLE operation on background thread to avoid blocking UI */
        fun bg(action: () -> Unit) {
            Thread(action).start()
        }

        private fun buildNotif(ctx: Context): android.app.Notification {
            val g = CoolerGlobal
            val content = "🌡️${g.currentTempString}  🔥${g.currentPowerLabel}  💡${g.currentLightLabel}"
            return NotificationCompat.Builder(ctx, CoolerNotification.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("❄️ 冰封散热背夹")
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(ctx, 0,
                    Intent(ctx, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .apply {
                    addAction(0, "🔄 切换功率",
                        PendingIntent.getService(ctx, 1,
                            Intent(ctx, CoolerService::class.java).apply {
                                action = CoolerNotification.ACTION_CYCLE_POWER
                            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                    addAction(0, "💡 切换灯效",
                        PendingIntent.getService(ctx, 2,
                            Intent(ctx, CoolerService::class.java).apply {
                                action = CoolerNotification.ACTION_CYCLE_LIGHT
                            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                    addAction(0, if (g.currentLightLabel == "⬛ 关闭") "💡 开灯" else "💡 关灯",
                        PendingIntent.getService(ctx, 3,
                            Intent(ctx, CoolerService::class.java).apply {
                                action = CoolerNotification.ACTION_TOGGLE_LIGHT
                            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                }
                .build()
        }

        private fun createChannel() {
            val ctx = appContext ?: return
            val nm = ctx.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(android.app.NotificationChannel(
                CoolerNotification.CHANNEL_ID, "散热器状态",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) })
        }
    }
}
