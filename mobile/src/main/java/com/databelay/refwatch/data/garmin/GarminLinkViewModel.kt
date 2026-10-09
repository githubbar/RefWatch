package com.databelay.refwatch.data.garmin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GarminLinkUiState(
    val code: PairingCode? = null,
    val secondsLeft: Long = 0,
    val devices: List<LinkedGarminDevice> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null
)

/**
 * The Settings "Garmin watch" section: asks for a pairing code, counts it down, and while it
 * shows, checks every few seconds whether a watch has used it, so the phone confirms the link
 * without the referee refreshing anything.
 */
@HiltViewModel
class GarminLinkViewModel @Inject constructor(
    private val repository: GarminLinkRepository
) : ViewModel() {
    private val _state = MutableStateFlow(GarminLinkUiState())
    val state: StateFlow<GarminLinkUiState> = _state.asStateFlow()

    /** Replaced in tests with the test scheduler's virtual clock. */
    internal var nowMillis: () -> Long = System::currentTimeMillis

    private var countdown: Job? = null

    init {
        refreshDevices()
    }

    fun refreshDevices() {
        viewModelScope.launch { loadDevices() }
    }

    fun requestCode() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            repository.createPairingCode()
                .onSuccess { code ->
                    _state.update { it.copy(code = code, busy = false) }
                    startCountdown(code)
                }
                .onFailure {
                    _state.update {
                        it.copy(busy = false, error = "Couldn't get a code. Check your connection and try again.")
                    }
                }
        }
    }

    fun unlink(deviceId: String) {
        viewModelScope.launch {
            repository.unlink(deviceId)
                .onFailure { _state.update { it.copy(error = "Couldn't unlink the watch. Try again.") } }
            loadDevices()
        }
    }

    private fun startCountdown(code: PairingCode) {
        countdown?.cancel()
        val knownIds = _state.value.devices.map { it.id }.toSet()
        countdown = viewModelScope.launch {
            var sincePoll = 0L
            while (true) {
                val left = secondsLeft(code.expiresAtMillis, nowMillis())
                _state.update { it.copy(secondsLeft = left) }
                if (left == 0L) {
                    _state.update { it.copy(code = null) }
                    loadDevices()
                    return@launch
                }
                if (sincePoll >= POLL_INTERVAL_MS) {
                    sincePoll = 0
                    loadDevices()
                    if (_state.value.devices.any { it.id !in knownIds }) {
                        _state.update { it.copy(code = null) }
                        return@launch
                    }
                }
                delay(TICK_MS)
                sincePoll += TICK_MS
            }
        }
    }

    private suspend fun loadDevices() {
        repository.listDevices().onSuccess { devices -> _state.update { it.copy(devices = devices) } }
    }

    companion object {
        const val TICK_MS = 1_000L
        const val POLL_INTERVAL_MS = 5_000L
    }
}
