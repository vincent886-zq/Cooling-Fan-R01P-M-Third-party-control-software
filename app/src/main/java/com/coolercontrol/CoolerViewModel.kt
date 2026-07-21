package com.coolercontrol

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CoolerViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = BleManager(application)
    private val appContext = application.applicationContext

    private val _uiState = MutableStateFlow(CoolerUiState())
    val uiState: StateFlow<CoolerUiState> = _uiState.asStateFlow()

    data class CoolerUiState(
        val temperature: Int? = null,
        val powerMode: String = "--",
        val lightMode: String = "--",
        val isConnected: Boolean = false,
        val isScanning: Boolean = false,
        val scannedDevices: List<ScannedDevice> = emptyList(),
        val log: List<String> = listOf("就绪 — 点击扫描"),
        val showNotif: Boolean = false
    )

    data class ScannedDevice(
        val name: String, val address: String, val rssi: Int
    )

    init {
        CoolerGlobal.bleManager = bleManager

        bleManager.onScanResult = { name, addr, rssi ->
            viewModelScope.launch {
                val current = _uiState.value.scannedDevices.toMutableList()
                val existing = current.indexOfFirst { it.address == addr }
                if (existing >= 0) {
                    if (rssi > current[existing].rssi)
                        current[existing] = ScannedDevice(name, addr, rssi)
                } else {
                    current.add(ScannedDevice(name, addr, rssi))
                }
                _uiState.value = _uiState.value.copy(
                    scannedDevices = current.sortedByDescending { it.rssi }
                )
            }
        }
        bleManager.onScanFinished = {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isScanning = false)
                addLog("扫描完成: ${_uiState.value.scannedDevices.size} 个设备")
            }
        }
        bleManager.onConnected = { connected ->
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isConnected = connected)
                if (_uiState.value.showNotif) CoolerService.refreshNotif(appContext)
            }
        }
        bleManager.onStatusUpdate = { status ->
            viewModelScope.launch {
                val powerLabel = status.powerMode?.label ?: _uiState.value.powerMode
                val lightLabel = status.lightMode?.label ?: _uiState.value.lightMode
                val temp = status.temperature ?: _uiState.value.temperature
                CoolerGlobal.currentTempString = temp?.let { "${it}°C" } ?: CoolerGlobal.currentTempString
                CoolerGlobal.currentPowerLabel = powerLabel
                CoolerGlobal.currentLightLabel = lightLabel
                _uiState.value = _uiState.value.copy(
                    temperature = temp,
                    powerMode = powerLabel,
                    lightMode = lightLabel
                )
                if (_uiState.value.showNotif) CoolerService.refreshNotif(appContext)
            }
        }
        bleManager.onLog = { msg ->
            viewModelScope.launch { addLog(msg) }
        }
    }

    fun startScan() {
        _uiState.value = _uiState.value.copy(isScanning = true, scannedDevices = emptyList())
        addLog("正在扫描...")
        bleManager.startScan()
    }

    fun connect(address: String) {
        _uiState.value = _uiState.value.copy(isConnected = false)
        addLog("连接中...")
        bleManager.connect(address)
    }

    fun disconnect() {
        bleManager.disconnect()
        _uiState.value = _uiState.value.copy(isConnected = false)
    }

    fun setPower(mode: CoolerProtocol.PowerMode) {
        bleManager.setPowerMode(mode)
    }

    fun setLight(mode: CoolerProtocol.LightMode) {
        bleManager.setLightMode(mode)
    }

    fun toggleNotif() {
        val newVal = !_uiState.value.showNotif
        _uiState.value = _uiState.value.copy(showNotif = newVal)
        val svc = Intent(appContext, CoolerService::class.java)
        if (newVal) {
            svc.action = "START"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                appContext.startForegroundService(svc)
            else
                appContext.startService(svc)
        } else {
            svc.action = "STOP"
            appContext.startService(svc)
        }
    }

    private fun addLog(msg: String) {
        val current = _uiState.value.log.toMutableList()
        current.add(0, msg)
        if (current.size > 50) current.removeAt(current.lastIndex)
        _uiState.value = _uiState.value.copy(log = current)
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.disconnect()
    }
}
