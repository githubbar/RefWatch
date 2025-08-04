package com.databelay.refwatch.wear.data // Or your correct package

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.databelay.refwatch.R
import com.databelay.refwatch.common.Game // Assuming you need parts of Game state
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.formatTime
import com.databelay.refwatch.common.hasTimer
import com.databelay.refwatch.common.isPlayablePhase
import com.databelay.refwatch.common.readable
import com.databelay.refwatch.wear.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Define your constants for notification
private const val ONGOING_NOTIFICATION_ID_SERVICE = 2 // Different from OngoingActivity's ID
private const val ONGOING_NOTIFICATION_CHANNEL_ID = "RefWatchGameTimerChannel" // Ensure this channel is created
const val ONGOING_NOTIFICATION_CHANNEL_NAME = "RefWatch Timer"

const val ONGOING_NOTIFICATION_ID_VM = 123
const val COUNTDOWN_INTERVAL_MS = 1000L
// Data class for timer updates
data class TimerState(
    val actualTimeElapsedInPeriodMillis: Long = 0L,
    val isTimerRunning: Boolean = false, // Service's knowledge: is the CountDownTimer ticking?
    val currentPhase: GamePhase = GamePhase.PRE_GAME,
    val regulationPeriodDurationMillis: Long = 0L,
    val displayedMillis: Long = 0L,
    val inAddedTime: Boolean = false
)

class GameTimerService : Service() {
    private val TAG = "GameTimerService"
    private val binder = LocalBinder()
    private lateinit var powerManager: PowerManager
    private lateinit var notificationManager: NotificationManager

    private var wakeLock: PowerManager.WakeLock? = null

