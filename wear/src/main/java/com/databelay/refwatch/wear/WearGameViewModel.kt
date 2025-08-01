package com.databelay.refwatch.wear // Your Wear OS package

import android.Manifest
import android.R
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.databelay.refwatch.common.AppJsonConfiguration
import com.databelay.refwatch.common.CardIssuedEvent
import com.databelay.refwatch.common.CardType
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.GameStatus
import com.databelay.refwatch.common.GenericLogEvent
import com.databelay.refwatch.common.GoalScoredEvent
import com.databelay.refwatch.common.PhaseChangedEvent
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.WearSyncConstants
import com.databelay.refwatch.common.formatTime
import com.databelay.refwatch.common.hasDuration
import com.databelay.refwatch.common.isBreak
import com.databelay.refwatch.common.isPlayablePhase
import com.databelay.refwatch.common.readable
import com.databelay.refwatch.wear.data.DataFetchStatus
import com.databelay.refwatch.wear.data.GameStorageWear
import com.databelay.refwatch.wear.utils.ONGOING_NOTIFICATION_CHANNEL_ID
import com.databelay.refwatch.wear.utils.ONGOING_NOTIFICATION_ID_VM
import com.databelay.refwatch.wear.utils.createOngoingTimerNotificationChannel
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import javax.inject.Inject


