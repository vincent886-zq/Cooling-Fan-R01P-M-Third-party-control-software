"""
小米冰封散热背夹 PC 控制 GUI
基于 PyQt5，依赖 xiaomi_cooler_control_v2.0.py 的核心控制器
"""

import sys
import os
import asyncio
import struct
import threading
import traceback
from enum import IntEnum

from PyQt5.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QLabel, QPushButton, QComboBox, QFrame, QGroupBox,
    QStatusBar, QMessageBox, QProgressBar, QSlider,
)
from PyQt5.QtCore import Qt, pyqtSignal, QObject
from PyQt5.QtGui import QFont, QColor, QPalette

# ---------- Import controller ----------
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cooler_ctrl import (
    XiaomiCoolerController, CoolerMode, LightMode,
    fmt_hex, asyncio
)

# ============================================================
# Async runner - runs asyncio in a separate thread
# ============================================================

class AsyncRunner(QObject):
    """Bridge between PyQt GUI thread and asyncio event loop."""
    log_signal = pyqtSignal(str)
    status_signal = pyqtSignal(str)
    failed_signal = pyqtSignal(str)
    temp_signal = pyqtSignal(int)
    power_signal = pyqtSignal(int)
    light_signal = pyqtSignal(int)
    connected_signal = pyqtSignal(bool)
    devices_signal = pyqtSignal(list)  # list of {name, address, rssi}

    def __init__(self):
        super().__init__()
        self.loop = asyncio.new_event_loop()
        self.ctrl = XiaomiCoolerController()
        self._running = False

    def start(self):
        """Start the event loop thread."""
        self._running = True
        t = threading.Thread(target=self._run_loop, daemon=True)
        t.start()

    def _run_loop(self):
        asyncio.set_event_loop(self.loop)
        self.loop.run_forever()

    def stop(self):
        self._running = False
        asyncio.run_coroutine_threadsafe(self._cleanup(), self.loop)
        self.loop.call_soon_threadsafe(self.loop.stop)

    async def _cleanup(self):
        await self.ctrl.disconnect()

    def _log(self, msg):
        self.log_signal.emit(str(msg))

    def scan_and_connect(self):
        """Full scan + connect flow (async)."""
        asyncio.run_coroutine_threadsafe(self._do_connect(), self.loop)

    def connect_by_mac(self, mac):
        """Connect by MAC address."""
        asyncio.run_coroutine_threadsafe(self._do_connect_mac(mac), self.loop)

    def scan_only(self, timeout: float = 8.0):
        """Scan only - populate dropdown via devices_signal."""
        asyncio.run_coroutine_threadsafe(self._do_scan_only(timeout), self.loop)

    async def _do_scan_only(self, timeout: float):
        self.status_signal.emit("正在扫描...")
        try:
            devices = await self.ctrl.scan_with_names(timeout=timeout)
        except Exception as e:
            self.failed_signal.emit(f"扫描失败: {e}")
            return
        if not devices:
            self.failed_signal.emit("没找到任何 BLE 设备")
            return
        self.devices_signal.emit(devices)
        self.status_signal.emit(f"找到 {len(devices)} 个设备")

    async def _do_connect(self):
        device = await self.ctrl.find_device(timeout=8)
        if not device:
            self.failed_signal.emit("Device not found")
            return
        self.status_signal.emit("Connecting...")
        if not await self.ctrl.connect(device):
            self.failed_signal.emit("Connection failed")
            return
        await self.ctrl.discover_services()
        await self.ctrl.do_auth()
        self.connected_signal.emit(True)
        self.status_signal.emit("Connected")
        self._start_watch()

    async def _do_connect_mac(self, mac):
        self.status_signal.emit(f"Connecting to {mac}...")
        self.ctrl._save_mac(mac)
        device = await self.ctrl.find_device(timeout=8)
        if not device:
            self.failed_signal.emit("Device not found")
            return
        if not await self.ctrl.connect(device):
            self.failed_signal.emit("Connection failed")
            return
        await self.ctrl.discover_services()
        await self.ctrl.do_auth()
        self.connected_signal.emit(True)
        self.status_signal.emit("Connected")
        self._start_watch()

    async def _do_disconnect(self):
        await self.ctrl.disconnect()
        self.connected_signal.emit(False)
        self.status_signal.emit("Disconnected")

    def disconnect(self):
        asyncio.run_coroutine_threadsafe(self._do_disconnect(), self.loop)

    def set_power(self, mode: CoolerMode):
        asyncio.run_coroutine_threadsafe(self.ctrl.set_mode(mode), self.loop)

    def set_light(self, mode: LightMode):
        asyncio.run_coroutine_threadsafe(self.ctrl.set_light_mode(mode), self.loop)

    def _start_watch(self):
        """Start periodic status polling via asyncio task."""
        asyncio.run_coroutine_threadsafe(self._watch_loop(), self.loop)

    def _poll_status(self):
        """Read current status and emit signals (kept for compat)."""
        asyncio.run_coroutine_threadsafe(self._do_poll(), self.loop)

    async def _watch_loop(self):
        """Async loop that polls status every 1s."""
        while True:
            try:
                await self._do_poll()
            except Exception as e:
                self.log_signal.emit(f"Poll error: {e}")
            await asyncio.sleep(1)

    async def _do_poll(self):
        rsp = None
        try:
            rsp = await self.ctrl._wait_notif(timeout=1.0)
        except Exception:
            pass
        if not rsp or b'\x81\xf4' not in rsp[:6]:
            return
        temp = power = light = None
        idx = rsp.find(b'\x00\x3f')
        if idx != -1 and idx + 2 < len(rsp):
            t = rsp[idx + 2]
            temp = t if t < 128 else t - 256
        idx = rsp.find(b'\x00\x40')
        if idx != -1 and idx + 2 < len(rsp):
            power = rsp[idx + 2]
        # 2-byte LE light parse (F4 verified 2026-07-19)
        idx = rsp.find(b'\x00\x42')
        if idx != -1 and idx + 3 < len(rsp):
            light_raw = rsp[idx + 2] | (rsp[idx + 3] << 8)
            from cooler_ctrl import F4_LIGHT_BYTES
            lm = F4_LIGHT_BYTES.get(light_raw)
            if lm is not None:
                light = int(lm)
        if temp is not None:
            self.temp_signal.emit(temp)
        if power is not None:
            self.power_signal.emit(power)
        if light is not None:
            self.light_signal.emit(light)


