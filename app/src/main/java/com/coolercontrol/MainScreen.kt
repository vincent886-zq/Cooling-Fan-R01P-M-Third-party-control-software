package com.coolercontrol

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coolercontrol.CoolerViewModel.ScannedDevice
import com.coolercontrol.CoolerViewModel.CoolerUiState

/**
 * Main screen — dark theme BLE cooler control UI
 */
@Composable
fun CoolerScreen(viewModel: CoolerViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    MaterialTheme(
        colorScheme = darkColorScheme(
            surface = Color(0xFF0F0F14),
            surfaceVariant = Color(0xFF1A1A24),
            onSurface = Color(0xFFE8E8F0),
            onSurfaceVariant = Color(0xFF8888A0),
            primary = Color(0xFF4A7AFF),
            secondary = Color(0xFF6C5CE7),
            tertiary = Color(0xFF00D68F),
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ---- Title ----
                Text(
                    "❄️ 冰封散热背夹",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Text(
                    "Xiaomi Cooling Fan BLE Control",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                // ---- Connection bar ----
                ConnectionBar(
                    isConnected = uiState.isConnected,
                    isScanning = uiState.isScanning,
                    onScan = { viewModel.startScan() },
                    onDisconnect = { viewModel.disconnect() }
                )

                // ---- Scanned devices ----
                if (uiState.isScanning || uiState.scannedDevices.isNotEmpty()) {
                    DeviceList(
                        devices = uiState.scannedDevices,
                        isScanning = uiState.isScanning,
                        onSelect = { viewModel.connect(it.address) }
                    )
                }

                // ---- Status cards ----
                StatusRow(uiState)

                // ---- Controls (only when connected) ----
                if (uiState.isConnected) {
                    Controls(
                        onSetPower = { viewModel.setPower(it) },
                        onSetLight = { viewModel.setLight(it) }
                    )
                }

                // ---- Log ----
                LogPanel(logs = uiState.log)
            }
        }
    }
}

// ================================================================
// Connection Bar
// ================================================================
@Composable
private fun ConnectionBar(
    isConnected: Boolean,
    isScanning: Boolean,
    onScan: () -> Unit,
    onDisconnect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = if (isConnected) Color(0xFF1B3A2D)
                    else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                if (isConnected) "✅ 已连接" else if (isScanning) "⏳ 扫描中..." else "❌ 未连接",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                fontSize = 13.sp,
                color = if (isConnected) Color(0xFF00D68F) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!isConnected) {
            Button(
                onClick = onScan,
                enabled = !isScanning,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(if (isScanning) "..." else "🔍 扫描")
            }
        } else {
            Button(
                onClick = onDisconnect,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEE5A24)
                )
            ) { Text("断开") }
        }
    }
}

// ================================================================
// Device List
// ================================================================
@Composable
private fun DeviceList(
    devices: List<ScannedDevice>,
    isScanning: Boolean,
    onSelect: (ScannedDevice) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            devices.take(8).forEach { device ->
                val isKnown = CoolerProtocol.KNOWN_NAME_SUBSTR.any {
                    device.name.lowercase().contains(it)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(device) }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            device.name,
                            fontWeight = if (isKnown) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp,
                            color = if (isKnown) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                        )
                        Text(device.address, fontSize = 11.sp,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${device.rssi} dBm", fontSize = 11.sp,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isScanning) {
                Text("扫描中...  ", fontSize = 12.sp,
                     color = MaterialTheme.colorScheme.onSurfaceVariant,
                     modifier = Modifier.padding(8.dp))
            }
        }
    }
}

// ================================================================
// Status Row
// ================================================================
@Composable
private fun StatusRow(state: CoolerUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatusCard("🌡️ 温度", state.temperature?.let { "${it}°C" } ?: "--°C",
                   tempColor(state.temperature))
        StatusCard("🔋 功率", state.powerMode,
                   MaterialTheme.colorScheme.onSurface)
        StatusCard("💡 灯效", state.lightMode,
                   MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun StatusCard(label: String, value: String, valueColor: Color) {
    Surface(
        modifier = Modifier.weight(1f),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = if (label.contains("温度")) 28.sp else 18.sp,
                 fontWeight = FontWeight.Bold, color = valueColor)
            Text(label, fontSize = 11.sp,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun tempColor(temp: Int?): Color = when {
    temp == null -> MaterialTheme.colorScheme.onSurfaceVariant
    temp < 10   -> Color(0xFF0088FF)
    temp < 20   -> Color(0xFF66BBFF)
    else        -> Color(0xFFFF8844)
}

// ================================================================
// Controls
// ================================================================
@Composable
private fun Controls(
    onSetPower: (CoolerProtocol.PowerMode) -> Unit,
    onSetLight: (CoolerProtocol.LightMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Power row
        ControlGroup("🔋 功率") {
            CoolerProtocol.PowerMode.entries.forEach { mode ->
                ControlButton(mode.label) { onSetPower(mode) }
            }
        }
        // Light row
        ControlGroup("💡 灯效") {
            CoolerProtocol.LightMode.entries.take(3).forEach { mode ->
                ControlButton(mode.label) { onSetLight(mode) }
            }
        }
        ControlGroup("💡 灯效（续）") {
            CoolerProtocol.LightMode.entries.drop(3).forEach { mode ->
                ControlButton(mode.label) { onSetLight(mode) }
            }
        }
    }
}

@Composable
private fun ControlGroup(title: String, content: @Composable RowScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 12.sp,
                 color = MaterialTheme.colorScheme.onSurfaceVariant,
                 fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

@Composable
private fun RowScope.ControlButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.weight(1f)
    ) {
        Text(text, fontSize = 12.sp, maxLines = 1)
    }
}

// ================================================================
// Log
// ================================================================
@Composable
private fun LogPanel(logs: List<String>) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp).heightIn(max = 140.dp)) {
            Text("日志", fontSize = 11.sp,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            logs.take(8).forEach { msg ->
                Text(msg, fontSize = 10.sp,
                     color = MaterialTheme.colorScheme.onSurfaceVariant,
                     fontFamily = MaterialTheme.typography.bodySmall.fontFamily)
            }
        }
    }
}
