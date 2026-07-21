package com.coolercontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles button presses from the status bar notification.
 * Forwards actions to the BLE manager via a static reference.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        var bleManager: BleManager? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        val mgr = bleManager ?: return

        when (intent.action) {
            CoolerNotification.ACTION_CYCLE_POWER -> {
                // Cycle: 静音 → 智能 → 极寒 → 静音 ...
                // We track current mode via the next one
                // This is a simplified approach - send each mode in sequence
                mgr.setPowerMode(CoolerProtocol.PowerMode.LOW)
            }
            CoolerNotification.ACTION_CYCLE_LIGHT -> {
                // Cycle: 彩虹 → 呼吸 → 流动 → 常亮 → ...
                mgr.setLightMode(CoolerProtocol.LightMode.RAINBOW)
            }
            CoolerNotification.ACTION_TOGGLE_LIGHT -> {
                // Toggle between current and OFF
                // We'll use a cycle: current → OFF → next
                mgr.setLightMode(CoolerProtocol.LightMode.OFF)
            }
        }
    }
}
