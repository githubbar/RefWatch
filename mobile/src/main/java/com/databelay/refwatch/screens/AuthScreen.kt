import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.databelay.refwatch.common.LegalLinks
import com.databelay.refwatch.common.theme.RefWatchMobileTheme

// In AuthScreen.kt

@Composable
fun AuthScreen(
    emailValue: String,
    passwordValue: String,
    isLoginMode: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onToggleLoginMode: () -> Unit,
    onAuthAction: () -> Unit, // Renamed from onSignInSuccess for clarity, handles both login/signup
    onClearError: () -> Unit, // To clear error when user types
    modifier: Modifier = Modifier // Keep the modifier
) {
    // No more internal remember states for email, password, isLoginMode
    // No more direct viewModel access for isLoading and errorMessage

    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding(), // Handles keyboard overlaps
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isLoginMode) "Welcome Back!" else "Create Account",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = if (isLoginMode) "Login to continue" else "Sign up to get started",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Conditional "Terms and Privacy Policy" text for Sign Up mode
        if (!isLoginMode) {
            val linkStyles = TextLinkStyles(
                style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)
            )
            val annotatedText = buildAnnotatedString {
                append("By signing up, you agree to our ")
                withLink(LinkAnnotation.Url(LegalLinks.TERMS_OF_USE_URL, linkStyles)) {
                    append("Terms of Service")
                }
                append(" and ")
                withLink(LinkAnnotation.Url(LegalLinks.PRIVACY_POLICY_URL, linkStyles)) {
                    append("Privacy Policy")
                }
                append(".")
            }
            Text(
                text = annotatedText,
                style = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Center),
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            Spacer(modifier = Modifier.height(24.dp))
        } else {
            Spacer(modifier = Modifier.height(24.dp + (MaterialTheme.typography.bodySmall.fontSize.value * 2).dp))
        }

        OutlinedTextField(
            value = emailValue, // Use parameter
            onValueChange = { newValue ->
                onEmailChange(newValue) // Call lambda
                if (errorMessage != null) {
                    onClearError() // Call lambda
                }
            },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !isLoading,
            isError = errorMessage != null && (
                    errorMessage.contains("email", ignoreCase = true) ||
                            errorMessage.contains("credentials", ignoreCase = true) ||
                            errorMessage.contains("empty", ignoreCase = true) && emailValue.isEmpty()
                    )
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = passwordValue, // Use parameter
            onValueChange = { newValue ->
                onPasswordChange(newValue) // Call lambda
                if (errorMessage != null) {
                    onClearError() // Call lambda
                }
            },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !isLoading,
            isError = errorMessage != null && (
                    errorMessage.contains("password", ignoreCase = true) ||
                            errorMessage.contains("credentials", ignoreCase = true) ||
                            errorMessage.contains("empty", ignoreCase = true) && passwordValue.isEmpty()
                    )
        )
        Spacer(modifier = Modifier.height(8.dp))

        errorMessage?.let { errMessage ->
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
                // The decision of what action to take (login vs signup)
                // is now handled by the stateful parent based on `isLoginMode`
                onAuthAction() // Call lambda
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
                onToggleLoginMode() // Call lambda
                if (errorMessage != null) {
                    onClearError() // Call lambda
                }
            },
            enabled = !isLoading
        ) {
            Text(if (isLoginMode) "Need an account? Sign Up" else "Have an account? Login")
        }
    }
}

// In your AuthScreen.kt or a preview file

// (Keep your PreviewAuthViewModel and FakeAuthRepository as before, they don't change for this refactor)

@Preview(name = "Stateless Login Mode - Light", showBackground = true,uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun StatelessAuthScreenPreview_LoginMode() {
    var email by remember { mutableStateOf("test@example.com") }
    var password by remember { mutableStateOf("password") }
    var isLoginMode by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    RefWatchMobileTheme {
        AuthScreen(
            emailValue = email,
            passwordValue = password,
            isLoginMode = isLoginMode,
            isLoading = isLoading,
            errorMessage = errorMessage,
            onEmailChange = { email = it },
            onPasswordChange = { password = it },
            onToggleLoginMode = { isLoginMode = !isLoginMode },
            onAuthAction = { isLoading = true /* Simulate action */ },
            onClearError = { errorMessage = null }
        )
    }}

@Preview(name = "Stateless Sign Up - Error", showBackground = true,uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun StatelessAuthScreenPreview_SignUpError() {
    var email by remember { mutableStateOf("newuser@example.com") }
    var password by remember { mutableStateOf("123") } // Short password might be an error
    var isLoginMode by remember { mutableStateOf(false) } // Sign Up Mode
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>("Password should be at least 6 characters.") }

    RefWatchMobileTheme {
        AuthScreen(
            emailValue = email,
            passwordValue = password,
            isLoginMode = isLoginMode,
            isLoading = isLoading,
            errorMessage = errorMessage,
            onEmailChange = { email = it; errorMessage = null }, // Clear error on change
            onPasswordChange = { password = it; errorMessage = null }, // Clear error on change
            onToggleLoginMode = { isLoginMode = !isLoginMode },
            onAuthAction = { /* Simulate action */ },
            onClearError = { errorMessage = null }
        )
    }
}