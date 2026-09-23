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
import kotlin.math.sqrt

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
    val isHrStreaming: Boolean = false,
    val isAccStreaming: Boolean = false,
    val latestHr: Int? = null,
    val latestRrMs: List<Int> = emptyList(),
    val hrSampleCount: Long = 0,
    val latestAcc: AccReading? = null,
    val accSampleCount: Long = 0,
    val message: String = "Not connected"
)

data class AccReading(
    val x: Int,
    val y: Int,
    val z: Int,
    val timestamp: Long,
    val magnitude: Double
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
    private var hrJob: Job? = null
    private var accJob: Job? = null

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
                        isHrStreaming = false,
                        isAccStreaming = false,
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
        stopStreams()
        runCatching { api.disconnectFromDevice(deviceId) }
            .onFailure { error ->
                _state.update {
                    it.copy(message = "Disconnect failed: ${error.message ?: "unknown error"}")
                }
            }
    }

    fun startHrStream() {
        val deviceId = _state.value.connectedDeviceId ?: run {
            _state.update { it.copy(message = "Connect to H10 before starting HR") }
            return
        }
        if (hrJob?.isActive == true) return

        _state.update {
            it.copy(
                isHrStreaming = true,
                message = "Starting heart rate stream"
            )
        }
        hrJob = scope.launch(Dispatchers.IO) {
            api.startHrStreaming(deviceId)
                .catch { error ->
                    Log.e(TAG, "HR stream failed", error)
                    _state.update {
                        it.copy(
                            isHrStreaming = false,
                            message = "HR stream failed: ${error.message ?: "unknown error"}"
                        )
                    }
                }
                .collect { hrData ->
                    val sample = hrData.samples.lastOrNull() ?: return@collect
                    _state.update {
                        it.copy(
                            latestHr = sample.hr,
                            latestRrMs = sample.rrsMs,
                            hrSampleCount = it.hrSampleCount + hrData.samples.size,
                            message = "HR ${sample.hr} bpm"
                        )
                    }
                }
        }
    }

    fun stopHrStream() {
        val deviceId = _state.value.connectedDeviceId
        hrJob?.cancel()
        hrJob = null
        if (deviceId != null) {
            scope.launch(Dispatchers.IO) {
                runCatching { api.stopHrStreaming(deviceId) }
            }
        }
        _state.update {
            it.copy(
                isHrStreaming = false,
                message = "Heart rate stream stopped"
            )
        }
    }

    fun startAccStream() {
        val deviceId = _state.value.connectedDeviceId ?: run {
            _state.update { it.copy(message = "Connect to H10 before starting ACC") }
            return
        }
        if (accJob?.isActive == true) return

        _state.update {
            it.copy(
                isAccStreaming = true,
                message = "Starting accelerometer stream"
            )
        }
        accJob = scope.launch(Dispatchers.IO) {
            runCatching {
                api.requestStreamSettings(
                    deviceId,
                    PolarBleApi.PolarDeviceDataType.ACC
                )
            }.onSuccess { settings ->
                api.startAccStreaming(deviceId, settings)
                    .catch { error ->
                        Log.e(TAG, "ACC stream failed", error)
                        _state.update {
                            it.copy(
                                isAccStreaming = false,
                                message = "ACC stream failed: ${error.message ?: "unknown error"}"
                            )
                        }
                    }
                    .collect { accData ->
                        val sample = accData.samples.lastOrNull() ?: return@collect
                        val reading = AccReading(
                            x = sample.x,
                            y = sample.y,
                            z = sample.z,
                            timestamp = sample.timeStamp,
                            magnitude = sqrt(
                                sample.x.toDouble() * sample.x +
                                    sample.y.toDouble() * sample.y +
                                    sample.z.toDouble() * sample.z
                            )
                        )
                        _state.update {
                            it.copy(
                                latestAcc = reading,
                                accSampleCount = it.accSampleCount + accData.samples.size,
                                message = "ACC samples ${it.accSampleCount + accData.samples.size}"
                            )
                        }
                    }
            }.onFailure { error ->
                Log.e(TAG, "ACC stream settings failed", error)
                _state.update {
                    it.copy(
                        isAccStreaming = false,
                        message = "ACC settings failed: ${error.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    fun stopAccStream() {
        accJob?.cancel()
        accJob = null
        _state.update {
            it.copy(
                isAccStreaming = false,
                message = "Accelerometer stream stopped"
            )
        }
    }

    fun stopStreams() {
        stopHrStream()
        stopAccStream()
    }

    fun shutdown() {
        stopScan()
        stopStreams()
        api.shutDown()
    }

    private companion object {
        const val TAG = "PolarH10Manager"
    }
}
