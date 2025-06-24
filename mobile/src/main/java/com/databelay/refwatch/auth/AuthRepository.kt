// In a new file, e.g., auth/AuthRepository.kt
package com.databelay.refwatch.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
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

    suspend fun signUpWithEmailPassword(email: String, pass: String, displayName: String? = null): Result<FirebaseUser> {
        return try {
            Log.d(TAG, "Attempting sign-up for email: $email")
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, pass).await()
            val firebaseUser = authResult.user
            if (firebaseUser == null) {
                Log.e(TAG, "Sign-up failed for email: $email - FirebaseUser is null after creation.")
                return Result.failure(Exception("Sign-up failed: User not created."))
            }

            Log.i(TAG, "Sign-up successful for email: $email, UID: ${firebaseUser.uid}")

            // Optionally, update the user's display name if provided
            if (displayName != null && displayName.isNotBlank()) {
                try {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(displayName)
                        .build()
                    firebaseUser.updateProfile(profileUpdates).await()
                    Log.i(TAG, "Display name '$displayName' set for user: ${firebaseUser.uid}")
                } catch (profileUpdateException: Exception) {
                    Log.w(TAG, "Sign-up successful, but failed to set display name for user: ${firebaseUser.uid}", profileUpdateException)
                    // Continue, as sign-up itself was successful
                }
            }
            // The AuthStateListener in currentUserFlow will eventually emit this new user.
            Result.success(firebaseUser)
        } catch (e: Exception) {
            Log.e(TAG, "Sign-up failed for email: $email", e)
            Result.failure(e)
        }
    }

    fun signOut() {
        firebaseAuth.signOut()
    }
    // You might want to add other methods like:
    // suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    // suspend fun reauthenticateUser(credential: AuthCredential): Result<Unit>
    // suspend fun deleteUser(): Result<Unit>
}