# ============================================================
# GUI Main Window
# ============================================================

class CoolerGUI(QMainWindow):
    def __init__(self):
        super().__init__()
        self.async_runner = AsyncRunner()
        self._init_ui()
        self._connect_signals()
        self.async_runner.start()

    def _init_ui(self):
        self.setWindowTitle("小米冰封散热背夹  PC 控制")
        self.setMinimumSize(680, 480)
        # No max limit — let the user resize freely

        central = QWidget()
        self.setCentralWidget(central)
        layout = QVBoxLayout(central)
        layout.setSpacing(10)
        layout.setContentsMargins(15, 15, 15, 15)

        # ---- Top bar: connection controls ----
        top_bar = QHBoxLayout()
        self.mac_combo = QComboBox()
        self.mac_combo.setEditable(True)
        self.mac_combo.setPlaceholderText("输入 MAC 或选择设备")
        self.mac_combo.setMinimumWidth(360)
        self.mac_combo.setToolTip("显示 设备名 (MAC)  ·  RSSI")
        self._load_saved_macs()

        self.btn_connect = QPushButton("🔗 连接")
        self.btn_connect.clicked.connect(self._on_connect_clicked)
        self.btn_scan = QPushButton("🔍 扫描")
        self.btn_scan.setToolTip("扫描 BLE 设备并填充到下拉框")
        self.btn_scan.clicked.connect(self._on_scan_clicked)
        self.btn_disconnect = QPushButton("❌ 断开")
        self.btn_disconnect.clicked.connect(self._on_disconnect_clicked)
        self.btn_disconnect.setEnabled(False)

        top_bar.addWidget(QLabel("连接:"))
        top_bar.addWidget(self.mac_combo)
        top_bar.addWidget(self.btn_connect)
        top_bar.addWidget(self.btn_scan)
        top_bar.addWidget(self.btn_disconnect)
        layout.addLayout(top_bar)

        # ---- Status display row ----
        status_row = QHBoxLayout()

        # Temperature display
        temp_frame = QFrame()
        temp_frame.setFrameStyle(QFrame.StyledPanel | QFrame.Raised)
        temp_frame.setMinimumHeight(130)
        temp_layout = QVBoxLayout(temp_frame)
        temp_layout.setAlignment(Qt.AlignCenter)
        self.temp_label = QLabel("--°C")
        self.temp_label.setFont(QFont("Arial", 44, QFont.Bold))
        self.temp_label.setAlignment(Qt.AlignCenter)
        self.temp_label.setStyleSheet("color: #888888;")
        temp_layout.addWidget(self.temp_label)
        temp_layout.addWidget(QLabel("当前温度", alignment=Qt.AlignCenter))
        status_row.addWidget(temp_frame)

        # Power mode display
        power_frame = QFrame()
        power_frame.setFrameStyle(QFrame.StyledPanel | QFrame.Raised)
        power_frame.setMinimumHeight(130)
        power_layout = QVBoxLayout(power_frame)
        power_layout.setAlignment(Qt.AlignCenter)
        self.power_label = QLabel("--")
        self.power_label.setFont(QFont("Arial", 32, QFont.Bold))
        self.power_label.setAlignment(Qt.AlignCenter)
        power_layout.addWidget(self.power_label)
        power_layout.addWidget(QLabel("当前功率模式", alignment=Qt.AlignCenter))
        status_row.addWidget(power_frame)

        layout.addLayout(status_row)

        # ---- Control row ----
        control_row = QHBoxLayout()

        # Power control
        power_group = QGroupBox("功率切换")
        power_group_layout = QVBoxLayout(power_group)
        self.power_combo = QComboBox()
        self.power_combo.addItems(["静音 (Low)", "智能 (Mid)", "极寒 (High)"])
        self.power_combo.currentIndexChanged.connect(self._on_power_changed)
        power_group_layout.addWidget(self.power_combo)
        control_row.addWidget(power_group)

        # Light control
        light_group = QGroupBox("灯效切换")
        light_group_layout = QVBoxLayout(light_group)
        self.light_combo = QComboBox()
        self.light_combo.addItems(["彩虹", "呼吸", "流动", "常亮", "关灯"])
        self.light_combo.currentIndexChanged.connect(self._on_light_changed)
        light_group_layout.addWidget(self.light_combo)
        control_row.addWidget(light_group)

        # Light on/off
        light_onoff_group = QGroupBox("灯效开关")
        light_onoff_layout = QVBoxLayout(light_onoff_group)
        self.btn_light_on = QPushButton("💡 开灯")
        self.btn_light_on.clicked.connect(lambda: self._set_light(LightMode.RAINBOW))
        self.btn_light_off = QPushButton("🔴 关灯")
        self.btn_light_off.clicked.connect(lambda: self._set_light(LightMode.OFF))
        light_onoff_layout.addWidget(self.btn_light_on)
        light_onoff_layout.addWidget(self.btn_light_off)
        control_row.addWidget(light_onoff_group)

        layout.addLayout(control_row)

        # ---- Status bar ----
        self.status_bar = QStatusBar()
        self.setStatusBar(self.status_bar)
        self.status_bar.showMessage("就绪 - 请连接散热器")

    def _connect_signals(self):
        r = self.async_runner
        r.log_signal.connect(lambda m: self.status_bar.showMessage(m, 5000))
        r.status_signal.connect(lambda m: self.status_bar.showMessage(m))
        r.failed_signal.connect(self._on_connection_failed)
        r.temp_signal.connect(self._update_temp)
        r.power_signal.connect(self._update_power)
        r.light_signal.connect(self._update_light)
        r.connected_signal.connect(self._on_connection_state)
        r.devices_signal.connect(self._populate_devices)

    def _load_saved_macs(self):
        """Show saved MAC as a single placeholder line (no auto-resolve)."""
        try:
            with open(self.async_runner.ctrl._mac_file) as f:
                mac = f.read().strip()
                if mac:
                    # Show "已保存" prefix; user can re-scan to confirm name
                    self.mac_combo.addItem(f"(已保存) {mac}")
                    self.mac_combo.setItemData(0, mac)
        except Exception:
            pass

    def _populate_devices(self, devices: list[dict]):
        """Fill dropdown with scanned devices: 'Name (MAC)  · RSSI'."""
        self.mac_combo.blockSignals(True)
        self.mac_combo.clear()
        # Add an instruction line
        self.mac_combo.addItem("─── 选择一个设备 ───", "")
        self.mac_combo.setItemData(0, "")
        first_known_idx = -1
        for d in devices:
            label = f"{d['name']}  ({d['address']})  · {d['rssi']} dBm"
            self.mac_combo.addItem(label, d['address'])
            # Highlight known cooler names with bold via font (PyQt5: setItemData role)
            if first_known_idx < 0:
                from PyQt5.QtGui import QFont as _QF
                is_known = any(k.lower() in d['name'].lower()
                               for k in self.async_runner.ctrl.KNOWN_NAMES)
                if is_known:
                    first_known_idx = self.mac_combo.count() - 1
        self.mac_combo.blockSignals(False)
        # Auto-select the first known cooler device (don't auto-connect)
        if first_known_idx > 0:
            self.mac_combo.setCurrentIndex(first_known_idx)
            self.status_bar.showMessage(
                f"✓ 已选中: {devices[first_known_idx - 1]['name']}  ·  点击 [连接] 确认",
                5000)
        else:
            self.status_bar.showMessage(
                f"找到 {len(devices)} 个设备  ·  请在下拉框中选择", 5000)
        # Re-enable scan button
        self.btn_scan.setEnabled(True)
        self.btn_scan.setText("🔍 扫描")
        self.btn_connect.setEnabled(True)

    def _on_connect_clicked(self):
        # Prefer the userData (real MAC) over displayed text
        mac = self.mac_combo.currentData()
        if not mac:
            # Fallback: try parsing displayed text
            text = self.mac_combo.currentText().strip()
            if not text or "───" in text or "(已保存)" in text:
                # For "(已保存) MAC" the text contains the MAC after the label
                if "(已保存)" in text:
                    mac = text.split(")")[-1].strip()
                else:
                    QMessageBox.warning(self, "提示", "请先扫描或在下拉框中选择一个设备")
                    return
            else:
                # Try to extract MAC from "Name (MAC)  · RSSI" format
                import re
                m = re.search(r"([0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2})", text)
                mac = m.group(1) if m else text
        if not mac:
            QMessageBox.warning(self, "提示", "无法识别 MAC 地址，请重新扫描")
            return
        self.btn_connect.setEnabled(False)
        self.btn_scan.setEnabled(False)
        self.async_runner.connect_by_mac(mac)

    def _on_scan_clicked(self):
        self.btn_connect.setEnabled(False)
        self.btn_scan.setEnabled(False)
        self.btn_scan.setText("扫描中...")
        self.status_bar.showMessage("正在扫描 BLE 设备 (8s)...")
        self.async_runner.scan_only(timeout=8)

    def _on_disconnect_clicked(self):
        self.async_runner.disconnect()

    def _on_connection_failed(self, reason):
        """Re-enable buttons after a failed connect/scan."""
        self.btn_connect.setEnabled(True)
        self.btn_scan.setEnabled(True)
        self.btn_scan.setText("🔍 扫描")
        self.status_bar.showMessage(f"✗ {reason} - 请重试", 5000)

    def _on_connection_state(self, connected):
        self.btn_connect.setEnabled(not connected)
        self.btn_scan.setEnabled(not connected)
        self.btn_disconnect.setEnabled(connected)
        self.btn_scan.setText("🔍 扫描")
        if connected:
            self.mac_combo.setEnabled(False)
        else:
            self.mac_combo.setEnabled(True)
            self.power_label.setText("--")
            self.temp_label.setText("--°C")
            self.temp_label.setStyleSheet("color: #888888;")

    def _on_power_changed(self, idx):
        if idx < 0:
            return
        # 0=静音(LOW), 1=智能(MID), 2=极寒(HIGH)
        mode_map = [CoolerMode.LOW, CoolerMode.MID, CoolerMode.HIGH]
        self.async_runner.set_power(mode_map[idx])

    def _on_light_changed(self, idx):
        if idx < 0:
            return
        # 0=彩虹, 1=呼吸, 2=流动, 3=常亮, 4=关灯
        mode_map = [LightMode.RAINBOW, LightMode.BREATH, LightMode.FLOWING,
                    LightMode.BRIGHT, LightMode.OFF]
        self.async_runner.set_light(mode_map[idx])

    def _set_light(self, mode):
        self.async_runner.set_light(mode)

    def _update_light(self, mode_idx: int):
        """Sync light combo box from F4 notification (accurate)."""
        if mode_idx < 0 or mode_idx > 4:
            return
        self.light_combo.blockSignals(True)
        self.light_combo.setCurrentIndex(mode_idx)
        self.light_combo.blockSignals(False)

    def _update_temp(self, temp):
        self.temp_label.setText(f"{temp}°C")
        # Color: below 10°C = blue, 10-20 = light blue, 20-30 = warm
        if temp < 10:
            color = "#0088ff"  # blue
        elif temp < 20:
            color = "#66bbff"  # light blue
        else:
            color = "#ff8844"  # warm orange
        self.temp_label.setStyleSheet(f"color: {color};")

    def _update_power(self, power_val):
        p = power_val if power_val < 3 else 0
        names = ["静音 (Low)", "智能 (Mid)", "极寒 (High)"]
        f4_to_combo = {0: 1, 1: 0, 2: 2}
        self.power_label.setText(names[f4_to_combo[p]])
        # Block signals so updating combo doesn't trigger F2 write
        self.power_combo.blockSignals(True)
        self.power_combo.setCurrentIndex(f4_to_combo[p])
        self.power_combo.blockSignals(False)

    def closeEvent(self, event):
        self.async_runner.stop()
        event.accept()


# ============================================================
# Entry point
# ============================================================

def main():
    app = QApplication(sys.argv)
    app.setStyle("Fusion")
    window = CoolerGUI()
    window.show()
    sys.exit(app.exec_())

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        with open("crash.log", "w", encoding="utf-8") as f:
            f.write(f"Error: {e}\n{traceback.format_exc()}")
        # Try to show error dialog
        try:
            from PyQt5.QtWidgets import QMessageBox
            app = QApplication(sys.argv)
            QMessageBox.critical(None, "启动失败", f"{e}\n\n详情见 crash.log")
        except Exception:
            input(f"启动失败: {e}\n按回车退出...")
