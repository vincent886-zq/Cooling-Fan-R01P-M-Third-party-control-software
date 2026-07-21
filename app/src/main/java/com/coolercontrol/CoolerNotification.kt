package com.coolercontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Manages the status bar notification for cooler status & controls.
 */
object CoolerNotification {
    const val CHANNEL_ID = "cooler_status"
    const val NOTIF_ID = 1001

    // Action identifiers (matched in NotificationActionReceiver)
    const val ACTION_CYCLE_POWER = "com.coolercontrol.CYCLE_POWER"
    const val ACTION_CYCLE_LIGHT = "com.coolercontrol.CYCLE_LIGHT"
    const val ACTION_TOGGLE_LIGHT = "com.coolercontrol.TOGGLE_LIGHT"
    const val ACTION_OPEN_APP = "com.coolercontrol.OPEN_APP"

    private var lastTemp: String = "--°C"
    private var lastPower: String = "--"
    private var lastLight: String = "--"
    private var lastConnected: Boolean = false

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, "散热器状态",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "冰封散热背夹实时温度/功率/灯效"
            setShowBadge(false)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    fun update(
        context: Context,
        temperature: Int?,
        powerMode: String,
        lightMode: String,
        isConnected: Boolean,
        shown: Boolean  // whether user has enabled notification
    ) {
        if (!shown) return

        lastTemp = temperature?.let { "${it}°C" } ?: lastTemp
        lastPower = powerMode
        lastLight = lightMode
        lastConnected = isConnected

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Tap to open app
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPend = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cycle power
        val powerIntent = Intent(ACTION_CYCLE_POWER).setPackage(context.packageName)
        val powerPend = PendingIntent.getBroadcast(
            context, 1, powerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cycle light
        val lightIntent = Intent(ACTION_CYCLE_LIGHT).setPackage(context.packageName)
        val lightPend = PendingIntent.getBroadcast(
            context, 2, lightIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Toggle light on/off
        val toggleIntent = Intent(ACTION_TOGGLE_LIGHT).setPackage(context.packageName)
        val togglePend = PendingIntent.getBroadcast(
            context, 3, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val content = "🌡️${lastTemp}  🔥$lastPower  💡$lastLight"

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (lastConnected) "❄️ 冰封散热背夹" else "❄️ 未连接")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setOngoing(lastConnected)
            .setContentIntent(openPend)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .apply {
                if (lastConnected) {
                    addAction(0, "🔄 切换功率", powerPend)
                    addAction(0, "💡 切换灯效", lightPend)
                    addAction(0, if (lastLight == "⬛ 关闭") "💡 开灯" else "💡 关灯", togglePend)
                }
            }
            .build()

        nm.notify(NOTIF_ID, notif)
    }

    fun cancel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID)
    }
}
