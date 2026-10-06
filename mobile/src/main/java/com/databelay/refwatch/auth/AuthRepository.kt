// In a new file, e.g., auth/AuthRepository.kt
package com.databelay.refwatch.auth

import android.util.Log
import com.databelay.refwatch.data.GameStorageMobile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.functions.functions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// --- Define the Interface ---
interface AuthRepository {
    fun observeUserId(): StateFlow<String?>
    fun getCurrentUserId(): String?
    fun observeCurrentUser(): StateFlow<FirebaseUser?> // Keep your existing currentUserFlow logic
    suspend fun fetchCustomTokenFromServer(): Result<String>
    suspend fun signInWithEmailPassword(email: String, pass: String): Result<FirebaseUser>
    suspend fun signUpWithEmailPassword(email: String, pass: String, displayName: String? = null): Result<FirebaseUser>
    fun signOut()
    suspend fun deleteUserAccount(): Result<Unit> // <<< ADDED METHOD
}

// --- Hilt-Injectable Implementation ---
@Singleton // This repository can be a singleton
class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val gameStorage: GameStorageMobile
    // If you need a specific CoroutineScope for stateIn, you can inject one:
    // @ApplicationScope private val externalScope: CoroutineScope // Requires defining @ApplicationScope
) : AuthRepository {

    companion object {
        private const val TAG = "FirebaseAuthRepo" // Changed tag slightly for clarity
    }

    // You need a CoroutineScope for stateIn.
    // If this is a @Singleton, an application-level scope is appropriate.
    // For simplicity here, creating one. In a larger app, inject a shared scope.
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun observeCurrentUser(): StateFlow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            Log.d(TAG, "AuthStateListener in Repository: User: ${auth.currentUser?.uid}")
            trySend(auth.currentUser).isSuccess
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose {
            Log.d(TAG, "AuthStateListener in Repository: Removing listener.")
            firebaseAuth.removeAuthStateListener(listener)
        }
    }.stateIn(
        scope = repositoryScope, // Provide a CoroutineScope
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = firebaseAuth.currentUser
    )

    override fun observeUserId(): StateFlow<String?> = callbackFlow<String?> {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.uid)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.stateIn(
        scope = repositoryScope, // Use the same scope
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = firebaseAuth.currentUser?.uid
    )

    override fun getCurrentUserId(): String? {
        return firebaseAuth.currentUser?.uid
    }

    override suspend fun fetchCustomTokenFromServer(): Result<String> {
        val currentUser = firebaseAuth.currentUser
        Log.d(TAG, "Attempting to call generateCustomToken. Current user: ${currentUser?.uid}, Email: ${currentUser?.email}")

        if (currentUser == null) {
            Log.e(TAG, "User is NULL. Cannot call generateCustomToken without authentication.")
            return Result.failure(Exception("User not authenticated for Cloud Function call"))
        }

        try {
            Log.d(TAG, "Attempting to refresh ID token...")
            val tokenResult = currentUser.getIdToken(true).await() // true forces refresh
            if (tokenResult?.token == null) {
                Log.e(TAG, "ID token is null after refresh attempt.")
                return Result.failure(Exception("Failed to refresh ID token (token is null) before Cloud Function call"))
            }
            Log.i(TAG, "ID token refreshed successfully. Token starts with: ${tokenResult.token?.take(10)}...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh ID token", e)
            return Result.failure(Exception("Failed to refresh ID token before Cloud Function call", e))
        }

        return try {
            val result = Firebase.functions
                .getHttpsCallable("generateCustomToken")
                .call()
                .await()

            val data = result.data as? Map<*, *>
            val customToken = data?.get("customToken") as? String

            if (customToken != null) {
                Log.i(TAG, "Successfully fetched custom token of length: ${customToken.length}")
                Result.success(customToken)
            } else {
                Log.e(TAG, "Custom token from server is null or not a string. Data received: ${result.data}")
                Result.failure(Exception("Failed to parse custom token from server response."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling generateCustomToken Cloud Function", e)
            Result.failure(e)
        }
    }

    override suspend fun signInWithEmailPassword(email: String, pass: String): Result<FirebaseUser> {
        return try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, pass).await()
            // FirebaseUser will be non-null on success from signInWithEmailAndPassword
            Result.success(authResult.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signUpWithEmailPassword(email: String, pass: String, displayName: String?): Result<FirebaseUser> {
        return try {
            Log.d(TAG, "Attempting sign-up for email: $email")
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, pass).await()
            val firebaseUser = authResult.user
                ?: return Result.failure(Exception("Sign-up failed: FirebaseUser is null after creation."))

            Log.i(TAG, "Sign-up successful for email: $email, UID: ${firebaseUser.uid}")

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
            Result.success(firebaseUser)
        } catch (e: Exception) {
            Log.e(TAG, "Sign-up failed for email: $email", e)
            Result.failure(e)
        }
    }

    override fun signOut() {
        Log.d(TAG, "Signing out user.")
        firebaseAuth.signOut()
        // The observers of observeUserId() and observeCurrentUser() will get the null emission.
    }

    override suspend fun deleteUserAccount(): Result<Unit> {
        val currentUser = observeCurrentUser().first() // Get the most recent user from the flow

        if (currentUser == null) {
            Log.w(TAG, "deleteUserAccount: No user currently authenticated to delete.")
            return Result.failure(Exception("No user authenticated to delete."))
        }

        // Erase stored games first: once the account is gone the security rules no longer let
        // this user reach them, and they would be orphaned in Firestore.
        gameStorage.deleteAllUserData(currentUser.uid).onFailure { return Result.failure(it) }

        return try {
            Log.d(TAG, "Attempting to delete account for user: ${currentUser.uid}")
            currentUser.delete().await() // Use await() for suspend function
            Log.i(TAG, "User account ${currentUser.uid} deleted successfully from Firebase Authentication.")
            // Note: After successful deletion, observeCurrentUser() will eventually emit null
            // due to the AuthStateListener.
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete user account ${currentUser.uid}", e)
            Result.failure(e)
        }
    }
}
