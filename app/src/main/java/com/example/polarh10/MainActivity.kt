package com.example.polarh10

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.polarh10.polar.HistorySessionItem
import com.example.polarh10.model.HrSample
import com.example.polarh10.polar.PolarConnectionState
import com.example.polarh10.polar.PolarDeviceItem
import com.example.polarh10.polar.PolarH10Manager
import com.example.polarh10.processing.MovementLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PolarH10App()
        }
    }
}

private enum class AppPage {
    Dashboard,
    History
}

@Composable
private fun PolarH10App() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val manager = remember { PolarH10Manager(context, scope) }
    val state by manager.state.collectAsState()
    var page by remember { mutableStateOf(AppPage.Dashboard) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        if (granted) {
            manager.startScan()
        }
    }

    DisposableEffect(Unit) {
        onDispose { manager.shutdown() }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (page) {
                AppPage.Dashboard -> DashboardScreen(
                    state = state,
                    onScan = {
                        permissionLauncher.launch(requiredBluetoothPermissions())
                    },
                    onStopScan = manager::stopScan,
                    onConnect = manager::connect,
                    onDisconnect = manager::disconnect,
                    onStartHr = manager::startHrStream,
                    onStopHr = manager::stopHrStream,
                    onStartAcc = manager::startAccStream,
                    onStopAcc = manager::stopAccStream,
                    onStartSession = manager::startSession,
                    onStopSession = manager::stopSession,
                    onImportSampleHistory = manager::importSampleHistory,
                    onOpenHistory = {
                        manager.refreshHistory()
                        page = AppPage.History
                    }
                )

                AppPage.History -> HistoryScreen(
                    state = state,
                    onBack = { page = AppPage.Dashboard },
                    onRefreshHistory = manager::refreshHistory,
                    onSelectSession = manager::loadHistoryDetail
                )
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    state: PolarConnectionState,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onStartHr: () -> Unit,
    onStopHr: () -> Unit,
    onStartAcc: () -> Unit,
    onStopAcc: () -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onImportSampleHistory: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Polar H10 Monitor",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Connection setup",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        StatusCard(
            title = "Connection",
            primary = connectionLabel(state),
            secondary = state.message
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = if (state.isScanning) onStopScan else onScan
            ) {
                Text(if (state.isScanning) "Stop Scan" else "Scan")
            }
            Button(
                modifier = Modifier.weight(1f),
                enabled = state.isConnected,
                onClick = onDisconnect
            ) {
                Text("Disconnect")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Heart Rate",
                value = state.latestHr?.let { "$it bpm" } ?: "--"
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Movement",
                value = if (state.movementLevel == MovementLevel.UNKNOWN) {
                    "--"
                } else {
                    state.movementLevel.name.lowercase()
                }
            )
        }

        StreamControls(
            state = state,
            onStartHr = onStartHr,
            onStopHr = onStopHr,
            onStartAcc = onStartAcc,
            onStopAcc = onStopAcc
        )

        SessionCard(
            state = state,
            onStartSession = onStartSession,
            onStopSession = onStopSession
        )

        ImportHistoryCard(
            state = state,
            onImportSampleHistory = onImportSampleHistory
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenHistory
        ) {
            Text("Open History")
        }

        SensorDataCard(state = state)

        DeviceList(
            devices = state.devices,
            onConnect = onConnect
        )

        StatusCard(
            title = "Ready Features",
            primary = if (state.readyFeatures.isEmpty()) "--" else state.readyFeatures.joinToString(),
            secondary = "HR and online streaming will be used in the next step"
        )

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun HistoryScreen(
    state: PolarConnectionState,
    onBack: () -> Unit,
    onRefreshHistory: () -> Unit,
    onSelectSession: (Long) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBack) {
                Text("Back")
            }
            Text(
                text = "History",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoadingHistory,
            onClick = onRefreshHistory
        ) {
            Text(if (state.isLoadingHistory) "Loading..." else "Refresh History")
        }

        if (state.historySessions.isEmpty()) {
            StatusCard(
                title = "Saved Sessions",
                primary = "No saved session yet",
                secondary = "Import sample history or record a new session first"
            )
        } else {
            state.historySessions.forEach { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSession(item.session.id) }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HistorySessionRow(item)
                        Text(
                            text = "Tap to view chart",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        state.selectedHistory?.let { detail ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Heart Rate Chart - Session #${detail.item.session.id}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HeartRateChart(samples = detail.hrSamples)
                    SampleRow("Samples", detail.hrSamples.size.toString())
                    SampleRow(
                        "Avg / Min / Max",
                        "%.1f / %d / %d bpm".format(
                            detail.item.hrStats.avg,
                            detail.item.hrStats.min,
                            detail.item.hrStats.max
                        )
                    )
                }
            }
        } ?: if (state.isLoadingHistoryDetail) {
            StatusCard(
                title = "Chart",
                primary = "Loading...",
                secondary = "Reading session data"
            )
        } else {
            StatusCard(
                title = "Chart",
                primary = "Select a session",
                secondary = "Tap a history item to show the heart rate curve"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun HeartRateChart(samples: List<HrSample>) {
    if (samples.size < 2) {
        Text(
            text = "Not enough HR data for chart",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val values = remember(samples) { samples.map { it.hr } }
    val minHr = values.minOrNull() ?: 0
    val maxHr = values.maxOrNull() ?: 0
    val range = (maxHr - minHr).coerceAtLeast(1)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {
        val leftPadding = 8.dp.toPx()
        val rightPadding = 8.dp.toPx()
        val topPadding = 12.dp.toPx()
        val bottomPadding = 18.dp.toPx()
        val chartWidth = size.width - leftPadding - rightPadding
        val chartHeight = size.height - topPadding - bottomPadding

        repeat(4) { index ->
            val y = topPadding + chartHeight * index / 3f
            drawLine(
                color = gridColor,
                start = Offset(leftPadding, y),
                end = Offset(size.width - rightPadding, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        val points = values.mapIndexed { index, hr ->
            val x = leftPadding + chartWidth * index / (values.lastIndex).coerceAtLeast(1)
            val normalized = (hr - minHr).toFloat() / range
            val y = topPadding + chartHeight * (1f - normalized)
            Offset(x, y)
        }

        points.zipWithNext().forEach { (start, end) ->
            drawLine(
                color = lineColor,
                start = start,
                end = end,
                strokeWidth = 3.dp.toPx()
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Min $minHr bpm",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Max $maxHr bpm",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
@Composable
private fun HistorySessionRow(item: HistorySessionItem) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Session #${item.session.id}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "${formatDate(item.session.startTime)} - ${durationText(item.session.startTime, item.session.endTime)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SampleRow("Device", item.session.deviceId)
        SampleRow(
            "HR avg / min / max",
            if (item.hrStats.count == 0L) {
                "--"
            } else {
                "%.1f / %d / %d bpm".format(
                    item.hrStats.avg,
                    item.hrStats.min,
                    item.hrStats.max
                )
            }
        )
        SampleRow("HR samples", item.session.hrCount.toString())
        SampleRow("ACC samples", item.session.accCount.toString())
        SampleRow(
            "ACC avg / peak",
            if (item.accStats.count == 0L) {
                "--"
            } else {
                "%.0f / %.0f mG".format(
                    item.accStats.avgMagnitude,
                    item.accStats.peakMagnitude
                )
            }
        )
    }
}

@Composable
private fun ImportHistoryCard(
    state: PolarConnectionState,
    onImportSampleHistory: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Sample History",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = state.importMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isImportingHistory,
                onClick = onImportSampleHistory
            ) {
                Text(if (state.isImportingHistory) "Importing..." else "Import Sample History")
            }
        }
    }
}

@Composable
private fun SessionCard(
    state: PolarConnectionState,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Session Recording",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            SampleRow(
                "Status",
                if (state.isSessionRecording) "Recording #${state.activeSessionId}" else "Not recording"
            )
            SampleRow("Saved HR samples", state.savedHrCount.toString())
            SampleRow("Saved ACC samples", state.savedAccCount.toString())
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = state.isConnected,
                onClick = if (state.isSessionRecording) onStopSession else onStartSession
            ) {
                Text(if (state.isSessionRecording) "Stop Session" else "Start Session")
            }
        }
    }
}

@Composable
private fun StreamControls(
    state: PolarConnectionState,
    onStartHr: () -> Unit,
    onStopHr: () -> Unit,
    onStartAcc: () -> Unit,
    onStopAcc: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            modifier = Modifier.weight(1f),
            enabled = state.isConnected,
            onClick = if (state.isHrStreaming) onStopHr else onStartHr
        ) {
            Text(if (state.isHrStreaming) "Stop HR" else "Start HR")
        }
        Button(
            modifier = Modifier.weight(1f),
            enabled = state.isConnected,
            onClick = if (state.isAccStreaming) onStopAcc else onStartAcc
        ) {
            Text(if (state.isAccStreaming) "Stop ACC" else "Start ACC")
        }
    }
}

@Composable
private fun SensorDataCard(state: PolarConnectionState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Live Samples",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            SampleRow("HR stream", if (state.isHrStreaming) "Running" else "Stopped")
            SampleRow("HR samples", state.hrSampleCount.toString())
            SampleRow("Avg / Min / Max HR", hrStatsText(state))
            SampleRow(
                "RR intervals",
                if (state.latestRrMs.isEmpty()) "--" else state.latestRrMs.joinToString(" ms, ", postfix = " ms")
            )
            HorizontalDivider()
            SampleRow("ACC stream", if (state.isAccStreaming) "Running" else "Stopped")
            SampleRow("ACC samples", state.accSampleCount.toString())
            SampleRow("Raw magnitude", state.latestAcc?.let { "%.0f mG".format(it.magnitude) } ?: "--")
            SampleRow("Smoothed movement", "%.0f mG".format(state.smoothedMovement))
            SampleRow("Peak movement", "%.0f mG".format(state.peakMovement))
            SampleRow(
                "ACC x/y/z",
                state.latestAcc?.let { "${it.x}, ${it.y}, ${it.z} mG" } ?: "--"
            )
        }
    }
}