@HiltViewModel
class WearGameViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val application: Application, // For NodeClient/CapabilityClient if used here
    private val savedStateHandle: SavedStateHandle,
    private val gameStorage: GameStorageWear,
    private val dataClient: DataClient, // For syncing game updates
    private val vibrator: Vibrator?
) : AndroidViewModel(application) {
    // FIXME: store ad-hoc game in a local cache (show in completed even if not connected to phone,sync on connection)

    private val TAG = "WearGameViewModel"

    // --- Scheduled Games List and Sync Status (from GameStorageWear) ---
    val gamesList: StateFlow<List<Game>> = gameStorage.gamesListFlow
    val dataFetchStatus: StateFlow<DataFetchStatus> = gameStorage.dataFetchStatusFlow

    // --- Active Game State (managed by this ViewModel) ---
    private val _activeGame = MutableStateFlow(loadInitialActiveGame())
    val activeGame: StateFlow<Game> = _activeGame.asStateFlow()

    private var gameCountDownTimer: CountDownTimer? = null

    // Combined map of all games (scheduled and the current active one)
    // Useful for screens that might need to look up any game by ID.
    val allGamesMap: StateFlow<Map<String, Game>> =
        combine(gamesList, _activeGame) { scheduled, active ->
            val gameMap = scheduled.associateBy { it.id }.toMutableMap()
            // The active game instance (potentially with live updates) overwrites any scheduled version
            gameMap[active.id] = active
            gameMap
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    init {
        Log.d(TAG, "WearGameViewModel initializing.")
        // Observe game state to manage OngoingActivity
        viewModelScope.launch {
            activeGame.collectLatest { game -> // Use collectLatest to restart if game instance changes
                manageOngoingActivity(game)
            }
        }
        // Log status changes from GameStorageWear
        viewModelScope.launch {
            dataFetchStatus.collect { status ->
                Log.i(TAG, "DataFetchStatus collected in ViewModel: $status")
                // Potentially trigger actions based on status, e.g., if phone reconnects
                if (status == DataFetchStatus.INITIAL && gamesList.value.isEmpty()) {
                    // If status is INITIAL (e.g., after phone reconnects) and we have no games,
                    // we might want to proactively try fetching or inform GameStorageWear.
                    // performConnectivityCheck() // Could call this, or let UI trigger it
                }
            }
        }

        viewModelScope.launch {
            gamesList.collect { games ->
                Log.i(TAG, "Scheduled games list collected in ViewModel: ${games.size} games")
                // If the active game was based on a scheduled game that got updated/removed,
                // you might need to handle that here. For now, activeGame is independent once selected/created.
            }
        }

        // Initialize timer and kick-off team for the initially loaded active game
        val initialGame = _activeGame.value
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

        Log.d(TAG, "Initial active game ID: ${initialGame.id}, Phase: ${initialGame.currentPhase}")
    }

    private fun manageOngoingActivity(game: Game) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (game.isTimerRunning) {
            // Create channel if needed (though Application class is better for one-time setup)
            createOngoingTimerNotificationChannel(applicationContext)

            val activityIntent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                applicationContext, 0, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val ongoingActivityStatus = Status.Builder()
                // Use addTemplate to set the primary text (often the time)
                .addTemplate("# ${game.displayedTimeMillis.formatTime()}")
                // Use addPart to add secondary information if needed.
                // The key "phase" is arbitrary but good for clarity.
                .addPart("phase", Status.TextPart("Phase: ${game.currentPhase.readable()}"))
                .build()

            val notificationBuilder = NotificationCompat.Builder(applicationContext, ONGOING_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_overlay)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setContentTitle("RefWatch Active")
                .setContentText("Timer: ${game.displayedTimeMillis.formatTime()}")
                .setContentIntent(pendingIntent)

            val ongoingActivity = OngoingActivity.Builder(applicationContext, ONGOING_NOTIFICATION_ID_VM, notificationBuilder)
                .setStaticIcon(R.drawable.ic_notification_overlay)
                .setTouchIntent(pendingIntent)
                .setStatus(ongoingActivityStatus)
                .build()

            ongoingActivity.apply(applicationContext)
        } else {
            notificationManager.cancel(ONGOING_NOTIFICATION_ID_VM)
        }
    }

    private fun loadInitialActiveGame(): Game {
        val savedGameJson: String? = savedStateHandle.get("activeGameJson")
        return if (savedGameJson != null) {
            try {
                Log.d(TAG, "Loading active game from SavedStateHandle.")
                AppJsonConfiguration.decodeFromString<Game>(savedGameJson)
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
            val activeGameJson = AppJsonConfiguration.encodeToString(_activeGame.value)
            savedStateHandle["activeGameJson"] = activeGameJson
            Log.d(TAG, "Active game state saved to SavedStateHandle. ID: ${_activeGame.value.id}")
            // Consider if/when to send active game updates to the phone here or more explicitly
            // sendActiveGameUpdateToPhone(_activeGame.value)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving active game state to JSON", e)
        }
    }

    fun createNewDefaultGame() {
        pauseTimer()
        val newDefaultGame = Game(gameDateTimeEpochMillis = System.currentTimeMillis())
        _activeGame.value = newDefaultGame
        updateCurrentPeriodKickOffTeam(GamePhase.PRE_GAME, newDefaultGame.kickOffTeam)
        _activeGame.update {
            it.copy(
                displayedTimeMillis = getDurationMillisForPhase(GamePhase.FIRST_HALF, it),
                status = GameStatus.IN_PROGRESS // Ad-hoc games are immediately in progress conceptually
            )
        }
        Log.d(TAG, "New default game created with ID: ${newDefaultGame.id}. Setting as active.")
        saveActiveGameState()
        syncNewAdHocGameToPhone(newDefaultGame) // Sync this new game to the phone
    }

    fun selectGameToStart(gameFromList: Game) {
        pauseTimer()
        Log.d(TAG, "Selecting game from list to start: ${gameFromList.id}, current status: ${gameFromList.status}")
        // Reset live state fields for the selected game, but keep its schedule info
        val cleanGameForStart = gameFromList.copy(
            currentPhase = GamePhase.PRE_GAME,
            homeScore = 0,
            awayScore = 0,
            displayedTimeMillis = getDurationMillisForPhase(GamePhase.FIRST_HALF, gameFromList),
            actualTimeElapsedInPeriodMillis = 0L,
            isTimerRunning = false,
            events = emptyList(),
            status = GameStatus.IN_PROGRESS, // Mark as IN_PROGRESS once user selects it to start
            lastUpdated = System.currentTimeMillis(),
            kickOffTeam = gameFromList.kickOffTeam // Keep original kick-off team
        )
        _activeGame.value = cleanGameForStart
        Log.d(TAG, "Selected game ${cleanGameForStart.id} set as active. Phase: PRE_GAME.")
        saveActiveGameState()
        // Optionally send an update to the phone that this game is now active on the watch
        // sendActiveGameUpdateToPhone(cleanGameForStart)
    }

    fun finishAndSyncActiveGame(onSyncComplete: () -> Unit) {
        Log.d(TAG, "finishAndSyncActiveGame called for game: ${_activeGame.value.id}")
        pauseTimer()

        val finalGameData = _activeGame.value.copy(
            currentPhase = GamePhase.GAME_ENDED,
            status = GameStatus.COMPLETED,
            isTimerRunning = false,
            lastUpdated = System.currentTimeMillis()
        )
        _activeGame.value = finalGameData // Update local state immediately

        Log.d(TAG, "Game ${finalGameData.id} marked as COMPLETED. Syncing to phone with ${finalGameData.events.size} events.")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = AppJsonConfiguration.encodeToString(finalGameData)
                val path = "${WearSyncConstants.GAME_UPDATE_FROM_WATCH_PATH_PREFIX}/${finalGameData.id}"
                val putDataMapReq = PutDataMapRequest.create(path)
                putDataMapReq.dataMap.putString(WearSyncConstants.GAME_UPDATE_PAYLOAD_KEY, jsonString)
                putDataMapReq.dataMap.putLong("timestamp", System.currentTimeMillis()) // For uniqueness
                putDataMapReq.setUrgent()

                dataClient.putDataItem(putDataMapReq.asPutDataRequest()).await()
                Log.i(TAG, "Successfully sent final game state for ${finalGameData.id} to phone.")

                // After successful sync, update this game in the local GameStorageWear as well
                // This ensures the main scheduledGamesList reflects the completion.
                // Note: The phone should be the ultimate source of truth for the scheduled list,
                // so this local update is more for immediate UI consistency on the watch
                // if the data layer update from phone back to watch is delayed.
                val currentGames = gameStorage.gamesListFlow.value.toMutableList()
                val index = currentGames.indexOfFirst { it.id == finalGameData.id }
                if (index != -1) {
                    currentGames[index] = finalGameData
                    // GameStorageWear doesn't have a direct "updateSingleGame"
                    // It expects the full list from phone.
                    // So, we'll rely on the phone to send the updated list back.
                    // For now, the activeGame is completed, and allGamesMap will reflect this.
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error sending final game state to phone for ${finalGameData.id}.", e)
            } finally {
                launch(Dispatchers.Main) {
                    resetActiveGameToDefaultOrNextScheduled()
                    onSyncComplete()
                }
            }
        }
    }

    fun resetActiveGameToDefaultOrNextScheduled() {
        pauseTimer()
        // Try to load the next available scheduled game that isn't completed
        val nextScheduledGame = gamesList.value
            .filter { it.status == GameStatus.SCHEDULED && it.id != _activeGame.value.id }
            .minByOrNull { it.gameDateTimeEpochMillis ?: Long.MAX_VALUE }

        if (nextScheduledGame != null) {
            Log.d(TAG, "Resetting active game to next scheduled: ${nextScheduledGame.id}")
            // Prepare it similarly to loadInitialActiveGame or selectGameToStart
            val cleanNextGame = nextScheduledGame.copy(
                currentPhase = GamePhase.PRE_GAME, homeScore = 0, awayScore = 0, events = emptyList(),
                status = GameStatus.SCHEDULED, isTimerRunning = false, actualTimeElapsedInPeriodMillis = 0L,
                displayedTimeMillis = getDurationMillisForPhase(GamePhase.FIRST_HALF, nextScheduledGame)
            )
            _activeGame.value = cleanNextGame
        } else {
            Log.d(TAG, "No next scheduled game. Resetting to new default game.")
            val newDefaultGame = Game()
            _activeGame.value = newDefaultGame.copy(
                displayedTimeMillis = getDurationMillisForPhase(GamePhase.FIRST_HALF, newDefaultGame)
            )
        }
        saveActiveGameState()
        Log.d(TAG, "Active game has been reset. New active game ID: ${_activeGame.value.id}")
    }


    /**
     * Called when the user explicitly wants to check/refresh connection status.
     * This might try to influence GameStorageWear's status or trigger a message to the phone.
     */
    fun performConnectivityCheckAndRefresh() {
        viewModelScope.launch {
            Log.d(TAG, "PerformConnectivityCheckAndRefresh called. Current DataFetchStatus: ${dataFetchStatus.value}")
            try {
                val capabilityClient = Wearable.getCapabilityClient(application)
                val nodes = capabilityClient.getCapability(WearSyncConstants.PHONE_APP_CAPABILITY, CapabilityClient.FILTER_REACHABLE).await().nodes
                val isPhoneConnected = nodes.any { it.isNearby }

                if (!isPhoneConnected) {
                    Log.w(TAG, "Connectivity Check: Phone not found or capability not advertised.")
                    // Only update if not already a more specific error and list is empty
                    if (gamesList.value.isEmpty() &&
                        dataFetchStatus.value != DataFetchStatus.ERROR_PARSING &&
                        dataFetchStatus.value != DataFetchStatus.ERROR_UNKNOWN) {
                        gameStorage.updateDataFetchStatus(DataFetchStatus.ERROR_PHONE_UNREACHABLE)
                    }
                } else {
                    Log.i(TAG, "Connectivity Check: Phone found with capability.")
                    // If phone is connected and status was error, set to INITIAL to allow data sync to occur
                    // (WearDataListenerService would see new data or onCapabilityChanged might have already set it)
                    if (dataFetchStatus.value == DataFetchStatus.ERROR_PHONE_UNREACHABLE || dataFetchStatus.value == DataFetchStatus.ERROR_PARSING) {
                        gameStorage.updateDataFetchStatus(DataFetchStatus.INITIAL)
                    }
                    // If successfully connected and status is INITIAL or we have no games,
                    // we might want to proactively request data from the phone.
                    // This requires a message to the phone app.
                    if (dataFetchStatus.value == DataFetchStatus.INITIAL || (dataFetchStatus.value != DataFetchStatus.FETCHING && gamesList.value.isEmpty())) {
                        Log.i(TAG, "Requesting data refresh from phone...")
                        gameStorage.updateDataFetchStatus(DataFetchStatus.FETCHING)
                        sendMessageToPhone(WearSyncConstants.GAMES_LIST_PATH, "refresh_pls".toByteArray())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during connectivity check or sending refresh message", e)
                if (gamesList.value.isEmpty()) { // Only if no data, assume unreachable
                    gameStorage.updateDataFetchStatus(DataFetchStatus.ERROR_PHONE_UNREACHABLE)
                }
            }
        }
    }

    private fun sendMessageToPhone(path: String, data: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val nodeClient = Wearable.getNodeClient(application)
                val nodes = nodeClient.connectedNodes.await()
                nodes.firstOrNull { it.isNearby }?.id?.let { nodeId -> // Send to the first nearby node
                    Wearable.getMessageClient(application).sendMessage(nodeId, path, data).await()
                    Log.i(TAG, "Message '$path' sent to node $nodeId")
                } ?: Log.w(TAG, "No nearby node found to send message '$path'")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message '$path' to phone", e)
            }
        }
    }


    private fun syncNewAdHocGameToPhone(newGame: Game) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val gameToSync = newGame.copy(status = GameStatus.IN_PROGRESS) // Ensure status is correctly set
                val jsonString = AppJsonConfiguration.encodeToString(gameToSync)
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

    // --- Timer Logic ---
    private fun startTimerLogic(millisInFuture: Long) {
        gameCountDownTimer?.cancel() // Cancel any existing timer
        if (millisInFuture <= 0) {
            _activeGame.update { it.copy(isTimerRunning = false, displayedTimeMillis = 0) }
            handleTimerFinish()
            return
        }
        _activeGame.update { it.copy(isTimerRunning = true) }
        gameCountDownTimer = object : CountDownTimer(millisInFuture, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _activeGame.update {
                    it.copy(
                        displayedTimeMillis = millisUntilFinished,
                        actualTimeElapsedInPeriodMillis = it.halfDurationMillis - millisUntilFinished // Or period specific duration
                    )
                }
            }
            override fun onFinish() {
                _activeGame.update { it.copy(isTimerRunning = false, displayedTimeMillis = 0) }
                handleTimerFinish()
            }
        }.start()
    }

    fun pauseTimer() {
        gameCountDownTimer?.cancel()
        _activeGame.update { it.copy(isTimerRunning = false) }
        saveActiveGameState() // Save timer state on pause
        Log.d(TAG, "Timer paused at ${_activeGame.value.displayedTimeMillis}")
    }

    fun startTimer() {
        if (!_activeGame.value.isTimerRunning && _activeGame.value.displayedTimeMillis > 0) {
            startTimerLogic(_activeGame.value.displayedTimeMillis)
            saveActiveGameState() // Save timer state on start
            Log.d(TAG, "Timer started/resumed.")
        } else if (_activeGame.value.displayedTimeMillis <= 0) {
            // If timer is at 0, perhaps move to next phase or reset based on game rules
            Log.d(TAG, "Timer at 0, cannot start.")
        }
    }

    private fun handleTimerFinish() {
        Log.d(TAG, "Timer finished for phase: ${_activeGame.value.currentPhase}")
        vibrate(VibrationPattern.TIMER_FINISH)
        // Auto-transition or specific logic based on current phase
        when (_activeGame.value.currentPhase) {
            GamePhase.FIRST_HALF -> {
                _activeGame.update {
                    it.copy(
                        currentPhase = GamePhase.HALF_TIME,
                        displayedTimeMillis = getDurationMillisForPhase(GamePhase.HALF_TIME, it),
                        actualTimeElapsedInPeriodMillis = 0 // Reset for halftime
                    )
                }
                updateCurrentPeriodKickOffTeam(GamePhase.HALF_TIME, _activeGame.value.kickOffTeam)
            }
            GamePhase.HALF_TIME -> {
                _activeGame.update {
                    it.copy(
                        currentPhase = GamePhase.SECOND_HALF,
                        displayedTimeMillis = getDurationMillisForPhase(GamePhase.SECOND_HALF, it),
                        actualTimeElapsedInPeriodMillis = 0 // Reset for 2nd half
                    )
                }
                updateCurrentPeriodKickOffTeam(GamePhase.SECOND_HALF, _activeGame.value.kickOffTeam)
            }
            GamePhase.SECOND_HALF -> {
                _activeGame.update { it.copy(currentPhase = GamePhase.GAME_ENDED) }
                // Potentially finishAndSyncActiveGame() here if that's the desired flow
            }
            // Handle other phases like Extra Time if applicable
            else -> Log.d(TAG, "Timer finished in unhandled phase: ${_activeGame.value.currentPhase}")
        }
        saveActiveGameState()
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

    // --- Game State Modifiers (Goals, Cards, Subs etc.) ---
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
        vibrate(VibrationPattern.GOAL_SCORED)
        saveActiveGameState()
        Log.d(TAG, "Goal added for $team. Score: ${_activeGame.value.homeScore}-${_activeGame.value.awayScore}")
    }

    fun updateHomeTeamName(name: String) {
        _activeGame.update { it.copy(homeTeamName = name, lastUpdated = System.currentTimeMillis()) }
        saveActiveGameState()
    }

    fun updateAwayTeamName(name: String) {
        _activeGame.update { it.copy(awayTeamName = name, lastUpdated = System.currentTimeMillis()) }
        saveActiveGameState()
    }
    // --- Other Game Actions (Placeholder for brevity, implement as needed) ---

    fun startSubstitution(team: Team) { /* ... */ saveActiveGameState() }
    fun endSubstitution(team: Team, playerIn: String, playerOut: String) { /* ... */ saveActiveGameState() }
    fun recordFreeKick(team: Team) { /* ... */ saveActiveGameState() }
    fun recordCorner(team: Team) { /* ... */ saveActiveGameState() }
    fun recordThrowIn(team: Team) { /* ... */ saveActiveGameState() }

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


    // --- UI Configuration / Game Setup ---
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
                lastUpdated = System.currentTimeMillis()
            )
        }
        saveActiveGameState()
    }

    fun setHalfDuration(minutes: Int) {
        _activeGame.update { currentGame ->
            val newHalfDurationMillis = minutes * 60 * 1000L
            var newDisplayedTime = currentGame.displayedTimeMillis

            // Adjust displayed time if timer not running or in pre-game
            if (currentGame.currentPhase == GamePhase.PRE_GAME || !currentGame.isTimerRunning) {
                // If it's a phase that uses half duration, update displayed time
                if (currentGame.currentPhase.usesHalfDuration()) {
                    val timeAlreadyElapsed = if (currentGame.isTimerRunning) { // Should be false here
                        (currentGame.halfDurationMillis - currentGame.displayedTimeMillis).coerceAtLeast(0L)
                    } else {
                        currentGame.actualTimeElapsedInPeriodMillis
                    }
                    newDisplayedTime = (newHalfDurationMillis - timeAlreadyElapsed).coerceAtLeast(0L)
                }
            }
            // For halftime, its duration might be set independently.
            // For now, only adjust if displayedTime is for a half.

            currentGame.copy(
                halfDurationMinutes = minutes,
                // halftimeDurationMinutes will be set by setHalftimeDuration
                displayedTimeMillis = newDisplayedTime,
                lastUpdated = System.currentTimeMillis()
            )
        }
        saveActiveGameState()
    }

    fun setHalftimeDuration(minutes: Int) {
        _activeGame.update { currentGame ->
            var newDisplayedTime = currentGame.displayedTimeMillis
            if (currentGame.currentPhase == GamePhase.HALF_TIME && !currentGame.isTimerRunning) {
                newDisplayedTime = (minutes * 60 * 1000L - currentGame.actualTimeElapsedInPeriodMillis).coerceAtLeast(0L)
            }
            currentGame.copy(
                halftimeDurationMinutes = minutes,
                displayedTimeMillis = newDisplayedTime,
                lastUpdated = System.currentTimeMillis()
            )
        }
        saveActiveGameState()
    }

    // --- Internal Helpers ---
    private fun getDurationMillisForPhase(phase: GamePhase, game: Game): Long {
        return when (phase) {
            GamePhase.PRE_GAME -> game.halfDurationMillis // Show 1st half duration in pre-game
            GamePhase.FIRST_HALF, GamePhase.SECOND_HALF,
            GamePhase.EXTRA_TIME_FIRST_HALF, GamePhase.EXTRA_TIME_SECOND_HALF -> game.halfDurationMillis
            GamePhase.HALF_TIME -> game.halftimeDurationMillis
            GamePhase.EXTRA_TIME_HALF_TIME -> game.extraTimeHalftimeDurationMillis
            else -> 0L // Full_Time, Game_Ended
        }
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

        // Keep the timer running during half-times
        if (_activeGame.value.currentPhase.isBreak())
            startTimer()
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
    * Checks if the shootout has ended based on logic to determine if the penalty shootout has concluded.
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

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrate(pattern: VibrationPattern) {
        if (vibrator?.hasVibrator() == true) {
            val effect = when (pattern) {
                VibrationPattern.TIMER_FINISH -> VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1)
                VibrationPattern.GOAL_SCORED -> VibrationEffect.createWaveform(longArrayOf(0, 150, 50, 150, 50, 300), -1)
                VibrationPattern.GENERIC_EVENT -> VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vibrator.vibrate(effect)
        }
    }

    enum class VibrationPattern {
        TIMER_FINISH, GOAL_SCORED, GENERIC_EVENT
    }

    override fun onCleared() {
        // Ensure OngoingActivity is cleared if ViewModel is destroyed
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(ONGOING_NOTIFICATION_ID_VM)
        super.onCleared()
        gameCountDownTimer?.cancel()
        Log.d(TAG, "WearGameViewModel onCleared.")
    }
}

