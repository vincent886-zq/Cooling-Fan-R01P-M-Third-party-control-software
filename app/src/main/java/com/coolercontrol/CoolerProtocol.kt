package com.coolercontrol

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
    const val WRITE_CHAR    = "0000af07-0000-1000-8000-00805f9b34fb"  // F2 command
    const val NOTIFY_CHAR   = "0000af08-0000-1000-8000-00805f9b34fb"  // F4 status

    // ================================================================
    // Known device name filters (for scan)
    // ================================================================
    val KNOWN_NAME_PREFIXES = listOf("Xiaomi", "MI", "Mijia", "Cooler", "冰封")
    val KNOWN_NAME_SUBSTR  = listOf(
        "xiaomi", "cooler", "mijia", "冰封", "散热"
    )

    // ================================================================
    // MMA Frame header/footer
    // ================================================================
    private val MMA_HEAD = byteArrayOf(0xFE, 0xDC, 0xBA.toByte())
    private val MMA_FOOT: Byte = 0xEF.toByte()

    // ================================================================
    // Auth — c0/c1 static challenge-response (from HCI log capture)
    // The device uses Telink CH32 for auth; all observed sessions
    // used the exact same static values (no dynamic challenge).
    // ================================================================
    val AUTH_FRAMES: List<ByteArray> = listOf(
        byteArrayOf(
            0xFE, 0xDC, 0xBA.toByte(), 0xC0, 0x50, 0x00, 0x12,
            0x9B.toByte(), 0x01, 0xF5.toByte(), 0x8E.toByte(),
            0x94, 0x9E.toByte(), 0xF4.toByte(), 0x6F, 0x0F,
            0xB3.toByte(), 0x3B, 0x42, 0xDD.toByte(), 0x4E,
            0xBA.toByte(), 0xC8.toByte(), 0xB7.toByte(), 0x0F, 0xEF.toByte()
        ),
        byteArrayOf(
            0xFE, 0xDC, 0xBA.toByte(), 0xC0, 0x51, 0x00, 0x03,
            0x01, 0x01, 0x00, 0xEF.toByte()
        ),
        byteArrayOf(
            0xFE, 0xDC, 0xBA.toByte(), 0xC1, 0x50, 0x00, 0x12,
            0x69, 0x01, 0x70, 0xE9.toByte(), 0x3E, 0xA1.toByte(),
            0x41, 0xE1.toByte(), 0xFC.toByte(), 0x67, 0x3E, 0x01,
            0x7E, 0x97, 0xEA.toByte(), 0xDC.toByte(), 0x6B, 0x96, 0xEF.toByte()
        ),
        byteArrayOf(
            0xFE, 0xDC, 0xBA.toByte(), 0xC1, 0x51, 0x00, 0x03,
            0x6A, 0x01, 0x00, 0xEF.toByte()
        )
    )

    val INIT_FRAMES: List<ByteArray> = listOf(
        byteArrayOf(0xFE, 0xDC, 0xBA.toByte(), 0xC1, 0x0C, 0x00, 0x05, 0x6B, 0x00, 0x00, 0x00, 0x00, 0xEF.toByte()),
        byteArrayOf(0xFE, 0xDC, 0xBA.toByte(), 0xC1, 0x02, 0x00, 0x05, 0x6C, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xEF.toByte()),
        byteArrayOf(0xFE, 0xDC, 0xBA.toByte(), 0xC1, 0x09, 0x00, 0x05, 0x6D, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xEF.toByte())
    )

    // ================================================================
    // Power mode — F2 write format
    //   frame: FE DC BA C1 F2 00 06 [seq] 04 00 40 [val 2B LE] EF
    //   val mapping:
    //     LOW  (静音) = 0x0001
    //     MID  (智能) = 0x0000
    //     HIGH (极寒) = 0x0002
    //   Physical feel confirmed by user (F4 value 0=Mid, 1=Low, 2=High).
    // ================================================================
    enum class PowerMode(val value: Int, val label: String) {
        LOW(0x0001, "静音"),
        MID(0x0000, "智能"),
        HIGH(0x0002, "极寒");

        companion object {
            // F4 notification value → PowerMode (F4: 0=Mid, 1=Low, 2=High)
            private val F4_MAP = mapOf(0 to MID, 1 to LOW, 2 to HIGH)
            fun fromF4(f4Value: Int): PowerMode = F4_MAP[f4Value] ?: MID
        }
    }

    fun buildPowerFrame(seq: Byte, mode: PowerMode): ByteArray {
        val valBytes = byteArrayOf(
            (mode.value and 0xFF).toByte(),
            (mode.value ushr 8).toByte()
        )
        return byteArrayOf(
            0xFE, 0xDC, 0xBA.toByte(), 0xC1, 0xF2.toByte(), 0x00, 0x06,
            seq, 0x04, 0x00, 0x40,
            valBytes[0], valBytes[1],
            0xEF.toByte()
        )
    }

    // ================================================================
    // Light mode — F2 write format
    //   frame: FE DC BA C1 F2 00 06 [seq] 04 00 42 [val 2B LE] EF
    //   val mapping (F4-verified by physical button press, 2026-07-19):
    //     RAINBOW = 0x0001  → 01 00  (byte1=1, byte2=0)
    //     BREATH  = 0x0202  → 02 02  (both bytes = 2)
    //     FLOWING = 0x0103  → 03 01  (byte1=3, byte2=1)
    //     BRIGHT  = 0x0104  → 04 01  (byte1=4, byte2=1)
    //     OFF     = 0x0000  → 00 00
    //   Critical: 2-byte LE, NOT big-endian!
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
        val valBytes = byteArrayOf(
            (mode.value and 0xFF).toByte(),
            (mode.value ushr 8).toByte()
        )
        return byteArrayOf(
            0xFE, 0xDC, 0xBA.toByte(), 0xC1, 0xF2.toByte(), 0x00, 0x06,
            seq, 0x04, 0x00, 0x42,
            valBytes[0], valBytes[1],
            0xEF.toByte()
        )
    }

    // ================================================================
    // F4 Notification parser
    //
    // Notification bundle format (AF08, 3 MMA frames per packet):
    //   FE DC BA 81 F4 [len] [seq] 04 00 [piid] [value...] EF
    //
    // piid 0x3F = temperature (1 byte, signed)
    // piid 0x40 = power mode  (1 byte, 0=Mid 1=Low 2=High)
    // piid 0x42 = light mode  (2 bytes LE)
    // ================================================================
    data class F4Status(
        val temperature: Int? = null,    // °C
        val powerMode: PowerMode? = null,
        val lightMode: LightMode? = null
    )

    fun parseF4(data: ByteArray): F4Status {
        var temp: Int? = null
        var power: PowerMode? = null
        var light: LightMode? = null

        var i = 0
        while (i < data.size - 3) {
            when {
                // Temperature: 00 3F [val]
                data[i] == 0x00 && data[i+1] == 0x3F.toByte() && i + 2 < data.size -> {
                    val raw = data[i+2].toInt() and 0xFF
                    temp = if (raw < 128) raw else raw - 256
                    i += 3
                }
                // Power: 00 40 [val]
                data[i] == 0x00 && data[i+1] == 0x40 && i + 2 < data.size -> {
                    val f4Val = data[i+2].toInt() and 0xFF
                    power = PowerMode.fromF4(f4Val)
                    i += 3
                }
                // Light: 00 42 [val 2B LE]
                data[i] == 0x00 && data[i+1] == 0x42 && i + 3 < data.size -> {
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
