package com.databelay.refwatch.auth // Or your package

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
import kotlinx.coroutines.tasks.await
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val firebaseAuth: FirebaseAuth // Hilt injects this from FirebaseModule
) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "AuthViewModel"
    }
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Private MutableStateFlow to hold the current user
    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    // Publicly exposed StateFlow (read-only)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    // You might also have a state for loading, errors, etc.
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        Log.d("AuthVM", "AuthStateListener triggered. Current user from auth: ${auth.currentUser?.uid}")
        _currentUser.value = auth.currentUser
        // You could also update other states here, e.g., if user is null, not loading.
    }

    init {
        // Set initial user state
        _currentUser.value = firebaseAuth.currentUser
        // Register the listener
        firebaseAuth.addAuthStateListener(authStateListener)
        Log.d("AuthVM", "AuthStateListener added. Initial user: ${_currentUser.value?.uid}")

        // Launch a coroutine to observe _currentUser and update _authState
        viewModelScope.launch {
            _currentUser.collect { firebaseUser ->
                Log.d(TAG, "_currentUser collected: ${firebaseUser?.uid}")
                if (firebaseUser != null) {
                    _authState.value = AuthState.Authenticated(firebaseUser)
                    Log.d(TAG, "_authState set to Authenticated.")
                } else {
                    // This handles both initial unauthenticated state and sign-out
                    _authState.value = AuthState.Unauthenticated
                    Log.d(TAG, "_authState set to Unauthenticated.")
                }
                // If _authState was Loading, this will now transition it.
            }
        }
        // The initial _authState.value is AuthState.Loading.
        // The collector above will immediately process the initial _currentUser.value.
        // If initialUser is null, it becomes Unauthenticated.
        // If initialUser is not null, it becomes Authenticated.
        // The AuthStateListener then handles subsequent changes.
    }

    fun signInWithEmailPassword(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading // Set loading state for this specific action
            _isLoading.value = true
            _authError.value = null
            try {
                val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
                // The AuthStateListener and the _currentUser.collect block will handle
                // transitioning to Authenticated or Unauthenticated state based on authResult.user
                // No need to directly set _authState.value = AuthState.Authenticated(it) here
                // if the listener is robust. However, for immediate feedback after this specific
                // action, explicitly setting it can be fine too.
                // For consistency with the listener driving state:
                if (authResult.user == null) { // Explicitly handle if signIn succeeds but user is null (rare)
                    _authState.value = AuthState.Error("Sign-in failed: User data not available.")
                }
                // Listener will update _currentUser, which updates _authState
            } catch (e: Exception) {
                Log.e(TAG, "Sign-in error", e)
                _authState.value = AuthState.Error(e.localizedMessage ?: "Sign-in failed.")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signUpWithEmailPassword(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            _isLoading.value = true
            _authError.value = null
            try {
                val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
                // Listener will update _currentUser, which updates _authState
                if (authResult.user == null) {
                    _authState.value = AuthState.Error("Sign-up failed: User not created properly.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sign-up error", e)
                _authState.value = AuthState.Error(e.localizedMessage ?: "Sign-up failed.")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signOut() {
        _authState.value = AuthState.Loading
        _isLoading.value = true
        firebaseAuth.signOut()
        // The AuthStateListener will update _authState to Unauthenticated
    }

    fun clearAuthError() {
        _authError.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // Unregister the listener when the ViewModel is cleared
        firebaseAuth.removeAuthStateListener(authStateListener)
    }
}