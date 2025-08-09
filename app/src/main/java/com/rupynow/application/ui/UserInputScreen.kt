package com.rupynow.application.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.rupynow.application.R
import com.rupynow.application.MainActivity
import com.rupynow.application.UserInputUiState

@Composable
fun UserInputScreen(
    onVerify: (String, String) -> Unit,
    initialPhoneNumber: String = "",
    initialEmail: String = "",
    context: MainActivity,
    onLoadingStateChange: (Boolean) -> Unit = {},
    onResetLoading: (() -> Unit) -> Unit = {}
) {
    var email by remember { mutableStateOf(initialEmail) }
    var phone by remember { mutableStateOf(initialPhoneNumber) }
    var uiState by remember { mutableStateOf<UserInputUiState>(UserInputUiState.Idle) }

    // Add loading state reset callback
    val resetLoading = {
        uiState = UserInputUiState.Idle
        onLoadingStateChange(false)
    }

    // Pass the reset callback to parent
    LaunchedEffect(Unit) {
        onResetLoading(resetLoading)
    }

    // Handle loading state from OTP generation
    LaunchedEffect(Unit) {
        // Reset loading state when screen is shown
        uiState = UserInputUiState.Idle
    }

    // Reset loading state when user interacts with inputs (indicating they want to retry)
    LaunchedEffect(email, phone) {
        if (uiState is UserInputUiState.Loading) {
            // If user is typing while loading, they might want to retry
            // This will be handled by the button click logic
        }
    }

    // Reset loading state when screen is recomposed (after error)
    LaunchedEffect(Unit) {
        // This will reset loading state when screen is recomposed
        // which happens after an error in OTP generation
        uiState = UserInputUiState.Idle
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // App Title
        Text(
            text = stringResource(R.string.welcome_to_rupynow),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.please_enter_details),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Email Input
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.email)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Email,
                    contentDescription = "Email"
                )
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Phone Input
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text(stringResource(R.string.phone_number)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Phone,
                    contentDescription = "Phone"
                )
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.weight(1f))

        // Show state-specific UI
        when (val currentState = uiState) {
            is UserInputUiState.Idle -> {
                // Verify Button
                Button(
                    onClick = {
                        if (email.isNotBlank() && phone.isNotBlank()) {
                            uiState = UserInputUiState.Loading
                            onVerify(email, phone)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    enabled = email.isNotBlank() && phone.isNotBlank()
                ) {
                    Text(
                        text = stringResource(R.string.verify),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            is UserInputUiState.Loading -> {
                Button(
                    onClick = { /* Disabled during loading */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    enabled = false
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            is UserInputUiState.Success -> {
                Button(
                    onClick = { /* Success state */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    enabled = false
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Success",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            is UserInputUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = currentState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            uiState = UserInputUiState.Idle
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "Try Again",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// Preview for UserInputScreen - Idle state
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun UserInputScreenIdlePreview() {
    MaterialTheme {
        UserInputScreen(
            onVerify = { _, _ -> /* Preview only */ },
            initialPhoneNumber = "9876543210",
            initialEmail = "user@example.com",
            context = LocalContext.current as MainActivity,
            onLoadingStateChange = { /* Preview only */ },
            onResetLoading = { /* Preview only */ }
        )
    }
}

// Preview for UserInputScreen - Loading state
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "UserInputScreen - Loading")
@Composable
fun UserInputScreenLoadingPreview() {
    MaterialTheme {
        UserInputScreen(
            onVerify = { _, _ -> /* Preview only */ },
            initialPhoneNumber = "9876543210",
            initialEmail = "user@example.com",
            context = LocalContext.current as MainActivity,
            onLoadingStateChange = { /* Preview only */ },
            onResetLoading = { /* Preview only */ }
        )
    }
}

// Preview for UserInputScreen - Error state
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "UserInputScreen - Error")
@Composable
fun UserInputScreenErrorPreview() {
    MaterialTheme {
        UserInputScreen(
            onVerify = { _, _ -> /* Preview only */ },
            initialPhoneNumber = "9876543210",
            initialEmail = "user@example.com",
            context = LocalContext.current as MainActivity,
            onLoadingStateChange = { /* Preview only */ },
            onResetLoading = { /* Preview only */ }
        )
    }
} 