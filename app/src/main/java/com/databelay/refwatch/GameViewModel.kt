package com.databelay.refwatch

import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Constants for SavedStateHandle (optional if you use serializable GameState directly)
// private const val GAME_STATE_KEY = "gameStateKey_v1" // Increment version if structure changes

class GameViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _gameState = MutableStateFlow(
        // For SavedStateHandle to work with custom classes, they need to be Parcelable or use custom Saver.
        // For simplicity here, we'll re-init if not found or use a simpler persistence strategy.
        // A robust app would make GameState Parcelable or use a custom Saver.
        // For now, we just load if present, otherwise default.
        savedStateHandle.get<GameState>("gameState") ?: GameState()
    )
    val gameState = _gameState.asStateFlow()

    private var countDownTimer: CountDownTimer? = null
    private var vibrator: Vibrator? = null

    fun setVibrator(v: Vibrator) {
        this.vibrator = v
    }

    init {
        // Initialize displayedTimeMillis based on current phase if loading from saved state
        _gameState.update {
            it.copy(displayedTimeMillis = getDurationMillisForPhase(it.currentPhase, it.settings))
        }
        // If timer was running and app was killed, restart it (simplified approach)
        if (_gameState.value.isTimerRunning) {
            startTimerLogic(_gameState.value.displayedTimeMillis)
        }
        updateCurrentPeriodKickOffTeam(_gameState.value.currentPhase) // Ensure kick-off team is correct
    }

    // --- Pre-Game Setup ---
    fun updateHomeTeamColor(color: Color) {
        _gameState.update { it.copy(settings = it.settings.copy(homeTeamColor = color)) }
        saveState()
    }

    fun updateAwayTeamColor(color: Color) {
        _gameState.update { it.copy(settings = it.settings.copy(awayTeamColor = color)) }
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
        _gameState.update {
            val newSettings = it.settings.copy(halfDurationMinutes = minutes)
            val newDisplayedTime = if (it.currentPhase == GamePhase.PRE_GAME ||
                (it.currentPhase == GamePhase.FIRST_HALF && !it.isTimerRunning && it.actualTimeElapsedInPeriodMillis == 0L)
            ) {
                newSettings.halfDurationMillis
            } else {
                it.displayedTimeMillis // Keep current if timer running or already started
            }
            it.copy(settings = newSettings, displayedTimeMillis = newDisplayedTime)
        }
        saveState()
    }

    fun setHalftimeDuration(minutes: Int) {
        _gameState.update {
            val newSettings = it.settings.copy(halftimeDurationMinutes = minutes)
            val newDisplayedTime = if (it.currentPhase == GamePhase.HALF_TIME && !it.isTimerRunning && it.actualTimeElapsedInPeriodMillis == 0L) {
                newSettings.halftimeDurationMillis
            } else {
                it.displayedTimeMillis
            }
            it.copy(settings = newSettings, displayedTimeMillis = newDisplayedTime)
        }
        saveState()
    }

    fun confirmSettingsAndStartGame() {
        if (_gameState.value.currentPhase == GamePhase.PRE_GAME) {
            changePhase(GamePhase.FIRST_HALF)
        }
    }

    // --- Timer Management ---
    fun toggleTimer() {
        if (_gameState.value.isTimerRunning) {
            pauseTimer()
        } else {
            // Only start timer if the phase is one that has a timed duration
            if (_gameState.value.currentPhase.hasDuration() && _gameState.value.displayedTimeMillis > 0) {
                startTimer()
            }
        }
    }

    private fun startTimer() {
        if (!_gameState.value.isTimerRunning && _gameState.value.displayedTimeMillis > 0) {
            _gameState.update { it.copy(isTimerRunning = true) }
            startTimerLogic(_gameState.value.displayedTimeMillis)
            saveState()
        }
    }

    private fun pauseTimer() {
        if (_gameState.value.isTimerRunning) {
            countDownTimer?.cancel()
            _gameState.update { it.copy(isTimerRunning = false) }
            saveState()
        }
    }

    private fun startTimerLogic(durationMillis: Long) {
        countDownTimer?.cancel()
        val startTime = System.currentTimeMillis()
        countDownTimer = object : CountDownTimer(durationMillis, 250) { // Tick more frequently for smoother UI
            override fun onTick(millisUntilFinished: Long) {
                _gameState.update {
                    val elapsedSinceLastTick = it.displayedTimeMillis - millisUntilFinished
                    it.copy(
                        displayedTimeMillis = millisUntilFinished,
                        actualTimeElapsedInPeriodMillis = it.actualTimeElapsedInPeriodMillis + elapsedSinceLastTick
                    )
                }
                // Save state less frequently to avoid performance issues, e.g., every few seconds or on pause
            }

            override fun onFinish() {
                _gameState.update {
                    it.copy(
                        displayedTimeMillis = 0,
                        actualTimeElapsedInPeriodMillis = getDurationMillisForPhase(it.currentPhase, it.settings), // Ensure it's maxed out
                        isTimerRunning = false
                    )
                }
                vibrateDevice()
                handleTimerFinish()
                saveState()
            }
        }.start()
    }

    private fun handleTimerFinish() {
        when (_gameState.value.currentPhase) {
            GamePhase.FIRST_HALF -> changePhase(GamePhase.HALF_TIME)
            GamePhase.HALF_TIME -> changePhase(GamePhase.SECOND_HALF)
            GamePhase.SECOND_HALF -> changePhase(GamePhase.FULL_TIME)
            // Add Extra Time logic if implemented
            else -> { /* Do nothing or log error */
            }
        }
    }

    // --- Game Actions ---
    fun addGoal(team: Team) {
        if (!_gameState.value.currentPhase.isPlayablePhase()) return

        val newHomeScore = if (team == Team.HOME) _gameState.value.homeScore + 1 else _gameState.value.homeScore
        val newAwayScore = if (team == Team.AWAY) _gameState.value.awayScore + 1 else _gameState.value.awayScore
        val gameTime = _gameState.value.actualTimeElapsedInPeriodMillis

        val goalEvent = GoalScoredEvent(
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
        val cardEvent = CardIssuedEvent(
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

    fun proceedToNextPhaseManually() {
        // This is for manually moving, e.g., if ref blows whistle early or for testing
        val currentPhase = _gameState.value.currentPhase
        if (_gameState.value.isTimerRunning) pauseTimer() // Pause timer before changing phase

        when (currentPhase) {
            GamePhase.FIRST_HALF -> changePhase(GamePhase.HALF_TIME)
            GamePhase.HALF_TIME -> changePhase(GamePhase.SECOND_HALF)
            GamePhase.SECOND_HALF -> changePhase(GamePhase.FULL_TIME)
            else -> {} // Can't manually proceed from PRE_GAME or FULL_TIME this way
        }
    }

    private fun changePhase(newPhase: GamePhase) {
        pauseTimer() // Ensure timer is stopped
        val settings = _gameState.value.settings
        val phaseChangeEvent = PhaseChangedEvent(
            newPhase = newPhase,
            // gameTimeMillis is the duration of the *previous* phase if it ended
            gameTimeMillis = if (_gameState.value.currentPhase.hasDuration()) getDurationMillisForPhase(_gameState.value.currentPhase, settings) else 0L
        )

        _gameState.update {
            it.copy(
                currentPhase = newPhase,
                displayedTimeMillis = getDurationMillisForPhase(newPhase, settings),
                actualTimeElapsedInPeriodMillis = 0L, // Reset elapsed time for the new period
                isTimerRunning = false,
                events = it.events + phaseChangeEvent
            )
        }
        updateCurrentPeriodKickOffTeam(newPhase)
        saveState()
    }

    private fun updateCurrentPeriodKickOffTeam(phase: GamePhase) {
        val initialKickOffTeam = _gameState.value.settings.kickOffTeam
        _gameState.update { currentState ->
            val newCurrentKickOff = when (phase) {
                GamePhase.FIRST_HALF -> initialKickOffTeam
                GamePhase.SECOND_HALF -> if (initialKickOffTeam == Team.HOME) Team.AWAY else Team.HOME
                // Define for other phases if needed (e.g., extra time)
                else -> currentState.settings.currentPeriodKickOffTeam // Keep current for other phases
            }
            currentState.copy(settings = currentState.settings.copy(currentPeriodKickOffTeam = newCurrentKickOff))
        }
    }


    private fun getDurationMillisForPhase(phase: GamePhase, settings: GameSettings): Long {
        return when (phase) {
            GamePhase.FIRST_HALF, GamePhase.SECOND_HALF -> settings.halfDurationMillis
            GamePhase.HALF_TIME -> settings.halftimeDurationMillis
            // GamePhase.EXTRA_TIME_FIRST_HALF, GamePhase.EXTRA_TIME_SECOND_HALF -> settings.extraTimeHalfDurationMillis
            // GamePhase.EXTRA_TIME_HALF_TIME -> 5 * 60 * 1000L // e.g. 5 mins
            else -> 0L // PRE_GAME, FULL_TIME
        }
    }

    private fun GamePhase.hasDuration(): Boolean {
        return this == GamePhase.FIRST_HALF || this == GamePhase.SECOND_HALF || this == GamePhase.HALF_TIME
        // || this == GamePhase.EXTRA_TIME_FIRST_HALF || ...
    }

    private fun GamePhase.isPlayablePhase(): Boolean {
        return this == GamePhase.FIRST_HALF || this == GamePhase.SECOND_HALF
        // || this == GamePhase.EXTRA_TIME_FIRST_HALF || ...
    }

    fun resetGame() {
        pauseTimer()
        val defaultSettings = GameState().settings // Keep original default settings idea
        _gameState.value = GameState(settings = defaultSettings.copy( // Preserve user chosen colors if desired, or fully reset
            homeTeamColor = _gameState.value.settings.homeTeamColor,
            awayTeamColor = _gameState.value.settings.awayTeamColor
        ))
        updateCurrentPeriodKickOffTeam(GamePhase.PRE_GAME)
        saveState()
    }

    // --- Utility ---
    private fun saveState() {
        savedStateHandle["gameState"] = _gameState.value
    }

    private fun vibrateDevice() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 200, 100, 200) // Vibrate pattern: off, on, off, on
                val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE)
                // -1 means do not repeat
                val effect = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                } else {
                    VibrationEffect.createWaveform(timings, -1)
                }
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 200, 100, 200), -1)
            }
        } catch (e: Exception) {
            // Log or handle error, e.g. if vibrator service not available
            println("Vibration failed: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        countDownTimer?.cancel()
    }
}