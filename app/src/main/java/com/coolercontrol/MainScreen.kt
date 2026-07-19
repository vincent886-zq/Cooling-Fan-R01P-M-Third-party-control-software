package com.coolercontrol

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coolercontrol.CoolerViewModel.ScannedDevice
import com.coolercontrol.CoolerViewModel.CoolerUiState

private val Bg1 = Color(0xFFF5F5F8)
private val CardBg = Color(0xFFFFFFFF)
private val CardBd = Color(0xFFE0E0E8)
private val Tx1 = Color(0xFF1A1A2E)
private val Tx2 = Color(0xFF8888AA)
private val Blue = Color(0xFF4A7AFF)
private val Purple = Color(0xFF7C5CFC)
private val Green = Color(0xFF00C48C)
private val Red = Color(0xFFFF4757)
private val Orange = Color(0xFFFF9F43)
private val DimW = Color(0x10000000)

private val cardBorder = BorderStroke(1.dp, CardBd)
private val borderSt = Color(0xFF2A2A40)

@Composable
fun CoolerScreen(viewModel: CoolerViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    MaterialTheme(
        colorScheme = lightColorScheme(
            surface = Bg1, surfaceVariant = CardBg,
            onSurface = Tx1, onSurfaceVariant = Tx2,
            primary = Blue, secondary = Purple,
        )
    ) {
        Box(Modifier.fillMaxSize().background(Bg1)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // Gradient header
                Box(Modifier.fillMaxWidth().background(
                    Brush.verticalGradient(listOf(Purple.copy(alpha=.08f), Color.Transparent))
                ).padding(start=20.dp, end=20.dp, top=28.dp, bottom=16.dp)) {
                    Column {
                        Text("❄️ CoolerControl", fontSize=22.sp, fontWeight=FontWeight.Bold, color=Tx1)
                        Text("小米冰封散热背夹", fontSize=13.sp, color=Tx2)
                    }
                }
                Column(Modifier.padding(horizontal=16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    TopBar(uiState, viewModel)

                    // Device list with auto hide/show
                    var showDevices by remember { mutableStateOf(true) }
                    LaunchedEffect(uiState.isConnected) {
                        showDevices = !uiState.isConnected
                    }
                    if (uiState.isScanning || uiState.scannedDevices.isNotEmpty()) {
                        DeviceCard(uiState, viewModel, showDevices,
                            onToggle = { showDevices = !showDevices })
                    }
                    if (uiState.isConnected) {
                        StatusCards(uiState)
                        Controls(uiState, viewModel)
                    } else {
                        Text("🔍 点击上方「扫描」查找散热器", fontSize=12.sp, color=Tx2,
                             modifier=Modifier.fillMaxWidth().padding(vertical=20.dp), textAlign=TextAlign.Center)
                    }
                    LogPanel(uiState.log)
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

// ============= Top Bar =============
@Composable
private fun TopBar(state: CoolerUiState, vm: CoolerViewModel) {
    Surface(shape=RoundedCornerShape(16.dp), color=CardBg,
            border = cardBorder) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(
                if (state.isConnected) Green else if (state.isScanning) Orange else DimW))
            Spacer(Modifier.width(10.dp))
            Text(if (state.isConnected) "已连接" else if (state.isScanning) "扫描中..." else "未连接",
                 fontSize=14.sp, fontWeight=FontWeight.Medium, color=Tx1)
            Spacer(Modifier.weight(1f))
            if (!state.isConnected) {
                Button(onClick={ vm.startScan() }, enabled=!state.isScanning,
                    shape=RoundedCornerShape(12.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=Blue),
                    contentPadding=PaddingValues(horizontal=16.dp, vertical=8.dp)
                ) { Text(if (state.isScanning) "..." else "🔍 扫描", fontSize=13.sp) }
            } else {
                Button(onClick={ vm.disconnect() }, shape=RoundedCornerShape(12.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=Red.copy(alpha=.15f)),
                    contentPadding=PaddingValues(horizontal=16.dp, vertical=8.dp)
                ) { Text("断开", fontSize=13.sp, color=Red) }
            }
        }
    }
}

// ============= Device List =============
@Composable
private fun DeviceCard(state: CoolerUiState, vm: CoolerViewModel,
                       show: Boolean, onToggle: () -> Unit) {
    Surface(shape=RoundedCornerShape(16.dp), color=CardBg,
            border = cardBorder) {
        Column(Modifier.padding(8.dp)) {
            // Toggle header
            Row(Modifier.fillMaxWidth().clickable { onToggle() }.padding(horizontal=12.dp, vertical=6.dp),
                verticalAlignment=Alignment.CenterVertically) {
                Text("📡 设备", fontSize=13.sp, color=Tx2, fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(if (show) "▲ 收起" else "▼ 展开", fontSize=12.sp, color=Tx2)
            }
            if (show) {
            state.scannedDevices.take(8).forEachIndexed { i, d ->
                val known = CoolerProtocol.KNOWN_NAME_SUBSTR.any { d.name.lowercase().contains(it) }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable { vm.connect(d.address) }.padding(12.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Text(if (known) "🔥" else "📡", fontSize=16.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.name, fontSize=14.sp, fontWeight=if (known) FontWeight.Bold else FontWeight.Normal,
                             color=if (known) Blue else Tx1)
                        Text(d.address, fontSize=10.sp, color=Tx2)
                    }
                    Text("${d.rssi} dBm", fontSize=11.sp, color=if (d.rssi > -70) Green else Tx2)
                }
                if (i < state.scannedDevices.size - 1) {
                    HorizontalDivider(color=DimW, thickness=.5.dp, modifier=Modifier.padding(horizontal=12.dp))
                }
            }
            }
            if (state.isScanning) {
                Spacer(Modifier.height(8.dp))
                Text("🔍 扫描中...", fontSize=12.sp, color=Tx2, modifier=Modifier.padding(horizontal=12.dp))
            }
        }
    }
}

// ============= Status =============
@Composable
private fun StatusCards(state: CoolerUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        StatusCard("🌡️", state.temperature?.let { "${it}°C" } ?: "--°C", "温度",
                   state.temperature?.let { if (it<10) Blue else if (it<20) Color(0xFF66BBFF) else Orange } ?: Tx2)
        StatusCard("⚡", state.powerMode, "功率", Tx1)
        StatusCard("💡", state.lightMode, "灯效", Tx1)
    }
}

@Composable
private fun RowScope.StatusCard(icon: String, value: String, label: String, valueColor: Color) {
    Surface(Modifier.weight(1f), shape=RoundedCornerShape(16.dp), color=CardBg,
            border = cardBorder) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment=Alignment.CenterHorizontally) {
            Text(icon, fontSize=18.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize= if (label=="温度") 24.sp else 16.sp,
                 fontWeight=FontWeight.Bold, color=valueColor, maxLines=1)
            Text(label, fontSize=11.sp, color=Tx2)
        }
    }
}

