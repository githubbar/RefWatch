package com.databelay.refwatch.wear // Your Wear OS package

import android.annotation.SuppressLint
import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import com.databelay.refwatch.common.AppJsonConfiguration
import com.databelay.refwatch.common.CardIssuedEvent
import com.databelay.refwatch.common.CardType
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GameEvent
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.GameStatus
import com.databelay.refwatch.common.GenericLogEvent
import com.databelay.refwatch.common.GoalScoredEvent
import com.databelay.refwatch.common.IWearGameViewModel
import com.databelay.refwatch.common.PenaltyEvent
import com.databelay.refwatch.common.PhaseChangedEvent
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.canHaveAddedTime
import com.databelay.refwatch.common.formatTime
import com.databelay.refwatch.common.hasTimer
import com.databelay.refwatch.common.isBreak
import com.databelay.refwatch.common.isKickOffSelectionPhase
import com.databelay.refwatch.common.isPlayablePhase
import com.databelay.refwatch.common.needsKickOff
import com.databelay.refwatch.common.needsKickOffSelection
import com.databelay.refwatch.common.opposite
import com.databelay.refwatch.common.readable
import com.databelay.refwatch.common.toSnapshotForStorage
import com.databelay.refwatch.common.usesHalfDuration
import com.databelay.refwatch.wear.data.GameStorageWear
import com.databelay.refwatch.wear.data.GameTimerService
import com.databelay.refwatch.wear.util.ConnectivityObserver // For network status
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map // For mapping network status
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import javax.inject.Inject



import android.content.SharedPreferences

