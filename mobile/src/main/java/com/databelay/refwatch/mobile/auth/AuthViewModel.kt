package com.databelay.refwatch.mobile.auth // Or your package

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed class AuthState {
    object Loading : AuthState()
    data class Authenticated(val user: FirebaseUser) : AuthState()
    object Unauthenticated : AuthState()
    data class Error(val message: String) : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    application: Application, // Hilt provides this
    private val authRepository: AuthRepository // Inject AuthRepository
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AuthViewModel"
    }
    // Private MutableStateFlows
    private val _currentUser = MutableStateFlow<FirebaseUser?>(null) // For direct user object access
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading) // Primary state for UI logic
    private val _isLoading = MutableStateFlow(true) // Start as true until initial check completes
    private val _authError = MutableStateFlow<String?>(null)

    // Publicly exposed StateFlows (read-only)
    // This StateFlow directly exposes the user from the repository's flow
    val currentUser: StateFlow<FirebaseUser?> = authRepository.currentUserFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null // Or get initial from FirebaseAuth directly if repo flow is cold
        )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val firebaseAuthListener = FirebaseAuth.AuthStateListener { auth ->
        val firebaseUser = auth.currentUser
        Log.d(TAG, "AuthStateListener triggered. User from auth: ${firebaseUser?.uid}")

        if (firebaseUser != null) {
            _currentUser.value = firebaseUser
            _authState.value = AuthState.Authenticated(firebaseUser)
        } else {
            _currentUser.value = null
            _authState.value = AuthState.Unauthenticated
        }
        // CRITICAL: Always update isLoading when the auth state is resolved by the listener
        _isLoading.value = false
        // Optionally clear error when auth state changes, or let UI actions clear it.
        // if (_authError.value != null && _authState.value !is AuthState.Error) {
        //     _authError.value = null
        // }
    }

    init {
        Log.d(TAG, "AuthViewModel initialized.")
        viewModelScope.launch {
            // Observe the repository's currentUserFlow to update internal AuthState
            authRepository.currentUserFlow.collect { user ->
                Log.d("AuthVM", "Collected user from repository: ${user?.uid}")
                if (user != null) {
                    _authState.value = AuthState.Authenticated(user)
                } else {
                    _authState.value = AuthState.Unauthenticated
                }
                _isLoading.value = false // Auth state resolved
            }
        }
        // Initial loading state will be set to false once the first emission from currentUserFlow arrives.
        // To avoid brief loading state if user is already logged in:
        viewModelScope.launch {
            val initialUser = authRepository.currentUserFlow // Get initial value
            if (initialUser == null) _isLoading.value = false // if no user, not loading for user data
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
                // No need to set _authState here, the collector on currentUserFlow will handle it.
                // _isLoading will also be set to false by that collector.
                Log.d("AuthVM", "Sign-in successful via repository.")
            }.onFailure { e ->
                Log.e("AuthVM", "Sign-in failed via repository", e)
                val errorMessage = e.message ?: "Sign-in failed."
                _authError.value = errorMessage
                _authState.value = AuthState.Error(errorMessage)
                _isLoading.value = false
            }
        }
    }

    fun signUpWithEmailPassword(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            val errorMsg = "Email and password cannot be empty."
            _authError.value = errorMsg
            // Don't set _authState to Error here for simple validation,
            // let the UI just show the _authError.
            // _authState.value = AuthState.Error(errorMsg) // This might be too aggressive for field validation
            _isLoading.value = false // Ensure loading is off for validation errors
            return // Stop further processing
        }
        viewModelScope.launch {
            _isLoading.value = true
            _authState.value = AuthState.Loading
            _authError.value = null // Clear previous errors before new attempt
            try {
                Log.d(TAG, "Attempting Firebase sign-in...")
                authRepository.signUpWithEmailPassword(email, password)
                Log.d(TAG, "Firebase signUpWithEmailAndPassword task successful.")
                // Listener will handle setting _authState to Authenticated and _isLoading to false
            } catch (e: Exception) {
                Log.e(TAG, "Firebase sign-up failed", e)
                val errorMessage = e.message ?: "Sign-up failed. Please try again."
                _authError.value = errorMessage
                _authState.value = AuthState.Error(errorMessage) // Set authState to Error on actual auth failure
                _isLoading.value = false
            }
        }
    }

    fun signOut() {
        Log.d(TAG, "signOut called.")
        // No need to set _isLoading or _authState to Loading for local signOut.
        // The AuthStateListener will handle these state changes.
        _authError.value = null // Clear any existing errors on sign out
        authRepository.signOut()
        // Listener will set _authState to Unauthenticated and _isLoading to false.
    }

    fun clearAuthError() {
        _authError.value = null
        // If current state is Error, move it to Unauthenticated or previous valid state
        if (_authState.value is AuthState.Error) {
            if (_currentUser.value != null) {
                // This case should be rare if error also unauthenticates
                _authState.value = AuthState.Authenticated(_currentUser.value!!)
            } else {
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared.")
    }
}