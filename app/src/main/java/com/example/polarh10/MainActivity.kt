package com.example.polarh10

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.polarh10.polar.PolarConnectionState
import com.example.polarh10.polar.PolarDeviceItem
import com.example.polarh10.polar.PolarH10Manager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PolarH10App()
        }
    }
}

@Composable
private fun PolarH10App() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val manager = remember { PolarH10Manager(context, scope) }
    val state by manager.state.collectAsState()
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
            DashboardScreen(
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
                onStopAcc = manager::stopAccStream
            )
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
    onStopAcc: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
                value = state.latestAcc?.let { "%.0f mG".format(it.magnitude) } ?: "--"
            )
        }

        StreamControls(
            state = state,
            onStartHr = onStartHr,
            onStopHr = onStopHr,
            onStartAcc = onStartAcc,
            onStopAcc = onStopAcc
        )

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

        Spacer(modifier = Modifier.weight(1f))
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
            SampleRow(
                "RR intervals",
                if (state.latestRrMs.isEmpty()) "--" else state.latestRrMs.joinToString(" ms, ", postfix = " ms")
            )
            HorizontalDivider()
            SampleRow("ACC stream", if (state.isAccStreaming) "Running" else "Stopped")
            SampleRow("ACC samples", state.accSampleCount.toString())
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
