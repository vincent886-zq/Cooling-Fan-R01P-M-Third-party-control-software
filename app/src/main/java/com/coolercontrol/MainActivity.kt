package com.coolercontrol

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Main entry point.
 *
 * Handles BLE permission requests at startup.
 */
class MainActivity : ComponentActivity() {

    private val requiredPermissions = mutableListOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
    ).apply {
        // Android 11 and below need location for BLE scanning
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions granted or denied — either way proceed
        // (user may need to enable BLE manually)
        setContent { CoolerScreen() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check BLE
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            setContent { BLEErrorScreen("此设备不支持蓝牙低功耗 (BLE)") }
            return
        }
        val btAdapter = ContextCompat.getSystemService(this, BluetoothAdapter::class.java)
        if (btAdapter == null || !btAdapter.isEnabled) {
            // Request user to enable Bluetooth
            val enableBt = registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { setContent { CoolerScreen() } }
            enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        // Request permissions
        val pending = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (pending.isNotEmpty()) {
            permissionLauncher.launch(pending.toTypedArray())
        } else {
            setContent { CoolerScreen() }
        }
    }
}

@androidx.compose.runtime.Composable
fun BLEErrorScreen(message: String) {
    androidx.compose.material3.MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            surface = androidx.compose.ui.graphics.Color(0xFF0F0F14),
            onSurface = androidx.compose.ui.graphics.Color(0xFFE8E8F0)
        )
    ) {
        androidx.compose.material3.Surface(
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            color = androidx.compose.material3.MaterialTheme.colorScheme.surface
        ) {
            androidx.compose.foundation.layout.Box(
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.material3.Text(
                    message,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = androidx.compose.ui.Modifier.padding(32.dp)
                )
            }
        }
    }
}
