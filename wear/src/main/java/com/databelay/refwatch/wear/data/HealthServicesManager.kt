package com.databelay.refwatch.wear.data

import android.content.Context
import android.util.Log
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.HealthServices
import androidx.health.services.client.HealthServicesClient
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseCapabilities
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.LocationData
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.awaitWithException
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import com.databelay.refwatch.common.HeartRateSample
import com.databelay.refwatch.common.LocationSample
import com.databelay.refwatch.common.StepSample
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

import android.content.SharedPreferences

@Singleton
class HealthServicesManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SharedPreferences
) {
    private val TAG = "HealthServicesManager"
    private val healthServicesClient: HealthServicesClient = HealthServices.getClient(context)
    private val exerciseClient: ExerciseClient = healthServicesClient.exerciseClient

    private val _locationUpdates = MutableStateFlow<LocationSample?>(null)
    val locationUpdates: StateFlow<LocationSample?> = _locationUpdates.asStateFlow()

    private val _heartRateUpdates = MutableStateFlow<HeartRateSample?>(null)
    val heartRateUpdates: StateFlow<HeartRateSample?> = _heartRateUpdates.asStateFlow()

    private val _stepUpdates = MutableStateFlow<StepSample?>(null)
    val stepUpdates: StateFlow<StepSample?> = _stepUpdates.asStateFlow()

    suspend fun startExercise(isAssistantReferee: Boolean = false) {
        Log.d(TAG, "Starting exercise (isAR: $isAssistantReferee)")

        val requiredPermissions = mutableListOf(
            android.Manifest.permission.BODY_SENSORS,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACTIVITY_RECOGNITION
        )

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            Log.e(TAG, "Cannot start exercise. Missing permissions: $missingPermissions")
            return
        }

        // Disable GPS tracking for Assistant Referees
        val collectPositionInfo = if (isAssistantReferee) false else prefs.getBoolean("collect_position_info", true)

        val capabilities = exerciseClient.getCapabilitiesWithException()
        val exerciseCapabilities = capabilities.getExerciseTypeCapabilities(ExerciseType.SOCCER)
        val dataTypes = mutableSetOf<DataType<*, *>>(
            DataType.HEART_RATE_BPM,
            DataType.STEPS,
            DataType.DISTANCE,
            DataType.STEPS_TOTAL,
            DataType.DISTANCE_TOTAL
        )
        if (collectPositionInfo) {
            dataTypes.add(DataType.LOCATION)
        }

        val filteredDataTypes = dataTypes.filter { it in exerciseCapabilities.supportedDataTypes }.toSet()

        val config = ExerciseConfig(
            exerciseType = ExerciseType.SOCCER,
            dataTypes = filteredDataTypes,
            isAutoPauseAndResumeEnabled = false,
            isGpsEnabled = collectPositionInfo
        )

        try {
            exerciseClient.startExerciseWithException(config)
            Log.d(TAG, "Exercise started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start exercise", e)
        }
    }

    suspend fun stopExercise() {
        Log.d(TAG, "Stopping exercise")
        try {
            val exerciseInfo = exerciseClient.getCurrentExerciseInfoAsync().awaitWithException()
            if (exerciseInfo.exerciseTrackedStatus == androidx.health.services.client.data.ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS) {
                exerciseClient.endExerciseWithException()
                Log.d(TAG, "Exercise stopped successfully")
            } else {
                Log.d(TAG, "No owned exercise in progress, skipping endExercise")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop exercise", e)
        }
    }

    fun exerciseUpdateFlow(): Flow<ExerciseUpdate> = callbackFlow {
        val callback = object : ExerciseUpdateCallback {
            override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
                trySend(update)
            }

            override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}

            override fun onRegistered() {}

            override fun onRegistrationFailed(throwable: Throwable) {
                close(throwable)
            }

            override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {
                Log.d(TAG, "Availability changed for $dataType: $availability")
            }
        }
        exerciseClient.setUpdateCallback(callback)
        awaitClose {
            exerciseClient.clearUpdateCallbackAsync(callback)
        }
    }

    private suspend fun ExerciseClient.getCapabilitiesWithException(): ExerciseCapabilities =
        this.getCapabilitiesAsync().awaitWithException()

    private suspend fun ExerciseClient.startExerciseWithException(config: ExerciseConfig) =
        this.startExerciseAsync(config).awaitWithException()

    private suspend fun ExerciseClient.endExerciseWithException() =
        this.endExerciseAsync().awaitWithException()
}
