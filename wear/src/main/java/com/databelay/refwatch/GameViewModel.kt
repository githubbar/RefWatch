package com.databelay.refwatch // Your package

import android.Manifest
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log // Import Log
import androidx.annotation.RequiresPermission
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.databelay.refwatch.common.*
class GameViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // =================== Scheduled Game List ===================
    private val _scheduledGames = MutableStateFlow<List<GameSettings>>(emptyList())
    val scheduledGames: StateFlow<List<GameSettings>> = _scheduledGames.asStateFlow()

    fun addScheduledGames(games: List<GameSettings>) {
        _scheduledGames.update { currentGames ->
            // Handle duplicates or merge logic if necessary
            (currentGames + games).distinctBy { it.id }.sortedBy { it.gameDateTimeEpochMillis }
        }
        // Persist these games (e.g., to SharedPreferences as JSON, or a Room database)
        saveScheduledGamesToPersistence()
        Log.d("GameViewModel", "Added ${games.size} games. Total: ${_scheduledGames.value.size}")
    }

    private fun loadScheduledGamesFromPersistence() {
        // Load games from SharedPreferences or Room on init
        // _scheduledGames.value = a_list_of_games_from_storage
    }

    private fun saveScheduledGamesToPersistence() {
        // Save _scheduledGames.value to SharedPreferences or Room
    }
