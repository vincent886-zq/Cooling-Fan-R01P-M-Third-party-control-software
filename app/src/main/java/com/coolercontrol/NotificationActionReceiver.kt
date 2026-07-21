package com.coolercontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.coolercontrol.CoolerProtocol.LightMode
import com.coolercontrol.CoolerProtocol.PowerMode

/**
 * Global state holder accessible from BroadcastReceiver.
 */
object CoolerGlobal {
    var currentTempString: String = "--°C"
    var currentPowerLabel: String = "--"
    var currentLightLabel: String = "--"
    var bleManager: BleManager? = null

    private val powerCycle = listOf("静音", "智能", "极寒")
    private val powerMap = mapOf(
        "静音" to PowerMode.LOW,
        "智能" to PowerMode.MID,
        "极寒" to PowerMode.HIGH
    )
    private val lightCycle = listOf(
        "🌈 彩虹", "🌬 呼吸", "💧 流动", "☀️ 常亮", "⬛ 关闭"
    )
    private val lightMap = mapOf(
        "🌈 彩虹" to LightMode.RAINBOW,
        "🌬 呼吸" to LightMode.BREATH,
        "💧 流动" to LightMode.FLOWING,
        "☀️ 常亮" to LightMode.BRIGHT,
        "⬛ 关闭" to LightMode.OFF
    )

    fun cyclePower() {
        val idx = powerCycle.indexOf(currentPowerLabel)
        val nextLabel = powerCycle[if (idx >= 0) (idx + 1) % 3 else 0]
        currentPowerLabel = nextLabel
        powerMap[nextLabel]?.let { bleManager?.setPowerMode(it) }
    }

    fun cycleLight() {
        val idx = lightCycle.indexOf(currentLightLabel)
        val nextLabel = lightCycle[if (idx >= 0) (idx + 1) % 5 else 0]
        currentLightLabel = nextLabel
        lightMap[nextLabel]?.let { bleManager?.setLightMode(it) }
    }

    fun toggleLight() {
        val nextLabel = if (currentLightLabel == "⬛ 关闭") "🌈 彩虹" else "⬛ 关闭"
        currentLightLabel = nextLabel
        lightMap[nextLabel]?.let { bleManager?.setLightMode(it) }
    }
}

/**
 * Handles button presses from the status bar notification.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CoolerNotification.ACTION_CYCLE_POWER -> CoolerGlobal.cyclePower()
            CoolerNotification.ACTION_CYCLE_LIGHT -> CoolerGlobal.cycleLight()
            CoolerNotification.ACTION_TOGGLE_LIGHT -> CoolerGlobal.toggleLight()
        }
    }
}
