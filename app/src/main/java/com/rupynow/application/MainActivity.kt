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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.rupynow.application.ui.App
import com.rupynow.application.workers.SmsSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

// Navigation destinations for the app
sealed class Screen {
    object CheckingUser : Screen()
    object Permissions : Screen()
    object UserInput : Screen()
    object OtpInput : Screen()
    object BasicDetails : Screen()
    object LoanOffer : Screen()
    object Kyc : Screen()
    object AadhaarVerification : Screen()
    object AadhaarOtp : Screen()
    object AadhaarDataConfirmation : Screen()
    object SelfieKyc : Screen()
    object LoanProcessing : Screen()
    object Success : Screen()
}

// UiState sealed class for UserInputScreen
sealed class UserInputUiState {
    object Idle : UserInputUiState()
    object Loading : UserInputUiState()
    data class Success(val message: String) : UserInputUiState()
    data class Error(val message: String) : UserInputUiState()
}

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
            val allPermissionsGranted = remember {
                mutableStateOf(checkAllPermissionsGranted())
            }

            // Use sealed class for navigation
            val currentScreen = remember { mutableStateOf<Screen>(Screen.CheckingUser) }
            val userEmail = remember { mutableStateOf("") }
            val userPhone = remember { mutableStateOf("") }
            val resetLoadingCallback = remember { mutableStateOf<(() -> Unit)?>(null) }

            // Check permissions and user status on app launch
            LaunchedEffect(Unit) {
                if (allPermissionsGranted.value) {
                    try {
                        val userPreferences = UserPreferences(this@MainActivity)
                        val existingUserId = userPreferences.userId.first()
                        if (existingUserId != null && existingUserId.isNotBlank()) {
                            Log.d("MainActivity", "Found existing userId: $existingUserId, navigating to basic details")
                            val analyticsService = AnalyticsService.getInstance(this@MainActivity)
                            analyticsService.logFeatureUsage("app_launch", "existing_user")
                            
                            // Start SMS sync immediately if userId exists
                            startSmsSync()
                            
                            currentScreen.value = Screen.BasicDetails
                        } else {
                            Log.d("MainActivity", "No existing userId found, starting from user input")
                            val analyticsService = AnalyticsService.getInstance(this@MainActivity)
                            analyticsService.logFeatureUsage("app_launch", "new_user")
                            currentScreen.value = Screen.UserInput
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error checking existing user: ${e.message}")
                        currentScreen.value = Screen.UserInput
                    }
                } else {
                    Log.d("MainActivity", "Permissions missing, showing permission screen")
                    currentScreen.value = Screen.Permissions
                }
            }

            App(
                allPermissionsGranted = allPermissionsGranted.value,
                currentScreen = currentScreen.value,
                onNavigate = { currentScreen.value = it },
                userEmail = userEmail,
                userPhone = userPhone,
                resetLoadingCallback = resetLoadingCallback,
                requestPermissions = this@MainActivity::requestPermissions,
                getPhoneNumber = this@MainActivity::getPhoneNumber,
                getGoogleAccountEmail = this@MainActivity::getGoogleAccountEmail,
                verifyOtpWithApi = this@MainActivity::verifyOtpWithApi,
                generateOtp = this@MainActivity::generateOtp,
                context = this
            )
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
                            "No mobile number. User data not found. Please restart the app.",
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
                                        
                                        // Start SMS sync after successful OTP verification and userId establishment
                                        startSmsSync()
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

