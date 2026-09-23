package com.example.polarh10.ui.format

import android.Manifest
import com.example.polarh10.model.HrSample
import com.example.polarh10.polar.HistoryDetail
import com.example.polarh10.polar.PolarConnectionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun connectionLabel(state: PolarConnectionState): String =
    when {
        state.isConnected -> "Connected"
        state.isConnecting -> "Connecting"
        state.isScanning -> "Scanning"
        else -> "Not connected"
    }

fun requiredBluetoothPermissions(): Array<String> =
    arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )

fun hrStatsText(state: PolarConnectionState): String =
    if (state.hrSampleCount == 0L) {
        "--"
    } else {
        "%.1f / %d / %d bpm".format(
            state.averageHr,
            state.minHr ?: 0,
            state.maxHr ?: 0
        )
    }

fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(timestamp))

fun durationText(startTime: Long, endTime: Long?): String {
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

fun durationMinutes(startTime: Long, endTime: Long?): Double {
    if (endTime == null) return 0.0
    val millis = (endTime - startTime).coerceAtLeast(0)
    return millis / 60000.0
}

fun historyHrText(value: Double, count: Long): String =
    if (count == 0L) "--" else "%.1f bpm".format(value)

fun historyIntHrText(value: Int, count: Long): String =
    if (count == 0L) "--" else "$value bpm"

fun estimatedCaloriesText(detail: HistoryDetail, weightKg: String): String {
    val weight = weightKg.toDoubleOrNull() ?: return "--"
    if (weight <= 0.0 || detail.item.hrStats.count == 0L) return "--"

    val minutes = durationMinutes(
        detail.item.session.startTime,
        detail.item.session.endTime
    )
    if (minutes <= 0.0) return "--"

    val calories = minutes * weight * detail.item.hrStats.avg / 200.0
    return "%.0f kcal".format(calories)
}

fun liveDurationText(state: PolarConnectionState): String {
    val startTime = state.activeSessionStartTime
    if (state.isSessionRecording && startTime != null) {
        return durationText(startTime, System.currentTimeMillis())
    }
    return if (state.savedHrCount > 0) {
        val minutes = state.savedHrCount / 60
        val seconds = state.savedHrCount % 60
        if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    } else {
        "--"
    }
}

fun liveCaloriesText(state: PolarConnectionState): String {
    val weight = state.weightKg.toDoubleOrNull() ?: return "--"
    if (weight <= 0.0 || state.hrSampleCount == 0L) return "--"

    val minutes = state.activeSessionStartTime?.let {
        (System.currentTimeMillis() - it).coerceAtLeast(0) / 60000.0
    } ?: (state.savedHrCount / 60.0)

    if (minutes <= 0.0) return "--"

    val calories = minutes * weight * state.averageHr / 200.0
    return "%.0f kcal".format(calories)
}

fun rrValuesFromHrSamples(samples: List<HrSample>): List<Int> =
    samples.flatMap { sample ->
        sample.rr
            ?.split(",")
            ?.mapNotNull { value -> value.trim().toIntOrNull() }
            .orEmpty()
    }

fun averageIntText(values: List<Int>, unit: String): String =
    if (values.isEmpty()) {
        "--"
    } else {
        "${values.average().toInt()} $unit"
    }
