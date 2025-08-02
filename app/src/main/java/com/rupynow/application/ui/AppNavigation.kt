package com.rupynow.application.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rupynow.application.MainActivity
import com.rupynow.application.Screen
import com.rupynow.application.data.UserPreferences
import com.rupynow.application.services.AnalyticsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppNavigation(
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
    context: Context
) {
    // State to store Aadhaar number for OTP verification
    var aadhaarNumber by remember { mutableStateOf("") }
    when {
        !allPermissionsGranted -> {
            LandingPage(
                onAcceptAll = {
                    val analyticsService = AnalyticsService.getInstance(context)
                    analyticsService.logButtonClick("accept_all_permissions", "landing_page")
                    requestPermissions { granted ->
                        // No need to navigate here, LaunchedEffect will handle
                    }
                }
            )
        }
        allPermissionsGranted -> {
            when (currentScreen) {
                Screen.CheckingUser -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Checking your account...",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Screen.Permissions -> {
                    LandingPage(
                        onAcceptAll = {
                            val analyticsService = AnalyticsService.getInstance(context)
                            analyticsService.logButtonClick("accept_all_permissions", "landing_page")
                            requestPermissions { granted ->
                                // No need to navigate here, LaunchedEffect will handle
                            }
                        }
                    )
                }
                Screen.UserInput -> {
                    UserInputScreen(
                        onVerify = { email, phone ->
                            val analyticsService = AnalyticsService.getInstance(context)
                            analyticsService.logUserRegistration(email, phone)
                            analyticsService.logConversion("user_registration")
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val userPreferences = UserPreferences(context)
                                    userPreferences.saveUserInfo(phone, email)
                                } catch (e: Exception) {
                                    Log.e("MainActivity", "Error saving user data: ${e.message}")
                                }
                            }
                            userEmail.value = email
                            userPhone.value = phone
                            generateOtp(email, phone, { success ->
                                if (success) onNavigate(Screen.OtpInput)
                            }, { resetLoadingCallback.value?.invoke() })
                        },
                        initialPhoneNumber = getPhoneNumber(),
                        initialEmail = getGoogleAccountEmail(),
                        context = context as MainActivity,
                        onLoadingStateChange = {},
                        onResetLoading = { resetCallback -> resetLoadingCallback.value = resetCallback }
                    )
                }
                Screen.OtpInput -> {
                    OtpInputScreen(
                        onOtpVerified = { otp, onResult ->
                            verifyOtpWithApi(otp) { success ->
                                if (success) {
                                    val analyticsService = AnalyticsService.getInstance(context)
                                    analyticsService.logConversion("otp_verification_success")
                                    onNavigate(Screen.BasicDetails)
                                }
                                onResult(success)
                            }
                        },
                        onBackPressed = { onNavigate(Screen.UserInput) },
                        context = context,
                        phoneNumber = userPhone.value
                    )
                }
                Screen.BasicDetails -> {
                    BasicDetailsScreen(
                        onSubmitDetails = { fullName, panNumber, onResult ->
                            val analyticsService = AnalyticsService.getInstance(context)
                            analyticsService.logFeatureUsage("basic_details", "submitted")
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val userPreferences = UserPreferences(context)
                                    // Save details if needed
                                } catch (e: Exception) {
                                    Log.e("MainActivity", "Error saving basic details: ${e.message}")
                                }
                            }
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    delay(1000)
                                    withContext(Dispatchers.Main) {
                                        analyticsService.logApiCall("basic_details", "success")
                                        onResult(true)
                                        onNavigate(Screen.LoanOffer)
                                    }
                                } catch (e: Exception) {
                                    Log.e("MainActivity", "Error submitting basic details: ${e.message}")
                                    withContext(Dispatchers.Main) {
                                        analyticsService.logApiCall("basic_details", "error")
                                        onResult(false)
                                    }
                                }
                            }
                        },
                        context = context
                    )
                }
                Screen.LoanOffer -> {
                    LoanOfferScreen(
                        onContinueToApply = {
                            val analyticsService = AnalyticsService.getInstance(context)
                            analyticsService.logFeatureUsage("loan_application", "continued")
                            onNavigate(Screen.Kyc)
                        },
                        context = context
                    )
                }
                Screen.Kyc -> {
                    KycScreen(
                        onVerifyViaAadhaar = {
                            val analyticsService = AnalyticsService.getInstance(context)
                            analyticsService.logFeatureUsage("kyc_verification", "aadhaar_started")
                            onNavigate(Screen.AadhaarVerification)
                        },
                        onVerifyViaDigiLocker = {
                            val analyticsService = AnalyticsService.getInstance(context)
                            analyticsService.logFeatureUsage("kyc_verification", "digilocker_started")
                            onNavigate(Screen.Success)
                        },
                        onVerifyViaSelfie = {
                            val analyticsService = AnalyticsService.getInstance(context)
                            analyticsService.logFeatureUsage("kyc_verification", "selfie_started")
                            onNavigate(Screen.SelfieKyc)
                        },
                        context = context
                    )
                }
                Screen.SelfieKyc -> {
                    SelfieKycScreen(
                        onSuccess = {
                            val analyticsService = AnalyticsService.getInstance(context)
                            analyticsService.logFeatureUsage("selfie_kyc_completed", "success")
                            onNavigate(Screen.Success)
                        },
                        onManualReview = {
                            val analyticsService = AnalyticsService.getInstance(context)
                            analyticsService.logFeatureUsage("selfie_kyc_manual_review", "requested")
                            onNavigate(Screen.Success) // Navigate to support/manual review screen
                        },
                        onBackPressed = { onNavigate(Screen.Kyc) },
                        context = context
                    )
                }
                Screen.AadhaarVerification -> {
                    AadhaarVerificationScreen(
                        onContinue = { aadhaarNum ->
                            val analyticsService = AnalyticsService.getInstance(context)
                            analyticsService.logFeatureUsage("aadhaar_verification", "completed")
                            // Store the Aadhaar number and navigate to OTP screen
                            aadhaarNumber = aadhaarNum
                            onNavigate(Screen.AadhaarOtp)
                        },
                        context = context
                    )
                }
                Screen.AadhaarOtp -> {
                    AadhaarOtpScreen(
                        onOtpVerified = { otp, onResult ->
                            val analyticsService = AnalyticsService.getInstance(context)
                            analyticsService.logFeatureUsage("aadhaar_otp_verification", "completed")
                            // Here you would typically make an API call to verify the Aadhaar OTP
                            // For now, we'll simulate success and navigate to data confirmation
                            CoroutineScope(Dispatchers.IO).launch {
                                delay(1000) // Simulate API call
                                withContext(Dispatchers.Main) {
                                    analyticsService.logApiCall("aadhaar_otp_verification", "success")
                                    onResult(true)
                                    onNavigate(Screen.AadhaarDataConfirmation)
                                }
                            }
                        },
                        onBackPressed = { onNavigate(Screen.AadhaarVerification) },
                        context = context,
                        aadhaarNumber = aadhaarNumber
                    )
                }
                Screen.AadhaarDataConfirmation -> {
                    AadhaarDataConfirmationScreen(
                        onConfirm = { aadhaarData, isAddressEdited ->
                            val analyticsService = AnalyticsService.getInstance(context)
                            analyticsService.logFeatureUsage("aadhaar_data_confirmation", "completed")
                            
                            if (isAddressEdited) {
                                analyticsService.logFeatureUsage("address_edited", "true")
                                // Here you would trigger address proof upload
                                // For now, we'll navigate to success
                            }
                            
                            CoroutineScope(Dispatchers.IO).launch {
                                delay(1000) // Simulate API call
                                withContext(Dispatchers.Main) {
                                    analyticsService.logApiCall("aadhaar_data_confirmation", "success")
                                    onNavigate(Screen.Success)
                                }
                            }
                        },
                        onBackPressed = { onNavigate(Screen.AadhaarOtp) },
                        context = context,
                        aadhaarData = AadhaarData(
                            name = "John Doe", // This would come from Aadhaar API
                            dateOfBirth = "15-03-1990",
                            gender = "Male",
                            address = "123 Main Street, Apartment 4B, New Delhi, Delhi 110001"
                        )
                    )
                }
                Screen.LoanProcessing -> {
                    // Loan application processing screen
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Processing Your Application",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "We're reviewing your loan application. This may take a few minutes.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Screen.Success -> {
                    // Success screen
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Application Submitted!",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Your loan application has been submitted successfully. We'll contact you soon.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// Preview for AppNavigation - Permissions screen
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun AppNavigationPermissionsPreview() {
    val userEmail = remember { mutableStateOf("") }
    val userPhone = remember { mutableStateOf("") }
    val resetLoadingCallback = remember { mutableStateOf<(() -> Unit)?>(null) }
    
    MaterialTheme {
        AppNavigation(
            allPermissionsGranted = false,
            currentScreen = Screen.Permissions,
            onNavigate = { /* Preview only */ },
            userEmail = userEmail,
            userPhone = userPhone,
            resetLoadingCallback = resetLoadingCallback,
            requestPermissions = { /* Preview only */ },
            getPhoneNumber = { "9876543210" },
            getGoogleAccountEmail = { "user@example.com" },
            verifyOtpWithApi = { _, _ -> /* Preview only */ },
            generateOtp = { _, _, _, _ -> /* Preview only */ },
            context = LocalContext.current
        )
    }
}

// Preview for AppNavigation - User Input screen
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "AppNavigation - User Input")
@Composable
fun AppNavigationUserInputPreview() {
    val userEmail = remember { mutableStateOf("user@example.com") }
    val userPhone = remember { mutableStateOf("9876543210") }
    val resetLoadingCallback = remember { mutableStateOf<(() -> Unit)?>(null) }
    
    MaterialTheme {
        AppNavigation(
            allPermissionsGranted = true,
            currentScreen = Screen.UserInput,
            onNavigate = { /* Preview only */ },
            userEmail = userEmail,
            userPhone = userPhone,
            resetLoadingCallback = resetLoadingCallback,
            requestPermissions = { /* Preview only */ },
            getPhoneNumber = { "9876543210" },
            getGoogleAccountEmail = { "user@example.com" },
            verifyOtpWithApi = { _, _ -> /* Preview only */ },
            generateOtp = { _, _, _, _ -> /* Preview only */ },
            context = LocalContext.current
        )
    }
}

// Preview for AppNavigation - Aadhaar OTP screen
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "AppNavigation - Aadhaar OTP")
@Composable
fun AppNavigationAadhaarOtpPreview() {
    val userEmail = remember { mutableStateOf("user@example.com") }
    val userPhone = remember { mutableStateOf("9876543210") }
    val resetLoadingCallback = remember { mutableStateOf<(() -> Unit)?>(null) }
    
    MaterialTheme {
        AppNavigation(
            allPermissionsGranted = true,
            currentScreen = Screen.AadhaarOtp,
            onNavigate = { /* Preview only */ },
            userEmail = userEmail,
            userPhone = userPhone,
            resetLoadingCallback = resetLoadingCallback,
            requestPermissions = { /* Preview only */ },
            getPhoneNumber = { "9876543210" },
            getGoogleAccountEmail = { "user@example.com" },
            verifyOtpWithApi = { _, _ -> /* Preview only */ },
            generateOtp = { _, _, _, _ -> /* Preview only */ },
            context = LocalContext.current
        )
    }
} 