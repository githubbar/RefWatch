package com.databelay.refwatch.mobile.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import java.util.UUID
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import kotlin.test.Test

@RunWith(AndroidJUnit4::class)
class FirebaseAuthDirectTest {

    private lateinit var firebaseAuth: FirebaseAuth
    private val testEmail = "testuser-${UUID.randomUUID()}@example.com"
    private val testPassword = "password123"

    @Before
    fun setup() {
        // Ensure Firebase is initialized (important for instrumented tests)
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        firebaseAuth = FirebaseAuth.getInstance()

        // Sign out any existing user to ensure a clean state for each test
        runBlocking { // Use runBlocking for setup/teardown in tests if needed
            if (firebaseAuth.currentUser != null) {
                firebaseAuth.signOut()
            }
        }
    }

    @After
    fun tearDown() {
        // Clean up: delete the test user if created, and sign out
        runBlocking {
            val user = firebaseAuth.currentUser
            if (user != null && user.email == testEmail) {
                try {
                    user.delete().await()
                } catch (e: Exception) {
                    // Ignore if deletion fails (e.g., requires recent sign-in)
                    println("Warning: Could not delete test user ${user.email}: ${e.message}")
                }
            }
            firebaseAuth.signOut() // Ensure sign out after each test
        }
    }

    @Test
    fun signUpAndSignInWithEmailPassword_succeeds() = runBlocking {
        // Sign Up
        var user: FirebaseUser? = null
        try {
            val authResult =
                firebaseAuth.createUserWithEmailAndPassword(testEmail, testPassword).await()
            user = authResult.user
            assertThat(user).isNotNull()
            assertThat(user?.email).isEqualTo(testEmail)
        } catch (e: Exception) {
            throw AssertionError("Sign up failed", e)
        }

        // Sign out after sign up to test sign in
        firebaseAuth.signOut()
        assertThat(firebaseAuth.currentUser).isNull()

        // Sign In
        try {
            val signInResult =
                firebaseAuth.signInWithEmailAndPassword(testEmail, testPassword).await()
            user = signInResult.user
            assertThat(user).isNotNull()
            assertThat(user?.email).isEqualTo(testEmail)
            assertThat(firebaseAuth.currentUser).isNotNull()
            assertThat(firebaseAuth.currentUser?.uid).isEqualTo(user?.uid)
        } catch (e: Exception) {
            throw AssertionError("Sign in failed", e)
        }
    }

    @Test
    fun signOut_clearsCurrentUser() = runBlocking {
        // First, sign in a user (or sign up)
        firebaseAuth.createUserWithEmailAndPassword(testEmail, testPassword).await()
        assertThat(firebaseAuth.currentUser).isNotNull()

        // Then, sign out
        firebaseAuth.signOut()
        assertThat(firebaseAuth.currentUser).isNull()
    }
}