package com.databelay.refwatch.data

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.databelay.refwatch.common.WearSyncConstants
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SharedPreferences,
    private val dataClient: DataClient
) : ViewModel() {

    /**
     * Whether the watch records GPS during a game. Like [logGoalScorer], the phone owns it
     * and the watch caches the last synced value.
     */
    private val _collectPositionInfo = MutableStateFlow(
        prefs.getBoolean(WearSyncConstants.KEY_COLLECT_POSITION_INFO, false)
    )
    val collectPositionInfo: StateFlow<Boolean> = _collectPositionInfo.asStateFlow()

    fun setCollectPositionInfo(enabled: Boolean) {
        prefs.edit().putBoolean(WearSyncConstants.KEY_COLLECT_POSITION_INFO, enabled).apply()
        _collectPositionInfo.value = enabled
        syncSettingsToWatch()
    }

    /**
     * Whether the watch asks who scored after each goal. The phone owns this setting; the
     * watch caches whatever the last sync delivered. Off by default, in which case the
     * watch records goals in a single tap as before.
     */
    private val _logGoalScorer = MutableStateFlow(
        prefs.getBoolean(WearSyncConstants.KEY_LOG_GOAL_SCORER, false)
    )
    val logGoalScorer: StateFlow<Boolean> = _logGoalScorer.asStateFlow()

    fun setLogGoalScorer(enabled: Boolean) {
        prefs.edit().putBoolean(WearSyncConstants.KEY_LOG_GOAL_SCORER, enabled).apply()
        _logGoalScorer.value = enabled
        syncSettingsToWatch()
    }

    /**
     * Pushes the phone-owned settings to the watch. The data layer keeps the item, so a
     * watch that is out of range picks it up on reconnect rather than missing the change.
     */
    private fun syncSettingsToWatch() {
        viewModelScope.launch {
            try {
                // Every phone-owned setting goes in the one item, so a watch that missed an
                // earlier change still ends up with the full, current set.
                val request = PutDataMapRequest.create(WearSyncConstants.PATH_SETTINGS).apply {
                    dataMap.putBoolean(
                        WearSyncConstants.KEY_LOG_GOAL_SCORER,
                        _logGoalScorer.value
                    )
                    dataMap.putBoolean(
                        WearSyncConstants.KEY_COLLECT_POSITION_INFO,
                        _collectPositionInfo.value
                    )
                    dataMap.putLong(
                        WearSyncConstants.KEY_SETTINGS_UPDATED_AT,
                        System.currentTimeMillis()
                    )
                }
                dataClient.putDataItem(request.asPutDataRequest().setUrgent()).await()
                Log.d(
                    TAG,
                    "Synced settings to watch. logGoalScorer=${_logGoalScorer.value}, " +
                        "collectPositionInfo=${_collectPositionInfo.value}"
                )
            } catch (e: Exception) {
                // A missing or unpaired watch is normal, not an error worth surfacing --
                // the phone keeps its own value and the watch picks it up when it next syncs.
                Log.w(TAG, "Could not sync settings to watch.", e)
            }
        }
    }

    companion object {
        private const val TAG = "SettingsViewModel"
    }
}