// ...

    // =================== Game State ===================
    private val _gameState = MutableStateFlow(
        savedStateHandle.get<GameState>("gameState") ?: GameState()
    )
    val gameState = _gameState.asStateFlow()

    private var gameCountDownTimer: CountDownTimer? = null // Renamed for clarity
    private var vibrator: Vibrator? = null

    fun setVibrator(v: Vibrator) {
        this.vibrator = v
    }

    init {
        // If loading from saved state and timer was running, restart it.
        // Also, ensure displayedTimeMillis is correct for the current phase.
        val initialState = _gameState.value
        val initialDisplayedTime = if (initialState.isTimerRunning && initialState.displayedTimeMillis > 0) {
            initialState.displayedTimeMillis
        } else {
            getDurationMillisForPhase(initialState.currentPhase, initialState.settings)
        }

        _gameState.update {
            it.copy(displayedTimeMillis = initialDisplayedTime)
        }

        if (initialState.isTimerRunning && initialDisplayedTime > 0) {
            startTimerLogic(initialDisplayedTime)
        }
        updateCurrentPeriodKickOffTeam(initialState.currentPhase)
    }

    // When updating colors, use the ARGB value
    fun updateHomeTeamColor(color: Color) {
        _gameState.update { currentState ->
            currentState.copy(
                settings = currentState.settings.copy(homeTeamColorArgb = color.toArgb()) // Save ARGB Int
            )
        }
        saveState()
    }

    fun updateAwayTeamColor(color: Color) {
        _gameState.update { currentState ->
            currentState.copy(
                settings = currentState.settings.copy(awayTeamColorArgb = color.toArgb()) // Save ARGB Int
            )
        }
        saveState()
    }

    fun setKickOffTeam(team: Team) {
        _gameState.update {
            it.copy(
                settings = it.settings.copy(kickOffTeam = team, currentPeriodKickOffTeam = team)
            )
        }
        saveState()
    }

    fun setHalfDuration(minutes: Int) {
        _gameState.update { currentState ->
            val newSettings = currentState.settings.copy(halfDurationMinutes = minutes)
            var newDisplayedTime = currentState.displayedTimeMillis
            // If in pre-game, or in first half and timer hasn't started/run, update display time
            if (currentState.currentPhase == GamePhase.PRE_GAME ||
                (currentState.currentPhase == GamePhase.FIRST_HALF && !currentState.isTimerRunning && currentState.actualTimeElapsedInPeriodMillis == 0L)
            ) {
                newDisplayedTime = newSettings.halfDurationMillis
            } else if (currentState.currentPhase == GamePhase.FIRST_HALF || currentState.currentPhase == GamePhase.SECOND_HALF) {
                // If timer is running, adjust remaining time if new duration is shorter
                if (currentState.isTimerRunning && newSettings.halfDurationMillis < currentState.settings.halfDurationMillis) {
                    val timeAlreadyElapsed = currentState.settings.halfDurationMillis - currentState.displayedTimeMillis
                    newDisplayedTime = (newSettings.halfDurationMillis - timeAlreadyElapsed).coerceAtLeast(0L)
                } else if (!currentState.isTimerRunning && currentState.actualTimeElapsedInPeriodMillis > 0L) {
                    // Timer paused, recalculate displayed time based on new duration and what has passed
                    newDisplayedTime = (newSettings.halfDurationMillis - currentState.actualTimeElapsedInPeriodMillis).coerceAtLeast(0L)
                }
            }
            currentState.copy(settings = newSettings, displayedTimeMillis = newDisplayedTime)
        }
        saveState()
    }

    fun setHalftimeDuration(minutes: Int) {
        _gameState.update { currentState ->
            val newSettings = currentState.settings.copy(halftimeDurationMinutes = minutes)
            var newDisplayedTime = currentState.displayedTimeMillis
            if (currentState.currentPhase == GamePhase.HALF_TIME && !currentState.isTimerRunning && currentState.actualTimeElapsedInPeriodMillis == 0L) {
                newDisplayedTime = newSettings.halftimeDurationMillis
            } else if (currentState.currentPhase == GamePhase.HALF_TIME) {
                if (currentState.isTimerRunning && newSettings.halftimeDurationMillis < currentState.settings.halftimeDurationMillis) {
                    val timeAlreadyElapsed = currentState.settings.halftimeDurationMillis - currentState.displayedTimeMillis
                    newDisplayedTime = (newSettings.halftimeDurationMillis - timeAlreadyElapsed).coerceAtLeast(0L)
                } else if (!currentState.isTimerRunning && currentState.actualTimeElapsedInPeriodMillis > 0L) {
                    newDisplayedTime = (newSettings.halftimeDurationMillis - currentState.actualTimeElapsedInPeriodMillis).coerceAtLeast(0L)
                }
            }
            currentState.copy(settings = newSettings, displayedTimeMillis = newDisplayedTime)
        }
        saveState()
    }

    fun confirmSettingsAndStartGame() {
        if (_gameState.value.currentPhase == GamePhase.PRE_GAME) {
            // Log who kicks off
            val kickOffTeam = _gameState.value.settings.kickOffTeam
            addEvent(GameEvent.GenericLogEvent(message = "${kickOffTeam.name} kicks off 1st Half"))
            changePhase(GamePhase.FIRST_HALF)
            // Optionally auto-start timer here, or let user do it via toggleTimer
            // startTimer()
        }
    }

    // --- Timer Management ---
    fun toggleTimer() {
        if (_gameState.value.isTimerRunning) {
            pauseTimer()
        } else {
            if (_gameState.value.currentPhase.hasDuration() && _gameState.value.displayedTimeMillis > 0) {
                startTimer()
            }
        }
    }

    fun startTimer() { // Made private as it's called by toggleTimer or internally
        val currentState = _gameState.value
        if (!currentState.isTimerRunning && currentState.displayedTimeMillis > 0 && currentState.currentPhase.hasDuration()) {
            _gameState.update { it.copy(isTimerRunning = true) }
            startTimerLogic(currentState.displayedTimeMillis)
            addEvent(GameEvent.GenericLogEvent(message = "Timer Started for ${currentState.currentPhase.name}"))
            saveState()
        }
    }

     fun pauseTimer() { // Made private
        if (_gameState.value.isTimerRunning) {
            gameCountDownTimer?.cancel()
            _gameState.update { it.copy(isTimerRunning = false) }
            addEvent(GameEvent.GenericLogEvent(message = "Timer Paused"))
            saveState()
        }
    }

    private fun startTimerLogic(durationMillisToRun: Long) {
        gameCountDownTimer?.cancel() // Cancel any existing timer

        // This is the crucial part: when the timer starts (or resumes),
        // the actualTimeElapsedInPeriodMillis should reflect what has *already passed* for this period.
        // displayedTimeMillis is what's left to count down.
        // The onTick will update actualTimeElapsedInPeriodMillis based on displayedTimeMillis changes.

        val initialActualTimeElapsed = _gameState.value.actualTimeElapsedInPeriodMillis
        val totalDurationForCurrentPhase = getDurationMillisForPhase(_gameState.value.currentPhase, _gameState.value.settings)

        gameCountDownTimer = object : CountDownTimer(durationMillisToRun, 250) {
            override fun onTick(millisUntilFinished: Long) {
                // Calculate time passed since this timer instance started
                val timePassedThisInstance = durationMillisToRun - millisUntilFinished
                _gameState.update {
                    it.copy(
                        displayedTimeMillis = millisUntilFinished,
                        // actualTimeElapsedInPeriodMillis is the sum of what had already passed
                        // before this timer instance started, plus what has passed in this instance.
                        actualTimeElapsedInPeriodMillis = initialActualTimeElapsed + timePassedThisInstance
                    )
                }
            }

            @RequiresPermission(Manifest.permission.VIBRATE)
            override fun onFinish() {
                _gameState.update {
                    it.copy(
                        displayedTimeMillis = 0,
                        actualTimeElapsedInPeriodMillis = totalDurationForCurrentPhase, // Ensure it's maxed out
                        isTimerRunning = false
                    )
                }
                vibrateDevice()
                handleTimerFinish() // This will call changePhase
                // saveState() is called within changePhase
            }
        }.start()
    }

    private fun handleTimerFinish() {
        // This method is called when the displayedTimeMillis reaches 0
        val currentPhase = _gameState.value.currentPhase
        addEvent(GameEvent.GenericLogEvent(message = "${currentPhase.name} Ended"))

        when (currentPhase) {
            GamePhase.FIRST_HALF -> changePhase(GamePhase.HALF_TIME)
            GamePhase.HALF_TIME -> changePhase(GamePhase.SECOND_HALF)
            GamePhase.SECOND_HALF -> changePhase(GamePhase.FULL_TIME)
            // Add Extra Time logic if implemented
            else -> { Log.w("GameViewModel", "Timer finished in unhandled phase: $currentPhase") }
        }
    }

    /**
     * Allows the referee to manually end the current active phase.
     */
    fun endCurrentPhaseEarly() {
        val currentPhase = _gameState.value.currentPhase

        if (currentPhase.hasDuration() && currentPhase != GamePhase.FULL_TIME && currentPhase != GamePhase.PRE_GAME) {
            pauseTimer() // Stop the timer first

            val phaseNameReadable = currentPhase.readable()
            addEvent(GameEvent.GenericLogEvent(
                message = "$phaseNameReadable ended early by referee.",
                gameTimeMillis = _gameState.value.actualTimeElapsedInPeriodMillis // Log actual time played
            ))

            // Directly call handleTimerFinish to transition to the next phase
            // This assumes handleTimerFinish correctly sets up the next phase.
            handleTimerFinish()
            Log.d("GameViewModel", "$phaseNameReadable ended early. Transitioning...")
        } else {
            Log.w("GameViewModel", "Attempted to end phase early in a non-active/non-timed state: $currentPhase")
        }
    }

    // --- Game Actions ---
    fun addGoal(team: Team) {
        if (!_gameState.value.currentPhase.isPlayablePhase()) return

        val newHomeScore = if (team == Team.HOME) _gameState.value.homeScore + 1 else _gameState.value.homeScore
        val newAwayScore = if (team == Team.AWAY) _gameState.value.awayScore + 1 else _gameState.value.awayScore
        val gameTime = _gameState.value.actualTimeElapsedInPeriodMillis

        val goalEvent = GameEvent.GoalScoredEvent(
            team = team,
            gameTimeMillis = gameTime,
            homeScoreAtTime = newHomeScore,
            awayScoreAtTime = newAwayScore
        )
        _gameState.update {
            it.copy(
                homeScore = newHomeScore,
                awayScore = newAwayScore,
                events = it.events + goalEvent
            )
        }
        saveState()
    }

    fun addCard(team: Team, playerNumber: Int, cardType: CardType) {
        if (!_gameState.value.currentPhase.isPlayablePhase()) return

        val gameTime = _gameState.value.actualTimeElapsedInPeriodMillis
        val cardEvent = GameEvent.CardIssuedEvent(
            team = team,
            playerNumber = playerNumber,
            cardType = cardType,
            gameTimeMillis = gameTime
        )
        _gameState.update {
            it.copy(events = it.events + cardEvent)
        }
        saveState()
    }

    private fun changePhase(newPhase: GamePhase) {
        pauseTimer() // Ensure timer is stopped if it was running for the old phase
        val settings = _gameState.value.settings
        val previousPhase = _gameState.value.currentPhase

        // gameTimeMillis for PhaseChangedEvent is the total duration of the *previous* phase if it just ended.
        // If manually changing phase or from a non-timed phase, it might be current actualTimeElapsed.
        val gameTimeForChangeEvent = if (previousPhase.hasDuration() && _gameState.value.displayedTimeMillis == 0L) {
            // This means the previous timed phase completed fully
            getDurationMillisForPhase(previousPhase, settings)
        } else {
            // Phase changed early or from a non-timed phase, log current elapsed time of previous phase
            _gameState.value.actualTimeElapsedInPeriodMillis
        }

        val phaseChangeEvent = GameEvent.PhaseChangedEvent(
            newPhase = newPhase,
            gameTimeMillis = gameTimeForChangeEvent
        )

        _gameState.update {
            it.copy(
                currentPhase = newPhase,
                displayedTimeMillis = getDurationMillisForPhase(newPhase, settings),
                actualTimeElapsedInPeriodMillis = 0L, // Reset elapsed time for the new phase
                isTimerRunning = false, // Timer should be explicitly started for new phase if needed
                events = it.events + phaseChangeEvent
            )
        }

        updateCurrentPeriodKickOffTeam(newPhase)

        // If the new phase has a duration and isn't FULL_TIME, and you want it to auto-start (e.g., halftime)
        if (newPhase.hasDuration() && newPhase != GamePhase.FULL_TIME && newPhase == GamePhase.HALF_TIME) { // Auto-start halftime
            startTimer()
        }
        // For FIRST_HALF and SECOND_HALF, timer is usually started by user (whistle)
        // unless confirmSettingsAndStartGame also calls startTimer.

        saveState()
    }

    private fun updateCurrentPeriodKickOffTeam(phase: GamePhase) {
        val initialKickOffTeam = _gameState.value.settings.kickOffTeam
        _gameState.update { currentState ->
            val newCurrentKickOff = when (phase) {
                GamePhase.FIRST_HALF -> initialKickOffTeam
                GamePhase.SECOND_HALF -> if (initialKickOffTeam == Team.HOME) Team.AWAY else Team.HOME
                // GamePhase.EXTRA_TIME_FIRST_HALF -> { /* Decide kick-off based on coin toss or rules */ initialKickOffTeam } // Example
                else -> currentState.settings.currentPeriodKickOffTeam
            }
            currentState.copy(settings = currentState.settings.copy(currentPeriodKickOffTeam = newCurrentKickOff))
        }
    }

    private fun getDurationMillisForPhase(phase: GamePhase, settings: GameSettings): Long {
        return when (phase) {
            GamePhase.FIRST_HALF, GamePhase.SECOND_HALF -> settings.halfDurationMillis
            GamePhase.HALF_TIME -> settings.halftimeDurationMillis
            // GamePhase.EXTRA_TIME_FIRST_HALF, GamePhase.EXTRA_TIME_SECOND_HALF -> settings.extraTimeHalfDurationMillis
            // GamePhase.EXTRA_TIME_HALF_TIME -> settings.extraTimeHalftimeDurationMillis
            else -> 0L
        }
    }

    // Helper extension on GamePhase enum (defined in models.kt or a utils file)
    // fun GamePhase.hasDuration(): Boolean { ... }
    // fun GamePhase.isPlayablePhase(): Boolean { ... }
    // fun GamePhase.name.readable(): String { return this.replace("_", " ").capitalizeWords() }

    private fun addEvent(event: GameEvent) { // Helper to add events
        _gameState.update {
            it.copy(events = it.events + event)
        }
    }

    fun resetGame() {
        pauseTimer()
        val currentSettings = _gameState.value.settings
        // Reset game state but keep configured team colors, half/halftime durations, and initial kick-off team
        _gameState.value = GameState(
            settings = GameSettings( // Create new settings but copy over user preferences
//                homeTeamColor = currentSettings.homeTeamColor,
//                awayTeamColor = currentSettings.awayTeamColor,
                halfDurationMinutes = currentSettings.halfDurationMinutes,
                halftimeDurationMinutes = currentSettings.halftimeDurationMinutes,
                kickOffTeam = currentSettings.kickOffTeam,
                currentPeriodKickOffTeam = currentSettings.kickOffTeam,
            )
        )
        // Set displayed time for PRE_GAME (which should be duration of first half initially)
        _gameState.update {
            it.copy(displayedTimeMillis = getDurationMillisForPhase(GamePhase.FIRST_HALF, it.settings))
        }
        updateCurrentPeriodKickOffTeam(GamePhase.PRE_GAME)
        saveState()
    }

    private fun saveState() {
        savedStateHandle["gameState"] = _gameState.value
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrateDevice() {
        // ... (vibration logic remains the same) ...
        try {
            if (vibrator?.hasVibrator() == false) return // Check if vibrator exists
            val timings = longArrayOf(0, 300, 200, 300) // Slightly longer pattern
            val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE)
            val effect =
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator?.vibrate(effect)
        } catch (e: Exception) {
            Log.e("GameViewModel", "Vibration failed", e)
        }
    }


    override fun onCleared() {
        super.onCleared()
        gameCountDownTimer?.cancel()
    }
}