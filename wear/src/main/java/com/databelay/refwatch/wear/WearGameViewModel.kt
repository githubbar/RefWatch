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
import kotlinx.coroutines.flow.*
import androidx.core.content.edit
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await

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
    private val gameStorage: GameStorageWear, // Hilt injects your singleton GameStorage
    private val dataClient: DataClient, // For syncing
    private val vibrator: Vibrator? // Vibrator for timer
) : AndroidViewModel(application) {
    private val TAG = "WearGameViewModel"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // =================== Scheduled Game List (Received from Phone) ===================
    // The ViewModel now gets the list by observing the flow from the injected GameStorage
    val scheduledGames: StateFlow<List<Game>> = gameStorage.gamesListFlow

    // =================== Active Game State ===================
    private val _activeGame = MutableStateFlow(loadInitialActiveGame())
    val activeGame: StateFlow<Game> = _activeGame.asStateFlow()

    // A unified map of all games known to the watch ---
    // It combines the scheduled list and the active game.
    val allGamesMap: StateFlow<Map<String, Game>> =
        combine(scheduledGames, activeGame) { scheduled, active ->
            val gameMap = scheduled.associateBy { it.id }.toMutableMap()
            // The active game always overwrites the scheduled version,
            // ensuring the log screen sees the most up-to-date "live" data.
            gameMap[active.id] = active
            gameMap
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )
    private var gameCountDownTimer: CountDownTimer? = null

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

    fun createNewDefaultGame() {
        pauseTimer()
        val newDefaultGame = Game(gameDateTimeEpochMillis = System.currentTimeMillis()) // Creates a game with a new unique ID and default values
        _activeGame.value = newDefaultGame // Set it as the active game immediately
        updateCurrentPeriodKickOffTeam(GamePhase.PRE_GAME, newDefaultGame.kickOffTeam)
        _activeGame.update {
            it.copy(displayedTimeMillis = getDurationMillisForPhase(GamePhase.FIRST_HALF, it))
        }
        Log.d(TAG, "New default game created with ID: ${newDefaultGame.id}. Setting as active and syncing to phone.")
        saveActiveGameState() // Save it to SavedStateHandle for session persistence

        // --- NEW LOGIC: Sync this new game to the phone ---
        syncNewAdHocGameToPhone(newDefaultGame)
    }

    private fun syncNewAdHocGameToPhone(newGame: Game) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = AppJsonConfiguration.encodeToString(newGame)
                val putDataMapReq = PutDataMapRequest.create(WearSyncConstants.NEW_AD_HOC_GAME_PATH)
                putDataMapReq.dataMap.putString(WearSyncConstants.NEW_GAME_PAYLOAD_KEY, jsonString)
                putDataMapReq.dataMap.putLong("timestamp", System.currentTimeMillis())
                putDataMapReq.setUrgent()

                dataClient.putDataItem(putDataMapReq.asPutDataRequest()).await()
                Log.i(TAG, "Successfully sent new ad-hoc game ${newGame.id} to phone.")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending new ad-hoc game to phone.", e)
            }
        }
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
            // sendActiveGameUpdateToPhone(_activeGame.value)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving active game state to JSON", e)
        }
    }

    // To be called when the user finishes a game.
    fun finishAndSyncActiveGame(onSyncComplete: () -> Unit) {
        Log.d(TAG, "finishAndSyncActiveGame called for game: ${_activeGame.value.id}")

        // The game is now considered completed.
        // We update the local activeGame state before sending it.
        val finalGameData = _activeGame.value.copy(
            currentPhase = GamePhase.GAME_ENDED, // Ensure it's marked as full time
            status = GameStatus.COMPLETED,      // <<< NEW: Mark the game as completed
            lastUpdated = System.currentTimeMillis()
        )

        // Update the local StateFlow immediately for responsive UI, even before sync completes
        _activeGame.value = finalGameData

        Log.d(TAG, "finishAndSyncActiveGame: Events in finalGameData before sending to phone: ${finalGameData.events.size} events. Details: ${finalGameData.events}")
        Log.d(TAG, "Game ${finalGameData.id} marked as COMPLETED. Syncing to phone.")


        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Serialize the *finalGameData* object which now has the COMPLETED status
                val jsonString = AppJsonConfiguration.encodeToString(finalGameData)
                val path = "${WearSyncConstants.GAME_UPDATE_FROM_WATCH_PATH_PREFIX}/${finalGameData.id}"
                // ... (rest of your DataClient logic to send the data)
                val putDataMapReq = PutDataMapRequest.create(path)
                putDataMapReq.dataMap.putString(WearSyncConstants.GAME_UPDATE_PAYLOAD_KEY, jsonString)
                // ...
                dataClient.putDataItem(putDataMapReq.asPutDataRequest()).await()
                Log.i(TAG, "Successfully sent final game state for game ${finalGameData.id} to phone.")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending final game state to phone.", e)
            } finally {
                launch(Dispatchers.Main) {
                    resetActiveGameToDefault()
                    onSyncComplete()
                }
            }
        }
    }


    // Add a helper to reset the active game to a neutral default state after a game is finished.
    fun resetActiveGameToDefault() {
        pauseTimer() // Ensure any lingering timers are stopped
        val newDefaultGame = Game() // Create a fresh default game
        _activeGame.value = newDefaultGame.copy(
            // Ensure displayed time is ready for the next pre-game setup
            displayedTimeMillis = getDurationMillisForPhase(GamePhase.FIRST_HALF, newDefaultGame)
        )
        saveActiveGameState() // Save the new default state
        Log.d(TAG, "Active game has been reset to default after finishing.")
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
            kickOffTeam = game.kickOffTeam
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
                kickOffTeam = team, // Also update for current pre-game/first-half
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
    fun kickOff() {
        val currentGame = _activeGame.value
        val teamName = if (currentGame.kickOffTeam == Team.HOME) currentGame.homeTeamName else currentGame.awayTeamName
        val kickOffMessage = "Kick Off - ${teamName} - ${currentGame.currentPhase.readable()}"
        val kickOffEvent = GenericLogEvent(message = kickOffMessage)

        _activeGame.update { it.copy(events = it.events + kickOffEvent) } // Log before changing phase
        startTimer() // Start the timer for the first half immediately after the kickoff
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

    fun startTimer() {
        val currentGame = _activeGame.value
        if (!currentGame.isTimerRunning && currentGame.displayedTimeMillis > 0 && currentGame.currentPhase.hasDuration()) {
            val startEvent = GenericLogEvent(message = "Timer Started for ${currentGame.currentPhase.readable()}")
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

    fun pauseTimer() {
        stopVibrate()
        if (_activeGame.value.isTimerRunning) {
            gameCountDownTimer?.cancel()
            val pauseEvent = GenericLogEvent(message = "Timer Paused")
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

    fun resetTimer() {
        pauseTimer()
        val currentGame = _activeGame.value
        _activeGame.update {
            it.copy(
                displayedTimeMillis = getDurationMillisForPhase(currentGame.currentPhase, currentGame),
                actualTimeElapsedInPeriodMillis = 0L, // Reset elapsed time for the current period
                lastUpdated = System.currentTimeMillis()
            )
        }
        saveActiveGameState()
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
            }
        }.start()
    }

    fun endCurrentPhase() {
        val currentGame = _activeGame.value
        pauseTimer() // Pause timer if running
        val earlyEndEvent = GenericLogEvent(
            message = "${currentGame.currentPhase.readable()} ended.",
            gameTimeMillis = currentGame.actualTimeElapsedInPeriodMillis.toDouble()
        )
        Log.d(TAG, "${currentGame.currentPhase.readable()} ended.")
        _activeGame.update { it.copy(events = it.events + earlyEndEvent) }

        val currentPhase = _activeGame.value.currentPhase

        when (currentPhase) {
            GamePhase.PRE_GAME ->  changePhase(GamePhase.KICK_OFF_SELECTION_FIRST_HALF)
            GamePhase.KICK_OFF_SELECTION_FIRST_HALF ->  changePhase(GamePhase.FIRST_HALF)
            GamePhase.FIRST_HALF -> changePhase(GamePhase.HALF_TIME)
            GamePhase.HALF_TIME -> changePhase(GamePhase.SECOND_HALF)
            GamePhase.SECOND_HALF ->
                if (_activeGame.value.hasExtraTime)
                    changePhase(GamePhase.KICK_OFF_SELECTION_EXTRA_TIME)
                else
                    changePhase(GamePhase.GAME_ENDED)
            GamePhase.KICK_OFF_SELECTION_EXTRA_TIME -> changePhase(GamePhase.EXTRA_TIME_FIRST_HALF)
            GamePhase.EXTRA_TIME_FIRST_HALF -> changePhase(GamePhase.EXTRA_TIME_HALF_TIME)
            GamePhase.EXTRA_TIME_HALF_TIME -> changePhase(GamePhase.EXTRA_TIME_SECOND_HALF)
            GamePhase.EXTRA_TIME_SECOND_HALF ->
                // If score is tied up after extra time move to penalties
                if (_activeGame.value.homeScore == _activeGame.value.awayScore) {
                    _activeGame.update { it.copy(hasPenalties = true) }
                    changePhase(GamePhase.KICK_OFF_SELECTION_PENALTIES)
                }
                else
                    changePhase(GamePhase.GAME_ENDED)
            GamePhase.KICK_OFF_SELECTION_PENALTIES -> changePhase(GamePhase.PENALTIES)
            GamePhase.PENALTIES -> changePhase(GamePhase.GAME_ENDED)

            else -> Log.w(TAG, "Timer finished in unhandled phase: $currentPhase")
        }
        saveActiveGameState()
    }

    fun addGoal(team: Team) {
        val currentGame = _activeGame.value
        if (!currentGame.currentPhase.isPlayablePhase()) return

        val newHomeScore = if (team == Team.HOME) currentGame.homeScore + 1 else currentGame.homeScore
        val newAwayScore = if (team == Team.AWAY) currentGame.awayScore + 1 else currentGame.awayScore
        val goalEvent = GoalScoredEvent(
            team = team,
            gameTimeMillis = currentGame.actualTimeElapsedInPeriodMillis.toDouble(),
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

        val cardEvent = CardIssuedEvent(
            team = team,
            playerNumber = playerNumber,
            cardType = cardType,
            gameTimeMillis = currentGame.actualTimeElapsedInPeriodMillis.toDouble()
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
        val phaseChangeEvent = PhaseChangedEvent(newPhase = newPhase, gameTimeMillis = gameTimeForChangeEvent.toDouble())

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

        if (newPhase == GamePhase.HALF_TIME || newPhase == GamePhase.EXTRA_TIME_HALF_TIME) { // Auto-start halftime timers
            startTimer()
        }
        saveActiveGameState() // Save state after all updates for the phase change
    }

    private fun updateCurrentPeriodKickOffTeam(phase: GamePhase, initialGameKickOffTeam: Team) {
        _activeGame.update { currentGame ->
            val newCurrentKickOff = when (phase) {
                GamePhase.SECOND_HALF -> if (initialGameKickOffTeam == Team.HOME) Team.AWAY else Team.HOME
                GamePhase.EXTRA_TIME_SECOND_HALF -> if (initialGameKickOffTeam == Team.HOME) Team.AWAY else Team.HOME
                else -> initialGameKickOffTeam // Keep existing for other phases
            }
            currentGame.copy(kickOffTeam = newCurrentKickOff)
            // No need to save state here, changePhase or other callers will save
        }
        Log.d("$TAG:updateCurrentPeriodKickOffTeam", "Updated kick-off team for phase $phase to ${_activeGame.value.kickOffTeam}")

    }

    /**
     * Records the result of a penalty attempt during a shootout.
     *
     * @param scored True if the penalty was scored, false otherwise.
     */
    fun recordPenaltyAttempt(scored: Boolean) {
        val currentGame = _activeGame.value
        val taker = currentGame.kickOffTeam // Get the calculated current taker

        if (currentGame.currentPhase != GamePhase.PENALTIES) {
            Log.w(TAG, "recordPenaltyAttempt called but game is not in PENALTIES phase.")
            return
        }

        Log.d(TAG, "Recording penalty attempt for ${taker.name}. Scored: $scored")

        _activeGame.update { game ->
            var newScoreHome = game.homeScore
            var newScoreAway = game.awayScore
            val newKickOffTeam = if (taker == Team.HOME) Team.AWAY else Team.HOME
            var updatedPenaltiesTakenHome = game.penaltiesTakenHome
            var updatedPenaltiesTakenAway = game.penaltiesTakenAway

            val eventMessage: String

            if (taker == Team.HOME) {
                updatedPenaltiesTakenHome++
                if (scored) {
                    newScoreHome++
                }
                eventMessage = "Penalty by ${game.homeTeamName} (${taker.name}): ${if (scored) "SCORED" else "MISSED/SAVED"}"
            } else { // taker == Team.AWAY
                updatedPenaltiesTakenAway++
                if (scored) {
                    newScoreAway++
                }
                eventMessage = "Penalty by ${game.awayTeamName} (${taker.name}): ${if (scored) "SCORED" else "MISSED/SAVED"}"
            }

            val penaltyEvent = GenericLogEvent( message = eventMessage)
            val updatedEvents = game.events + penaltyEvent

            // This is a critical piece.
            // Example conditions:
            // 1. After 5 kicks each, if scores are different.
            // 2. Before 5 kicks if one team has an unassailable lead
            //    (e.g., Home scores 3, Away misses 3; Home leads 3-0 with 2 kicks left for Away, Away can only reach 2).
            // 3. In sudden death (after 5 kicks each and scores are tied), if one team scores and the other misses in the same round.
            var newPhase = game.currentPhase
            if (checkShootoutEndCondition(newScoreHome, newScoreAway, updatedPenaltiesTakenHome, updatedPenaltiesTakenAway)) {
                newPhase = GamePhase.GAME_ENDED
                Log.i(TAG, "Penalty shootout ended. Final Score: H $newScoreHome - A $newScoreAway")
                // You might want to set a flag or specific event indicating the shootout winner
            }

            game.copy(
                homeScore = newScoreHome,
                awayScore = newScoreAway,
                penaltiesTakenHome = updatedPenaltiesTakenHome,
                penaltiesTakenAway = updatedPenaltiesTakenAway,
                events = updatedEvents,
                kickOffTeam = newKickOffTeam,
                currentPhase = newPhase // Update phase if shootout ended
            )
        }
        // syncActiveGameToMobile() // Sync after update
    }

    /**
     * Placeholder for logic to determine if the penalty shootout has concluded.
     * This needs to be implemented thoroughly.
     */
    private fun checkShootoutEndCondition(
        currentHomeScore: Int,
        currentAwayScore: Int,
        penaltiesTakenHome: Int,
        penaltiesTakenAway: Int,
        shootoutRoundLimit: Int = 5 // Standard is 5 rounds before sudden death
    ): Boolean {
        // Only check if both teams have taken the same number of penalties
        // or if one team has completed their set of 'shootoutRoundLimit' kicks
        // and the other team cannot catch up.

        // If less than shootoutRoundLimit kicks each, check for unassailable lead
        if (penaltiesTakenHome < shootoutRoundLimit || penaltiesTakenAway < shootoutRoundLimit) {
            val kicksRemainingHome = shootoutRoundLimit - penaltiesTakenHome
            val kicksRemainingAway = shootoutRoundLimit - penaltiesTakenAway

            // Home has unassailable lead
            if (currentHomeScore > currentAwayScore + kicksRemainingAway) return true
            // Away has unassailable lead
            if (currentAwayScore > currentHomeScore + kicksRemainingHome) return true
        }

        // After shootoutRoundLimit kicks (or during if unassailable lead isn't met but kicks are equal)
        if (penaltiesTakenHome >= shootoutRoundLimit && penaltiesTakenAway >= shootoutRoundLimit && penaltiesTakenHome == penaltiesTakenAway) {
            return currentHomeScore != currentAwayScore // If scores are different after 5 rounds, it's over
        }

        // Sudden death logic (both teams have taken same number of kicks beyond shootoutRoundLimit)
        if (penaltiesTakenHome > shootoutRoundLimit && penaltiesTakenHome == penaltiesTakenAway) {
            return currentHomeScore != currentAwayScore // In any sudden death round, if scores differ, it's over
        }

        return false // Shootout continues
    }

    private fun getDurationMillisForPhase(phase: GamePhase, gameSettings: Game): Long {
        return when (phase) {
            GamePhase.FIRST_HALF, GamePhase.SECOND_HALF -> gameSettings.halfDurationMillis
            GamePhase.HALF_TIME -> gameSettings.halftimeDurationMillis
            GamePhase.EXTRA_TIME_FIRST_HALF, GamePhase.EXTRA_TIME_SECOND_HALF -> gameSettings.extraTimeHalfDurationMillis
            GamePhase.EXTRA_TIME_HALF_TIME -> gameSettings.extraTimeHalftimeDurationMillis
            else -> 0L // Default for PRE_GAME, FULL_TIME
        }
    }
    fun setToHaveExtraTime() {
        // Mark that extra time will be played
        _activeGame.update { it.copy(hasExtraTime = true, lastUpdated = System.currentTimeMillis()) }
        saveActiveGameState()
    }

    fun setToHavePenalties() {
        // Mark that extra time will be played
        _activeGame.update { it.copy(hasPenalties = true, lastUpdated = System.currentTimeMillis()) }
        saveActiveGameState()
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrateDevice() {
        try {
            if (vibrator?.hasVibrator() == true) { // Check hasVibrator before using
                Log.d(TAG, "Starting continuous vibration")
                // Vibrate continuously: pattern of 500ms on, 500ms off, repeat indefinitely (index 0)
                val timings = longArrayOf(0, 300, 300) // Start immediately, on for 500ms, off for 500ms
                val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0) // Corresponding amplitudes
                val effect = VibrationEffect.createWaveform(timings, amplitudes, 0) // Repeat from index 0
                vibrator?.vibrate(effect)
            } else {
                Log.w(TAG, "No vibrator available or permission denied.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun stopVibrate() {
        try {
            if (vibrator?.hasVibrator() == true) {
                Log.d(TAG, "Stopping vibration")
                vibrator?.cancel()
            } else {
                Log.w(TAG, "No vibrator available to stop or permission denied.")
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

    fun updateHomeTeamName(name: String) {
        _activeGame.update { it.copy(homeTeamName = name, lastUpdated = System.currentTimeMillis()) }
        saveActiveGameState()
    }

    fun updateAwayTeamName(name: String) {
        _activeGame.update { it.copy(awayTeamName = name, lastUpdated = System.currentTimeMillis()) }
        saveActiveGameState()
    }
}
