package com.databelay.refwatch.wear

import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.GoalScoredEvent
import com.databelay.refwatch.common.LocationSample
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.toSnapshotForStorage
import org.junit.Test
import kotlin.system.measureNanoTime

/**
 * Local unit tests to measure the performance of data model operations.
 * These run on the JVM and help identify if data structure operations are the bottleneck.
 */
class PerformanceUnitTest {

    @Test
    fun measureHeavyGameCopyAndSnapshotPerformance() {
        // Create a game with significant history to simulate a game near the end of regulation
        val heavyGame = Game.defaults().copy(
            currentPhase = GamePhase.SECOND_HALF,
            homeScore = 2,
            awayScore = 1,
            // 50 events
            events = List(50) { 
                GoalScoredEvent(team = Team.HOME, gameTimeMillis = it.toDouble(), homeScoreAtTime = 1, awayScoreAtTime = 0) 
            },
            // 2000 location samples (approx 3 hours at 5s interval)
            locationHistory = List(2000) { 
                LocationSample(timestamp = it.toLong() * 5000, latitude = 51.5, longitude = -0.1) 
            }
        )

        // Measure time to add an event (includes copy and list concatenation)
        val addEventTime = measureNanoTime {
            heavyGame.addEvent(
                GoalScoredEvent(team = Team.AWAY, gameTimeMillis = 4500.0, homeScoreAtTime = 2, awayScoreAtTime = 2)
            )
        }
        println("Adding event to heavy game: ${addEventTime / 1_000_000.0}ms")

        // Measure time to create a snapshot (this happens on every state change in the VM)
        val snapshotTime = measureNanoTime {
            heavyGame.toSnapshotForStorage()
        }
        println("Creating snapshot for storage: ${snapshotTime / 1_000_000.0}ms")
    }
}