@Composable
private fun SampleRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DeviceList(
    devices: List<PolarDeviceItem>,
    onConnect: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Found Devices",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            if (devices.isEmpty()) {
                Text(
                    text = "No device found yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(180.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(devices) { device ->
                        DeviceRow(
                            device = device,
                            onClick = { onConnect(device.deviceId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: PolarDeviceItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = device.deviceId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "${device.rssi} dBm",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun StatusCard(
    title: String,
    primary: String,
    secondary: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = secondary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Card(modifier = modifier.height(112.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun connectionLabel(state: PolarConnectionState): String =
    when {
        state.isConnected -> "Connected"
        state.isConnecting -> "Connecting"
        state.isScanning -> "Scanning"
        else -> "Not connected"
    }

private fun requiredBluetoothPermissions(): Array<String> =
    arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )

private fun hrStatsText(state: PolarConnectionState): String =
    if (state.hrSampleCount == 0L) {
        "--"
    } else {
        "%.1f / %d / %d bpm".format(
            state.averageHr,
            state.minHr ?: 0,
            state.maxHr ?: 0
        )
    }

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun durationText(startTime: Long, endTime: Long?): String {
    if (endTime == null) return "running"
    val seconds = ((endTime - startTime) / 1000).coerceAtLeast(0)
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (minutes > 0) {
        "${minutes}m ${remainingSeconds}s"
    } else {
        "${remainingSeconds}s"
    }
}
