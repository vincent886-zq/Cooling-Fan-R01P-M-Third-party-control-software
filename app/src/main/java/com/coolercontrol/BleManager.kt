package com.coolercontrol

import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.Timer
import java.util.TimerTask
import java.util.UUID

/**
 * BLE Manager — scan, connect, GATT read/write/notify
 *
 * Uses Android's standard BluetoothLeScanner and BluetoothGatt APIs.
 * All callbacks run on the main thread unless specified.
 */
class BleManager(private val context: Context) {

    // ================================================================
    // Callbacks
    // ================================================================
    var onScanResult: ((name: String, address: String, rssi: Int) -> Unit)? = null
    var onScanFinished: (() -> Unit)? = null
    var onConnected: ((Boolean) -> Unit)? = null   // true=connected, false=disconnected
    var onStatusUpdate: ((CoolerProtocol.F4Status) -> Unit)? = null
    var onLog: ((String) -> Unit)? = null

    // ================================================================
    // State
    // ================================================================
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var seq: Byte = 1
    private var isScanning = false
    private var isConnected = false

    init {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    }

    // ================================================================
    // Scan
    // ================================================================
    fun startScan(timeoutMs: Long = 8000) {
        if (isScanning) return
        val scanner = bluetoothLeScanner ?: run {
            onLog?.invoke("BLE not available")
            return
        }
        isScanning = true

        // Filter: look for Telink service or known name prefixes
        val filters = CoolerProtocol.KNOWN_NAME_PREFIXES.map { prefix ->
            ScanFilter.Builder().setDeviceName(prefix).build()
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(filters, settings, scanCallback)
        onLog?.invoke("Scanning...")

        // Auto-stop after timeout
        Timer().schedule(object : TimerTask() {
            override fun run() {
                stopScan()
            }
        }, timeoutMs)
    }

    fun stopScan() {
        if (!isScanning) return
        bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        onScanFinished?.invoke()
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: "(unknown)"
            val rssi = result.rssi
            onScanResult?.invoke(name, device.address, rssi)
        }
    }

    // ================================================================
    // Connect
    // ================================================================
    fun connect(address: String) {
        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return
        onLog?.invoke("Connecting to $address...")
        gatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        writeChar = null
        isConnected = false
        onConnected?.invoke(false)
        onLog?.invoke("Disconnected")
    }

    // ================================================================
    // GATT Callbacks
    // ================================================================
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    isConnected = true
                    onLog?.invoke("GATT connected")
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    isConnected = false
                    writeChar = null
                    onConnected?.invoke(false)
                    onLog?.invoke("GATT disconnected")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) {
                onLog?.invoke("Service discovery failed")
                return
            }
            val service = gatt.getService(java.util.UUID.fromString(CoolerProtocol.SERVICE_UUID))
                ?: run { onLog?.invoke("Service not found"); return }

            writeChar = service.getCharacteristic(
                java.util.UUID.fromString(CoolerProtocol.WRITE_CHAR)
            )
            val notifyChar = service.getCharacteristic(
                java.util.UUID.fromString(CoolerProtocol.NOTIFY_CHAR)
            )

            if (writeChar == null || notifyChar == null) {
                onLog?.invoke("Required chars not found")
                return
            }

            // Enable notifications
            gatt.setCharacteristicNotification(notifyChar, true)
            val descriptor = notifyChar.getDescriptor(
                java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            )
            descriptor?.let {
                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(it)
            }

            onLog?.invoke("Services ready, starting auth...")

            // Auth in background thread to avoid blocking main thread
            Thread {
                doAuth()
            }.start()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            val data = characteristic?.value ?: return
            val status = CoolerProtocol.parseF4(data)
            onStatusUpdate?.invoke(status)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            // Write completed (write-with-response)
        }
    }

    // ================================================================
    // Auth + Commands
    // ================================================================
    private fun doAuth() {
        onLog?.invoke("Auth c0/c1...")
        CoolerProtocol.AUTH_FRAMES.forEach { frame ->
            write(frame, delayMs = 150)
        }
        CoolerProtocol.INIT_FRAMES.forEach { frame ->
            write(frame, delayMs = 80)
        }
        onLog?.invoke("Auth complete ✓")
        onConnected?.invoke(true)
    }

    fun setPowerMode(mode: CoolerProtocol.PowerMode) {
        val frame = CoolerProtocol.buildPowerFrame(seq, mode)
        seq = ((seq.toInt() + 1) and 0xFF).toByte()
        write(frame)
        onLog?.invoke("→ Power: ${mode.label}")
    }

    fun setLightMode(mode: CoolerProtocol.LightMode) {
        val frame = CoolerProtocol.buildLightFrame(seq, mode)
        seq = ((seq.toInt() + 1) and 0xFF).toByte()
        write(frame)
        onLog?.invoke("→ Light: ${mode.label}")
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun write(data: ByteArray, delayMs: Long = 0) {
        val char = writeChar ?: return
        mainHandler.post {
            try {
                char.value = data
                gatt?.writeCharacteristic(char)
            } catch (e: Exception) {
                onLog?.invoke("Write failed: ${e.message}")
            }
        }
        if (delayMs > 0) Thread.sleep(delayMs)
    }
}