@OptIn(FlowPreview::class)
@HiltViewModel
class WearGameViewModel @Inject constructor(
    @ApplicationContext applicationContext: Context,
    private val savedStateHandle: SavedStateHandle,
    private val gameStorage: GameStorageWear,
    private val vibrator: Vibrator?,
    private val prefs: SharedPreferences
) : AndroidViewModel(applicationContext as Application), IWearGameViewModel {
    private val tag = "WearGameViewModel"

    private val _collectPositionInfo = MutableStateFlow(prefs.getBoolean("collect_position_info", false))
    override val collectPositionInfo: StateFlow<Boolean> = _collectPositionInfo.asStateFlow()

    fun setCollectPositionInfo(enabled: Boolean) {
        prefs.edit { putBoolean("collect_position_info", enabled) }
        _collectPositionInfo.value = enabled
    }

    override val gamesList: StateFlow<List<Game>> = gameStorage.gamesListFlow
        .onEach { // DEBUG LOGGING
//            Log.d(tag, "gamesList updated. Total games: ${list.size}")
//            list.forEach { game ->
//                Log.d(
//                    tag,
//                    "Game in gamesList - ID: ${game.id}, Status: ${game.status}, Score: ${game.homeScore}-${game.awayScore}, Events: ${game.events.size}"
//                )
//            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        ) // Ensure initialValue and proper stateIn usage

    override val isOnline: StateFlow<Boolean> = gameStorage.networkStatusFlow.map {
        it == ConnectivityObserver.Status.AVAILABLE
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Initialize _activeGame as potentially null or with a loading state initially
    private val _activeGame = MutableStateFlow<Game?>(null) // Start as null
    override val activeGame: StateFlow<Game?> = _activeGame.asStateFlow() // Expose as nullable


    private var isCurrentGameSessionActive = false

    // Keep track of whether the reminder vibration is currently supposed to be active
    private var isAddedTimeReminderVibrating = false
    @SuppressLint("StaticFieldLeak")
    private var gameTimerService: GameTimerService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(tag, "GameTimerService connected")
            val binder = service as GameTimerService.LocalBinder
            gameTimerService = binder.getService()
            isServiceBound = true

            gameTimerService?.timerStateFlow
                ?.distinctUntilChanged { oldState, newState ->
                    oldState.isTimerRunning == newState.isTimerRunning &&
                            oldState.displayedMillis == newState.displayedMillis &&
                            oldState.actualTimeElapsedInPeriodMillis == newState.actualTimeElapsedInPeriodMillis &&
                            oldState.inAddedTime == newState.inAddedTime &&
                            oldState.latestHeartRate?.timestamp == newState.latestHeartRate?.timestamp &&
                            oldState.latestSteps?.timestamp == newState.latestSteps?.timestamp &&
                            oldState.latestLocation?.timestamp == newState.latestLocation?.timestamp
                }
                ?.onEach { serviceState ->
                    val currentActiveGame = _activeGame.value

                    // Only update if phases match to avoid race conditions during transitions where
                    // service might still be reporting the end-state of a previous phase (like 5min halftime)
                    // while the VM has already moved to the next phase (like 2nd half).
                    if (currentActiveGame != null && serviceState.currentPhase != currentActiveGame.currentPhase) {
                        Log.d(tag, "Ignoring service update due to phase mismatch: Service=${serviceState.currentPhase}, VM=${currentActiveGame.currentPhase}")
                        return@onEach
                    }

                    if (serviceState.inAddedTime && currentActiveGame?.currentPhase?.canHaveAddedTime() == true) {
                        if (!isAddedTimeReminderVibrating) {
                            startAddedTimeReminderVibration()
                        }
                    } else {
                        if (isAddedTimeReminderVibrating) {
                            stopAddedTimeReminderVibration()
                        }
                    }

                    _activeGame.update { currentGame ->
                        if (currentGame == null) return@update null
                        
                        // --- DOWNSAMPLING LOGIC to prevent 1MB Firestore limit ---
                        // Only append if it's been at least 5 seconds since the last sample
                        
                        val newLocations = if (serviceState.latestLocation != null && currentGame.isAssistantReferee == false) {
                            val lastLoc = currentGame.locationHistory.lastOrNull()
                            if (lastLoc == null || (serviceState.latestLocation.timestamp - lastLoc.timestamp) >= 5000) {
                                currentGame.locationHistory + serviceState.latestLocation
                            } else currentGame.locationHistory
                        } else currentGame.locationHistory

                        val newHR = if (serviceState.latestHeartRate != null) {
                            val lastHR = currentGame.heartRateHistory.lastOrNull()
                            if (lastHR == null || (serviceState.latestHeartRate.timestamp - lastHR.timestamp) >= 5000) {
                                currentGame.heartRateHistory + serviceState.latestHeartRate
                            } else currentGame.heartRateHistory
                        } else currentGame.heartRateHistory

                        // Steps are deltas, we should always append if the timestamp is new to not lose steps
                        val newSteps = if (serviceState.latestSteps != null && currentGame.stepHistory.lastOrNull()?.timestamp != serviceState.latestSteps.timestamp) {
                            currentGame.stepHistory + serviceState.latestSteps
                        } else currentGame.stepHistory

                        currentGame.copy(
                            isTimerRunning = serviceState.isTimerRunning,
                            displayedTimeMillis = serviceState.displayedMillis,
                            actualTimeElapsedInPeriodMillis = serviceState.actualTimeElapsedInPeriodMillis,
                            inAddedTime = serviceState.inAddedTime,
                            locationHistory = newLocations,
                            heartRateHistory = newHR,
                            stepHistory = newSteps
                        )
                    }
                }
                ?.launchIn(viewModelScope)

            val currentActiveGameOnConnect = _activeGame.value
            currentActiveGameOnConnect?.let { gameToConfigure ->
                if (gameToConfigure.status == GameStatus.IN_PROGRESS) {
                    Log.d(
                        tag,
                        "Service connected. Current game phase: ${gameToConfigure.currentPhase}, timer running: ${gameToConfigure.isTimerRunning}"
                    )
                    gameTimerService?.configureTimerForGame(
                        game = gameToConfigure,
                        startImmediately = gameToConfigure.isTimerRunning
                    )
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(tag, "GameTimerService disconnected")
            gameTimerService = null
            isServiceBound = false
        }
    }

    init {
        Log.d(tag, "WearGameViewModel initializing.")

        viewModelScope.launch {
            val initialGames = gameStorage.gamesListFlow
                .filter { it.isNotEmpty() }
                .stateIn(viewModelScope)
                .first()

            val loadedGame = loadInitialActiveGameInternal(initialGames)
            _activeGame.value = loadedGame

            loadedGame.let { game ->
                _activeGame.update { current ->
                    current?.copy(
                        displayedTimeMillis = calculateInitialDisplayTime(game)
                    )
                }
            }
        }

        _activeGame.filterNotNull()
            .onEach { game ->
                if (game.status != GameStatus.COMPLETED) {
                    saveActiveGameStateToHandle()
                }
            }.launchIn(viewModelScope)

        _activeGame.filterNotNull()
            .debounce(750L) 
            .onEach { latestGameToSave ->
                // Move heavy snapshot and saving logic to Dispatchers.Default
                viewModelScope.launch(Dispatchers.Default) {
                    val snapshot = latestGameToSave.toSnapshotForStorage()
                    if (latestGameToSave.status != GameStatus.COMPLETED) {
                        Log.i(tag, "Significant change for game ${snapshot.id}. Persisting to gameStorage.")
                        gameStorage.addOrUpdateGame(latestGameToSave)
                    }
                }
            }.launchIn(viewModelScope)


        isOnline.onEach { online ->
            Log.i(tag, "Network status in ViewModel: ${if (online) "Online" else "Offline"}")
        }.launchIn(viewModelScope)

        bindToGameTimerService()
    }


    private fun startForegroundService() {
        val intent = Intent(getApplication(), GameTimerService::class.java)
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    private fun calculateInitialDisplayTime(game: Game): Long { 
        var initialDisplayTime = game.regulationPeriodDurationMillis()
        if (!game.isTimerRunning && game.currentPhase.hasTimer()) {
            val regulationDuration = game.regulationPeriodDurationMillis()
            initialDisplayTime = if (game.actualTimeElapsedInPeriodMillis >= regulationDuration) {
                game.actualTimeElapsedInPeriodMillis - regulationDuration
            } else {
                regulationDuration - game.actualTimeElapsedInPeriodMillis
            }
        }
        return initialDisplayTime
    }

    private fun bindToGameTimerService() {
        if (!isServiceBound && gameTimerService == null) {
            Intent(getApplication(), GameTimerService::class.java).also { intent ->
                getApplication<Application>().bindService(
                    intent,
                    serviceConnection,
                    Context.BIND_AUTO_CREATE
                )
            }
        }
    }

    private fun unbindFromGameTimerService() {
        if (isServiceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isServiceBound = false
            gameTimerService = null
            Log.d(tag, "Unbound from GameTimerService.")
        }
    }
    private var addedTimeReminderJob: Job? = null

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun startAddedTimeReminderVibration() {
        if (vibrator?.hasVibrator() != true) {
            Log.w(tag, "No vibrator available to start reminder.")
            return
        }
        stopAddedTimeReminderVibration() // Cancel any previous job

        isAddedTimeReminderVibrating = true
        addedTimeReminderJob = viewModelScope.launch {
            while (isAddedTimeReminderVibrating && isActive) { // Check isActive for coroutine cancellation
                Log.d(tag, "ADDED_TIME_REMINDER: Triggering one-shot vibration.")
                // Use a version of your pattern that doesn't repeat indefinitely
                val oneShotPattern = VibrationEffect.createWaveform(
                    longArrayOf(0, 150, 50, 150), // Shorter, or your full pattern once
                    intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE),
                    -1 // No repeat
                )
                vibrator.vibrate(oneShotPattern)
                delay(5000) // Vibrate every 5 seconds (adjust interval as needed)
            }
            Log.d(tag, "Added time reminder job loop finished. isAddedTimeReminderVibrating: $isAddedTimeReminderVibrating")
        }
        Log.d(tag, "ADDED_TIME_REMINDER one-shot mechanism started with repeating job.")
    }

    private fun stopAddedTimeReminderVibration() {
        addedTimeReminderJob?.cancel()
        addedTimeReminderJob = null
        // It's still good to call vibrator.cancel() here to stop any immediate vibration
        // if the job is cancelled mid-vibration.
        vibrator?.cancel()
        isAddedTimeReminderVibrating = false
        Log.d(tag, "ADDED_TIME_REMINDER vibration mechanism stopped (job cancelled, vibrator cancelled).")
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(tag, "WearGameViewModel onCleared")
        stopAddedTimeReminderVibration()
        unbindFromGameTimerService()
    }

    private fun loadInitialActiveGameInternal(currentGames: List<Game>): Game {
        val activeGameId: String? = savedStateHandle["activeGameId"]
        activeGameId?.let { id ->
            val gameFromList = currentGames.find { it.id == id }
            if (gameFromList != null) {
                Log.d(tag, "Loaded active game from list using ID from SavedStateHandle: $id")
                return gameFromList
            }
        }

        // Fallback: check for the old activeGameJson just in case of an update during a game
        val savedGameJson: String? = savedStateHandle["activeGameJson"]
        savedGameJson?.let { json ->
            try {
                val gameFromState = AppJsonConfiguration.decodeFromString<Game>(json)
                Log.d(tag, "Loaded active game from SavedStateHandle (legacy JSON): ${gameFromState.id}")
                return gameFromState
            } catch (e: Exception) {
                Log.e(tag, "Error decoding game from SavedStateHandle", e)
            }
        }

        Log.d(tag, "No active game found in SavedStateHandle, trying from provided scheduled games.")
        val firstScheduledGame = currentGames.firstOrNull { it.status == GameStatus.SCHEDULED }

        return firstScheduledGame?.copy(
            currentPhase = GamePhase.NOT_STARTED,
            homeScore = 0, awayScore = 0, events = emptyList()
        ) ?: Game().also {
            Log.e(
                tag,
                "LOAD_INITIAL_ACTIVE_GAME_INTERNAL: No game from state or scheduled. Using new default. ID: ${it.id}"
            )
        }
    }

    private fun saveActiveGameStateToHandle() {
        _activeGame.value?.let { game ->
            savedStateHandle["activeGameId"] = game.id
        }
    }

    /**
     * Adds a new game event to the active game.
     * This will update the activeGame StateFlow with a new Game instance.
     */
    fun addEvent(event: GameEvent) {
        var updatedGameInstance: Game? = null
        _activeGame.update { currentGame ->
            val gameAfterEventAdded = currentGame?.addEvent(event)
            updatedGameInstance = gameAfterEventAdded
            gameAfterEventAdded
        }

        updatedGameInstance?.let { gameToSave ->
            viewModelScope.launch {
                gameStorage.addOrUpdateGame(gameToSave) // Update in storage
                Log.d(tag, "Event added to active game ${gameToSave.id} and saved to storage.")
            }
        }
        Log.d(tag, "Event added: ${event.displayString}. Current events count: ${_activeGame.value?.events?.size}")
    }
    /**
     * Removes a game event.
     * If gameId is null (default), it removes the event from the currently active game.
     * If gameId is provided, it removes the event from the game with that specific ID.
     * The updated game is saved to storage, which should refresh gamesList and _activeGame if affected.
     * Assumes Game.removeEvent handles internal consistency (e.g., score adjustments).
     *
     * @param eventToRemove The event object to remove.
     * @param gameId The ID of the game to remove the event from. Defaults to null, targeting the active game.
     */
    fun removeEvent(eventToRemove: GameEvent, gameId: String? = null) { // Combined function
        viewModelScope.launch {
            val gameToModify: Game?
            val modifyingActiveGame: Boolean

            if (gameId == null) {
                // Target the active game
                gameToModify = _activeGame.value
                modifyingActiveGame = true
                if (gameToModify == null) {
                    Log.w(tag, "removeEvent called for active game, but activeGame is null.")
                    return@launch
                }
            } else {
                // Target a specific game by ID
                gameToModify = gamesList.value.firstOrNull { it.id == gameId }
                modifyingActiveGame = _activeGame.value?.id == gameId
                if (gameToModify == null) {
                    Log.w(tag, "removeEvent: Game with ID $gameId not found in gamesList.")
                    return@launch
                }
            }

            // Remove the event from this specific game instance
            val updatedGame = gameToModify.removeEvent(eventToRemove) // Assumes Game.removeEvent handles score/stats

            if (updatedGame == gameToModify) {
                // This means the event wasn't found in targetGame.removeEvent or no change was made
                Log.w(tag, "removeEvent: Event ${eventToRemove.id} (or equivalent) not found in game ${gameToModify.id}, or no change made.")
                return@launch
            }

            // Save the updated game back to storage
            gameStorage.addOrUpdateGame(updatedGame)
            Log.d(tag, "Game ${updatedGame.id} updated in storage after removing event: ${eventToRemove.displayString}.")

            // If the active game was modified (either directly or because its ID matched the provided gameId),
            // update _activeGame StateFlow directly for immediate UI consistency.
            if (modifyingActiveGame) {
                _activeGame.value = updatedGame
                Log.d(tag, "Active game ${updatedGame.id} was updated after event removal.")
            }
        }
        // Log outside the coroutine for immediate feedback, though the actual update is async
        Log.d(tag, "removeEvent called for event: ${eventToRemove.displayString}, gameId: $gameId. Events in active game: ${_activeGame.value?.events?.size}")
    }


    fun createNewDefaultGame() {
        cancelTimer()
        val newDefaultGame = Game(gameDateTimeEpochMillis = System.currentTimeMillis())
        _activeGame.value = newDefaultGame.copy(
            displayedTimeMillis = newDefaultGame.regulationPeriodDurationMillis(GamePhase.FIRST_HALF),
        )
        Log.d(tag, "New default game created with ID: ${newDefaultGame.id}. Setting as active.")
    }

    fun selectGameToStart(gameFromList: Game) {
        cancelTimer()
        Log.d(tag, "Selecting game from list to start: ${gameFromList.id}")
        val cleanGameForStart = gameFromList.copy(
            currentPhase = GamePhase.NOT_STARTED,
            homeScore = 0,
            awayScore = 0,
            displayedTimeMillis = gameFromList.regulationPeriodDurationMillis(GamePhase.FIRST_HALF),
            actualTimeElapsedInPeriodMillis = 0L,
            isTimerRunning = false,
            events = emptyList(),
            lastUpdated = System.currentTimeMillis()
        )
        _activeGame.value = cleanGameForStart
        Log.d(tag, "Selected game ${cleanGameForStart.id} set as active.")
    }

    fun finishAndSyncActiveGame(gameId: String) {
        viewModelScope.launch {
            cancelTimer()

            val gameToFinish = _activeGame.value?.takeIf { it.id == gameId }
                ?: gamesList.value.firstOrNull { it.id == gameId }

            if (gameToFinish == null) {
                Log.w(tag, "finishAndSyncActiveGame: Game with ID $gameId not found to finish.")
                return@launch
            }

            val finishedGame = gameToFinish.copy(
                isTimerRunning = false,
                displayedTimeMillis = 0L, 
                actualTimeElapsedInPeriodMillis = gameToFinish.actualTimeElapsedInPeriodMillis
            )

            _activeGame.update {
                if (it?.id == finishedGame.id) {
                    finishedGame
                } else {
                    it
                }
            }

            gameStorage.addOrUpdateGame(finishedGame)
            Log.i(tag, "Game ${finishedGame.id} marked as COMPLETED and saved.")

            if (_activeGame.value?.id == finishedGame.id && _activeGame.value?.status == GameStatus.COMPLETED) {
                Log.d(tag, "The active game is now the one just completed: ${finishedGame.id}")
            }
        }
    }

    @Deprecated("GameStorageWear handles sync automatically. This function may be removed.")
    fun attemptSyncPendingGames() {
        Log.w(
            tag,
            "attemptSyncPendingGames called, but this is now primarily handled by GameStorageWear."
        )
    }

    fun toggleTimer() {
        val currentGame = _activeGame.value ?: return
        val currentPhase = currentGame.currentPhase
        vibrate(VibrationPattern.GENERIC_EVENT)

        if (!currentPhase.hasTimer()) {
            Log.w(
                tag,
                "toggleTimer called in a non-timed phase: ${currentPhase.readable()}. No action."
            )
            return
        }

        if (currentGame.status == GameStatus.IN_PROGRESS && !currentGame.isTimerRunning && !isCurrentGameSessionActive) {
            Log.i(
                tag,
                "toggleTimer: Starting NEW GAME SESSION for phase ${currentPhase.readable()}."
            )
            startForegroundService()
            gameTimerService?.commandStartGameSessionAndTimer(currentGame, currentGame.actualTimeElapsedInPeriodMillis)
            isCurrentGameSessionActive = true
        }

        if (currentGame.isTimerRunning) {
            gameTimerService?.pauseGameTimer(reason = "Paused: ${currentPhase.readable()}")
            Log.d(tag, "Timer PAUSED for ${currentPhase.readable()}.")
        } else {
            if (currentGame.status != GameStatus.IN_PROGRESS) {
                Log.w(
                    tag,
                    "Attempted to start timer for a game not in progress: ${currentPhase.readable()}."
                )
                return
            }
            Log.d(tag, "ViewModel about to call service.resumeGameTimer. Game details: ID=${currentGame.id}, Phase=${currentGame.currentPhase}, Elapsed=${currentGame.actualTimeElapsedInPeriodMillis}, IsTimerRunning=${currentGame.isTimerRunning}, InAddedTime=${currentGame.inAddedTime}")
            startForegroundService()
            gameTimerService?.resumeGameTimer(currentGame)
            Log.d(tag, "Timer RESUMED for ${currentPhase.readable()}.")
            if (!isCurrentGameSessionActive) { 
                startForegroundService()
                gameTimerService?.commandStartGameSessionAndTimer(currentGame, currentGame.actualTimeElapsedInPeriodMillis)
                isCurrentGameSessionActive = true
            }
        }
    }

    fun cancelTimer() {
        Log.d(tag, "cancelTimer called in ViewModel.")
        gameTimerService?.commandStopGameSessionAndCleanup {
            stopAddedTimeReminderVibration()
        }
        isCurrentGameSessionActive = false
        _activeGame.update {
            it?.copy(isTimerRunning = false)
        }
    }

    fun proceedToNextPhaseManager(gameAtPeriodEndInput: Game) {
        val gameAtPeriodEnd = gameAtPeriodEndInput.copy(isTimerRunning = false)

        if (gameAtPeriodEnd.currentPhase.isPlayablePhase()) {
            val regulationDur = gameAtPeriodEnd.regulationPeriodDurationMillis()
            if (gameAtPeriodEnd.actualTimeElapsedInPeriodMillis > regulationDur) {
                val addedTimePlayed =
                    gameAtPeriodEnd.actualTimeElapsedInPeriodMillis - regulationDur
                Log.i(
                    tag,
                    "Period ${gameAtPeriodEnd.currentPhase} ended. Added time played: ${addedTimePlayed.formatTime()}"
                )
            }
        }

        val lastPhaseKickOffTeam = gameAtPeriodEnd.kickOffTeam
        val nextPhase: GamePhase = when (gameAtPeriodEnd.currentPhase) {
            GamePhase.NOT_STARTED -> GamePhase.PRE_GAME
            GamePhase.PRE_GAME -> GamePhase.KICK_OFF_SELECTION_FIRST_HALF
            GamePhase.KICK_OFF_SELECTION_FIRST_HALF -> GamePhase.FIRST_HALF
            GamePhase.FIRST_HALF -> GamePhase.HALF_TIME
            GamePhase.HALF_TIME -> GamePhase.SECOND_HALF
            GamePhase.SECOND_HALF -> {
                if (gameAtPeriodEnd.hasExtraTime) {
                    GamePhase.KICK_OFF_SELECTION_EXTRA_TIME
                } else if (gameAtPeriodEnd.hasPenalties && gameAtPeriodEnd.isTied) {
                    GamePhase.KICK_OFF_SELECTION_PENALTIES
                } else {
                    GamePhase.GAME_ENDED
                }
            }
            GamePhase.KICK_OFF_SELECTION_EXTRA_TIME -> GamePhase.EXTRA_TIME_FIRST_HALF
            GamePhase.EXTRA_TIME_FIRST_HALF -> GamePhase.EXTRA_TIME_HALF_TIME
            GamePhase.EXTRA_TIME_HALF_TIME -> GamePhase.EXTRA_TIME_SECOND_HALF
            GamePhase.EXTRA_TIME_SECOND_HALF -> if (gameAtPeriodEnd.homeScore == gameAtPeriodEnd.awayScore && gameAtPeriodEnd.hasPenalties) GamePhase.KICK_OFF_SELECTION_PENALTIES else GamePhase.GAME_ENDED
            GamePhase.KICK_OFF_SELECTION_PENALTIES -> GamePhase.PENALTIES
            GamePhase.PENALTIES -> GamePhase.GAME_ENDED
            else -> gameAtPeriodEnd.currentPhase
        }

        val newKickOffTeam = when (nextPhase) {
            GamePhase.FIRST_HALF, GamePhase.EXTRA_TIME_FIRST_HALF, GamePhase.PENALTIES -> lastPhaseKickOffTeam
            GamePhase.SECOND_HALF, GamePhase.EXTRA_TIME_SECOND_HALF -> lastPhaseKickOffTeam.opposite()
            else -> lastPhaseKickOffTeam
        }

        val updatedGame = gameAtPeriodEnd.copy(
            currentPhase = nextPhase,
            actualTimeElapsedInPeriodMillis = 0L,
            inAddedTime = false,
            displayedTimeMillis = gameAtPeriodEnd.regulationPeriodDurationMillis(nextPhase),
            kickOffTeam = newKickOffTeam,
            lastUpdated = System.currentTimeMillis()
        )

        // Add PhaseChangedEvent for better analytics filtering
        val phaseEvent = PhaseChangedEvent(
            newPhase = nextPhase,
            timestamp = System.currentTimeMillis().toDouble(),
            gameTimeMillis = gameAtPeriodEnd.actualTimeElapsedInPeriodMillis.toDouble()
        )
        _activeGame.update { it?.copy(
            currentPhase = nextPhase,
            actualTimeElapsedInPeriodMillis = 0L,
            inAddedTime = false,
            displayedTimeMillis = updatedGame.displayedTimeMillis,
            kickOffTeam = newKickOffTeam,
            lastUpdated = System.currentTimeMillis(),
            events = it.events + phaseEvent
        ) }

        if (updatedGame.currentPhase == GamePhase.GAME_ENDED) {
            Log.i(tag, "Game Ended phase reached. Stopping timer.")
            cancelTimer()
        } else {
            if (updatedGame.currentPhase.isBreak()) {
                startForegroundService()
            }
            gameTimerService?.configureTimerForGame(
                game = updatedGame,
                startImmediately = updatedGame.currentPhase.isBreak()
            )
        }

        Log.i(
            tag,
            "Phase ${gameAtPeriodEnd.currentPhase} ended. New phase: ${updatedGame.currentPhase}. Kick-off: ${updatedGame.kickOffTeam}"
        )
    }


    fun setKickOffTeam(team: Team) {
        _activeGame.update {
            it?.copy(
                kickOffTeam = team,
                lastUpdated = System.currentTimeMillis()
            )
        }
        vibrate(VibrationPattern.GENERIC_EVENT)
        Log.d(tag, "Kick-off team for current context set to $team")
    }


    fun kickOff() {
        val currentGame = _activeGame.value ?: return
        val currentPhase = currentGame.currentPhase

        if (currentPhase.needsKickOff()) {
            val teamName =
                if (currentGame.kickOffTeam == Team.HOME) currentGame.homeTeamName else currentGame.awayTeamName
            val kickOffMessage = "Kick Off - $teamName - ${currentPhase.readable()}"
            val kickOffEvent = GenericLogEvent(message = kickOffMessage)
            Log.i(tag, kickOffMessage)
            // Use the new addGameEventToList method
            addEvent(kickOffEvent)
            startForegroundService()
            gameTimerService?.startGameTimer(currentGame, currentGame.actualTimeElapsedInPeriodMillis, currentGame.inAddedTime)
            vibrate(VibrationPattern.GENERIC_EVENT)
        } else {
            Log.w(tag, "KickOff action attempted in inappropriate phase: $currentPhase")
        }
    }


    fun setToHaveExtraTime() {
        _activeGame.update {
            it?.copy(
                hasExtraTime = true,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    fun setToHavePenalties() {
        _activeGame.update {
            it?.copy(
                hasPenalties = true,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    fun addGoal(team: Team) {
        val currentGame = _activeGame.value ?: return
        if (!currentGame.currentPhase.isPlayablePhase()) return

        val newHomeScore =
            if (team == Team.HOME) currentGame.homeScore + 1 else currentGame.homeScore
        val newAwayScore =
            if (team == Team.AWAY) currentGame.awayScore + 1 else currentGame.awayScore
        val goalEvent = GoalScoredEvent(
            team = team,
            gameTimeMillis = currentGame.actualTimeElapsedInPeriodMillis.toDouble(),
            homeScoreAtTime = newHomeScore,
            awayScoreAtTime = newAwayScore
        )
        // Update game state including the new event via _activeGame.update, then add to list via specific method
        _activeGame.update {
            it?.copy(
                homeScore = newHomeScore,
                awayScore = newAwayScore
                // events list will be updated by addGameEventToList
            )?.addEvent(goalEvent) // Call addEvent from Game.kt which returns the new Game state
        }
        vibrate(VibrationPattern.GOAL_SCORED)
        Log.d(
            tag,
            "Goal added for $team. Score: ${_activeGame.value?.homeScore}-${_activeGame.value?.awayScore}"
        )
    }


    fun addCard(team: Team, playerNumber: Int, cardType: CardType) {
        val currentGame = _activeGame.value ?: return
        if (!currentGame.currentPhase.isPlayablePhase()) return

        val cardEvent = CardIssuedEvent(
            team = team,
            playerNumber = playerNumber,
            cardType = cardType,
            gameTimeMillis = currentGame.actualTimeElapsedInPeriodMillis.toDouble()
        )
        // Update game state by calling addEvent from Game.kt via _activeGame.update
        _activeGame.update { 
            it?.addEvent(cardEvent) // Call addEvent from Game.kt which returns the new Game state
        }
    }

    fun updateHomeTeamName(name: String) {
        _activeGame.update {
            it?.copy(
                homeTeamName = name,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    fun updateAwayTeamName(name: String) {
        _activeGame.update {
            it?.copy(
                awayTeamName = name,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    fun updateHomeTeamColor(color: Color) {
        _activeGame.update {
            it?.copy(
                homeTeamColorArgb = color.toArgb(),
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    fun updateAwayTeamColor(color: Color) {
        _activeGame.update {
            it?.copy(
                awayTeamColorArgb = color.toArgb(),
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    fun setHalfDuration(minutes: Int) {
        _activeGame.update { currentGame ->
            currentGame?.let { game ->
                val newHalfDurationMillis = minutes * 60 * 1000L
                var newDisplayedTime = game.displayedTimeMillis
                if ((game.currentPhase == GamePhase.PRE_GAME || game.currentPhase.isKickOffSelectionPhase()) && !game.isTimerRunning) {
                    newDisplayedTime = newHalfDurationMillis
                } else if (game.currentPhase.usesHalfDuration() && !game.isTimerRunning) {
                    val timeAlreadyElapsed = game.actualTimeElapsedInPeriodMillis
                    newDisplayedTime =
                        (newHalfDurationMillis - timeAlreadyElapsed).coerceAtLeast(0L)
                }
                game.copy(
                    halfDurationMinutes = minutes,
                    displayedTimeMillis = newDisplayedTime,
                    lastUpdated = System.currentTimeMillis()
                )
            }
        }
    }

    fun setHalftimeDuration(minutes: Int) {
        _activeGame.update { currentGame ->
            currentGame?.let { game ->
                var newDisplayedTime = game.displayedTimeMillis
                if (game.currentPhase == GamePhase.HALF_TIME && !game.isTimerRunning) {
                    newDisplayedTime =
                        (minutes * 60 * 1000L - game.actualTimeElapsedInPeriodMillis).coerceAtLeast(
                            0L
                        )
                }
                game.copy(
                    halftimeDurationMinutes = minutes,
                    displayedTimeMillis = newDisplayedTime,
                    lastUpdated = System.currentTimeMillis()
                )
            }
        }
    }

    fun resetGame() {
        val originalGame = _activeGame.value ?: run {
            Log.w(
                tag,
                "resetGame called but no active game to reset. Creating new default game instead."
            )
            createNewDefaultGame()
            return
        }
        Log.d(tag, "Reset game called for game: ${originalGame.id}")
        cancelTimer()

        val resetGame = Game(
            id = originalGame.id,
            gameDateTimeEpochMillis = originalGame.gameDateTimeEpochMillis
        )
            .copy(
                gameNumber = originalGame.gameNumber,
                fieldNumber = originalGame.fieldNumber,
                homeTeamName = originalGame.homeTeamName,
                awayTeamName = originalGame.awayTeamName,
                ageGroup = originalGame.ageGroup,
                halfDurationMinutes = originalGame.halfDurationMinutes,
                halftimeDurationMinutes = originalGame.halftimeDurationMinutes,
                hasExtraTime = originalGame.hasExtraTime,
                hasPenalties = originalGame.hasPenalties,
                kickOffTeam = originalGame.kickOffTeam,
                displayedTimeMillis = Game().regulationPeriodDurationMillis(GamePhase.FIRST_HALF),
                currentPhase = GamePhase.NOT_STARTED,
            )
        _activeGame.value = resetGame
    }

    fun resetTimer() {
        val gameBeforeReset = _activeGame.value ?: return
        cancelTimer()
        _activeGame.update {
            it?.copy(
                displayedTimeMillis = gameBeforeReset.regulationPeriodDurationMillis(gameBeforeReset.currentPhase),
                actualTimeElapsedInPeriodMillis = 0L,
                lastUpdated = System.currentTimeMillis()
            )
        }
        val gameAfterReset = _activeGame.value ?: return
        val resetMessage =
            "Timer for the period ${gameAfterReset.currentPhase.readable()} has been reset."
        val resetEvent = GenericLogEvent(message = resetMessage)
        // Use the new addGameEventToList method
        addEvent(resetEvent)

        gameTimerService?.configureTimerForGame(
            game = gameAfterReset,
            startImmediately = false
        ) 
    }

    fun recordPenaltyAttempt(scored: Boolean) {
        val currentGame = _activeGame.value ?: return
        val taker = currentGame.kickOffTeam

        if (currentGame.currentPhase != GamePhase.PENALTIES) {
            Log.w(tag, "recordPenaltyAttempt called but game is not in PENALTIES phase.")
            return
        }
        Log.d(tag, "Recording penalty attempt for ${taker.name}. Scored: $scored")
        vibrate(VibrationPattern.GOAL_SCORED)

        _activeGame.update { game ->
            game?.let {
                var newScoreHome = it.homeScore
                var newScoreAway = it.awayScore
                val newKickOffTeamForNext = it.kickOffTeam.opposite()
                val updatedPenaltiesTakenHome: Int
                val updatedPenaltiesTakenAway: Int

                if (taker == Team.HOME) {
                    updatedPenaltiesTakenHome = it.penaltiesTakenHome + 1
                    updatedPenaltiesTakenAway = it.penaltiesTakenAway
                    if (scored) newScoreHome++
                } else { 
                    updatedPenaltiesTakenHome = it.penaltiesTakenHome
                    updatedPenaltiesTakenAway = it.penaltiesTakenAway + 1
                    if (scored) newScoreAway++
                }
                val penaltyEvent = PenaltyEvent(
                    team = taker,
                    gameTimeMillis = game.actualTimeElapsedInPeriodMillis.toDouble(),
                    homeScoreAtTime = newScoreHome,
                    awayScoreAtTime = newScoreAway,
                    scored = scored,
                )

                var newPhase = it.currentPhase
                if (checkShootoutEndCondition(
                        newScoreHome,
                        newScoreAway,
                        updatedPenaltiesTakenHome,
                        updatedPenaltiesTakenAway
                    )
                ) {
                    newPhase = GamePhase.GAME_ENDED
                    Log.i(
                        tag,
                        "Penalty shootout ended. Final Score: H $newScoreHome - A $newScoreAway"
                    )
                }

                // Apply score and penalty count changes directly, then add event using Game's method
                it.copy(
                    homeScore = newScoreHome,
                    awayScore = newScoreAway,
                    penaltiesTakenHome = updatedPenaltiesTakenHome,
                    penaltiesTakenAway = updatedPenaltiesTakenAway,
                    kickOffTeam = newKickOffTeamForNext,
                    currentPhase = newPhase
                    // events list will be updated by addEvent
                ).addEvent(penaltyEvent) // Call addEvent from Game.kt
            }
        }
    }


    private fun checkShootoutEndCondition(
        currentHomeScore: Int, currentAwayScore: Int,
        penaltiesTakenHome: Int, penaltiesTakenAway: Int,
        shootoutRoundLimit: Int = 5
    ): Boolean {
        if (penaltiesTakenHome >= shootoutRoundLimit && penaltiesTakenAway >= shootoutRoundLimit) {
            return currentHomeScore != currentAwayScore && penaltiesTakenHome == penaltiesTakenAway
        } else {
            val kicksRemainingHome = shootoutRoundLimit - penaltiesTakenHome
            val kicksRemainingAway = shootoutRoundLimit - penaltiesTakenAway

            if (currentHomeScore > currentAwayScore + kicksRemainingAway) return true
            if (currentAwayScore > currentHomeScore + kicksRemainingHome) return true
            return false
        }
    }


    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrate(pattern: VibrationPattern) {
        if (vibrator?.hasVibrator() == true) {
            val effect = when (pattern) {
                VibrationPattern.ADDED_TIME_REMINDER -> VibrationEffect.createWaveform(
                    longArrayOf(0, 150, 50, 150, 450, 150, 50, 150), // pattern: buzz-buzz --- buzz-buzz
                    intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE),
                    0 // Repeat from the start of the pattern (index 0)
                )

                VibrationPattern.GOAL_SCORED -> VibrationEffect.createWaveform(
                    longArrayOf(
                        0,
                        150,
                        50,
                        150,
                        50
                    ), -1
                )

                VibrationPattern.GENERIC_EVENT -> VibrationEffect.createOneShot(
                    200,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            }
            vibrator.vibrate(effect)
        }
    }

    enum class VibrationPattern {
        ADDED_TIME_REMINDER, GOAL_SCORED, GENERIC_EVENT
    }
}
