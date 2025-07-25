package com.databelay.refwatch.auth

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    var isLoginMode by remember { mutableStateOf(true) }

    val authState by authViewModel.authState.collectAsState()
    val isLoading by authViewModel.isLoading.collectAsState()

    // Capture the errorMessage value here
    val currentErrorMessage = authViewModel.authError.collectAsState().value
    // Or, if you prefer to keep the 'by' delegate for other uses, do it just before usage:
    // val errorMessage by authViewModel.authError.collectAsState()
    // val currentErrorMessage = errorMessage // If needed for a specific block

    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            Log.d("AuthScreen", "Authentication successful. Calling onSignInSuccess.")
            onSignInSuccess()
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
                if (currentErrorMessage != null) { // Use local variable
                    authViewModel.clearAuthError()
                }
            },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !isLoading,
            isError = currentErrorMessage != null && ( // Use local variable
                    currentErrorMessage.contains("email", ignoreCase = true) ||
                            currentErrorMessage.contains("credentials", ignoreCase = true) ||
                            currentErrorMessage.contains("empty", ignoreCase = true) && email.isEmpty()
                    )
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                if (currentErrorMessage != null) { // Use local variable
                    authViewModel.clearAuthError()
                }
            },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !isLoading,
            isError = currentErrorMessage != null && ( // Use local variable
                    currentErrorMessage.contains("password", ignoreCase = true) ||
                            currentErrorMessage.contains("credentials", ignoreCase = true) ||
                            currentErrorMessage.contains("empty", ignoreCase = true) && password.isEmpty()
                    )
        )
        Spacer(modifier = Modifier.height(8.dp))

        currentErrorMessage?.let { errMessage -> // Use local variable
            Text(
                text = errMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (isLoginMode) {
                    Log.d("AuthScreen", "Attempting Login with Email: $email, Password: $password")
                    authViewModel.signInWithEmailPassword(email, password)
                } else {
                    Log.d("AuthScreen", "Attempting Sign Up with Email: $email, Password: $password")
                    authViewModel.signUpWithEmailPassword(email, password)
                }
            },
            modifier = Modifier.fillMaxWidth(0.8f),
            enabled = !isLoading
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
                if (currentErrorMessage != null) { // Use local variable
                    authViewModel.clearAuthError()
                }
            },
            enabled = !isLoading
        ) {
            Text(if (isLoginMode) "Need an account? Sign Up" else "Have an account? Login")
        }
    }
}

