package com.databelay.refwatch.wear // Your Wear OS package

import android.Manifest
import android.app.Application // For context if needed for persistence
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel // Use AndroidViewModel for Application context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.databelay.refwatch.common.* // Your common Game, GamePhase, Team, GameEvent, etc.
import com.databelay.refwatch.wear.data.GameStorageWear
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.databelay.refwatch.common.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import androidx.core.content.edit

// Assume this is your Wear OS specific storage helper (similar to phone's but for Wear)
// You'll need to implement this for Wear OS using SharedPreferences or DataStore
object WearAppStorage {
    private const val PREFS_NAME = "RefWatchWearPrefs"
    private const val KEY_SCHEDULED_GAMES = "scheduledGames"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun saveScheduledGames(context: Application, games: List<Game>) {
        try {
            val jsonString = json.encodeToString(games)
            context.getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE).edit {
                putString(KEY_SCHEDULED_GAMES, jsonString)
            }
        } catch (e: Exception) {
            Log.e("WearAppStorage", "Error saving scheduled games", e)
        }
    }

    fun loadScheduledGames(context: Application): List<Game> {
        return try {
            val jsonString = context.getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)
                .getString(KEY_SCHEDULED_GAMES, null)
            if (jsonString != null) {
                json.decodeFromString<List<Game>>(jsonString)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("WearAppStorage", "Error loading scheduled games", e)
            emptyList()
        }
    }
}

