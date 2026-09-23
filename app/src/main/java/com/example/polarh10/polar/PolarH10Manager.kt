package com.example.polarh10.polar

import android.content.Context
import android.util.Log
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHealthThermometerData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PolarDeviceItem(
    val deviceId: String,
    val name: String,
    val rssi: Int
)

data class PolarConnectionState(
    val bluetoothEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val connectedDeviceId: String? = null,
    val batteryLevel: Int? = null,
    val readyFeatures: Set<String> = emptySet(),
    val devices: List<PolarDeviceItem> = emptyList(),
    val message: String = "Not connected"
)

class PolarH10Manager(
    context: Context,
    private val scope: CoroutineScope
) {
    private val api: PolarBleApi = PolarBleApiDefaultImpl.defaultImplementation(
        context.applicationContext,
        setOf(
            PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
            PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING
        )
    )

    private val _state = MutableStateFlow(PolarConnectionState())
    val state: StateFlow<PolarConnectionState> = _state.asStateFlow()

    private var scanJob: Job? = null

    init {
        api.setAutomaticReconnection(true)
        api.setApiCallback(object : PolarBleApiCallback() {
            override fun blePowerStateChanged(powered: Boolean) {
                _state.update {
                    it.copy(
                        bluetoothEnabled = powered,
                        message = if (powered) "Bluetooth is on" else "Bluetooth is off"
                    )
                }
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                _state.update {
                    it.copy(
                        isConnecting = true,
                        connectedDeviceId = polarDeviceInfo.deviceId,
                        message = "Connecting to ${polarDeviceInfo.deviceId}"
                    )
                }
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                _state.update {
                    it.copy(
                        isConnecting = false,
                        isConnected = true,
                        connectedDeviceId = polarDeviceInfo.deviceId,
                        message = "Connected to ${polarDeviceInfo.deviceId}"
                    )
                }
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                _state.update {
                    it.copy(
                        isConnecting = false,
                        isConnected = false,
                        connectedDeviceId = null,
                        batteryLevel = null,
                        readyFeatures = emptySet(),
                        message = "Disconnected from ${polarDeviceInfo.deviceId}"
                    )
                }
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                _state.update {
                    it.copy(
                        batteryLevel = level,
                        message = "Battery $level%"
                    )
                }
            }

            override fun bleSdkFeatureReady(
                identifier: String,
                feature: PolarBleApi.PolarBleSdkFeature
            ) {
                _state.update {
                    it.copy(
                        readyFeatures = it.readyFeatures + feature.name,
                        message = "Ready: ${feature.name}"
                    )
                }
            }

            override fun disInformationReceived(identifier: String, disInfo: DisInfo) = Unit

            override fun htsNotificationReceived(
                identifier: String,
                data: PolarHealthThermometerData
            ) = Unit
        })
    }

    fun startScan() {
        if (scanJob?.isActive == true) return
        _state.update {
            it.copy(
                isScanning = true,
                devices = emptyList(),
                message = "Scanning for Polar devices..."
            )
        }
        scanJob = scope.launch(Dispatchers.IO) {
            api.searchForDevice()
                .catch { error ->
                    Log.e(TAG, "Scan failed", error)
                    _state.update {
                        it.copy(
                            isScanning = false,
                            message = "Scan failed: ${error.message ?: "unknown error"}"
                        )
                    }
                }
                .collect { device ->
                    val item = PolarDeviceItem(
                        deviceId = device.deviceId,
                        name = device.name.ifBlank { "Polar device" },
                        rssi = device.rssi
                    )
                    _state.update { current ->
                        val withoutDuplicate = current.devices.filterNot {
                            it.deviceId == item.deviceId
                        }
                        current.copy(
                            devices = (withoutDuplicate + item).sortedByDescending { it.rssi },
                            message = "Found ${item.name}"
                        )
                    }
                }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _state.update {
            it.copy(
                isScanning = false,
                message = "Scan stopped"
            )
        }
    }

    fun connect(deviceId: String) {
        stopScan()
        _state.update {
            it.copy(
                isConnecting = true,
                connectedDeviceId = deviceId,
                message = "Connecting to $deviceId"
            )
        }
        runCatching { api.connectToDevice(deviceId) }
            .onFailure { error ->
                _state.update {
                    it.copy(
                        isConnecting = false,
                        message = "Connect failed: ${error.message ?: "unknown error"}"
                    )
                }
            }
    }

    fun disconnect() {
        val deviceId = _state.value.connectedDeviceId ?: return
        runCatching { api.disconnectFromDevice(deviceId) }
            .onFailure { error ->
                _state.update {
                    it.copy(message = "Disconnect failed: ${error.message ?: "unknown error"}")
                }
            }
    }

    fun shutdown() {
        stopScan()
        api.shutDown()
    }

    private companion object {
        const val TAG = "PolarH10Manager"
    }
}
