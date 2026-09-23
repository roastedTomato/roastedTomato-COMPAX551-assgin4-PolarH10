package com.example.polarh10.processing

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class HrSummary(
    val latest: Int? = null,
    val sampleCount: Long = 0,
    val average: Double = 0.0,
    val min: Int? = null,
    val max: Int? = null
)

data class MovementSummary(
    val latestMagnitude: Double = 0.0,
    val smoothedIntensity: Double = 0.0,
    val sampleCount: Long = 0,
    val peakIntensity: Double = 0.0,
    val level: MovementLevel = MovementLevel.UNKNOWN
)

enum class MovementLevel {
    UNKNOWN,
    LOW,
    MODERATE,
    HIGH
}

class SensorProcessor {
    private var hrSum = 0L
    private var hrMin: Int? = null
    private var hrMax: Int? = null
    private var hrCount = 0L

    private var restingMagnitude: Double? = null
    private var smoothedIntensity = 0.0
    private var accCount = 0L
    private var peakIntensity = 0.0

    fun addHeartRate(hr: Int): HrSummary {
        hrCount += 1
        hrSum += hr
        hrMin = hrMin?.let { min(it, hr) } ?: hr
        hrMax = hrMax?.let { max(it, hr) } ?: hr
        return HrSummary(
            latest = hr,
            sampleCount = hrCount,
            average = hrSum.toDouble() / hrCount,
            min = hrMin,
            max = hrMax
        )
    }

    fun addAccelerometer(x: Int, y: Int, z: Int): MovementSummary {
        val magnitude = sqrt(
            x.toDouble() * x +
                y.toDouble() * y +
                z.toDouble() * z
        )
        if (restingMagnitude == null) {
            restingMagnitude = magnitude
        }

        val intensity = abs(magnitude - (restingMagnitude ?: magnitude))
        smoothedIntensity = if (accCount == 0L) {
            intensity
        } else {
            SMOOTHING_ALPHA * intensity + (1 - SMOOTHING_ALPHA) * smoothedIntensity
        }
        accCount += 1
        peakIntensity = max(peakIntensity, smoothedIntensity)

        return MovementSummary(
            latestMagnitude = magnitude,
            smoothedIntensity = smoothedIntensity,
            sampleCount = accCount,
            peakIntensity = peakIntensity,
            level = movementLevel(smoothedIntensity)
        )
    }

    fun reset() {
        hrSum = 0L
        hrMin = null
        hrMax = null
        hrCount = 0L
        restingMagnitude = null
        smoothedIntensity = 0.0
        accCount = 0L
        peakIntensity = 0.0
    }

    private fun movementLevel(intensity: Double): MovementLevel =
        when {
            intensity <= 0.0 -> MovementLevel.UNKNOWN
            intensity < LOW_THRESHOLD -> MovementLevel.LOW
            intensity < HIGH_THRESHOLD -> MovementLevel.MODERATE
            else -> MovementLevel.HIGH
        }

    private companion object {
        const val SMOOTHING_ALPHA = 0.2
        const val LOW_THRESHOLD = 80.0
        const val HIGH_THRESHOLD = 250.0
    }
}
