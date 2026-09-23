package com.example.polarh10.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.polarh10.model.EcgSample
import com.example.polarh10.model.HrSample
import com.example.polarh10.ui.theme.Danger
import com.example.polarh10.ui.theme.Good
import com.example.polarh10.ui.theme.Stroke

@Composable
fun HeartRateChart(samples: List<HrSample>) {
    HeartRateValueChart(values = remember(samples) { samples.map { it.hr } })
}

@Composable
fun EcgChart(samples: List<EcgSample>) {
    EcgValueChart(values = remember(samples) { samples.map { it.voltage } })
}

@Composable
fun RrValueChart(values: List<Int>) {
    SimpleLineChart(
        values = values,
        emptyText = "Not enough RR data for chart",
        unit = "ms",
        lineColor = MaterialTheme.colorScheme.secondary
    )
}

@Composable
fun HeartRateValueChart(values: List<Int>) {
    SimpleLineChart(
        values = values,
        emptyText = "Not enough HR data for chart",
        unit = "bpm",
        lineColor = Danger
    )
}

@Composable
fun EcgValueChart(values: List<Int>) {
    SimpleLineChart(
        values = values,
        emptyText = "Not enough ECG data for chart",
        unit = "uV",
        lineColor = Good
    )
}

@Composable
fun SimpleLineChart(
    values: List<Int>,
    emptyText: String,
    unit: String,
    lineColor: Color
) {
    if (values.size < 2) {
        Text(
            text = emptyText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val gridColor = Stroke
    val minValue = values.minOrNull() ?: 0
    val maxValue = values.maxOrNull() ?: 0
    val range = (maxValue - minValue).coerceAtLeast(1)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
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

        val points = values.mapIndexed { index, value ->
            val x = leftPadding + chartWidth * index / (values.lastIndex).coerceAtLeast(1)
            val normalized = (value - minValue).toFloat() / range
            val y = topPadding + chartHeight * (1f - normalized)
            Offset(x, y)
        }

        points.zipWithNext().forEach { (start, end) ->
            drawLine(
                color = lineColor,
                start = start,
                end = end,
                strokeWidth = 2.dp.toPx()
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Min $minValue $unit",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Max $maxValue $unit",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
