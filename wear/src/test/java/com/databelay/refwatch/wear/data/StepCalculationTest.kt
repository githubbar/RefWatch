package com.databelay.refwatch.wear.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StepCalculationTest {

    // Simple simulation of the logic in GameTimerService
    private var lastCumulativeSteps = 0L
    private var lastCumulativeDistance = 0.0

    private fun calculateSteps(
        totalSteps: Long?,
        stepsDelta: Int?,
        distanceTotal: Double?
    ): Int {
        var stepsDeltaToRecord: Int? = null

        // 1. Cumulative steps
        if (totalSteps != null) {
            val currentTotal = totalSteps
            if (lastCumulativeSteps in 1..<currentTotal) {
                stepsDeltaToRecord = (currentTotal - lastCumulativeSteps).toInt()
            }
            lastCumulativeSteps = currentTotal
        }

        // 2. Delta steps
        if (stepsDeltaToRecord == null || stepsDeltaToRecord == 0) {
            if (stepsDelta != null && stepsDelta > 0) {
                stepsDeltaToRecord = stepsDelta
            }
        }

        // 3. Distance fallback
        if (stepsDeltaToRecord == null || stepsDeltaToRecord == 0) {
            if (distanceTotal != null) {
                val currentDistTotal = distanceTotal
                if (lastCumulativeDistance > 0.0 && currentDistTotal > lastCumulativeDistance) {
                    val distDelta = currentDistTotal - lastCumulativeDistance
                    stepsDeltaToRecord = (distDelta * 1.31).toInt()
                }
                lastCumulativeDistance = currentDistTotal
            }
        }

        return stepsDeltaToRecord ?: 0
    }

    @Test
    fun testStepCalculation_Cumulative() {
        lastCumulativeSteps = 0L
        
        // First update: baseline
        assertEquals(0, calculateSteps(100L, null, null))
        assertEquals(100L, lastCumulativeSteps)

        // Second update: 10 steps
        assertEquals(10, calculateSteps(110L, null, null))
        assertEquals(110L, lastCumulativeSteps)
    }

    @Test
    fun testStepCalculation_DistanceFallback() {
        lastCumulativeDistance = 0.0
        
        // First update: baseline
        assertEquals(0, calculateSteps(null, null, 100.0))
        assertEquals(100.0, lastCumulativeDistance, 0.01)

        // Second update: 10 meters -> ~13 steps
        assertEquals(13, calculateSteps(null, null, 110.0))
        assertEquals(110.0, lastCumulativeDistance, 0.01)
    }

    @Test
    fun testStepCalculation_MissingBaseline() {
        lastCumulativeSteps = 0L
        lastCumulativeDistance = 0.0
        
        // If the first update already has a large value, but our lastCumulative is 0,
        // it skips the first delta because of 'lastCumulativeSteps in 1..<currentTotal'
        assertEquals(0, calculateSteps(5000L, null, null))
        
        // Subsequent updates work
        assertEquals(10, calculateSteps(5010L, null, null))
    }

    @Test
    fun testStepCalculation_ZeroStepsWithMeters() {
        lastCumulativeSteps = 0L
        lastCumulativeDistance = 0.0

        // Simulation: Health Services gives LOCATION but NOT STEPS or DISTANCE data types
        // This is what the user is experiencing.
        
        // In GameTimerService, if totalSteps is null and totalDistance is null:
        val steps = calculateSteps(null, null, null) 
        assertEquals(0, steps)
        
        // Even if we "know" distance from Location, current logic doesn't use it for steps.
    }
}
