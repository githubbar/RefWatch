package com.databelay.refwatch.mobile

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val phonePinger: PhonePinger // Hilt injects PhonePinger
) : ViewModel() {

    fun onSendPingClicked() {
        // If sendPing were a suspend function, you'd launch a coroutine:
        // viewModelScope.launch { phonePinger.sendPing() }
        // Since it launches its own coroutine internally, direct call is fine.
        phonePinger.sendPing()
    }
}