    private var gameCountDownTimer: CountDownTimer? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob) // Use Dispatchers.Main for CountDownTimer

    // --- StateFlow for communication with ViewModel ---
    private val _timerStateFlow = MutableStateFlow(TimerState())
    val timerStateFlow: StateFlow<TimerState> = _timerStateFlow.asStateFlow()
    // ---

    // To hold the full game state or relevant parts passed from ViewModel
    private var currentInternalGame: Game? = null

    inner class LocalBinder : Binder() {
        fun getService(): GameTimerService = this@GameTimerService
    }

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager // Initialize here
        // Ensure your notification channel is created (ideally in Application.onCreate)
        createNotificationChannel()

        // Start in foreground immediately or very soon after creation
        startForeground(ONGOING_NOTIFICATION_ID_SERVICE, createServiceNotification("Timer Initializing..."))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            ONGOING_NOTIFICATION_CHANNEL_ID,
            ONGOING_NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW // Or IMPORTANCE_DEFAULT. Avoid HIGH for ongoing unless absolutely necessary.
        ).apply {
            description = "Shows the current game timer"
            // Configure other channel properties if needed (e.g., enableLights, lightColor)
            // For ongoing timers, you usually don't want sound or vibration on the channel itself,
            // as the notification updates frequently.
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
        Log.d(TAG, "Notification channel created: $ONGOING_NOTIFICATION_CHANNEL_ID")
    }
    // Not using onStartCommand directly for primary control if using binding for start/stop
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // You might use this if you want the service to auto-restart or handle deep links to start timer
        // For now, let's assume ViewModel controls via binding.
        // START_NOT_STICKY is fine if ViewModel is expected to always re-bind and restart if needed.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld != true) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RefWatch::GameTimerWakeLockTag").apply {
                setReferenceCounted(false)
                acquire() // No timeout, relies on explicit release
            }
            // Log.d("GameTimerService", "WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                // Log.d("GameTimerService", "WakeLock released")
            }
        }
        wakeLock = null
    }

    fun configureTimerForGame(game: Game, startImmediately: Boolean) {
        serviceScope.launch { // Ensure on Main thread for CountDownTimer interaction
            Log.d(TAG, "Service configuring for game: ${game.id}, phase: ${game.currentPhase}, startImmediately: $startImmediately")
            currentInternalGame = game
            val initialElapsed = game.actualTimeElapsedInPeriodMillis
            val regulationDuration = game.regulationPeriodDurationMillis()
            val isAdded = initialElapsed >= regulationDuration
            val displayedMillis = if (isAdded) {initialElapsed - regulationDuration} else {regulationDuration - initialElapsed}
            val initialIsTimerRunning = startImmediately && (game.currentPhase.hasTimer())
            _timerStateFlow.update {
                it.copy(
                    currentPhase = game.currentPhase,
                    isTimerRunning = false, // Will be set true by startGameTimer if called
                    displayedMillis = displayedMillis,
                    actualTimeElapsedInPeriodMillis = initialElapsed,
                    inAddedTime = isAdded,
                    regulationPeriodDurationMillis = game.regulationPeriodDurationMillis(),
                )
            }
            updateNotificationAndOngoingActivity(
                if (startImmediately) displayedMillis.formatTime() else "Ready: ${game.currentPhase.readable()}"
            )

            if (initialIsTimerRunning) {
                startGameTimer(game, initialElapsed, isAdded)
            }
        }
    }


    private fun canPostNotifications(): Boolean {
        // POST_NOTIFICATIONS permission is only required from Android 13 (API 33) onwards.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            )
            Log.d("$TAG:timer", "POST_NOTIFICATIONS permission check result: $result")
            return result == PackageManager.PERMISSION_GRANTED
        }
        return true // Automatically granted on versions below Android 13
    }


    /**
     * Starts the game timer for the given game.
     * If a timer is already running, it will be cancelled and a new one started
     * with the provided parameters.
     *
     * This method acquires a wakelock, updates the internal timer state,
     * starts the service in the foreground (if not already), and initializes
     * a [CountDownTimer] to manage the timing logic.
     *
     * @param game The [Game] object containing details about the current game state.
     * @param initialElapsedMillis The elapsed time in milliseconds to start the timer from.
     *                             Defaults to 0L.
     * @param isAddedTimeInitially A boolean indicating if the timer is starting directly into added time.
     *                             Defaults to false.
     */
    fun startGameTimer(game: Game, initialElapsedMillis: Long = 0L, isAddedTimeInitially: Boolean = false) {
        serviceScope.launch {
            if (_timerStateFlow.value.isTimerRunning) {
                gameCountDownTimer?.cancel()
            }
            currentInternalGame = game // Store the game
            acquireWakeLock()
            val regulationDuration = game.regulationPeriodDurationMillis()

            _timerStateFlow.update {
                it.copy(
                    currentPhase = game.currentPhase,
                    isTimerRunning = true,
                    actualTimeElapsedInPeriodMillis = initialElapsedMillis,
                    inAddedTime = isAddedTimeInitially,
                    displayedMillis = if (isAddedTimeInitially) initialElapsedMillis else regulationDuration - initialElapsedMillis,
                    regulationPeriodDurationMillis = regulationDuration,
                )
            }

            // Start Foreground Service if not already started with the right state
            // This is crucial. Even if service is running, ensure it's in FG mode now.
            startForeground(ONGOING_NOTIFICATION_ID_SERVICE, createServiceNotification(_timerStateFlow.value.displayedMillis.formatTime()))
            Log.d(TAG, "GameTimerService brought to foreground explicitly for timer start.")

            val timeToCountFrom = Long.MAX_VALUE // For continuous running
            gameCountDownTimer = object : CountDownTimer(timeToCountFrom, COUNTDOWN_INTERVAL_MS) {
                override fun onTick(millisUntilFinished_unused: Long) {
                    if (!(_timerStateFlow.value.isTimerRunning)) { // Check our own state flag
                        this.cancel() // Stop if service state says so
                        releaseWakeLock()
                        return
                    }

                    val currentActualElapsed = _timerStateFlow.value.actualTimeElapsedInPeriodMillis + 1000L
                    val regulationDuration =
                        _timerStateFlow.value.regulationPeriodDurationMillis
                    var newIsAddedTime = _timerStateFlow.value.inAddedTime
                    val currentPhase = _timerStateFlow.value.currentPhase

                    var newDisplayedMillis: Long

                    // --- ADAPT YOUR VIEWMODEL'S TIMER LOGIC HERE ---
                    if (currentPhase.isPlayablePhase()) {
                        if (newIsAddedTime || (currentActualElapsed >= regulationDuration && regulationDuration > 0)) {
                            newIsAddedTime = true
                            newDisplayedMillis = currentActualElapsed - regulationDuration
                            // Consider logic for period end detection here if not handled by ViewModel externally
                        } else {
                            newDisplayedMillis = regulationDuration - currentActualElapsed
                        }
                    } else { // Break or other non-playable phase (timer might count down break time or stay 00:00)
                        // Adapt logic for breaks if service should manage break timing
                        newDisplayedMillis = 0 // Placeholder
                    }
                    // --- END ADAPTATION ---

                    _timerStateFlow.update { currentState ->
                        val newElapsed = currentState.actualTimeElapsedInPeriodMillis + COUNTDOWN_INTERVAL_MS
                        val currentRegulationDuration =
                            currentState.regulationPeriodDurationMillis // Use from state
                        val isInAddedTimeNow = newElapsed >= currentRegulationDuration
                        val displayValue = if (isInAddedTimeNow) {
                            newElapsed - currentRegulationDuration
                        } else {
                            currentRegulationDuration - newElapsed
                        }
                        currentState.copy(
                            actualTimeElapsedInPeriodMillis = newElapsed,
                            displayedMillis = displayValue,
                            inAddedTime = isInAddedTimeNow
                        )
                    }
                    // Update notification if needed
                    updateNotificationAndOngoingActivity(_timerStateFlow.value.displayedMillis.formatTime())
                }

                override fun onFinish() { // Should ideally not be reached with Long.MAX_VALUE
                    // This would be an abnormal stop. Normal stop is handled by stopGameTimer.
                    _timerStateFlow.value = _timerStateFlow.value.copy(isTimerRunning = false)
                    releaseWakeLock()
                    stopForegroundSafely()
                }
            }.start()
            updateNotificationAndOngoingActivity(_timerStateFlow.value.displayedMillis.formatTime())
        }
    }

    /**
     * Pauses the current countdown timer, keeps the wakelock (if configured to do so on pause),
     * and updates the timer state.
     * @param updateNotificationText Optional text to update the ongoing notification with (e.g., "Paused", "Period Ended")
     */
    fun pauseGameTimer(updateNotificationText: String? = null) {
        serviceScope.launch {
            if (!_timerStateFlow.value.isTimerRunning) {
                Log.d(TAG, "pauseGameTimer called, but timer was not running.")
                return@launch
            }
            _timerStateFlow.update { it.copy(isTimerRunning = false) }
            val textForNotification = updateNotificationText ?: "Paused: ${_timerStateFlow.value.displayedMillis.formatTime()}"
            updateNotificationAndOngoingActivity(textForNotification) // Update with "Paused" or "Period Ended"
        }
    }


    fun resumeGameTimer(game: Game) { // Pass game for context (e.g. current phase duration)
        Log.d(TAG, "resumeGameTimer called.")
        serviceScope.launch {
            val currentState = _timerStateFlow.value
            if (currentState.isTimerRunning) {
                Log.w(TAG, "resumeGameTimer called, but timer is already running.")
                return@launch
            }
            // ViewModel ensures this is a valid resume context (e.g. game was paused in a playable phase).
            // WakeLock is assumed to be held by the session.

            currentInternalGame = game // Update internal game for context if needed by startGameTimerLogic
            val currentPhase = game.currentPhase // Use fresh phase from potentially updated game object
            if (!currentPhase.hasTimer()) {
                Log.w(TAG, "Attempted to resume timer in non-timed phase: $currentPhase. Not resuming.")
                _timerStateFlow.update { it.copy(isTimerRunning = false) } // Ensure it's not running
                return@launch
            }

            Log.d(TAG, "Resuming timer for ${currentPhase}. Phase from game: ${game.currentPhase}. WakeLock is held.")
            _timerStateFlow.update {
                it.copy(
                    isTimerRunning = true,
                    currentPhase = game.currentPhase, // Ensure phase is updated
                    regulationPeriodDurationMillis = game.regulationPeriodDurationMillis() // Ensure duration is updated
                    // actualElapsedMillis and inAddedTime are preserved from before pause
                )
            }
            startForeground(ONGOING_NOTIFICATION_ID_SERVICE, createServiceNotification(_timerStateFlow.value.displayedMillis.formatTime()))
            startGameTimer(game, currentState.actualTimeElapsedInPeriodMillis, currentState.inAddedTime)
        }
    }

    /**
     * Called by ViewModel when the game session is completely finished.
     * Stops timer, RELEASES WAKELOCK, and cleans up.
     */
    fun commandStopGameSessionAndCleanup() {
        serviceScope.launch {
            Log.i(TAG, "COMMAND: Stop Game Session & Cleanup. Releasing WakeLock.")
            gameCountDownTimer?.cancel()
            gameCountDownTimer = null

            _timerStateFlow.update {
                it.copy(
                    isTimerRunning = false,
                    displayedMillis = 0L, // Or relevant end-game display
                    actualTimeElapsedInPeriodMillis = 0L,
                    currentPhase = it.currentPhase ?: GamePhase.GAME_ENDED // Default to a terminal phase
                )
            }
            releaseWakeLock() // <<<< WAKELOCK RELEASED HERE >>>>
            updateNotificationAndOngoingActivity("Game Ended")
            stopForegroundSafely()
        }
    }

    // Your existing stopForegroundSafely method
    private fun stopForegroundSafely() {
        // Check if there's any reason to stay in foreground (e.g. another game queued immediately)
        // For now, assume if stopGameTimerAndCleanup is called, we can try to stop foreground.
        if (_timerStateFlow.value.isTimerRunning) {
            Log.d(TAG, "stopForegroundSafely: Timer is still running, not stopping foreground.")
            return
        }

        Log.d(TAG, "stopForegroundSafely: Attempting to stop foreground and service if idle.")
        stopForeground(STOP_FOREGROUND_REMOVE) // Or STOP_FOREGROUND_DETACH if you want to keep notification temporarily
        // Consider calling stopSelf() if no active timers and no clients bound,
        // or if the service's job is truly done.
        // This depends on your service's lifecycle requirements (start-sticky vs. self-stopping).
        // If it should stop when work is done:
        // if (!isAnyClientBound && !_timerStateFlow.value.isTimerRunning) { // You'd need to track bound clients
        //    stopSelf()
        // }
    }

      private fun createServiceNotification(contentText: String): Notification {
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, ONGOING_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round) // USE YOUR ACTUAL ICON
            .setContentTitle("RefWatch Timer Active")
            .setContentText(contentText)
            .setCategory(NotificationCompat.CATEGORY_SERVICE) // Or CATEGORY_STOPWATCH
            .setOngoing(true) // Crucial for foreground service + ongoing activity behavior
            .setOnlyAlertOnce(true)
        // .setContentIntent(pendingIntent) // Set by OngoingActivity builder if used

        // --- Integrate OngoingActivity API ---
        val status = Status.Builder()
            .addTemplate(contentText) // Or a more structured template like "# $contentText"
            // Add other parts if needed, e.g., .addPart("phase", Status.TextPart(...))
            .build()

        // If using androidx.wear:wear-ongoing:1.1.0 or later
        // (You have androidx.wear:wear-ongoing:1.1.0-beta01 [3] which is fine)
        val ongoingActivity = OngoingActivity.Builder(applicationContext, ONGOING_NOTIFICATION_ID_SERVICE, notificationBuilder)
            .setStaticIcon(R.mipmap.ic_launcher_round) // USE YOUR ACTUAL ICON
            .setTouchIntent(pendingIntent)
            .setStatus(status)
            .build()

        // Apply the OngoingActivity features to the notification builder
        ongoingActivity.apply(this) // Modifies the notificationBuilder

        return notificationBuilder.build()
        // --- End OngoingActivity Integration ---
    }

    // Call this when you need to update the text AND the OngoingActivity status
    private fun updateNotificationAndOngoingActivity(newContentText: String, newStatusText: String = newContentText) {
        val activityIntent = Intent(this, MainActivity::class.java).apply { /* ... */ }
        val pendingIntent = PendingIntent.getActivity(this, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notificationBuilder = NotificationCompat.Builder(this, ONGOING_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("RefWatch Timer Active")
            .setContentText(newContentText)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        val status = Status.Builder().addTemplate(newStatusText).build()
        val ongoingActivity = OngoingActivity.Builder(applicationContext, ONGOING_NOTIFICATION_ID_SERVICE, notificationBuilder)
            .setStaticIcon(R.mipmap.ic_launcher_round)
            .setTouchIntent(pendingIntent)
            .setStatus(status)
            .build()
        ongoingActivity.apply(this) // Modifies the notificationBuilder

        val notification = notificationBuilder.build()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ONGOING_NOTIFICATION_ID_SERVICE, notification)
    }

    /**
     * Called by ViewModel when a game session truly starts (e.g., moving to first half).
     * Acquires WakeLock for the session and starts the timer.
     */
    fun commandStartGameSessionAndTimer(game: Game, initialElapsedMillis: Long = 0L, isAddedTimeInitially: Boolean = false) {
        serviceScope.launch {
            currentInternalGame = game
            Log.i(TAG, "COMMAND: Start Game Session & Timer for ${game.currentPhase}. Acquiring WakeLock.")
            acquireWakeLock() // <<<< WAKELOCK ACQUIRED HERE (once per session) >>>>

            // Update state to reflect timer is running and other game parameters
            _timerStateFlow.update {
                it.copy(
                    currentPhase = game.currentPhase,
                    isTimerRunning = false,
                    actualTimeElapsedInPeriodMillis = initialElapsedMillis,
                    inAddedTime = isAddedTimeInitially,
                    displayedMillis = if (isAddedTimeInitially) initialElapsedMillis - game.regulationPeriodDurationMillis() else game.regulationPeriodDurationMillis() - initialElapsedMillis,
                    regulationPeriodDurationMillis = game.regulationPeriodDurationMillis()
                )
            }
            startForeground(ONGOING_NOTIFICATION_ID_SERVICE, createServiceNotification(_timerStateFlow.value.displayedMillis.formatTime()))
            acquireWakeLock()
        }
    }

    override fun onDestroy() {
        // Log.d("GameTimerService", "onDestroy called")
        gameCountDownTimer?.cancel()
        releaseWakeLock()
        serviceJob.cancel() // Cancel all coroutines started by serviceScope
        super.onDestroy()
    }
}
