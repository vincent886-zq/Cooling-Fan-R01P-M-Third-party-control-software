package com.coolercontrol

/**
 * Notification constants and action identifiers.
 * All notification management is handled by CoolerService.
 */
object CoolerNotification {
    const val CHANNEL_ID = "cooler_status"
    const val NOTIF_ID = 1001

    const val ACTION_CYCLE_POWER = "com.coolercontrol.CYCLE_POWER"
    const val ACTION_CYCLE_LIGHT = "com.coolercontrol.CYCLE_LIGHT"
    const val ACTION_TOGGLE_LIGHT = "com.coolercontrol.TOGGLE_LIGHT"
    const val ACTION_OPEN_APP = "com.coolercontrol.OPEN_APP"
}
