package com.example.polarh10.ui.dashboard

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.polarh10.polar.PolarConnectionState
import com.example.polarh10.polar.PolarDeviceItem
import com.example.polarh10.processing.MovementLevel
import com.example.polarh10.ui.components.HeartRateValueChart
import com.example.polarh10.ui.components.EcgValueChart
import com.example.polarh10.ui.components.MetricCard
import com.example.polarh10.ui.components.RrValueChart
import com.example.polarh10.ui.components.SampleRow
import com.example.polarh10.ui.components.StatusCard
import com.example.polarh10.ui.components.StatusPill
import com.example.polarh10.ui.format.averageIntText
import com.example.polarh10.ui.format.connectionLabel
import com.example.polarh10.ui.format.historyHrText
import com.example.polarh10.ui.format.hrStatsText
import com.example.polarh10.ui.format.liveCaloriesText
import com.example.polarh10.ui.format.liveDurationText
import com.example.polarh10.ui.history.HistoryMetricGrid
import com.example.polarh10.ui.theme.Danger
import com.example.polarh10.ui.theme.Good
import com.example.polarh10.ui.theme.Panel
import com.example.polarh10.ui.theme.PanelStrong
import com.example.polarh10.ui.theme.Stroke
import com.example.polarh10.ui.theme.heartRateColor
import com.example.polarh10.ui.theme.movementColor

@Composable
fun DashboardScreen(
    state: PolarConnectionState,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onStartHr: () -> Unit,
    onStopHr: () -> Unit,
    onStartAcc: () -> Unit,
    onStopAcc: () -> Unit,
    onStartEcg: () -> Unit,
    onStopEcg: () -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Polar H10",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Workout monitor",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(
                    text = connectionLabel(state),
                    color = if (state.isConnected) Good else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PanelStrong),
                    onClick = if (state.isScanning) onStopScan else onScan
                ) {
                    Text(if (state.isScanning) "Stop Scan" else "Scan")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = state.isConnected,
                    colors = ButtonDefaults.buttonColors(containerColor = PanelStrong),
                    onClick = onDisconnect
                ) {
                    Text("Disconnect")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HeartRateHeroCard(
                    modifier = Modifier.weight(1f),
                    state = state
                )
                MovementHeroCard(
                    modifier = Modifier.weight(1f),
                    state = state
                )
            }

            WorkoutSummaryCard(state = state)

            LiveHeartRateCard(state = state)

            LiveRrCard(state = state)

            StreamControls(
                state = state,
                onStartHr = onStartHr,
                onStopHr = onStopHr,
                onStartAcc = onStartAcc,
                onStopAcc = onStopAcc,
                onStartEcg = onStartEcg,
                onStopEcg = onStopEcg
            )

            LiveEcgCard(state = state)

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = state.isConnected,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isSessionRecording) Danger else Good,
                    contentColor = Color.White
                ),
                onClick = if (state.isSessionRecording) onStopSession else onStartSession
            ) {
                Text(if (state.isSessionRecording) "Stop Session" else "Start Session")
            }
            Button(
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = Color.White
                ),
                onClick = onOpenHistory
            ) {
                Text("Open History")
            }
        }
    }
}

@Composable
fun HeartRateHeroCard(
    modifier: Modifier = Modifier,
    state: PolarConnectionState
) {
    val hrColor = heartRateColor(state.latestHr)
    Card(
        modifier = modifier.height(170.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = BorderStroke(1.dp, Stroke),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Heart Rate",
                style = MaterialTheme.typography.labelLarge,
                color = hrColor
            )
            Text(
                text = state.latestHr?.let { "$it" } ?: "--",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = hrColor
            )
            Text(
                text = state.latestHr?.let { "bpm" } ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MovementHeroCard(
    modifier: Modifier = Modifier,
    state: PolarConnectionState
) {
    val movementColor = movementColor(state.movementLevel)
    Card(
        modifier = modifier.height(170.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = BorderStroke(1.dp, Stroke),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Intensity",
                style = MaterialTheme.typography.labelLarge,
                color = movementColor
            )
            Text(
                text = if (state.movementLevel == MovementLevel.UNKNOWN) {
                    "--"
                } else {
                    state.movementLevel.name.lowercase().replaceFirstChar { it.uppercase() }
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = movementColor
            )
            Text(
                text = state.latestAcc?.let { "%.0f mG".format(it.magnitude) } ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun WorkoutSummaryCard(state: PolarConnectionState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Current Session",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        HistoryMetricGrid(
            duration = liveDurationText(state),
            avgHr = historyHrText(state.averageHr, state.hrSampleCount),
            calories = liveCaloriesText(state),
            minHr = state.minHr?.let { "$it bpm" } ?: "--",
            maxHr = state.maxHr?.let { "$it bpm" } ?: "--",
            avgHrColor = heartRateColor(state.averageHr.takeIf { state.hrSampleCount > 0 }?.toInt()),
            minHrColor = heartRateColor(state.minHr),
            maxHrColor = heartRateColor(state.maxHr),
            onCaloriesClick = null
        )
    }
}

@Composable
fun LiveHeartRateCard(state: PolarConnectionState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = BorderStroke(1.dp, Stroke),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Live Heart Rate Curve",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            HeartRateValueChart(values = state.liveHrValues)
        }
    }
}

@Composable
fun LiveRrCard(state: PolarConnectionState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = BorderStroke(1.dp, Stroke),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Live RR Intervals",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            RrValueChart(values = state.liveRrValues)
            SampleRow("Latest RR", state.latestRrMs.lastOrNull()?.let { "$it ms" } ?: "--")
            SampleRow("Avg RR", averageIntText(state.liveRrValues, "ms"))
        }
    }
}

@Composable
fun LiveEcgCard(state: PolarConnectionState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = BorderStroke(1.dp, Stroke),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Live ECG Curve",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            EcgValueChart(values = state.liveEcgValues)
            SampleRow("Latest ECG", state.latestEcgVoltage?.let { "$it uV" } ?: "--")
            SampleRow("ECG samples", state.ecgSampleCount.toString())
        }
    }
}

@Composable
fun SessionCard(
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
            SampleRow("Saved ECG samples", state.savedEcgCount.toString())
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
fun StreamControls(
    state: PolarConnectionState,
    onStartHr: () -> Unit,
    onStopHr: () -> Unit,
    onStartAcc: () -> Unit,
    onStopAcc: () -> Unit,
    onStartEcg: () -> Unit,
    onStopEcg: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = state.isConnected,
            onClick = if (state.isEcgStreaming) onStopEcg else onStartEcg
        ) {
            Text(if (state.isEcgStreaming) "Stop ECG" else "Start ECG")
        }
    }
}

@Composable
fun SensorDataCard(state: PolarConnectionState) {
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
            SampleRow("Avg RR", averageIntText(state.liveRrValues, "ms"))
            HorizontalDivider()
            SampleRow("ECG stream", if (state.isEcgStreaming) "Running" else "Stopped")
            SampleRow("ECG samples", state.ecgSampleCount.toString())
            SampleRow("Latest ECG", state.latestEcgVoltage?.let { "$it uV" } ?: "--")
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
fun DeviceList(
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
fun DeviceRow(
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
