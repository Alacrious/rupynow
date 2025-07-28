package com.rupynow.application

import android.Manifest
import android.accounts.AccountManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.rupynow.application.data.OtpGenerateRequest
import com.rupynow.application.data.OtpVerifyRequest
import com.rupynow.application.data.UserPreferences
import com.rupynow.application.network.RetrofitProvider
import com.rupynow.application.services.AnalyticsService
import com.rupynow.application.services.NotificationService
import com.rupynow.application.ui.BasicDetailsScreen
import com.rupynow.application.ui.KycScreen
import com.rupynow.application.ui.LoanOfferScreen
import com.rupynow.application.ui.OtpInputScreen
import com.rupynow.application.workers.SmsSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit


class MainActivity : ComponentActivity() {

    private val permissions = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_PHONE_NUMBERS,
        Manifest.permission.POST_NOTIFICATIONS
    )


    private var permissionCallback: ((Boolean) -> Unit)? = null


    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        val analyticsService = AnalyticsService.getInstance(this)

        if (allGranted) {
            Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
            analyticsService.logPermissionGranted("all_permissions")

            // Start SMS sync when all permissions are granted
            startSmsSync()

            permissionCallback?.invoke(true)
        } else {
            Toast.makeText(this, "Some permissions were denied", Toast.LENGTH_SHORT).show()
            analyticsService.logPermissionDenied("some_permissions")
            permissionCallback?.invoke(false)
        }
        permissionCallback = null // Clear the callback after use
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize device_id if not available
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userPreferences = UserPreferences(this@MainActivity)
                val deviceId = userPreferences.getOrCreateDeviceId()
                Log.d("MainActivity", "Device ID: $deviceId")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error initializing device_id: ${e.message}")
            }
        }

        // Initialize services
        val analyticsService = AnalyticsService.getInstance(this)
        NotificationService.createNotificationChannel(this)

        // Log app open
        analyticsService.logAppOpen()
        analyticsService.logSessionStart()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val allPermissionsGranted = remember {
                        mutableStateOf(checkAllPermissionsGranted())
                    }

                    // Initialize screen state
                    val currentScreen = remember { mutableStateOf("checking_user") }
                    val userEmail = remember { mutableStateOf("") }
                    val userPhone = remember { mutableStateOf("") }
                    val resetLoadingCallback = remember { mutableStateOf<(() -> Unit)?>(null) }

                    // Check permissions and user status on app launch
                    LaunchedEffect(Unit) {
                        if (allPermissionsGranted.value) {
                            // All permissions granted, check for existing user
                            try {
                                val userPreferences = UserPreferences(this@MainActivity)
                                val existingUserId = userPreferences.userId.first()

                                if (existingUserId != null && existingUserId.isNotBlank()) {
                                    Log.d(
                                        "MainActivity",
                                        "Found existing userId: $existingUserId, navigating to basic details"
                                    )
                                    val analyticsService =
                                        AnalyticsService.getInstance(this@MainActivity)
                                    analyticsService.logFeatureUsage("app_launch", "existing_user")
                                    currentScreen.value = "basic_details"
                                } else {
                                    Log.d(
                                        "MainActivity",
                                        "No existing userId found, starting from user input"
                                    )
                                    val analyticsService =
                                        AnalyticsService.getInstance(this@MainActivity)
                                    analyticsService.logFeatureUsage("app_launch", "new_user")
                                    currentScreen.value = "user_input"
                                }
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Error checking existing user: ${e.message}")
                                currentScreen.value = "user_input"
                            }
                        } else {
                            // Permissions missing, stay on permission screen
                            Log.d("MainActivity", "Permissions missing, showing permission screen")
                            currentScreen.value = "permissions"
                        }
                    }

                    // Handle screen navigation based on permissions and user status
                    when {
                        !allPermissionsGranted.value -> {
                            // Show permission screen
                            LandingPage(
                                onAcceptAll = {
                                    val analyticsService = AnalyticsService.getInstance(this)
                                    analyticsService.logButtonClick(
                                        "accept_all_permissions",
                                        "landing_page"
                                    )

                                    this@MainActivity.requestPermissions { granted ->
                                        allPermissionsGranted.value = granted
                                    }
                                }
                            )
                        }

                        allPermissionsGranted.value -> {
                            when (currentScreen.value) {
                                "checking_user" -> {
                                    // Show loading screen while checking for existing user
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

                                "permissions" -> {
                                    // Show permission screen
                                    LandingPage(
                                        onAcceptAll = {
                                            val analyticsService =
                                                AnalyticsService.getInstance(this)
                                            analyticsService.logButtonClick(
                                                "accept_all_permissions",
                                                "landing_page"
                                            )

                                            this@MainActivity.requestPermissions { granted ->
                                                allPermissionsGranted.value = granted
                                            }
                                        }
                                    )
                                }

                                "user_input" -> {
                                    // Reset any previous error states when returning to user input
                                    UserInputScreen(
                                        onVerify = { email, phone ->
                                            // Handle verification logic here
                                            val analyticsService =
                                                AnalyticsService.getInstance(this)
                                            analyticsService.logUserRegistration(email, phone)
                                            analyticsService.logConversion("user_registration")

                                            // Save to DataStore
                                            CoroutineScope(Dispatchers.IO).launch {
                                                try {
                                                    val userPreferences =
                                                        UserPreferences(this@MainActivity)
                                                    userPreferences.saveUserInfo(phone, email)
                                                } catch (e: Exception) {
                                                    Log.e(
                                                        "MainActivity",
                                                        "Error saving user data: ${e.message}"
                                                    )
                                                }
                                            }

                                            // Store user data
                                            userEmail.value = email
                                            userPhone.value = phone

                                            // Generate OTP and only navigate if successful
                                            this@MainActivity.generateOtp(
                                                email = email,
                                                phone = phone,
                                                onComplete = { success ->
                                                    if (success) {
                                                        currentScreen.value = "otp_input"
                                                    }
                                                },
                                                onError = {
                                                    // Reset loading state on any error
                                                    resetLoadingCallback.value?.invoke()
                                                }
                                            )
                                        },
                                        initialPhoneNumber = this@MainActivity.getPhoneNumber(),
                                        initialEmail = this@MainActivity.getGoogleAccountEmail(),
                                        context = this,
                                        onLoadingStateChange = { isLoading ->
                                            // This callback will be used to reset loading state on errors
                                        },
                                        onResetLoading = { resetCallback ->
                                            resetLoadingCallback.value = resetCallback
                                        }
                                    )
                                }

                                "otp_input" -> {
                                    OtpInputScreen(
                                        onOtpVerified = { otp, onResult ->
                                            // Handle OTP verification with the new API
                                            this@MainActivity.verifyOtpWithApi(otp) { success ->
                                                if (success) {
                                                    val analyticsService =
                                                        AnalyticsService.getInstance(this)
                                                    analyticsService.logConversion("otp_verification_success")
                                                    currentScreen.value = "basic_details"
                                                }
                                                // Pass result back to the screen
                                                onResult(success)
                                            }
                                        },
                                        onBackPressed = {
                                            currentScreen.value = "user_input"
                                        },
                                        context = this,
                                        phoneNumber = userPhone.value
                                    )
                                }

                                                            "loan_offer" -> {
                                LoanOfferScreen(
                                    onContinueToApply = {
                                        // Navigate to KYC screen
                                        val analyticsService = AnalyticsService.getInstance(this)
                                        analyticsService.logFeatureUsage("loan_application", "continued")
                                        currentScreen.value = "kyc"
                                    },
                                    context = this
                                )
                            }
                                                        "basic_details" -> {
                                BasicDetailsScreen(
                                    onSubmitDetails = { fullName, panNumber, onResult ->
                                        // Handle basic details submission
                                        val analyticsService =
                                            AnalyticsService.getInstance(this)
                                        analyticsService.logFeatureUsage(
                                            "basic_details",
                                            "submitted"
                                        )

                                        // Save user details
                                        CoroutineScope(Dispatchers.IO).launch {
                                            try {
                                                val userPreferences =
                                                    UserPreferences(this@MainActivity)
                                                // You can add additional fields to UserPreferences if needed
                                            } catch (e: Exception) {
                                                Log.e(
                                                    "MainActivity",
                                                    "Error saving basic details: ${e.message}"
                                                )
                                            }
                                        }

                                        // Simulate API call with success/failure handling
                                        CoroutineScope(Dispatchers.IO).launch {
                                            try {
                                                // Here you would make the actual API call
                                                // For now, we'll simulate a successful response
                                                delay(1000) // Simulate network delay

                                                withContext(Dispatchers.Main) {
                                                    // Log successful API call
                                                    analyticsService.logApiCall(
                                                        "basic_details",
                                                        "success"
                                                    )
                                                    onResult(true)

                                                    // Navigate to loan offer screen
                                                    currentScreen.value = "loan_offer"
                                                }
                                            } catch (e: Exception) {
                                                Log.e(
                                                    "MainActivity",
                                                    "Error submitting basic details: ${e.message}"
                                                )
                                                withContext(Dispatchers.Main) {
                                                    analyticsService.logApiCall(
                                                        "basic_details",
                                                        "error"
                                                    )
                                                    onResult(false)
                                                }
                                            }
                                        }
                                    },
                                    context = this
                                )
                            }

                            "kyc" -> {
                                KycScreen(
                                    onVerifyViaAadhaar = {
                                        // Handle Aadhaar verification
                                        val analyticsService = AnalyticsService.getInstance(this)
                                        analyticsService.logFeatureUsage("kyc_verification", "aadhaar_started")
                                        // For now, navigate to success screen
                                        currentScreen.value = "success"
                                    },
                                    onVerifyViaDigiLocker = {
                                        // Handle DigiLocker verification
                                        val analyticsService = AnalyticsService.getInstance(this)
                                        analyticsService.logFeatureUsage("kyc_verification", "digilocker_started")
                                        // For now, navigate to success screen
                                        currentScreen.value = "success"
                                    },
                                    context = this
                                )
                            }

                            "loan_processing" -> {
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

                            "success" -> {
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
            }
        }
    }

    private fun checkAllPermissionsGranted(): Boolean {
            return permissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
        }

    private fun getPhoneNumber(): String {
            return try {
                val telephonyManager =
                    getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_PHONE_NUMBERS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    telephonyManager.line1Number ?: ""
                } else {
                    ""
                }
            } catch (e: Exception) {
                ""
            }
        }

        private fun getGoogleAccountEmail(): String {
            return try {
                val accountManager = getSystemService(Context.ACCOUNT_SERVICE) as AccountManager
                val accounts = accountManager.getAccountsByType("com.google")

                // Get the first Google account (usually the primary one)
                if (accounts.isNotEmpty()) {
                    val primaryAccount = accounts.first()
                    return primaryAccount.name
                }

                // If no Google accounts, try to get any account
                val allAccounts = accountManager.accounts
                if (allAccounts.isNotEmpty()) {
                    return allAccounts.first().name
                }

                ""
            } catch (e: Exception) {
                ""
            }
        }

        private fun requestPermissions(onPermissionsResult: (Boolean) -> Unit) {
            val permissionsToRequest = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()

            if (permissionsToRequest.isEmpty()) {
                Toast.makeText(this, "All permissions already granted!", Toast.LENGTH_SHORT).show()
                onPermissionsResult(true)
                return
            }

            // Store the callback to be called when permissions are granted
            permissionCallback = onPermissionsResult

            permissionLauncher.launch(permissionsToRequest)
        }

        fun startSmsSync() {
            // Schedule SMS sync work when permissions are granted
            val work = PeriodicWorkRequestBuilder<SmsSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                    "SmsSync",
                    ExistingPeriodicWorkPolicy.KEEP,
                    work
                )
        }

        fun verifyOtpWithApi(otpCode: String, onComplete: (Boolean) -> Unit = {}) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d("MainActivity", "Starting OTP verification for code: $otpCode")

                    // Get stored user data
                    val userPreferences = UserPreferences(this@MainActivity)
                    val mobileNumber = userPreferences.mobileNumber.first()
                    val userId = userPreferences.userId.first()
                    val deviceId = userPreferences.getOrCreateDeviceId()

                    Log.d(
                        "MainActivity",
                        "User data - Mobile: $mobileNumber, UserId: ${userId ?: "null (first install)"}, DeviceId: $deviceId"
                    )

                    if (mobileNumber.isNullOrBlank()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@MainActivity,
                                "User data not found. Please restart the app.",
                                Toast.LENGTH_LONG
                            ).show()
                            onComplete(false)
                        }
                        return@launch
                    }

                    // userId can be null on first install, that's okay
                    val finalUserId = userId ?: ""

                    val request = OtpVerifyRequest(
                        mobileNumber = mobileNumber,
                        userId = finalUserId,
                        otpCode = otpCode,
                        deviceId = deviceId
                    )

                    Log.d("MainActivity", "🔥 FIRING OTP VERIFY API - Code: $otpCode")
                    Log.d(
                        "MainActivity",
                        "🌐 API URL: https://daf01b84975c.ngrok-free.app/api/user/auth/otp/verify"
                    )
                    val response = RetrofitProvider.authApi.verifyOtp(request = request)

                    Log.d(
                        "MainActivity",
                        "API Response - Code: ${response.code()}, Success: ${response.isSuccessful}"
                    )

                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            val otpResponse = response.body()
                            if (otpResponse?.status == "OK") {
                                // Save userId from OTP verification response if not already saved
                                val verificationUserId = otpResponse.data?.userId
                                if (verificationUserId != null) {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            val userPreferences = UserPreferences(this@MainActivity)
                                            userPreferences.saveUserId(verificationUserId)
                                            Log.d(
                                                "MainActivity",
                                                "Saved userId from OTP verification: $verificationUserId"
                                            )
                                        } catch (e: Exception) {
                                            Log.e(
                                                "MainActivity",
                                                "Error saving userId from OTP verification: ${e.message}"
                                            )
                                        }
                                    }
                                }

                                // Log successful API call
                                val analyticsService =
                                    AnalyticsService.getInstance(this@MainActivity)
                                analyticsService.logApiCall("otp_verify", "success")
                                onComplete(true)
                            } else {
                                val errorMessage =
                                    "Error: ${otpResponse?.message ?: "Verification failed"}"
                                Toast.makeText(
                                    this@MainActivity,
                                    errorMessage,
                                    Toast.LENGTH_LONG
                                ).show()

                                Log.e("MainActivity", errorMessage)

                                // Log failed API call
                                val analyticsService =
                                    AnalyticsService.getInstance(this@MainActivity)
                                analyticsService.logApiCall("otp_verify", "failed")
                                onComplete(false)
                            }
                        } else {
                            val networkErrorMessage =
                                "Network error: ${response.code()} message: ${response.message()}"
                            Toast.makeText(
                                this@MainActivity,
                                networkErrorMessage,
                                Toast.LENGTH_LONG
                            ).show()

                            Log.e("MainActivity", networkErrorMessage)

                            // Log network error
                            val analyticsService = AnalyticsService.getInstance(this@MainActivity)
                            analyticsService.logApiCall("otp_verify", "network_error")
                            onComplete(false)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val exceptionMessage = "Error: ${e.message}"
                        Toast.makeText(
                            this@MainActivity,
                            exceptionMessage,
                            Toast.LENGTH_LONG
                        ).show()

                        Log.e("MainActivity", exceptionMessage, e)

                        // Log exception
                        val analyticsService = AnalyticsService.getInstance(this@MainActivity)
                        analyticsService.logApiCall("otp_verify", "exception")
                        onComplete(false)
                    }
                }
            }
        }

        fun generateOtp(
            email: String,
            phone: String,
            onComplete: (Boolean) -> Unit = {},
            onError: () -> Unit = {}
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Add timeout for API call (30 seconds)
                    withTimeout(30000L) {
                        Log.d(
                            "MainActivity",
                            "Starting OTP generation for email: $email, phone: $phone"
                        )

                        // Get or create device_id
                        val userPreferences = UserPreferences(this@MainActivity)
                        val deviceId = userPreferences.getOrCreateDeviceId()

                        val request = OtpGenerateRequest(
                            mobileNumber = phone,
                            email = email,
                            deviceId = deviceId
                        )

                        Log.d(
                            "MainActivity",
                            "🔥 FIRING OTP GENERATE API - Email: $email, Phone: $phone"
                        )
                        Log.d(
                            "MainActivity",
                            "🌐 API URL: https://daf01b84975c.ngrok-free.app/api/user/auth/otp/generate"
                        )
                        val response = RetrofitProvider.authApi.generateOtp(
                            authorization = "Basic YWRtaW46YWRtaW4=",
                            request = request
                        )

                        withContext(Dispatchers.Main) {
                            if (response.isSuccessful) {
                                val otpResponse = response.body()
                                if (otpResponse?.status == "OK") {

                                    // Save userId for OTP verification
                                    val userId = otpResponse.data?.userId
                                    if (userId != null) {
                                        CoroutineScope(Dispatchers.IO).launch {
                                            try {
                                                val userPreferences =
                                                    UserPreferences(this@MainActivity)
                                                userPreferences.saveUserId(userId)
                                            } catch (e: Exception) {
                                                Log.e(
                                                    "MainActivity",
                                                    "Error saving userId: ${e.message}"
                                                )
                                            }
                                        }
                                    }

                                    // Log successful API call
                                    val analyticsService =
                                        AnalyticsService.getInstance(this@MainActivity)
                                    analyticsService.logApiCall("otp_generate", "success")
                                    onComplete(true)
                                } else {
                                    val errorMessage =
                                        "Error: ${otpResponse?.message ?: "Unknown error"}"
                                    Toast.makeText(
                                        this@MainActivity,
                                        errorMessage,
                                        Toast.LENGTH_LONG
                                    ).show()

                                    Log.e("MainActivity", errorMessage)

                                    // Log failed API call
                                    val analyticsService =
                                        AnalyticsService.getInstance(this@MainActivity)
                                    analyticsService.logApiCall("otp_generate", "failed")
                                    onComplete(false)
                                    onError()
                                }
                            } else {
                                val networkErrorMessage =
                                    "Network error: ${response.code()}, message: ${response.message()}"
                                Toast.makeText(
                                    this@MainActivity,
                                    networkErrorMessage,
                                    Toast.LENGTH_LONG
                                ).show()

                                Log.e("MainActivity", networkErrorMessage)

                                // Log network error
                                val analyticsService =
                                    AnalyticsService.getInstance(this@MainActivity)
                                analyticsService.logApiCall("otp_generate", "network_error")
                                onComplete(false)
                                onError()
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val exceptionMessage = when (e) {
                            is kotlinx.coroutines.TimeoutCancellationException -> "Request timed out. Please try again."
                            else -> "Error: ${e.message}"
                        }
                        Toast.makeText(
                            this@MainActivity,
                            exceptionMessage,
                            Toast.LENGTH_LONG
                        ).show()

                        Log.e("MainActivity", exceptionMessage, e)

                        // Log exception
                        val analyticsService = AnalyticsService.getInstance(this@MainActivity)
                        analyticsService.logApiCall("otp_generate", "exception")
                        onComplete(false)
                        onError()
                    }
                }
            }
        }


    }

    @Composable
    fun LandingPage(onAcceptAll: () -> Unit) {
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
                text = "Welcome to RupyNow",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "We need a few permissions to provide you with the best experience",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Permission Sections
            PermissionSection(
                title = "SMS Permissions",
                description = "We need SMS access to send and receive messages for payment confirmations and transaction notifications.",
                icon = Icons.Filled.Info
            )

            Spacer(modifier = Modifier.height(24.dp))

            PermissionSection(
                title = "Device Information",
                description = "We need device info to ensure secure transactions and provide personalized services based on your device.",
                icon = Icons.Filled.Phone
            )

            Spacer(modifier = Modifier.height(24.dp))

            PermissionSection(
                title = "Location Access",
                description = "We need location access to provide location-based services and ensure transaction security.",
                icon = Icons.Filled.LocationOn
            )

            Spacer(modifier = Modifier.weight(1f))

            // Accept All Button
            Button(
                onClick = onAcceptAll,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Accept All Permissions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    @Composable
    fun PermissionSection(
        title: String,
        description: String,
        icon: ImageVector
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .padding(top = 4.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }

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
        var isLoading by remember { mutableStateOf(false) }

        // Add loading state reset callback
        val resetLoading = {
            isLoading = false
            onLoadingStateChange(false)
        }

        // Pass the reset callback to parent
        LaunchedEffect(Unit) {
            onResetLoading(resetLoading)
        }

        // Handle loading state from OTP generation
        LaunchedEffect(Unit) {
            // Reset loading state when screen is shown
            isLoading = false
        }

        // Reset loading state when user interacts with inputs (indicating they want to retry)
        LaunchedEffect(email, phone) {
            if (isLoading) {
                // If user is typing while loading, they might want to retry
                // This will be handled by the button click logic
            }
        }

        // Reset loading state when screen is recomposed (after error)
        LaunchedEffect(Unit) {
            // This will reset loading state when screen is recomposed
            // which happens after an error in OTP generation
            isLoading = false
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
                text = "Welcome to RupyNow",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Please enter your details to continue",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Email Input
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
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
                label = { Text("Phone Number") },
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

            // Verify Button
            Button(
                onClick = {
                    if (email.isNotBlank() && phone.isNotBlank()) {
                        isLoading = true
                        onVerify(email, phone)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                enabled = email.isNotBlank() && phone.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = "Verify",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }