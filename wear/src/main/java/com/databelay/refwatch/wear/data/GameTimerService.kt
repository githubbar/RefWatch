package com.databelay.refwatch.wear.data // Or your correct package

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataPoint
import androidx.health.services.client.data.CumulativeDataPoint
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.databelay.refwatch.R
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.GameStatus
import com.databelay.refwatch.common.HeartRateSample
import com.databelay.refwatch.common.LocationSample
import com.databelay.refwatch.common.StepSample
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.canHaveAddedTime
import com.databelay.refwatch.common.hasTimer
import com.databelay.refwatch.common.readable
import com.databelay.refwatch.common.status
import dagger.hilt.android.AndroidEntryPoint
import com.databelay.refwatch.wear.MainActivity
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

const val ONGOING_NOTIFICATION_ID_SERVICE = 1
const val ONGOING_NOTIFICATION_CHANNEL_ID = "game_timer_service_channel"
const val ONGOING_NOTIFICATION_CHANNEL_NAME = "Game Timer Service"

const val ONGOING_NOTIFICATION_ID_VM = 2
const val COUNTDOWN_INTERVAL_MS = 1000L
const val MAX_ADDED_TIME_COUNTUP_DURATION = 20 * 60 * 1000L // 20 minutes

const val SIMULATION_MODE = false // Toggle this for debugging without movement
data class TimerState(
    val actualTimeElapsedInPeriodMillis: Long = 0L,
    val isTimerRunning: Boolean = false,
    val currentPhase: GamePhase = GamePhase.NOT_STARTED,
    val regulationPeriodDurationMillis: Long = 0L,
    val displayedMillis: Long = 0L,
    val inAddedTime: Boolean = false,
    val latestLocation: LocationSample? = null,
    val latestHeartRate: HeartRateSample? = null,
    val latestSteps: StepSample? = null
)

@AndroidEntryPoint
class GameTimerService : Service() {
    @Inject lateinit var healthServicesManager: HealthServicesManager
    private val TAG = "GameTimerService"
    private val binder = LocalBinder()
    private var isServiceForeground = false
    private lateinit var powerManager: PowerManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null

    private var wakeLock: PowerManager.WakeLock? = null

    private var gameCountDownTimer: CountDownTimer? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob) // Use Dispatchers.Main for CountDownTimer

    // --- StateFlow for communication with ViewModel ---
    private val _timerStateFlow = MutableStateFlow(TimerState())
    val timerStateFlow: StateFlow<TimerState> = _timerStateFlow.asStateFlow()

    // To hold the full game state or relevant parts passed from ViewModel
    private var currentInternalGame: Game? = null
    private var timeTickerStartedSystemTime = 0L // SystemClock.elapsedRealtime() when a ticker starts
    private var initialMillisForCurrentTicker = 0L // The millisInFuture the current ticker was started with

    private var lastCumulativeSteps = -1L
    private var lastCumulativeDistance = -1.0
    private var lastLocationForStepEstimation: android.location.Location? = null

    private var lastSensorSteps = -1L
    private val stepSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                val currentTotal = event.values[0].toLong()
                if (lastSensorSteps != -1L && currentTotal > lastSensorSteps) {
                    val delta = (currentTotal - lastSensorSteps).toInt()
                    _timerStateFlow.update { state ->
                        state.copy(
                            latestSteps = StepSample(
                                timestamp = System.currentTimeMillis(),
                                delta = delta
                            )
                        )
                    }
                    Log.d(TAG, "Steps from SensorManager: $delta (Total: $currentTotal)")
                }
                lastSensorSteps = currentTotal
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    inner class LocalBinder : Binder() {
        fun getService(): GameTimerService = this@GameTimerService
    }

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager // Initialize here
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        createNotificationChannel()
        Log.d(TAG, "Service Created. Step sensor available: ${stepSensor != null}")

        serviceScope.launch {
            healthServicesManager.exerciseUpdateFlow().collect { update ->
                val location = update.latestMetrics.getData(DataType.LOCATION).lastOrNull()
                val hr = update.latestMetrics.getData(DataType.HEART_RATE_BPM).lastOrNull { it.value > 0.0 }

                var stepsDeltaToRecord: Int? = null

                // Only use Health Services steps if direct SensorManager is not available
                if (stepSensor == null) {
                    // 1. Try cumulative steps (STEPS_TOTAL) - Often more reliable on Wear OS
                    val totalStepsPoint = update.latestMetrics.getData(DataType.STEPS_TOTAL)
                    if (totalStepsPoint != null) {
                        val currentTotal = totalStepsPoint.total
                        if (lastCumulativeSteps != -1L && currentTotal > lastCumulativeSteps) {
                            stepsDeltaToRecord = (currentTotal - lastCumulativeSteps).toInt()
                        }
                        lastCumulativeSteps = currentTotal
                    }

                    // 2. If no delta from cumulative, try deltas (STEPS)
                    if (stepsDeltaToRecord == null || stepsDeltaToRecord == 0) {
                        val stepsDeltaList = update.latestMetrics.getData(DataType.STEPS)
                        if (stepsDeltaList.isNotEmpty()) {
                            stepsDeltaToRecord = stepsDeltaList.sumOf { it.value.toInt() }
                        }
                    }
                }

                // 3. Fallback to distance estimation (DISTANCE_TOTAL or DISTANCE)
                if (stepsDeltaToRecord == null || stepsDeltaToRecord == 0) {
                    val totalDistancePoint = update.latestMetrics.getData(DataType.DISTANCE_TOTAL)
                    if (totalDistancePoint != null) {
                        val currentDistTotal = totalDistancePoint.total
                        if (lastCumulativeDistance != -1.0 && currentDistTotal > lastCumulativeDistance) {
                            val distDelta = currentDistTotal - lastCumulativeDistance
                            stepsDeltaToRecord = (distDelta * 1.31).toInt()
                        }
                        lastCumulativeDistance = currentDistTotal
                    } else {
                        val distanceList = update.latestMetrics.getData(DataType.DISTANCE)
                        if (distanceList.isNotEmpty()) {
                            val distanceDelta = distanceList.sumOf { it.value }
                            if (distanceDelta > 0.1) {
                                stepsDeltaToRecord = (distanceDelta * 1.31).toInt()
                            }
                        }
                    }
                }

                // 4. Fallback to GPS distance estimation (if LOCATION is present but DISTANCE is not)
                if ((stepsDeltaToRecord == null || stepsDeltaToRecord == 0) && location != null) {
                    val currentLoc = android.location.Location("fused").apply {
                        latitude = location.value.latitude
                        longitude = location.value.longitude
                    }
                    lastLocationForStepEstimation?.let { last ->
                        val distanceDelta = last.distanceTo(currentLoc).toDouble()
                        if (distanceDelta > 1.0) { // Only count if moved more than 1 meter
                             stepsDeltaToRecord = (distanceDelta * 1.31).toInt()
                             Log.d(TAG, "Steps estimated from GPS delta: $stepsDeltaToRecord (dist: $distanceDelta)")
                        }
                    }
                    lastLocationForStepEstimation = currentLoc
                }

                _timerStateFlow.update { state ->
                    state.copy(
                        latestLocation = location?.let {
                            LocationSample(
                                timestamp = System.currentTimeMillis(),
                                latitude = it.value.latitude,
                                longitude = it.value.longitude,
                                altitude = it.value.altitude
                            )
                        },
                        latestHeartRate = hr?.let {
                            HeartRateSample(
                                timestamp = System.currentTimeMillis(),
                                bpm = it.value
                            )
                        },
                        latestSteps = if (stepsDeltaToRecord != null && stepsDeltaToRecord > 0) {
                            StepSample(
                                timestamp = System.currentTimeMillis(),
                                delta = stepsDeltaToRecord
                            )
                        } else state.latestSteps
                    )
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            ONGOING_NOTIFICATION_CHANNEL_ID,
            ONGOING_NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW 
        ).apply {
            description = "Shows the current game timer"
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
        Log.d(TAG, "Notification channel created: $ONGOING_NOTIFICATION_CHANNEL_ID")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RefWatch:GameTimerWakeLock")
            wakeLock?.acquire()
            Log.d(TAG, "WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.d(TAG, "WakeLock released")
        }
        wakeLock = null
    }

    fun configureTimerForGame(game: Game, startImmediately: Boolean) {
        Log.d(TAG, "configureTimerForGame called. Game ID: ${game.id}, Phase: ${game.currentPhase}, startImmediately: $startImmediately")
        currentInternalGame = game.copy() // Keep a local copy

        // Always cancel existing timer before reconfiguring, in case we're switching phases
        // while a timer (like a break timer) is still running.
        if (gameCountDownTimer != null) {
            Log.d(TAG, "Cancelling existing timer during configuration.")
            gameCountDownTimer?.cancel()
            gameCountDownTimer = null
        }

        // 1. Determine regulation duration for current phase
        val currentRegulationDuration = game.regulationPeriodDurationMillis()

        // 2. Initialize timer state
        val initialElapsed = game.actualTimeElapsedInPeriodMillis
        val initialIsInAddedTime = game.inAddedTime || (game.currentPhase.hasTimer() && initialElapsed >= currentRegulationDuration)
        
        val initialDisplayed = if (initialIsInAddedTime) {
            initialElapsed - currentRegulationDuration // Counting up from 0 in added time
        } else {
            currentRegulationDuration - initialElapsed // Counting down to 0 in regulation
        }

        _timerStateFlow.update {
            it.copy(
                actualTimeElapsedInPeriodMillis = initialElapsed,
                isTimerRunning = false, // Will be set in startGameTimer if needed
                currentPhase = game.currentPhase,
                regulationPeriodDurationMillis = currentRegulationDuration,
                displayedMillis = initialDisplayed,
                inAddedTime = initialIsInAddedTime
            )
        }

        if (startImmediately) {
            startGameTimer(game, initialElapsed, initialIsInAddedTime)
        } else {
             // If we're not starting, just ensure foreground/notification reflects current phase
             updateNotificationAndOngoingActivity("${game.currentPhase.readable()}", false)
        }
    }

    private fun canPostNotifications(): Boolean {
        return ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun startGameTimer(game: Game, elapsedMillisAtActivation: Long, isInAddedTimeInitially: Boolean) {
        Log.d(TAG, "startGameTimer called. Phase: ${game.currentPhase}, ElapsedAtActivation: $elapsedMillisAtActivation, IsInAddedTime: $isInAddedTimeInitially")
        
        if (gameCountDownTimer != null) {
            Log.w(TAG, "Timer already running, stopping existing timer before starting new one.")
            gameCountDownTimer?.cancel()
            gameCountDownTimer = null
        }

        // Regulation duration for the current phase
        val currentRegulationDuration = game.regulationPeriodDurationMillis()

        // Explicitly update the phase and duration in the state flow as we start
        _timerStateFlow.update { it.copy(
            currentPhase = game.currentPhase,
            regulationPeriodDurationMillis = currentRegulationDuration,
            inAddedTime = isInAddedTimeInitially
        ) }

        // Register step sensor listener
        sensorManager.unregisterListener(stepSensorListener)
        stepSensor?.let {
            sensorManager.registerListener(stepSensorListener, it, SensorManager.SENSOR_DELAY_UI)
            Log.d(TAG, "Step sensor listener registered.")
        }

        acquireWakeLock()
        
        serviceScope.launch {
            initialMillisForCurrentTicker = if (isInAddedTimeInitially) {
                MAX_ADDED_TIME_COUNTUP_DURATION // In added time, ticker just runs a long time
            } else {
                val remainingInRegulation = currentRegulationDuration - elapsedMillisAtActivation
                if (remainingInRegulation <= 0) { // Should not happen if logic is correct, but handle
                    Log.w(TAG, "Attempting to start timer with 0 or negative time remaining in regulation. Finishing period.")
                    // For now, just don't start the timer and update state.
                    _timerStateFlow.update { it.copy(isTimerRunning = false, actualTimeElapsedInPeriodMillis = currentRegulationDuration, displayedMillis = 0) }
                    onTimerFinishActions(game.currentPhase) // Call a method that handles transitions
                    return@launch
                }
                remainingInRegulation
            }

            if (initialMillisForCurrentTicker <= 0 && !isInAddedTimeInitially) {
                Log.e(TAG, "Error: initialMillisForCurrentTicker is zero or negative for regulation time. Cannot start timer.")
                _timerStateFlow.update { it.copy(isTimerRunning = false) }
                releaseWakeLock()
                stopForegroundSafely("Timer Error")
                return@launch
            }

            healthServicesManager.startExercise(game.isAssistantReferee) // Start HS tracking

            Log.d(TAG, "Starting CountdownTimer. For Phase: ${game.currentPhase}, Initial Ticker ms: $initialMillisForCurrentTicker, ElapsedAtActivation: $elapsedMillisAtActivation, IsInAddedTime: $isInAddedTimeInitially, RegDuration: $currentRegulationDuration")

            timeTickerStartedSystemTime = SystemClock.elapsedRealtime()

            gameCountDownTimer = object : CountDownTimer(initialMillisForCurrentTicker, COUNTDOWN_INTERVAL_MS) {
                override fun onTick(millisUntilFinished: Long) {
                    val currentTimerState = _timerStateFlow.value // Get latest state
                    val timeThisTickerHasRun = initialMillisForCurrentTicker - millisUntilFinished

                    val newActualElapsed: Long
                    val newDisplayedMillis: Long

                    if (currentTimerState.inAddedTime) {
                        // elapsedMillisAtActivation should be currentRegulationDuration when added time starts
                        newActualElapsed = elapsedMillisAtActivation + timeThisTickerHasRun
                        newDisplayedMillis = timeThisTickerHasRun // Display shows time *into* added time
                    } else {
                        // In regulation time
                        newActualElapsed = elapsedMillisAtActivation + timeThisTickerHasRun
                        newDisplayedMillis = currentRegulationDuration - newActualElapsed // Display shows time remaining in regulation
                    }

                    _timerStateFlow.update {
                        it.copy(
                            actualTimeElapsedInPeriodMillis = newActualElapsed,
                            displayedMillis = newDisplayedMillis,
                            isTimerRunning = true
                        )
                    }

                    if (SIMULATION_MODE) {
                       generateSimulatedData()
                    }

                    updateNotificationAndOngoingActivity("${game.currentPhase.readable()}", true)
                }

                override fun onFinish() {
                    Log.d(TAG, "CountdownTimer finished (Regulation end or Ticker end)")
                    val finalState = _timerStateFlow.value
                    if (!finalState.inAddedTime && finalState.currentPhase.canHaveAddedTime()) {
                         // End of regulation
                         _timerStateFlow.update { 
                            it.copy(
                                inAddedTime = true,
                                actualTimeElapsedInPeriodMillis = currentRegulationDuration,
                                displayedMillis = 0L
                            ) 
                         }
                         // Restart ticker for added time
                         startGameTimer(game, currentRegulationDuration, true)
                    } else if (finalState.inAddedTime || finalState.currentPhase.hasTimer()) {
                         // Truly finished added time (e.g. max duration reached) OR finished a break (no added time)
                         pauseGameTimerInternally("Period End")
                    }
                }
            }.start()

            // After starting the timer, set FGS and Ongoing Activity
            updateNotificationAndOngoingActivity("${game.currentPhase.readable()}", true)
        }
    }

    private fun onTimerFinishActions(phase: GamePhase) {
        // Logic for when the timer hits zero at end of regulation
        // This is now partially handled in onFinish of CountDownTimer
    }

    private fun pauseGameTimerInternally(reason: String) {
        Log.d(TAG, "pauseGameTimerInternally: $reason")
        gameCountDownTimer?.cancel()
        gameCountDownTimer = null
        _timerStateFlow.update { it.copy(isTimerRunning = false) }
        updateNotificationAndOngoingActivity("${_timerStateFlow.value.currentPhase.readable()} (Paused)", false)
        releaseWakeLock()
    }

    fun pauseGameTimer(reason: String? = null) {
        Log.d(TAG, "pauseGameTimer called. Reason: $reason")
        gameCountDownTimer?.cancel()
        gameCountDownTimer = null
        sensorManager.unregisterListener(stepSensorListener)
        lastSensorSteps = -1L
        _timerStateFlow.update { it.copy(isTimerRunning = false) }
        updateNotificationAndOngoingActivity("${_timerStateFlow.value.currentPhase.readable()} (Paused)", false)
        releaseWakeLock()
        // We DON'T stop HS tracking on pause, only on end of game
    }

    fun resumeGameTimer(game: Game) {
        Log.d(TAG, "resumeGameTimer called. Phase: ${game.currentPhase}")
        currentInternalGame = game.copy()
        
        val currentRegulationDuration = game.regulationPeriodDurationMillis()

        val elapsed = game.actualTimeElapsedInPeriodMillis
        val inAddedTime = game.inAddedTime || (game.currentPhase.canHaveAddedTime() && elapsed >= currentRegulationDuration)

        _timerStateFlow.update {
            it.copy(
                isTimerRunning = true,
                currentPhase = game.currentPhase,
                actualTimeElapsedInPeriodMillis = elapsed,
                regulationPeriodDurationMillis = currentRegulationDuration,
                inAddedTime = inAddedTime
            )
        }
        startGameTimer(game, elapsed, inAddedTime)
    }

    fun stopGameTimerAndSession() {
        Log.d(TAG, "stopGameTimerAndSession called. Redirecting to cleanup.")
        commandStopGameSessionAndCleanup { }
    }

    fun commandStopGameSessionAndCleanup(onComplete: () -> Unit) {
        Log.d(TAG, "commandStopGameSessionAndCleanup called")
        gameCountDownTimer?.cancel()
        gameCountDownTimer = null
        sensorManager.unregisterListener(stepSensorListener)
        lastSensorSteps = -1L

        serviceScope.launch {
            try {
                healthServicesManager.stopExercise()
                Log.d(TAG, "Health Services exercise stopped successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop Health Services exercise", e)
            } finally {
                _timerStateFlow.update { TimerState() }
                releaseWakeLock()
                stopForegroundSafely("Cleanup")
                onComplete()
            }
        }
    }

    private fun stopForegroundSafely(reason: String?) {
        Log.d(TAG, "stopForegroundSafely: $reason")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        isServiceForeground = false
        notificationManager.cancel(ONGOING_NOTIFICATION_ID_SERVICE)
        stopSelf()
    }

    private fun createServiceNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, ONGOING_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("RefWatch Match")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_stat_refwatch)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun updateNotificationAndOngoingActivity(statusText: String, isRunning: Boolean) {
        if (!canPostNotifications()) return

        val notificationBuilder = NotificationCompat.Builder(this, ONGOING_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("RefWatch Match")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_stat_refwatch)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        notificationBuilder.setContentIntent(pendingIntent)

        // --- Ongoing Activity (Wear OS) ---
        // We must apply the ongoing activity to the builder BEFORE building the notification
        val ongoingActivity = OngoingActivity.Builder(
            this, ONGOING_NOTIFICATION_ID_SERVICE, notificationBuilder
        ).setAnimatedIcon(R.drawable.ic_stat_refwatch)
         .setStaticIcon(R.drawable.ic_stat_refwatch)
         .setTouchIntent(pendingIntent)
         .setStatus(
             Status.Builder()
                 .addPart("status", Status.TextPart(statusText))
                 .build()
         )
        
        ongoingActivity.build().apply(this)

        val notification = notificationBuilder.build()

        // START_FOREGROUND should only be called once or when needed to keep service alive
        // subsequent updates can just use notify()
        if (!isForegroundServiceRunning()) {
            var foregroundServiceType = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                foregroundServiceType = foregroundServiceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                foregroundServiceType = foregroundServiceType or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            }
            startForeground(ONGOING_NOTIFICATION_ID_SERVICE, notification, foregroundServiceType)
            isServiceForeground = true
        } else {
            notificationManager.notify(ONGOING_NOTIFICATION_ID_SERVICE, notification)
        }
    }

    fun commandStartGameSessionAndTimer(game: Game, elapsedMillis: Long) {
        Log.d(TAG, "commandStartGameSessionAndTimer: Phase: ${game.currentPhase}, Elapsed: $elapsedMillis")
        currentInternalGame = game.copy()
        
        val currentRegulationDuration = game.regulationPeriodDurationMillis()

        val inAddedTime = game.inAddedTime || (game.currentPhase.canHaveAddedTime() && elapsedMillis >= currentRegulationDuration)

        _timerStateFlow.update {
            it.copy(
                isTimerRunning = true,
                currentPhase = game.currentPhase,
                actualTimeElapsedInPeriodMillis = elapsedMillis,
                regulationPeriodDurationMillis = currentRegulationDuration,
                inAddedTime = inAddedTime
            )
        }
        startGameTimer(game, elapsedMillis, inAddedTime)
    }
    private fun isForegroundServiceRunning(): Boolean {
        return isServiceForeground
    }

    private fun generateSimulatedData() {
        val now = System.currentTimeMillis()
        _timerStateFlow.update { state ->
            state.copy(
                latestLocation = LocationSample(
                    timestamp = now,
                    latitude = 51.5074 + (Math.random() - 0.5) * 0.001,
                    longitude = -0.1278 + (Math.random() - 0.5) * 0.001,
                    altitude = 20.0 + Math.random() * 5.0
                ),
                latestHeartRate = HeartRateSample(
                    timestamp = now,
                    bpm = 120.0 + (Math.random() - 0.5) * 10.0
                ),
                latestSteps = StepSample(
                    timestamp = now,
                    delta = (1..3).random()
                )
            )
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service Destroyed.")
        gameCountDownTimer?.cancel()
        sensorManager.unregisterListener(stepSensorListener)
        serviceJob.cancel()
        releaseWakeLock()
        super.onDestroy()
    }
}
