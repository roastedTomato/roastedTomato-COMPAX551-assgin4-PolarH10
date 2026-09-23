package com.example.polarh10.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.polarh10.polar.HistoryDetail
import com.example.polarh10.polar.HistorySessionItem
import com.example.polarh10.polar.PolarConnectionState
import com.example.polarh10.ui.components.EcgChart
import com.example.polarh10.ui.components.HeartRateChart
import com.example.polarh10.ui.components.MetricCard
import com.example.polarh10.ui.components.RrValueChart
import com.example.polarh10.ui.components.SampleRow
import com.example.polarh10.ui.components.StatusCard
import com.example.polarh10.ui.format.averageIntText
import com.example.polarh10.ui.format.durationText
import com.example.polarh10.ui.format.estimatedCaloriesText
import com.example.polarh10.ui.format.formatDate
import com.example.polarh10.ui.format.historyHrText
import com.example.polarh10.ui.format.historyIntHrText
import com.example.polarh10.ui.format.rrValuesFromHrSamples
import com.example.polarh10.ui.theme.heartRateColor

@Composable
fun HistoryScreen(
    state: PolarConnectionState,
    onBack: () -> Unit,
    onRefreshHistory: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onImportSampleHistory: () -> Unit,
    onWeightChange: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    var showWeightDialog by remember { mutableStateOf(false) }
    var weightInput by remember(state.weightKg) { mutableStateOf(state.weightKg) }

    if (showWeightDialog) {
        AlertDialog(
            onDismissRequest = { showWeightDialog = false },
            title = { Text("Body Weight") },
            text = {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = weightInput,
                    onValueChange = { weightInput = it },
                    label = { Text("Weight") },
                    suffix = { Text("kg") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onWeightChange(weightInput)
                        showWeightDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWeightDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

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

        state.selectedHistory?.let { detail ->
            HistoryDetailCard(
                detail = detail,
                weightKg = state.weightKg,
                onCaloriesClick = {
                    weightInput = state.weightKg
                    showWeightDialog = true
                }
            )
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

        if (state.historySessions.isEmpty()) {
            StatusCard(
                title = "Saved Sessions",
                primary = "No saved session yet",
                secondary = "Import sample history or record a new session first"
            )
        } else {
            Text(
                text = "Saved Sessions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
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

        ImportHistoryCard(
            state = state,
            onImportSampleHistory = onImportSampleHistory
        )

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun HistoryDetailCard(
    detail: HistoryDetail,
    weightKg: String,
    onCaloriesClick: (() -> Unit)?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Session #${detail.item.session.id}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            HistoryMetricGrid(
                duration = durationText(detail.item.session.startTime, detail.item.session.endTime),
                avgHr = historyHrText(detail.item.hrStats.avg, detail.item.hrStats.count),
                calories = estimatedCaloriesText(detail, weightKg),
                minHr = historyIntHrText(detail.item.hrStats.min, detail.item.hrStats.count),
                maxHr = historyIntHrText(detail.item.hrStats.max, detail.item.hrStats.count),
                avgHrColor = heartRateColor(detail.item.hrStats.avg.takeIf { detail.item.hrStats.count > 0 }?.toInt()),
                minHrColor = heartRateColor(detail.item.hrStats.min.takeIf { detail.item.hrStats.count > 0 }),
                maxHrColor = heartRateColor(detail.item.hrStats.max.takeIf { detail.item.hrStats.count > 0 }),
                onCaloriesClick = onCaloriesClick
            )

            Text(
                text = "Heart Rate Curve",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            HeartRateChart(samples = detail.hrSamples)
            HorizontalDivider()
            Text(
                text = "RR Interval Curve",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            val rrValues = remember(detail.hrSamples) { rrValuesFromHrSamples(detail.hrSamples) }
            RrValueChart(values = rrValues)
            SampleRow("RR intervals", rrValues.size.toString())
            SampleRow("Avg RR", averageIntText(rrValues, "ms"))
            HorizontalDivider()
            Text(
                text = "ECG Curve",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            EcgChart(samples = detail.ecgSamples)
            SampleRow("ECG samples", detail.item.session.ecgCount.toString())
        }
    }
}

@Composable
fun HistoryMetricGrid(
    duration: String,
    avgHr: String,
    calories: String,
    minHr: String,
    maxHr: String,
    avgHrColor: Color = MaterialTheme.colorScheme.onSurface,
    minHrColor: Color = MaterialTheme.colorScheme.onSurface,
    maxHrColor: Color = MaterialTheme.colorScheme.onSurface,
    onCaloriesClick: (() -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Duration",
                value = duration
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Avg HR",
                value = avgHr,
                valueColor = avgHrColor
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Calories",
                value = calories,
                onClick = onCaloriesClick
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Min HR",
                value = minHr,
                valueColor = minHrColor
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Max HR",
                value = maxHr,
                valueColor = maxHrColor
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun HistorySessionRow(item: HistorySessionItem) {
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
fun ImportHistoryCard(
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
