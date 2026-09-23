package com.example.polarh10.polar

import android.content.Context
import android.util.Log
import com.example.polarh10.db.DatabaseHelper
import com.example.polarh10.importer.SampleHistoryImporter
import com.example.polarh10.model.AccStats
import com.example.polarh10.model.AccSample
import com.example.polarh10.model.HrSample
import com.example.polarh10.model.HrStats
import com.example.polarh10.model.SessionSummary
import com.example.polarh10.processing.MovementLevel
import com.example.polarh10.processing.SensorProcessor
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
    val isHrStreaming: Boolean = false,
    val isAccStreaming: Boolean = false,
    val latestHr: Int? = null,
    val averageHr: Double = 0.0,
    val minHr: Int? = null,
    val maxHr: Int? = null,
    val latestRrMs: List<Int> = emptyList(),
    val hrSampleCount: Long = 0,
    val latestAcc: AccReading? = null,
    val smoothedMovement: Double = 0.0,
    val peakMovement: Double = 0.0,
    val movementLevel: MovementLevel = MovementLevel.UNKNOWN,
    val accSampleCount: Long = 0,
    val activeSessionId: Long? = null,
    val isSessionRecording: Boolean = false,
    val savedHrCount: Long = 0,
    val savedAccCount: Long = 0,
    val isImportingHistory: Boolean = false,
    val importedSessionId: Long? = null,
    val importMessage: String = "No sample history imported",
    val historySessions: List<HistorySessionItem> = emptyList(),
    val isLoadingHistory: Boolean = false,
    val selectedHistory: HistoryDetail? = null,
    val isLoadingHistoryDetail: Boolean = false,
    val message: String = "Not connected"
)

data class HistorySessionItem(
    val session: SessionSummary,
    val hrStats: HrStats,
    val accStats: AccStats
)