// Extension function for GamePhase if not already present in common
fun GamePhase.usesHalfDuration(): Boolean {
    return this == GamePhase.FIRST_HALF || this == GamePhase.SECOND_HALF ||
            this == GamePhase.EXTRA_TIME_FIRST_HALF || this == GamePhase.EXTRA_TIME_SECOND_HALF ||
            this == GamePhase.PRE_GAME // PRE_GAME often shows the upcoming half duration
}


/*
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


@HiltViewModel
class WearGameViewModel @Inject constructor(
    private val application: Application, // Hilt can provide this
    private val savedStateHandle: SavedStateHandle, // Hilt provides this
    private val gameStorage: GameStorageWear, // Hilt injects your singleton GameStorage
    private val dataClient: DataClient, // For syncing
    private val vibrator: Vibrator? // Vibrator for timer
) : AndroidViewModel(application) {
    private val TAG = "WearGameViewModel"

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
                AppJsonConfiguration.decodeFromString<Game>(savedGameJson)
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
            val activeGameJson = AppJsonConfiguration.encodeToString(_activeGame.value)
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

    */
/**
     * Records the result of a penalty attempt during a shootout.
     *
     * @param scored True if the penalty was scored, false otherwise.
     *//*

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

    */
/**
     * Placeholder for logic to determine if the penalty shootout has concluded.
     * This needs to be implemented thoroughly.
     *//*

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
*/
