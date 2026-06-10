package com.databelay.refwatch.wear

import android.util.Log
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.theme.RefWatchWearTheme
import com.databelay.refwatch.wear.presentation.screens.MainGameDisplayScreen
import com.databelay.refwatch.wear.presentation.screens.TeamActionsPage
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests to measure latency from button press to UI refresh.
 * These logs will appear in Logcat under the "LATENCY_TEST" tag.
 * Run these on a real device or emulator to see performance metrics.
 */
@RunWith(AndroidJUnit4::class)
class PerformanceLatencyTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun measureKickOffLatency() {
        var startTime = 0L
        var firstRefreshAfterClick = 0L
        
        // Use a state to force recomposition and measure it
        var testGame by mutableStateOf(Game.defaults().copy(currentPhase = GamePhase.FIRST_HALF))

        composeTestRule.setContent {
            RefWatchWearTheme {
                SideEffect {
                    if (startTime > 0 && firstRefreshAfterClick == 0L && testGame.isTimerRunning) {
                        firstRefreshAfterClick = System.nanoTime()
                        val latencyMs = (firstRefreshAfterClick - startTime) / 1_000_000.0
                        Log.i("LATENCY_TEST", "KICK_OFF Screen Refresh Latency: ${latencyMs}ms")
                    }
                }

                MainGameDisplayScreen(
                    game = testGame,
                    onKickOff = {
                        startTime = System.nanoTime()
                        Log.d("LATENCY_TEST", "Kick Off Pressed at $startTime")
                        // Simulate ViewModel update
                        testGame = testGame.copy(isTimerRunning = true)
                    }
                )
            }
        }

        // Wait for UI to settle
        composeTestRule.waitForIdle()

        // Find and click the button
        composeTestRule.onNodeWithText("Kick Off").performClick()
        
        // Wait for the change to be reflected
        composeTestRule.waitForIdle()
    }

    @Test
    fun measureAddGoalLatency() {
        var startTime = 0L
        var firstRefreshAfterClick = 0L
        
        var testGame by mutableStateOf(Game.defaults().copy(currentPhase = GamePhase.FIRST_HALF))

        composeTestRule.setContent {
            RefWatchWearTheme {
                SideEffect {
                    // Check if score actually changed to avoid logging initial composition
                    if (startTime > 0 && firstRefreshAfterClick == 0L && testGame.homeScore > 0) {
                        firstRefreshAfterClick = System.nanoTime()
                        val latencyMs = (firstRefreshAfterClick - startTime) / 1_000_000.0
                        Log.i("LATENCY_TEST", "ADD_GOAL (+1) Screen Refresh Latency: ${latencyMs}ms")
                    }
                }

                TeamActionsPage(
                    team = Team.HOME,
                    teamName = "Home",
                    teamColor = androidx.compose.ui.graphics.Color.Red,
                    isPlayablePhase = true,
                    onAddGoal = {
                        startTime = System.nanoTime()
                        Log.d("LATENCY_TEST", "+1 Pressed at $startTime")
                        testGame = testGame.copy(homeScore = testGame.homeScore + 1)
                    },
                    onNavigateToLogCard = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("+1").performClick()
        composeTestRule.waitForIdle()
    }
}
