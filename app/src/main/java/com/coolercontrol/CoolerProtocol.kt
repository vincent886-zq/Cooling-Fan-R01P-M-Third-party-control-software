package com.coolercontrol

/** Helper: Int varargs → ByteArray (saves typing  everywhere) */
private fun b(vararg bytes: Int) = ByteArray(bytes.size) { bytes[it].toByte() }

/**
 * Xiaomi Cooler BLE Protocol Constants
 *
 * Hardware: 小米冰封散热背夹 (Xiaomi Cooling Fan)
 * Protocol: Telink vendor-specific MMA frames over BLE
 *
 * All values verified with physical button presses (2026-07-19).
 */
object CoolerProtocol {

    // ================================================================
    // BLE Service & Characteristic UUIDs
    // ================================================================
    const val SERVICE_UUID  = "0000af00-0000-1000-8000-00805f9b34fb"
    const val WRITE_CHAR    = "0000af07-0000-1000-8000-00805f9b34fb"
    const val NOTIFY_CHAR   = "0000af08-0000-1000-8000-00805f9b34fb"

    // ================================================================
    // Known device name filters (for scan)
    // ================================================================
    val KNOWN_NAME_PREFIXES = listOf("Xiaomi", "MI", "Mijia", "Cooler", "冰封")
    val KNOWN_NAME_SUBSTR  = listOf("xiaomi", "cooler", "mijia", "冰封", "散热")

    // ================================================================
    // Auth — c0/c1 static challenge-response (from HCI log)
    // ================================================================
    val AUTH_FRAMES: List<ByteArray> = listOf(
        b(
            0xFE, 0xDC, 0xBA, 0xC0, 0x50, 0x00, 0x12,
            0x9B, 0x01, 0xF5, 0x8E,
            0x94, 0x9E, 0xF4, 0x6F, 0x0F,
            0xB3, 0x3B, 0x42, 0xDD, 0x4E,
            0xBA, 0xC8, 0xB7, 0x0F, 0xEF
        ),
        b(
            0xFE, 0xDC, 0xBA, 0xC0, 0x51, 0x00, 0x03,
            0x01, 0x01, 0x00, 0xEF
        ),
        b(
            0xFE, 0xDC, 0xBA, 0xC1, 0x50, 0x00, 0x12,
            0x69, 0x01, 0x70, 0xE9, 0x3E, 0xA1,
            0x41, 0xE1, 0xFC, 0x67, 0x3E, 0x01,
            0x7E, 0x97, 0xEA, 0xDC, 0x6B, 0x96, 0xEF
        ),
        b(
            0xFE, 0xDC, 0xBA, 0xC1, 0x51, 0x00, 0x03,
            0x6A, 0x01, 0x00, 0xEF
        )
    )

    val INIT_FRAMES: List<ByteArray> = listOf(
        b(0xFE, 0xDC, 0xBA, 0xC1, 0x0C, 0x00, 0x05, 0x6B, 0x00, 0x00, 0x00, 0x00, 0xEF),
        b(0xFE, 0xDC, 0xBA, 0xC1, 0x02, 0x00, 0x05, 0x6C, 0xFF, 0xFF, 0xFF, 0xFF, 0xEF),
        b(0xFE, 0xDC, 0xBA, 0xC1, 0x09, 0x00, 0x05, 0x6D, 0xFF, 0xFF, 0xFF, 0xFF, 0xEF)
    )

    // ================================================================
    // Power mode
    // ================================================================
    enum class PowerMode(val value: Int, val label: String) {
        LOW(0x0001, "静音"),
        MID(0x0000, "智能"),
        HIGH(0x0002, "极寒");

        companion object {
            private val F4_MAP = mapOf(0 to MID, 1 to LOW, 2 to HIGH)
            fun fromF4(f4Value: Int): PowerMode = F4_MAP[f4Value] ?: MID
        }
    }

    fun buildPowerFrame(seq: Byte, mode: PowerMode): ByteArray {
        return b(
            0xFE, 0xDC, 0xBA, 0xC1, 0xF2, 0x00, 0x06,
            seq.toInt(), 0x04, 0x00, 0x40,
            (mode.value and 0xFF),
            (mode.value ushr 8),
            0xEF
        )
    }

    // ================================================================
    // Light mode
    // ================================================================
    enum class LightMode(val value: Int, val label: String) {
        RAINBOW(0x0001, "🌈 彩虹"),
        BREATH(0x0202, "🌬 呼吸"),
        FLOWING(0x0103, "💧 流动"),
        BRIGHT(0x0104, "☀️ 常亮"),
        OFF(0x0000, "⬛ 关闭");

        companion object {
            val F4_MAP: Map<Int, LightMode> = entries.associateBy { it.value }
        }
    }

    fun buildLightFrame(seq: Byte, mode: LightMode): ByteArray {
        return b(
            0xFE, 0xDC, 0xBA, 0xC1, 0xF2, 0x00, 0x06,
            seq.toInt(), 0x04, 0x00, 0x42,
            (mode.value and 0xFF),
            (mode.value ushr 8),
            0xEF
        )
    }

    // ================================================================
    // F4 Notification parser
    // ================================================================
    data class F4Status(
        val temperature: Int? = null,
        val powerMode: PowerMode? = null,
        val lightMode: LightMode? = null
    )

    fun parseF4(data: ByteArray): F4Status {
        var temp: Int? = null
        var power: PowerMode? = null
        var light: LightMode? = null

        var i = 0
        val b0 = 0x00
        val b3F = 0x3F
        val b40 = 0x40
        val b42 = 0x42

        while (i < data.size - 3) {
            when {
                // Temperature: 00 3F [val]
                data[i] == b0 && data[i+1] == b3F && i + 2 < data.size -> {
                    val raw = data[i+2].toInt() and 0xFF
                    temp = if (raw < 128) raw else raw - 256
                    i += 3
                }
                // Power: 00 40 [val]
                data[i] == b0 && data[i+1].toInt() == b40 && i + 2 < data.size -> {
                    val f4Val = data[i+2].toInt() and 0xFF
                    power = PowerMode.fromF4(f4Val)
                    i += 3
                }
                // Light: 00 42 [val 2B LE]
                data[i] == b0 && data[i+1].toInt() == b42 && i + 3 < data.size -> {
                    val leVal = (data[i+2].toInt() and 0xFF) or
                                ((data[i+3].toInt() and 0xFF) shl 8)
                    light = LightMode.F4_MAP[leVal]
                    i += 4
                }
                else -> i++
            }
        }
        return F4Status(temp, power, light)
    }
}
