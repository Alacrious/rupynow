package com.rupynow.application.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rupynow.application.Screen
import com.rupynow.application.UserInputUiState

@Composable
fun App(
    allPermissionsGranted: Boolean,
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    userEmail: androidx.compose.runtime.MutableState<String>,
    userPhone: androidx.compose.runtime.MutableState<String>,
    resetLoadingCallback: androidx.compose.runtime.MutableState<(() -> Unit)?>,
    requestPermissions: ((Boolean) -> Unit) -> Unit,
    getPhoneNumber: () -> String,
    getGoogleAccountEmail: () -> String,
    verifyOtpWithApi: (String, (Boolean) -> Unit) -> Unit,
    generateOtp: (String, String, (Boolean) -> Unit, () -> Unit) -> Unit,
    context: android.content.Context
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AppNavigation(
                allPermissionsGranted = allPermissionsGranted,
                currentScreen = currentScreen,
                onNavigate = onNavigate,
                userEmail = userEmail,
                userPhone = userPhone,
                resetLoadingCallback = resetLoadingCallback,
                requestPermissions = requestPermissions,
                getPhoneNumber = getPhoneNumber,
                getGoogleAccountEmail = getGoogleAccountEmail,
                verifyOtpWithApi = verifyOtpWithApi,
                generateOtp = generateOtp,
                context = context
            )
        }
    }
}

// Simple preview showing the app theme
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "App Theme Preview")
@Composable
fun AppThemePreview() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Text(
                text = "RupyNow App",
                style = MaterialTheme.typography.headlineLarge
            )
        }
    }
} 