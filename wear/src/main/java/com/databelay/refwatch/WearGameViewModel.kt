package com.databelay.refwatch // Your Wear OS package

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Assume this is your Wear OS specific storage helper (similar to phone's but for Wear)
// You'll need to implement this for Wear OS using SharedPreferences or DataStore
object WearAppStorage {
    private const val PREFS_NAME = "RefWatchWearPrefs"
    private const val KEY_SCHEDULED_GAMES = "scheduledGames"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun saveScheduledGames(context: Application, games: List<Game>) {
        try {
            val jsonString = json.encodeToString(games)
            context.getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE).edit()
                .putString(KEY_SCHEDULED_GAMES, jsonString).apply()
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


class WearGameViewModel(
    private val application: Application, // Inject Application context
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) { // Extend AndroidViewModel

    private val TAG = "WearGameVM"

    // JSON Serializer for SavedStateHandle if Game is not Parcelable
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // =================== Scheduled Game List (Received from Phone) ===================
    private val _scheduledGames = MutableStateFlow<List<Game>>(emptyList())
    val scheduledGames: StateFlow<List<Game>> = _scheduledGames.asStateFlow()

    fun updateScheduledGames(games: List<Game>) {
        _scheduledGames.update {
            // Assuming phone sends the full, sorted list. Overwrite.
            Log.d(TAG, "Updating scheduled games with ${games.size} new games.")
            games // Just take the new list from phone
        }
        // Persist this list locally on the watch
        WearAppStorage.saveScheduledGames(application, _scheduledGames.value)
    }

    private fun loadScheduledGamesFromPersistence() {
        viewModelScope.launch { // Load asynchronously
            val loadedGames = WearAppStorage.loadScheduledGames(application)
            _scheduledGames.value = loadedGames
            Log.d(TAG, "Loaded ${loadedGames.size} scheduled games from persistence.")
        }
    }

    // =================== Active Game State ===================
    // Game object itself holds all settings and live state.
    private val _activeGame = MutableStateFlow(loadInitialActiveGame())
    val activeGame: StateFlow<Game> = _activeGame.asStateFlow()

    private var gameCountDownTimer: CountDownTimer? = null
    private var vibrator: Vibrator? = null // Injected or set from Activity/Service

    fun setVibrator(v: Vibrator) {
        this.vibrator = v
    }

    init {
        Log.d(TAG, "GameViewModel initializing.")
        loadScheduledGamesFromPersistence() // Load list of games available for selection

        // Initialize active game state
        val initialGame = _activeGame.value
        val initialDisplayedTime = if (initialGame.isTimerRunning && initialGame.displayedTimeMillis > 0) {
            initialGame.displayedTimeMillis // Resume where it left off if timer was running
        } else {
            // Set to full duration of the current phase if timer wasn't running or phase is new
            getDurationMillisForPhase(initialGame.currentPhase, initialGame)
        }

        _activeGame.update {
            it.copy(displayedTimeMillis = initialDisplayedTime)
        }

        if (initialGame.isTimerRunning && initialDisplayedTime > 0) {
            Log.d(TAG, "Init: Timer was running for ${initialGame.currentPhase}, restarting.")
            startTimerLogic(initialDisplayedTime)
        }
        // Ensure currentPeriodKickOffTeam is correct based on initial phase
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
            Game() // Default new game
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

    private fun saveActiveGameState() {
        try {
            val activeGameJson = json.encodeToString(_activeGame.value)
            savedStateHandle["activeGameJson"] = activeGameJson
            Log.d(TAG, "Active game state saved to SavedStateHandle.")
            // TODO: Also send this _activeGame.value to the phone via DataClient
            // This is where you'd call a function to sync this specific game's state
            // sendActiveGameUpdateToPhone(_activeGame.value)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving active game state to JSON", e)
        }
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
        Log.d(TAG, "GameViewModel cleared, timer cancelled.")
    }
}

//package com.databelay.refwatch // Your package
//
//import android.Manifest
//import android.os.CountDownTimer
//import android.os.VibrationEffect
//import android.os.Vibrator
//import android.util.Log // Import Log
//import androidx.annotation.RequiresPermission
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.graphics.toArgb
//import androidx.lifecycle.SavedStateHandle
//import androidx.lifecycle.ViewModel
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.StateFlow
//import kotlinx.coroutines.flow.asStateFlow
//import kotlinx.coroutines.flow.update
//import com.databelay.refwatch.common.*
//class GameViewModel(
//    private val savedStateHandle: SavedStateHandle
//) : ViewModel() {
//
//    // =================== Scheduled Game List ===================
//    private val _scheduledGames = MutableStateFlow<List<Game>>(emptyList())
//    val scheduledGames: StateFlow<List<Game>> = _scheduledGames.asStateFlow()
//
//    fun addScheduledGames(games: List<Game>) {
//        _scheduledGames.update { currentGames ->
//            // Handle duplicates or merge logic if necessary
//            (currentGames + games).distinctBy { it.id }.sortedBy { it.gameDateTimeEpochMillis }
//        }
//        // Persist these games (e.g., to SharedPreferences as JSON, or a Room database)
//        saveScheduledGamesToPersistence()
//        Log.d("GameViewModel", "Added ${games.size} games. Total: ${_scheduledGames.value.size}")
//    }
//
//    private fun loadScheduledGamesFromPersistence() {
//        // Load games from SharedPreferences or Room on init
//        // _scheduledGames.value = a_list_of_games_from_storage
//    }
//
//    private fun saveScheduledGamesToPersistence() {
//        // Save _scheduledGames.value to SharedPreferences or Room
//    }
//// ...
//
//    // =================== Game State ===================
//    private val _gameState = MutableStateFlow(
//        savedStateHandle.get<GameState>("gameState") ?: GameState()
//    )
//    val gameState = _gameState.asStateFlow()
//
//    private var gameCountDownTimer: CountDownTimer? = null // Renamed for clarity
//    private var vibrator: Vibrator? = null
//
//    fun setVibrator(v: Vibrator) {
//        this.vibrator = v
//    }
//
//    init {
//        // If loading from saved state and timer was running, restart it.
//        // Also, ensure displayedTimeMillis is correct for the current phase.
//        val initialState = _gameState.value
//        val initialDisplayedTime = if (initialState.isTimerRunning && initialState.displayedTimeMillis > 0) {
//            initialState.displayedTimeMillis
//        } else {
//            getDurationMillisForPhase(initialState.currentPhase, initialState.settings)
//        }
//
//        _gameState.update {
//            it.copy(displayedTimeMillis = initialDisplayedTime)
//        }
//
//        if (initialState.isTimerRunning && initialDisplayedTime > 0) {
//            startTimerLogic(initialDisplayedTime)
//        }
//        updateCurrentPeriodKickOffTeam(initialState.currentPhase)
//    }
//
//    // When updating colors, use the ARGB value
//    fun updateHomeTeamColor(color: Color) {
//        _gameState.update { currentState ->
//            currentState.copy(
//                settings = currentState.settings.copy(homeTeamColorArgb = color.toArgb()) // Save ARGB Int
//            )
//        }
//        saveState()
//    }
//
//    fun updateAwayTeamColor(color: Color) {
//        _gameState.update { currentState ->
//            currentState.copy(
//                settings = currentState.settings.copy(awayTeamColorArgb = color.toArgb()) // Save ARGB Int
//            )
//        }
//        saveState()
//    }
//
//    fun setKickOffTeam(team: Team) {
//        _gameState.update {
//            it.copy(
//                settings = it.settings.copy(kickOffTeam = team, currentPeriodKickOffTeam = team)
//            )
//        }
//        saveState()
//    }
//
//    fun setHalfDuration(minutes: Int) {
//        _gameState.update { currentState ->
//            val newSettings = currentState.settings.copy(halfDurationMinutes = minutes)
//            var newDisplayedTime = currentState.displayedTimeMillis
//            // If in pre-game, or in first half and timer hasn't started/run, update display time
//            if (currentState.currentPhase == GamePhase.PRE_GAME ||
//                (currentState.currentPhase == GamePhase.FIRST_HALF && !currentState.isTimerRunning && currentState.actualTimeElapsedInPeriodMillis == 0L)
//            ) {
//                newDisplayedTime = newSettings.halfDurationMillis
//            } else if (currentState.currentPhase == GamePhase.FIRST_HALF || currentState.currentPhase == GamePhase.SECOND_HALF) {
//                // If timer is running, adjust remaining time if new duration is shorter
//                if (currentState.isTimerRunning && newSettings.halfDurationMillis < currentState.settings.halfDurationMillis) {
//                    val timeAlreadyElapsed = currentState.settings.halfDurationMillis - currentState.displayedTimeMillis
//                    newDisplayedTime = (newSettings.halfDurationMillis - timeAlreadyElapsed).coerceAtLeast(0L)
//                } else if (!currentState.isTimerRunning && currentState.actualTimeElapsedInPeriodMillis > 0L) {
//                    // Timer paused, recalculate displayed time based on new duration and what has passed
//                    newDisplayedTime = (newSettings.halfDurationMillis - currentState.actualTimeElapsedInPeriodMillis).coerceAtLeast(0L)
//                }
//            }
//            currentState.copy(settings = newSettings, displayedTimeMillis = newDisplayedTime)
//        }
//        saveState()
//    }
//
//    fun setHalftimeDuration(minutes: Int) {
//        _gameState.update { currentState ->
//            val newSettings = currentState.settings.copy(halftimeDurationMinutes = minutes)
//            var newDisplayedTime = currentState.displayedTimeMillis
//            if (currentState.currentPhase == GamePhase.HALF_TIME && !currentState.isTimerRunning && currentState.actualTimeElapsedInPeriodMillis == 0L) {
//                newDisplayedTime = newSettings.halftimeDurationMillis
//            } else if (currentState.currentPhase == GamePhase.HALF_TIME) {
//                if (currentState.isTimerRunning && newSettings.halftimeDurationMillis < currentState.settings.halftimeDurationMillis) {
//                    val timeAlreadyElapsed = currentState.settings.halftimeDurationMillis - currentState.displayedTimeMillis
//                    newDisplayedTime = (newSettings.halftimeDurationMillis - timeAlreadyElapsed).coerceAtLeast(0L)
//                } else if (!currentState.isTimerRunning && currentState.actualTimeElapsedInPeriodMillis > 0L) {
//                    newDisplayedTime = (newSettings.halftimeDurationMillis - currentState.actualTimeElapsedInPeriodMillis).coerceAtLeast(0L)
//                }
//            }
//            currentState.copy(settings = newSettings, displayedTimeMillis = newDisplayedTime)
//        }
//        saveState()
//    }
//
//    fun confirmSettingsAndStartGame() {
//        if (_gameState.value.currentPhase == GamePhase.PRE_GAME) {
//            // Log who kicks off
//            val kickOffTeam = _gameState.value.settings.kickOffTeam
//            addEvent(GameEvent.GenericLogEvent(message = "${kickOffTeam.name} kicks off 1st Half"))
//            changePhase(GamePhase.FIRST_HALF)
//            // Optionally auto-start timer here, or let user do it via toggleTimer
//            // startTimer()
//        }
//    }
//
//    // --- Timer Management ---
//    fun toggleTimer() {
//        if (_gameState.value.isTimerRunning) {
//            pauseTimer()
//        } else {
//            if (_gameState.value.currentPhase.hasDuration() && _gameState.value.displayedTimeMillis > 0) {
//                startTimer()
//            }
//        }
//    }
//
//    fun startTimer() { // Made private as it's called by toggleTimer or internally
//        val currentState = _gameState.value
//        if (!currentState.isTimerRunning && currentState.displayedTimeMillis > 0 && currentState.currentPhase.hasDuration()) {
//            _gameState.update { it.copy(isTimerRunning = true) }
//            startTimerLogic(currentState.displayedTimeMillis)
//            addEvent(GameEvent.GenericLogEvent(message = "Timer Started for ${currentState.currentPhase.name}"))
//            saveState()
//        }
//    }
//
//     fun pauseTimer() { // Made private
//        if (_gameState.value.isTimerRunning) {
//            gameCountDownTimer?.cancel()
//            _gameState.update { it.copy(isTimerRunning = false) }
//            addEvent(GameEvent.GenericLogEvent(message = "Timer Paused"))
//            saveState()
//        }
//    }
//
//    private fun startTimerLogic(durationMillisToRun: Long) {
//        gameCountDownTimer?.cancel() // Cancel any existing timer
//
//        // This is the crucial part: when the timer starts (or resumes),
//        // the actualTimeElapsedInPeriodMillis should reflect what has *already passed* for this period.
//        // displayedTimeMillis is what's left to count down.
//        // The onTick will update actualTimeElapsedInPeriodMillis based on displayedTimeMillis changes.
//
//        val initialActualTimeElapsed = _gameState.value.actualTimeElapsedInPeriodMillis
//        val totalDurationForCurrentPhase = getDurationMillisForPhase(_gameState.value.currentPhase, _gameState.value.settings)
//
//        gameCountDownTimer = object : CountDownTimer(durationMillisToRun, 250) {
//            override fun onTick(millisUntilFinished: Long) {
//                // Calculate time passed since this timer instance started
//                val timePassedThisInstance = durationMillisToRun - millisUntilFinished
//                _gameState.update {
//                    it.copy(
//                        displayedTimeMillis = millisUntilFinished,
//                        // actualTimeElapsedInPeriodMillis is the sum of what had already passed
//                        // before this timer instance started, plus what has passed in this instance.
//                        actualTimeElapsedInPeriodMillis = initialActualTimeElapsed + timePassedThisInstance
//                    )
//                }
//            }
//
//            @RequiresPermission(Manifest.permission.VIBRATE)
//            override fun onFinish() {
//                _gameState.update {
//                    it.copy(
//                        displayedTimeMillis = 0,
//                        actualTimeElapsedInPeriodMillis = totalDurationForCurrentPhase, // Ensure it's maxed out
//                        isTimerRunning = false
//                    )
//                }
//                vibrateDevice()
//                handleTimerFinish() // This will call changePhase
//                // saveState() is called within changePhase
//            }
//        }.start()
//    }
//
//    private fun handleTimerFinish() {
//        // This method is called when the displayedTimeMillis reaches 0
//        val currentPhase = _gameState.value.currentPhase
//        addEvent(GameEvent.GenericLogEvent(message = "${currentPhase.name} Ended"))
//
//        when (currentPhase) {
//            GamePhase.FIRST_HALF -> changePhase(GamePhase.HALF_TIME)
//            GamePhase.HALF_TIME -> changePhase(GamePhase.SECOND_HALF)
//            GamePhase.SECOND_HALF -> changePhase(GamePhase.FULL_TIME)
//            // Add Extra Time logic if implemented
//            else -> { Log.w("GameViewModel", "Timer finished in unhandled phase: $currentPhase") }
//        }
//    }
//
//    //  Allows the referee to manually end the current active phase.
//    fun endCurrentPhaseEarly() {
//        val currentPhase = _gameState.value.currentPhase
//
//        if (currentPhase.hasDuration() && currentPhase != GamePhase.FULL_TIME && currentPhase != GamePhase.PRE_GAME) {
//            pauseTimer() // Stop the timer first
//
//            val phaseNameReadable = currentPhase.readable()
//            addEvent(GameEvent.GenericLogEvent(
//                message = "$phaseNameReadable ended early by referee.",
//                gameTimeMillis = _gameState.value.actualTimeElapsedInPeriodMillis // Log actual time played
//            ))
//
//            // Directly call handleTimerFinish to transition to the next phase
//            // This assumes handleTimerFinish correctly sets up the next phase.
//            handleTimerFinish()
//            Log.d("GameViewModel", "$phaseNameReadable ended early. Transitioning...")
//        } else {
//            Log.w("GameViewModel", "Attempted to end phase early in a non-active/non-timed state: $currentPhase")
//        }
//    }
//
//    // --- Game Actions ---
//    fun addGoal(team: Team) {
//        if (!_gameState.value.currentPhase.isPlayablePhase()) return
//
//        val newHomeScore = if (team == Team.HOME) _gameState.value.homeScore + 1 else _gameState.value.homeScore
//        val newAwayScore = if (team == Team.AWAY) _gameState.value.awayScore + 1 else _gameState.value.awayScore
//        val gameTime = _gameState.value.actualTimeElapsedInPeriodMillis
//
//        val goalEvent = GameEvent.GoalScoredEvent(
//            team = team,
//            gameTimeMillis = gameTime,
//            homeScoreAtTime = newHomeScore,
//            awayScoreAtTime = newAwayScore
//        )
//        _gameState.update {
//            it.copy(
//                homeScore = newHomeScore,
//                awayScore = newAwayScore,
//                events = it.events + goalEvent
//            )
//        }
//        saveState()
//    }
//
//    fun addCard(team: Team, playerNumber: Int, cardType: CardType) {
//        if (!_gameState.value.currentPhase.isPlayablePhase()) return
//
//        val gameTime = _gameState.value.actualTimeElapsedInPeriodMillis
//        val cardEvent = GameEvent.CardIssuedEvent(
//            team = team,
//            playerNumber = playerNumber,
//            cardType = cardType,
//            gameTimeMillis = gameTime
//        )
//        _gameState.update {
//            it.copy(events = it.events + cardEvent)
//        }
//        saveState()
//    }
//
//    private fun changePhase(newPhase: GamePhase) {
//        pauseTimer() // Ensure timer is stopped if it was running for the old phase
//        val settings = _gameState.value.settings
//        val previousPhase = _gameState.value.currentPhase
//
//        // gameTimeMillis for PhaseChangedEvent is the total duration of the *previous* phase if it just ended.
//        // If manually changing phase or from a non-timed phase, it might be current actualTimeElapsed.
//        val gameTimeForChangeEvent = if (previousPhase.hasDuration() && _gameState.value.displayedTimeMillis == 0L) {
//            // This means the previous timed phase completed fully
//            getDurationMillisForPhase(previousPhase, settings)
//        } else {
//            // Phase changed early or from a non-timed phase, log current elapsed time of previous phase
//            _gameState.value.actualTimeElapsedInPeriodMillis
//        }
//
//        val phaseChangeEvent = GameEvent.PhaseChangedEvent(
//            newPhase = newPhase,
//            gameTimeMillis = gameTimeForChangeEvent
//        )
//
//        _gameState.update {
//            it.copy(
//                currentPhase = newPhase,
//                displayedTimeMillis = getDurationMillisForPhase(newPhase, settings),
//                actualTimeElapsedInPeriodMillis = 0L, // Reset elapsed time for the new phase
//                isTimerRunning = false, // Timer should be explicitly started for new phase if needed
//                events = it.events + phaseChangeEvent
//            )
//        }
//
//        updateCurrentPeriodKickOffTeam(newPhase)
//
//        // If the new phase has a duration and isn't FULL_TIME, and you want it to auto-start (e.g., halftime)
//        if (newPhase.hasDuration() && newPhase != GamePhase.FULL_TIME && newPhase == GamePhase.HALF_TIME) { // Auto-start halftime
//            startTimer()
//        }
//        // For FIRST_HALF and SECOND_HALF, timer is usually started by user (whistle)
//        // unless confirmSettingsAndStartGame also calls startTimer.
//
//        saveState()
//    }
//
//    private fun updateCurrentPeriodKickOffTeam(phase: GamePhase) {
//        val initialKickOffTeam = _gameState.value.settings.kickOffTeam
//        _gameState.update { currentState ->
//            val newCurrentKickOff = when (phase) {
//                GamePhase.FIRST_HALF -> initialKickOffTeam
//                GamePhase.SECOND_HALF -> if (initialKickOffTeam == Team.HOME) Team.AWAY else Team.HOME
//                // GamePhase.EXTRA_TIME_FIRST_HALF -> { /* Decide kick-off based on coin toss or rules */ initialKickOffTeam } // Example
//                else -> currentState.settings.currentPeriodKickOffTeam
//            }
//            currentState.copy(settings = currentState.settings.copy(currentPeriodKickOffTeam = newCurrentKickOff))
//        }
//    }
//
//    private fun getDurationMillisForPhase(phase: GamePhase, settings: Game): Long {
//        return when (phase) {
//            GamePhase.FIRST_HALF, GamePhase.SECOND_HALF -> settings.halfDurationMillis
//            GamePhase.HALF_TIME -> settings.halftimeDurationMillis
//            // GamePhase.EXTRA_TIME_FIRST_HALF, GamePhase.EXTRA_TIME_SECOND_HALF -> settings.extraTimeHalfDurationMillis
//            // GamePhase.EXTRA_TIME_HALF_TIME -> settings.extraTimeHalftimeDurationMillis
//            else -> 0L
//        }
//    }
//
//    // Helper extension on GamePhase enum (defined in models.kt or a utils file)
//    // fun GamePhase.hasDuration(): Boolean { ... }
//    // fun GamePhase.isPlayablePhase(): Boolean { ... }
//    // fun GamePhase.name.readable(): String { return this.replace("_", " ").capitalizeWords() }
//
//    private fun addEvent(event: GameEvent) { // Helper to add events
//        _gameState.update {
//            it.copy(events = it.events + event)
//        }
//    }
//
//    fun resetGame() {
//        pauseTimer()
//        val currentSettings = _gameState.value.settings
//        // Reset game state but keep configured team colors, half/halftime durations, and initial kick-off team
//        _gameState.value = GameState(
//            settings = Game( // Create new settings but copy over user preferences
////                homeTeamColor = currentSettings.homeTeamColor,
////                awayTeamColor = currentSettings.awayTeamColor,
//                halfDurationMinutes = currentSettings.halfDurationMinutes,
//                halftimeDurationMinutes = currentSettings.halftimeDurationMinutes,
//                kickOffTeam = currentSettings.kickOffTeam,
//                currentPeriodKickOffTeam = currentSettings.kickOffTeam,
//            )
//        )
//        // Set displayed time for PRE_GAME (which should be duration of first half initially)
//        _gameState.update {
//            it.copy(displayedTimeMillis = getDurationMillisForPhase(GamePhase.FIRST_HALF, it.settings))
//        }
//        updateCurrentPeriodKickOffTeam(GamePhase.PRE_GAME)
//        saveState()
//    }
//
//    private fun saveState() {
//        savedStateHandle["gameState"] = _gameState.value
//    }
//
//    @RequiresPermission(Manifest.permission.VIBRATE)
//    private fun vibrateDevice() {
//        // ... (vibration logic remains the same) ...
//        try {
//            if (vibrator?.hasVibrator() == false) return // Check if vibrator exists
//            val timings = longArrayOf(0, 300, 200, 300) // Slightly longer pattern
//            val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE)
//            val effect =
//                VibrationEffect.createWaveform(timings, amplitudes, -1)
//            vibrator?.vibrate(effect)
//        } catch (e: Exception) {
//            Log.e("GameViewModel", "Vibration failed", e)
//        }
//    }
//
//
//    override fun onCleared() {
//        super.onCleared()
//        gameCountDownTimer?.cancel()
//    }
//}