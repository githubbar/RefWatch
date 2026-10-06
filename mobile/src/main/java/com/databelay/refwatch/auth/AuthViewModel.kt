package com.databelay.refwatch.auth // Or your package

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.databelay.refwatch.common.WearSyncConstants
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

sealed class AuthState {
    object Loading : AuthState()
    data class Authenticated(val user: FirebaseUser) : AuthState()
    object Unauthenticated : AuthState()
    data class Error(val message: String) : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val application: Application, // Hilt provides this
    private val authRepository: AuthRepository,
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AuthViewModel"
    }


    // --- StateFlows derived from AuthRepository ---
    val currentUser: StateFlow<FirebaseUser?> = authRepository.observeCurrentUser()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = authRepository.getCurrentUserId()?.let { userId ->
                // Attempt to construct a minimal FirebaseUser if only ID is known initially,
                // or rely on observeCurrentUser to eventually emit the full object.
                // For simplicity, we can start with null and let observeCurrentUser populate it.
                // This initialValue here is for the currentUser StateFlow, not currentUserId.
                null // Or try to get current user directly if authRepository had such a method.
                // The authRepository.observeCurrentUser() will provide the initial value from its own stateIn.
            }
        )

    val currentUserId: StateFlow<String?> = authRepository.observeUserId()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = authRepository.getCurrentUserId() // Get initial directly from repo
        )
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    private val _isLoading = MutableStateFlow(true)
    private val _authError = MutableStateFlow<String?>(null)

    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val authError: StateFlow<String?> = _authError.asStateFlow()

    init {
        Log.d(TAG, "AuthViewModel initialized.")
        viewModelScope.launch {
            // Observe the currentUser from the repository to update AuthState
            // and trigger actions like sending data to the watch.
            authRepository.observeCurrentUser().collect { firebaseUser ->
                Log.d(TAG, "User from AuthRepository: ${firebaseUser?.uid}")
                if (firebaseUser != null) {
                    _authState.value = AuthState.Authenticated(firebaseUser)
                    fetchCustomTokenAndSendDataToWatch(firebaseUser)
                } else {
                    _authState.value = AuthState.Unauthenticated
                    sendAuthDataToWatch(null, null) // Send logout signal
                }
                _isLoading.value = false // Set loading to false once we have a user state
            }
        }
    }

    private fun fetchCustomTokenAndSendDataToWatch(user: FirebaseUser) {
        viewModelScope.launch {
            Log.d(TAG, "User ${user.uid} authenticated. Attempting to get REAL custom token via Cloud Function.")

            val customTokenResult = authRepository.fetchCustomTokenFromServer() // Call the repository

            customTokenResult.onSuccess { customToken ->
                Log.i(TAG, "Successfully fetched custom token from server.")
                sendAuthDataToWatch(user.uid, customToken)
            }.onFailure { exception ->
                Log.e(TAG, "Failed to fetch custom token from server", exception)
                // Handle the error appropriately - maybe show an error message to the user
                // or retry. For now, we won't send anything to the watch if token fetching fails.
                _authError.value = "Failed to prepare watch sign-in: ${exception.localizedMessage}"
                // Optionally, you could still try to send a "login without token" signal
                // or a specific error signal to the watch if that's part of your design.
            }
        }
    }

    private fun sendAuthDataToWatch(userId: String?, customToken: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dataClient = Wearable.getDataClient(application)
                val putDataMapReq = PutDataMapRequest.create(WearSyncConstants.PATH_PHONE_USER_ID)

                if (userId != null && customToken != null) {
                    putDataMapReq.dataMap.putString(WearSyncConstants.KEY_USER_ID, userId)
                    putDataMapReq.dataMap.putString(WearSyncConstants.KEY_CUSTOM_AUTH_TOKEN, customToken)
                    Log.i(TAG, "Preparing to send User ID ('$userId') and Custom Token (length: ${customToken.length}) to watch.")
                } else {
                    Log.i(TAG, "Preparing to send logout signal (empty DataMap for user path) to watch.")
                    // For logout, ensure dataMap is empty or explicitly cleared if needed by watch
                    // Sending an empty map for the path is a common way to signal cleared state.
                }

                val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
                val dataItem = dataClient.putDataItem(putDataReq).await()
                Log.i(TAG, "Successfully sent auth data (DataItem: ${dataItem.uri}) to watch. UserID: '$userId'")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to send auth data (UserID: '$userId') to watch.", e)
            }
        }
    }

    fun signInWithEmailPassword(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authError.value = "Email and password cannot be empty."
            _isLoading.value = false
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _authState.value = AuthState.Loading
            _authError.value = null
            val result = authRepository.signInWithEmailPassword(email, password)
            result.onSuccess {
                // AuthStateListener will handle Authenticated state and triggering data send to watch
                Log.d(TAG, "Sign-in successful via repository.")
            }.onFailure { e ->
                Log.e(TAG, "Sign-in failed via repository", e)
                val errorMessage = e.message ?: "Sign-in failed."
                _authError.value = errorMessage
                _authState.value = AuthState.Error(errorMessage)
                _isLoading.value = false // Ensure loading is stopped on failure
            }
        }
    }

    fun signUpWithEmailPassword(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            val errorMsg = "Email and password cannot be empty."
            _authError.value = errorMsg
            _isLoading.value = false
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _authState.value = AuthState.Loading
            _authError.value = null
            try {
                // authRepository.signUpWithEmailPassword internally calls Firebase
                // The AuthStateListener will detect the new user and trigger data send.
                authRepository.signUpWithEmailPassword(email, password)
                Log.d(TAG, "Firebase signUpWithEmailAndPassword task initiated via repository.")
            } catch (e: Exception) { // Catching from repository call if it throws directly
                Log.e(TAG, "Sign-up initiation failed", e)
                val errorMessage = e.message ?: "Sign-up failed. Please try again."
                _authError.value = errorMessage
                _authState.value = AuthState.Error(errorMessage)
                _isLoading.value = false
            }
        }
    }

    fun signOut() {
        Log.d(TAG, "signOut called.")
        _authError.value = null
        authRepository.signOut()
        // AuthStateListener will handle Unauthenticated state and triggering data send to watch.
        // No need to set _isLoading to true for a local signOut operation.
        // Listener will set _isLoading to false.
    }


    fun clearAuthError() {
        _authError.value = null
        // If the current state is Error, revert to Authenticated or Unauthenticated based on current user
        if (_authState.value is AuthState.Error) {
            if (currentUser.value != null) { // Use the currentUser StateFlow
                _authState.value = AuthState.Authenticated(currentUser.value!!)
            } else {
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    // `deleteUserAccount` function will use `authRepository`
    fun deleteUserAccount() {
        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null // Clear previous errors

            Log.d(TAG, "Attempting to delete user account via repository.")
            val deleteResult = authRepository.deleteUserAccount()

            deleteResult.onSuccess {
                Log.i(TAG, "User account deletion process successful via repository.")
                // The observeCurrentUser() collector in the init block will automatically
                // update _authState to Unauthenticated and _isLoading to false
                // when the user state changes in Firebase.
                // No need to directly set _authState or _isLoading here upon success of this call.
            }.onFailure { exception ->
                Log.e(TAG, "Failed to delete user account via repository.", exception)
                _authError.value = if (exception is FirebaseAuthRecentLoginRequiredException) {
                    // Firebase only deletes an account signed in within the last few minutes.
                    // The games are already erased; signing in again lets the retry finish.
                    "For security, sign out and sign back in, then delete your account again."
                } else {
                    "Failed to delete account: ${exception.localizedMessage ?: "Unknown error"}"
                }
                _isLoading.value = false // Explicitly stop loading on failure
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // No need to remove listener here if AuthRepository manages its own lifecycle
        // or if using callbackFlows that clean up on scope cancellation.
        Log.d(TAG, "AuthViewModel cleared.")
    }

}
