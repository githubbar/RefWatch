package com.databelay.refwatch.auth // Or your package

import android.app.Application
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
        _currentUser.value = auth.currentUser
        // You could also update other states here, e.g., if user is null, not loading.
    }

    init {
        // Set initial user state
        _currentUser.value = firebaseAuth.currentUser
        // Register the listener
        firebaseAuth.addAuthStateListener(authStateListener)
    }

    fun signInWithEmailPassword(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
                authResult.user?.let {
                    _authState.value = AuthState.Authenticated(it)
                } ?: run {
                    _authState.value = AuthState.Error("Sign-in failed: User not found.")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "Sign-in failed.")
            }
        }
    }

    fun signUpWithEmailPassword(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
                authResult.user?.let {
                    // Optionally sign them in directly or prompt for verification
                    _authState.value = AuthState.Authenticated(it)
                    // You might want to create a user profile document in Firestore here
                } ?: run {
                    _authState.value = AuthState.Error("Sign-up failed: User not created.")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "Sign-up failed.")
            }
        }
    }

    fun signOut() {
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