@HiltViewModel
class WearGameViewModel @Inject constructor(
    private val application: Application, // Hilt can provide this
    private val savedStateHandle: SavedStateHandle, // Hilt provides this
    private val gameStorage: GameStorageWear // Hilt injects your singleton GameStorage
) : AndroidViewModel(application) {
    private val TAG = "WearGameViewModel"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // =================== Scheduled Game List (Received from Phone) ===================
    // The ViewModel now gets the list by observing the flow from the injected GameStorage
    val scheduledGames: StateFlow<List<Game>> = gameStorage.gamesListFlow

    // =================== Active Game State ===================
    private val _activeGame = MutableStateFlow(loadInitialActiveGame())
    val activeGame: StateFlow<Game> = _activeGame.asStateFlow()

    private var gameCountDownTimer: CountDownTimer? = null
    // You can inject the Vibrator too using a Hilt module if you want!
    // For now, let's keep it simple.
    private val vibrator = application.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    init {
        Log.d(TAG, "WearGameViewModel initializing.")
        // NO NEED to call loadScheduledGamesFromPersistence() anymore.
        // GameStorage handles its own loading in its init block, and we observe its flow.

        val initialGame = _activeGame.value
        // ... (rest of your init logic for the timer and kick-off team remains the same) ...
        val initialDisplayedTime = if (initialGame.isTimerRunning && initialGame.displayedTimeMillis > 0) {
            initialGame.displayedTimeMillis
        } else {
            getDurationMillisForPhase(initialGame.currentPhase, initialGame)
        }
        _activeGame.update { it.copy(displayedTimeMillis = initialDisplayedTime) }
        if (initialGame.isTimerRunning && initialDisplayedTime > 0) {
            startTimerLogic(initialDisplayedTime)
        }
        updateCurrentPeriodKickOffTeam(initialGame.currentPhase, initialGame.kickOffTeam)
    }

    private fun loadInitialActiveGame(): Game {
        val savedGameJson: String? = savedStateHandle.get("activeGameJson")
        return if (savedGameJson != null) {
            try {
                Log.d(TAG, "Loading active game from SavedStateHandle.")
                json.decodeFromString<Game>(savedGameJson)
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding active game from JSON, using default.", e)
                Game() // Default new game
            }
        } else {
            Log.d(TAG, "No active game in SavedStateHandle, using default new game.")
            // On first start, try to load the first scheduled game if available
            val firstScheduledGame = gameStorage.getGames().firstOrNull()
            if (firstScheduledGame != null) {
                Log.d(
                    TAG,
                    "No active game, but found scheduled game. Preparing ${firstScheduledGame.id}."
                )
                // Return a "clean" version of the first scheduled game, ready to start
                firstScheduledGame.copy(
                    currentPhase = GamePhase.PRE_GAME,
                    homeScore = 0, awayScore = 0, events = emptyList()
                )
            } else {
                Game() // Default new game if no scheduled games either
            }
        }
    }

    private fun saveActiveGameState() {
        try {
            val activeGameJson = json.encodeToString(_activeGame.value)
            savedStateHandle["activeGameJson"] = activeGameJson
            Log.d(TAG, "Active game state saved to SavedStateHandle.")
            // TODO: Also send this _activeGame.value to the phone via DataClient
            // sendActiveGameUpdateToPhone(_activeGame.value)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving active game state to JSON", e)
        }
    }

    // Call this when a user selects a game from the _scheduledGames list
    fun selectGameToStart(game: Game) {
        pauseTimer() // Stop any timer from a previous game
        // Reset live state fields for the selected game, but keep its schedule info
        val cleanGameForStart = game.copy(
            currentPhase = GamePhase.PRE_GAME,
            homeScore = 0,
            awayScore = 0,
            displayedTimeMillis = getDurationMillisForPhase(GamePhase.FIRST_HALF, game), // Display 1st half duration
            actualTimeElapsedInPeriodMillis = 0L,
            isTimerRunning = false,
            events = emptyList(),
            lastUpdated = System.currentTimeMillis(),
            // Ensure kickOffTeam is set correctly for the first half
            currentPeriodKickOffTeam = game.kickOffTeam
        )
        _activeGame.value = cleanGameForStart
        Log.d(TAG, "Selected game ${game.id} to start. Phase: PRE_GAME.")
        saveActiveGameState()
    }

    fun updateHomeTeamColor(color: Color) {
        _activeGame.update { it.copy(homeTeamColorArgb = color.toArgb(), lastUpdated = System.currentTimeMillis()) }
        saveActiveGameState()
    }

    fun updateAwayTeamColor(color: Color) {
        _activeGame.update { it.copy(awayTeamColorArgb = color.toArgb(), lastUpdated = System.currentTimeMillis()) }
        saveActiveGameState()
    }

    fun setKickOffTeam(team: Team) { // This is the overall designated kick-off team for 1st half
        _activeGame.update {
            it.copy(
                kickOffTeam = team,
                currentPeriodKickOffTeam = team, // Also update for current pre-game/first-half
                lastUpdated = System.currentTimeMillis()
            )
        }
        saveActiveGameState()
    }

    fun setHalfDuration(minutes: Int) {
        _activeGame.update { currentGame ->
            var newDisplayedTime = currentGame.displayedTimeMillis
            if (currentGame.currentPhase == GamePhase.PRE_GAME ||
                (currentGame.currentPhase == GamePhase.FIRST_HALF && !currentGame.isTimerRunning && currentGame.actualTimeElapsedInPeriodMillis == 0L)) {
                newDisplayedTime = minutes * 60 * 1000L
            } else if (currentGame.currentPhase == GamePhase.FIRST_HALF || currentGame.currentPhase == GamePhase.SECOND_HALF) {
                if (currentGame.isTimerRunning && (minutes * 60 * 1000L) < currentGame.halfDurationMillis) {
                    val timeAlreadyElapsed = currentGame.halfDurationMillis - currentGame.displayedTimeMillis
                    newDisplayedTime = ((minutes * 60 * 1000L) - timeAlreadyElapsed).coerceAtLeast(0L)
                } else if (!currentGame.isTimerRunning && currentGame.actualTimeElapsedInPeriodMillis > 0L) {
                    newDisplayedTime = ((minutes * 60 * 1000L) - currentGame.actualTimeElapsedInPeriodMillis).coerceAtLeast(0L)
                }
            }
            currentGame.copy(
                halfDurationMinutes = minutes,
                displayedTimeMillis = newDisplayedTime,
                lastUpdated = System.currentTimeMillis()
            )
        }
        saveActiveGameState()
    }

    fun setHalftimeDuration(minutes: Int) {
        _activeGame.update { currentGame ->
            var newDisplayedTime = currentGame.displayedTimeMillis
            if (currentGame.currentPhase == GamePhase.HALF_TIME && !currentGame.isTimerRunning && currentGame.actualTimeElapsedInPeriodMillis == 0L) {
                newDisplayedTime = minutes * 60 * 1000L
            } else if (currentGame.currentPhase == GamePhase.HALF_TIME) {
                if (currentGame.isTimerRunning && (minutes * 60 * 1000L) < currentGame.halftimeDurationMillis) {
                    val timeAlreadyElapsed = currentGame.halftimeDurationMillis - currentGame.displayedTimeMillis
                    newDisplayedTime = ((minutes * 60 * 1000L) - timeAlreadyElapsed).coerceAtLeast(0L)
                } else if (!currentGame.isTimerRunning && currentGame.actualTimeElapsedInPeriodMillis > 0L) {
                    newDisplayedTime = ((minutes * 60 * 1000L) - currentGame.actualTimeElapsedInPeriodMillis).coerceAtLeast(0L)
                }
            }
            currentGame.copy(
                halftimeDurationMinutes = minutes,
                displayedTimeMillis = newDisplayedTime,
                lastUpdated = System.currentTimeMillis()
            )
        }
        saveActiveGameState()
    }

    fun confirmSettingsAndStartGame() {
        val currentGame = _activeGame.value
        if (currentGame.currentPhase == GamePhase.PRE_GAME) {
            val kickOffEvent = GameEvent.GenericLogEvent(message = "${currentGame.kickOffTeam.name} kicks off 1st Half")
            _activeGame.update { it.copy(events = it.events + kickOffEvent) } // Log before changing phase
            changePhase(GamePhase.FIRST_HALF) // This will also save state
        }
    }

    fun toggleTimer() {
        val currentGame = _activeGame.value
        if (currentGame.isTimerRunning) {
            pauseTimer()
        } else {
            if (currentGame.currentPhase.hasDuration() && currentGame.displayedTimeMillis > 0) {
                startTimer()
            }
        }
    }

    private fun startTimer() {
        val currentGame = _activeGame.value
        if (!currentGame.isTimerRunning && currentGame.displayedTimeMillis > 0 && currentGame.currentPhase.hasDuration()) {
            val startEvent = GameEvent.GenericLogEvent(message = "Timer Started for ${currentGame.currentPhase.readable()}")
            _activeGame.update {
                it.copy(
                    isTimerRunning = true,
                    events = it.events + startEvent,
                    lastUpdated = System.currentTimeMillis()
                )
            }
            startTimerLogic(currentGame.displayedTimeMillis)
            saveActiveGameState()
        }
    }

    private fun pauseTimer() {
        if (_activeGame.value.isTimerRunning) {
            gameCountDownTimer?.cancel()
            val pauseEvent = GameEvent.GenericLogEvent(message = "Timer Paused")
            _activeGame.update {
                it.copy(
                    isTimerRunning = false,
                    events = it.events + pauseEvent,
                    lastUpdated = System.currentTimeMillis()
                )
            }
            saveActiveGameState()
        }
    }

    private fun startTimerLogic(durationMillisToRun: Long) {
        gameCountDownTimer?.cancel()
        val gameAtTimerStart = _activeGame.value // Capture state at the moment timer starts
        val totalDurationForCurrentPhase = getDurationMillisForPhase(gameAtTimerStart.currentPhase, gameAtTimerStart)

        Log.d(TAG, "startTimerLogic: durationToRun=$durationMillisToRun, currentPhase=${gameAtTimerStart.currentPhase}, initialActualElapsed=${gameAtTimerStart.actualTimeElapsedInPeriodMillis}")

        gameCountDownTimer = object : CountDownTimer(durationMillisToRun, 250) { // Tick interval
            override fun onTick(millisUntilFinished: Long) {
                val timePassedThisInstance = durationMillisToRun - millisUntilFinished
                _activeGame.update {
                    it.copy(
                        displayedTimeMillis = millisUntilFinished,
                        actualTimeElapsedInPeriodMillis = gameAtTimerStart.actualTimeElapsedInPeriodMillis + timePassedThisInstance,
                        // lastUpdated = System.currentTimeMillis() // Updating too frequently, save explicitly
                    )
                }
            }

            @RequiresPermission(Manifest.permission.VIBRATE)
            override fun onFinish() {
                Log.d(TAG, "Timer onFinish for phase: ${gameAtTimerStart.currentPhase}")
                _activeGame.update {
                    it.copy(
                        displayedTimeMillis = 0,
                        actualTimeElapsedInPeriodMillis = totalDurationForCurrentPhase,
                        isTimerRunning = false,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
                vibrateDevice()
                handleTimerFinish() // This calls changePhase, which saves state
            }
        }.start()
    }

    private fun handleTimerFinish() {
        val currentPhase = _activeGame.value.currentPhase
        val endEvent = GameEvent.GenericLogEvent(message = "${currentPhase.readable()} Ended (Timer)")
        _activeGame.update { it.copy(events = it.events + endEvent) }

        when (currentPhase) {
            GamePhase.FIRST_HALF -> changePhase(GamePhase.HALF_TIME)
            GamePhase.HALF_TIME -> changePhase(GamePhase.SECOND_HALF)
            GamePhase.SECOND_HALF -> changePhase(GamePhase.FULL_TIME)
            else -> Log.w(TAG, "Timer finished in unhandled phase: $currentPhase")
        }
    }

    fun endCurrentPhaseEarly() {
        val currentGame = _activeGame.value
        if (currentGame.currentPhase.hasDuration() && currentGame.currentPhase != GamePhase.FULL_TIME && currentGame.currentPhase != GamePhase.PRE_GAME) {
            pauseTimer()
            val earlyEndEvent = GameEvent.GenericLogEvent(
                message = "${currentGame.currentPhase.readable()} ended early by referee.",
                gameTimeMillis = currentGame.actualTimeElapsedInPeriodMillis
            )
            _activeGame.update { it.copy(events = it.events + earlyEndEvent) }
            handleTimerFinish() // Transitions to next phase
            Log.d(TAG, "${currentGame.currentPhase.readable()} ended early.")
        }
    }

    fun addGoal(team: Team) {
        val currentGame = _activeGame.value
        if (!currentGame.currentPhase.isPlayablePhase()) return

        val newHomeScore = if (team == Team.HOME) currentGame.homeScore + 1 else currentGame.homeScore
        val newAwayScore = if (team == Team.AWAY) currentGame.awayScore + 1 else currentGame.awayScore
        val goalEvent = GameEvent.GoalScoredEvent(
            team = team,
            gameTimeMillis = currentGame.actualTimeElapsedInPeriodMillis,
            homeScoreAtTime = newHomeScore,
            awayScoreAtTime = newAwayScore
        )
        _activeGame.update {
            it.copy(
                homeScore = newHomeScore,
                awayScore = newAwayScore,
                events = it.events + goalEvent,
                lastUpdated = System.currentTimeMillis()
            )
        }
        saveActiveGameState()
    }

    fun addCard(team: Team, playerNumber: Int, cardType: CardType) {
        val currentGame = _activeGame.value
        if (!currentGame.currentPhase.isPlayablePhase()) return

        val cardEvent = GameEvent.CardIssuedEvent(
            team = team,
            playerNumber = playerNumber,
            cardType = cardType,
            gameTimeMillis = currentGame.actualTimeElapsedInPeriodMillis
        )
        _activeGame.update {
            it.copy(
                events = it.events + cardEvent,
                lastUpdated = System.currentTimeMillis()
            )
        }
        saveActiveGameState()
    }

    private fun changePhase(newPhase: GamePhase) {
        pauseTimer()
        val currentGame = _activeGame.value
        val previousPhase = currentGame.currentPhase

        val gameTimeForChangeEvent = if (previousPhase.hasDuration() && currentGame.displayedTimeMillis == 0L) {
            getDurationMillisForPhase(previousPhase, currentGame)
        } else {
            currentGame.actualTimeElapsedInPeriodMillis
        }
        val phaseChangeEvent = GameEvent.PhaseChangedEvent(newPhase = newPhase, gameTimeMillis = gameTimeForChangeEvent)

        _activeGame.update {
            it.copy(
                currentPhase = newPhase,
                displayedTimeMillis = getDurationMillisForPhase(newPhase, it),
                actualTimeElapsedInPeriodMillis = 0L,
                isTimerRunning = false,
                events = it.events + phaseChangeEvent,
                lastUpdated = System.currentTimeMillis()
            )
        }
        // Update kick-off team for the new period
        updateCurrentPeriodKickOffTeam(newPhase, _activeGame.value.kickOffTeam) // Pass the original kickOffTeam

        if (newPhase == GamePhase.HALF_TIME) { // Auto-start halftime timer
            startTimer()
        }
        saveActiveGameState() // Save state after all updates for the phase change
    }

    private fun updateCurrentPeriodKickOffTeam(phase: GamePhase, initialGameKickOffTeam: Team) {
        _activeGame.update { currentGame ->
            val newCurrentKickOff = when (phase) {
                GamePhase.FIRST_HALF, GamePhase.PRE_GAME -> initialGameKickOffTeam
                GamePhase.SECOND_HALF -> if (initialGameKickOffTeam == Team.HOME) Team.AWAY else Team.HOME
                // TODO: Add logic for extra time kick-offs if needed
                else -> currentGame.currentPeriodKickOffTeam // Keep existing for other phases
            }
            currentGame.copy(currentPeriodKickOffTeam = newCurrentKickOff)
            // No need to save state here, changePhase or other callers will save
        }
    }


    private fun getDurationMillisForPhase(phase: GamePhase, gameSettings: Game): Long {
        return when (phase) {
            GamePhase.FIRST_HALF, GamePhase.SECOND_HALF -> gameSettings.halfDurationMillis
            GamePhase.HALF_TIME -> gameSettings.halftimeDurationMillis
            // Add Extra Time logic based on gameSettings if implemented
            else -> 0L // Default for PRE_GAME, FULL_TIME
        }
    }

    fun resetActiveGameToSelectedSettings(selectedGame: Game) {
        pauseTimer()
        // This function is for when a user chooses a game from the list to make it active.
        // It should reset the live state but keep the schedule/config details.
        val newActiveGame = selectedGame.copy( // Start with the selected game's settings
            currentPhase = GamePhase.PRE_GAME,
            homeScore = 0,
            awayScore = 0,
            displayedTimeMillis = getDurationMillisForPhase(GamePhase.FIRST_HALF, selectedGame),
            actualTimeElapsedInPeriodMillis = 0L,
            isTimerRunning = false,
            events = emptyList(),
            lastUpdated = System.currentTimeMillis(),
            currentPeriodKickOffTeam = selectedGame.kickOffTeam // Reset to original kick-off
        )
        _activeGame.value = newActiveGame
        updateCurrentPeriodKickOffTeam(GamePhase.PRE_GAME, newActiveGame.kickOffTeam)
        Log.d(TAG, "Active game reset to settings from game: ${selectedGame.id}")
        saveActiveGameState()
    }

    fun createNewDefaultGame() { // For starting a completely new game without selection
        pauseTimer()
        _activeGame.value = Game() // Creates a game with default constructor values
        updateCurrentPeriodKickOffTeam(GamePhase.PRE_GAME, _activeGame.value.kickOffTeam)
        // Set initial displayed time for the first half for the new default game
        _activeGame.update {
            it.copy(displayedTimeMillis = getDurationMillisForPhase(GamePhase.FIRST_HALF, it))
        }
        Log.d(TAG, "New default game created and made active.")
        saveActiveGameState()
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrateDevice() {
        try {
            if (vibrator?.hasVibrator() == true) { // Check hasVibrator before using
                val timings = longArrayOf(0, 300, 200, 300)
                val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE)
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator?.vibrate(effect)
            } else {
                Log.w(TAG, "No vibrator available or permission denied.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        gameCountDownTimer?.cancel()
        Log.d(TAG, "WearGameViewModel cleared, timer cancelled.")
    }
}
