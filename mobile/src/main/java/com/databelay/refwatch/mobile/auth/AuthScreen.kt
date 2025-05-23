package com.databelay.refwatch.mobile.auth

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    authViewModel: AuthViewModel,
    onSignInSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoginMode by remember { mutableStateOf(true) } // Toggle between Login and Sign Up

    // Observe the authentication state from the ViewModel
    val authState by authViewModel.authState.collectAsState()
    val isLoading by authViewModel.isLoading.collectAsState()
    val errorMessage by authViewModel.authError.collectAsState()

    // Effect to trigger navigation when authentication is successful
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            Log.d("AuthScreen", "Authentication successful. Calling onSignInSuccess.")
            onSignInSuccess()
        }
    }

    // Effect to clear error message when user starts typing in EITHER field,
    // but only if an error message is currently being shown.
    LaunchedEffect(email, password, errorMessage) {
        if (errorMessage != null && (email.isNotEmpty() || password.isNotEmpty())) {
            // More specific: clear only if the input fields that *had* an error are now being typed into.
            // For simplicity, any typing after an error is shown can clear it.
            // OR, only clear when a new auth attempt is made.
            // For now, let's remove this auto-clear on typing and clear it explicitly.
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (isLoginMode) "Login" else "Sign Up",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                if (errorMessage != null) authViewModel.clearAuthError() // Clear error on typing
            },            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !isLoading,
            isError = errorMessage?.contains("Email", ignoreCase = true) == true || errorMessage?.contains("empty", ignoreCase = true) == true
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                if (errorMessage != null) authViewModel.clearAuthError() // Clear error on typing
            },            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !isLoading,
            isError = errorMessage?.contains("Password", ignoreCase = true) == true || errorMessage?.contains("empty", ignoreCase = true) == true
        )
        Spacer(modifier = Modifier.height(8.dp))

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            // Optionally, auto-clear the error after a delay or on text input change
            LaunchedEffect(email, password) { // Clear error if user starts typing again
                if (it.isNotEmpty()) { // only clear if there was an error
                    authViewModel.clearAuthError()
                }
            }
        }


        Spacer(modifier = Modifier.height(16.dp))
        // FIXME: if login is empty, error flickers and disapppears
        Button(
            onClick = {
                // Explicitly clear any previous error message before a new attempt
                if (errorMessage != null) { // Check if there actually is an error
                    authViewModel.clearAuthError()
                }
                if (isLoginMode) {
                    Log.d("AuthScreen", "Attempting Login with Email: $email")
                    authViewModel.signInWithEmailPassword(email, password)
                } else {
                    Log.d("AuthScreen", "Attempting Sign Up with Email: $email")
                    authViewModel.signUpWithEmailPassword(email, password)
                }
            },
            modifier = Modifier.fillMaxWidth(0.8f),
            enabled = !isLoading // Disable button while loading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(if (isLoginMode) "Login" else "Sign Up")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = {
                isLoginMode = !isLoginMode
                if (errorMessage != null) authViewModel.clearAuthError()
            },
            enabled = !isLoading
        ) {
            Text(if (isLoginMode) "Need an account? Sign Up" else "Have an account? Login")
        }
    }
}