data class HistoryDetail(
    val item: HistorySessionItem,
    val hrSamples: List<HrSample>
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
    private val appContext = context.applicationContext
    private val database = DatabaseHelper(appContext)
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
    private val processor = SensorProcessor()

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
                        latestHr = null,
                        averageHr = 0.0,
                        minHr = null,
                        maxHr = null,
                        latestRrMs = emptyList(),
                        hrSampleCount = 0,
                        latestAcc = null,
                        smoothedMovement = 0.0,
                        peakMovement = 0.0,
                        movementLevel = MovementLevel.UNKNOWN,
                        accSampleCount = 0,
                        activeSessionId = null,
                        isSessionRecording = false,
                        savedHrCount = 0,
                        savedAccCount = 0,
                        message = "Connected to ${polarDeviceInfo.deviceId}"
                    )
                }
                processor.reset()
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
        refreshHistory()
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
        stopSession()
        stopStreams()
        runCatching { api.disconnectFromDevice(deviceId) }
            .onFailure { error ->
                _state.update {
                    it.copy(message = "Disconnect failed: ${error.message ?: "unknown error"}")
                }
            }
    }

    fun startSession() {
        val deviceId = _state.value.connectedDeviceId ?: run {
            _state.update { it.copy(message = "Connect to H10 before starting a session") }
            return
        }
        if (_state.value.isSessionRecording) return

        scope.launch(Dispatchers.IO) {
            runCatching { database.createSession(deviceId) }
                .onSuccess { sessionId ->
                    _state.update {
                        it.copy(
                            activeSessionId = sessionId,
                            isSessionRecording = true,
                            savedHrCount = 0,
                            savedAccCount = 0,
                            message = "Session #$sessionId started"
                        )
                    }
                    startHrStream()
                    startAccStream()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(message = "Start session failed: ${error.message ?: "unknown error"}")
                    }
                }
        }
    }

    fun stopSession() {
        val sessionId = _state.value.activeSessionId ?: return
        scope.launch(Dispatchers.IO) {
            runCatching { database.endSession(sessionId) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            activeSessionId = null,
                            isSessionRecording = false,
                            message = "Session #$sessionId saved"
                        )
                    }
                    refreshHistory()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(message = "Stop session failed: ${error.message ?: "unknown error"}")
                    }
                }
        }
    }

    fun importSampleHistory() {
        if (_state.value.isImportingHistory) return
        _state.update {
            it.copy(
                isImportingHistory = true,
                importMessage = "Importing sample history..."
            )
        }
        scope.launch(Dispatchers.IO) {
            runCatching {
                SampleHistoryImporter(appContext, database).importFromAssets()
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        isImportingHistory = false,
                        importedSessionId = result.sessionId,
                        importMessage = "Imported session #${result.sessionId}: ${result.hrCount} HR, ${result.accCount} ACC",
                        message = "Sample history imported"
                    )
                }
                refreshHistory()
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isImportingHistory = false,
                        importMessage = "Import failed: ${error.message ?: "unknown error"}",
                        message = "Sample import failed"
                    )
                }
            }
        }
    }

    fun refreshHistory() {
        _state.update { it.copy(isLoadingHistory = true) }
        scope.launch(Dispatchers.IO) {
            runCatching {
                database.getSessions().map { session ->
                    HistorySessionItem(
                        session = session,
                        hrStats = database.getHrStats(session.id),
                        accStats = database.getAccStats(session.id)
                    )
                }
            }.onSuccess { sessions ->
                _state.update {
                    it.copy(
                        historySessions = sessions,
                        isLoadingHistory = false
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isLoadingHistory = false,
                        message = "Load history failed: ${error.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    fun loadHistoryDetail(sessionId: Long) {
        _state.update { it.copy(isLoadingHistoryDetail = true) }
        scope.launch(Dispatchers.IO) {
            runCatching {
                val session = database.getSession(sessionId)
                    ?: error("Session #$sessionId not found")
                val item = HistorySessionItem(
                    session = session,
                    hrStats = database.getHrStats(sessionId),
                    accStats = database.getAccStats(sessionId)
                )
                HistoryDetail(
                    item = item,
                    hrSamples = database.getHrSamples(sessionId, Int.MAX_VALUE, 0)
                )
            }.onSuccess { detail ->
                _state.update {
                    it.copy(
                        selectedHistory = detail,
                        isLoadingHistoryDetail = false
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isLoadingHistoryDetail = false,
                        message = "Load detail failed: ${error.message ?: "unknown error"}"
                    )
                }
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
                    val summary = processor.addHeartRate(sample.hr)
                    val sessionId = _state.value.activeSessionId
                    if (sessionId != null) {
                        database.insertHr(
                            sessionId = sessionId,
                            timestamp = System.currentTimeMillis(),
                            hr = sample.hr,
                            rr = sample.rrsMs.takeIf { it.isNotEmpty() }?.joinToString(",")
                        )
                    }
                    _state.update {
                        it.copy(
                            latestHr = summary.latest,
                            averageHr = summary.average,
                            minHr = summary.min,
                            maxHr = summary.max,
                            latestRrMs = sample.rrsMs,
                            hrSampleCount = summary.sampleCount,
                            savedHrCount = if (sessionId != null) it.savedHrCount + 1 else it.savedHrCount,
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
                        val summary = processor.addAccelerometer(sample.x, sample.y, sample.z)
                        val reading = AccReading(
                            x = sample.x,
                            y = sample.y,
                            z = sample.z,
                            timestamp = sample.timeStamp,
                            magnitude = summary.latestMagnitude
                        )
                        val sessionId = _state.value.activeSessionId
                        if (sessionId != null) {
                            database.insertAccBatch(
                                accData.samples.map { accSample ->
                                    AccSample(
                                        id = 0,
                                        sessionId = sessionId,
                                        timestamp = accSample.timeStamp,
                                        x = accSample.x,
                                        y = accSample.y,
                                        z = accSample.z
                                    )
                                }
                            )
                        }
                        _state.update {
                            it.copy(
                                latestAcc = reading,
                                smoothedMovement = summary.smoothedIntensity,
                                peakMovement = summary.peakIntensity,
                                movementLevel = summary.level,
                                accSampleCount = summary.sampleCount,
                                savedAccCount = if (sessionId != null) {
                                    it.savedAccCount + accData.samples.size
                                } else {
                                    it.savedAccCount
                                },
                                message = "Movement ${summary.level.name.lowercase()}"
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
        stopSession()
        stopScan()
        stopStreams()
        api.shutDown()
        database.close()
    }

    private companion object {
        const val TAG = "PolarH10Manager"
    }
}
