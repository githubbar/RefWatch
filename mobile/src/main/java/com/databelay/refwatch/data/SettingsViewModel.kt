package com.databelay.refwatch.data

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SharedPreferences
) : ViewModel() {

    private val _collectPositionInfo = MutableStateFlow(
        prefs.getBoolean(PREF_COLLECT_POSITION_INFO, false)
    )
    val collectPositionInfo: StateFlow<Boolean> = _collectPositionInfo.asStateFlow()

    fun setCollectPositionInfo(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_COLLECT_POSITION_INFO, enabled).apply()
        _collectPositionInfo.value = enabled
    }

    companion object {
        const val PREF_COLLECT_POSITION_INFO = "collect_position_info"
    }
}