// ============= Controls =============
@Composable
private fun Controls(state: CoolerUiState, vm: CoolerViewModel) {
    ControlGroup("⚡ 功率切换") {
        CoolerProtocol.PowerMode.entries.forEach { mode ->
            CtrlBtn(mode.label) { vm.setPower(mode) }
        }
    }
    ControlGroup("💡 灯效切换") {
        CoolerProtocol.LightMode.entries.forEach { mode ->
            CtrlBtn(mode.label) { vm.setLight(mode) }
        }
    }
}

@Composable
private fun ControlGroup(title: String, content: @Composable RowScope.() -> Unit) {
    Surface(shape=RoundedCornerShape(16.dp), color=CardBg,
            border = cardBorder) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize=13.sp, color=Tx2, fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp), content=content)
        }
    }
}

@Composable
private fun RowScope.CtrlBtn(text: String, onClick: () -> Unit) {
    Button(onClick=onClick, shape=RoundedCornerShape(12.dp),
        colors=ButtonDefaults.buttonColors(containerColor=CardBd, contentColor=Tx1),
        contentPadding=PaddingValues(horizontal=10.dp, vertical=10.dp),
        modifier=Modifier.weight(1f)
    ) { Text(text, fontSize=12.sp, fontWeight=FontWeight.Medium, maxLines=1) }
}

// ============= Log =============
@Composable
private fun LogPanel(logs: List<String>) {
    Surface(shape=RoundedCornerShape(16.dp), color=CardBg,
            border = cardBorder) {
        Column(Modifier.padding(14.dp).heightIn(max=130.dp)) {
            Text("📋 日志", fontSize=12.sp, color=Tx2)
            Spacer(Modifier.height(6.dp))
            logs.take(6).forEach { msg ->
                Text(msg, fontSize=10.sp, color=Tx2, lineHeight=16.sp)
            }
        }
    }
}
