package com.databelay.refwatch.common

import kotlinx.serialization.Serializable
import kotlin.math.*

@Serializable
data class LocationSample(
    val timestamp: Long = 0L,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double? = null,
    val accuracy: Float? = null
)

@Serializable
data class HeartRateSample(
    val timestamp: Long = 0L,
    val bpm: Double = 0.0
)

@Serializable
data class StepSample(
    val timestamp: Long = 0L,
    val delta: Int = 0
)

/**
 * Extension function to calculate the total distance covered in a list of location samples.
 * Uses the Haversine formula.
 */
fun List<LocationSample>.calculateTotalDistanceMeters(): Double {
    if (size < 2) return 0.0
    var totalDistance = 0.0
    for (i in 0 until size - 1) {
        totalDistance += haversineDistance(this[i], this[i + 1])
    }
    return totalDistance
}

private fun haversineDistance(s1: LocationSample, s2: LocationSample): Double {
    val r = 6371000.0 // Earth radius in meters
    val lat1 = Math.toRadians(s1.latitude)
    val lon1 = Math.toRadians(s1.longitude)
    val lat2 = Math.toRadians(s2.latitude)
    val lon2 = Math.toRadians(s2.longitude)

    val dLat = lat2 - lat1
    val dLon = lon2 - lon1

    val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    return r * c
}

fun List<HeartRateSample>.calculateAverageHeartRate(): Double {
    val validSamples = filter { it.bpm > 0 }
    if (validSamples.isEmpty()) return 0.0
    return validSamples.map { it.bpm }.average()
}

fun List<StepSample>.calculateTotalSteps(): Int {
    return sumOf { it.delta }
}

/**
 * Filters movement data to only include periods where the game was active,
 * and removes spatial outliers (e.g. toilet trips).
 */
fun List<LocationSample>.filterGameMovement(
    gameEvents: List<GameEvent>
): List<LocationSample> {
    if (this.size < 10) return this

    // 1. Filter by game phase (ignore pre-game, halftime, post-game)
    val playablePhases = setOf(
        GamePhase.FIRST_HALF,
        GamePhase.SECOND_HALF,
        GamePhase.EXTRA_TIME_FIRST_HALF,
        GamePhase.EXTRA_TIME_SECOND_HALF,
        GamePhase.PENALTIES
    )

    val activeIntervals = mutableListOf<Pair<Double, Double>>()
    var currentStart: Double? = null

    val phaseEvents = gameEvents.filterIsInstance<PhaseChangedEvent>().sortedBy { it.timestamp }
    for (event in phaseEvents) {
        if (event.newPhase in playablePhases) {
            if (currentStart == null) currentStart = event.timestamp
        } else {
            if (currentStart != null) {
                activeIntervals.add(currentStart to event.timestamp)
                currentStart = null
            }
        }
    }
    if (currentStart != null) activeIntervals.add(currentStart to Double.MAX_VALUE)

    val timeFiltered = if (activeIntervals.isEmpty()) {
        this
    } else {
        this.filter { sample ->
            activeIntervals.any { (start, end) -> sample.timestamp.toDouble() in start..end }
        }
    }

    if (timeFiltered.size < 10) return timeFiltered

    // 2. Filter outliers (remove 1% total)
    // Use 0.5th and 99.5th percentile to focus on the field and ignore "toilet trips"
    val sortedLat = timeFiltered.map { it.latitude }.sorted()
    val sortedLon = timeFiltered.map { it.longitude }.sorted()
    val lowIdx = (timeFiltered.size * 0.005).toInt()
    val highIdx = (timeFiltered.size * 0.995).toInt().coerceAtMost(timeFiltered.size - 1)

    val latMin = sortedLat[lowIdx]
    val latMax = sortedLat[highIdx]
    val lonMin = sortedLon[lowIdx]
    val lonMax = sortedLon[highIdx]

    return timeFiltered.filter {
        it.latitude in latMin..latMax && it.longitude in lonMin..lonMax
    }
}
