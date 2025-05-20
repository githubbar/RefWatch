// In a new file, e.g., auth/AuthRepository.kt
package com.databelay.refwatch.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton // This repository can be a singleton
class AuthRepository @Inject constructor(private val firebaseAuth: FirebaseAuth) {

    companion object {
        private const val TAG = "AuthRepository"
    }

    // Exposes a Flow of the current FirebaseUser
    val currentUserFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            Log.d(TAG, "AuthStateListener in Repository: User: ${auth.currentUser?.uid}")
            trySend(auth.currentUser).isSuccess // Offer the new user state
        }
        firebaseAuth.addAuthStateListener(listener)
        // When the flow is cancelled, remove the listener
        awaitClose {
            Log.d(TAG, "AuthStateListener in Repository: Removing listener.")
            firebaseAuth.removeAuthStateListener(listener)
        }
    }

    // You can also add suspend functions for signIn, signOut, signUp here
    suspend fun signInWithEmailPassword(email: String, pass: String): Result<FirebaseUser> {
        return try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, pass).await()
            Result.success(authResult.user!!) // Assuming user is not null on success
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        firebaseAuth.signOut()
    }
    // ... other auth methods ...
}