package com.rupynow.application

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import android.telephony.TelephonyManager
import android.content.Context
import androidx.activity.ComponentActivity
import android.accounts.AccountManager
import android.accounts.Account
import android.app.Activity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import com.rupynow.application.services.AnalyticsService
import com.rupynow.application.services.NotificationService
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.rupynow.application.workers.SmsSyncWorker
import java.util.concurrent.TimeUnit
import com.rupynow.application.network.RetrofitProvider
import com.rupynow.application.data.OtpGenerateRequest
import com.rupynow.application.data.OtpVerifyRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import android.util.Log
import com.rupynow.application.data.UserPreferences
import com.rupynow.application.ui.OtpInputScreen


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
                    
                                        if (allPermissionsGranted.value) {
                        val currentScreen = remember { mutableStateOf("user_input") }
                        val userEmail = remember { mutableStateOf("") }
                        val userPhone = remember { mutableStateOf("") }
                        val resetLoadingCallback = remember { mutableStateOf<(() -> Unit)?>(null) }
                        
                        when (currentScreen.value) {
                            "user_input" -> {
                                // Reset any previous error states when returning to user input
                                UserInputScreen(
                                    onVerify = { email, phone ->
                                        // Handle verification logic here
                                        val analyticsService = AnalyticsService.getInstance(this)
                                        analyticsService.logUserRegistration(email, phone)
                                        analyticsService.logConversion("user_registration")
                                        
                                        // Save to DataStore
                                        CoroutineScope(Dispatchers.IO).launch {
                                            try {
                                                val userPreferences = UserPreferences(this@MainActivity)
                                                userPreferences.saveUserInfo(phone, email)
                                            } catch (e: Exception) {
                                                Log.e("MainActivity", "Error saving user data: ${e.message}")
                                            }
                                        }
                                        
                                        // Store user data
                                        userEmail.value = email
                                        userPhone.value = phone
                                        
                                        // Generate OTP and only navigate if successful
                                        generateOtp(
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
                                    initialPhoneNumber = getPhoneNumber(),
                                    initialEmail = getGoogleAccountEmail(),
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
                                        verifyOtpWithApi(otp) { success ->
                                            if (success) {
                                                val analyticsService = AnalyticsService.getInstance(this)
                                                analyticsService.logConversion("otp_verification_success")
                                                currentScreen.value = "success"
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
                                        text = "Verification Successful!",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Text(
                                        text = "Welcome to RupyNow",
                                        style = MaterialTheme.typography.bodyLarge,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        LandingPage(
                            onAcceptAll = { 
                                val analyticsService = AnalyticsService.getInstance(this)
                                analyticsService.logButtonClick("accept_all_permissions", "landing_page")
                                
                                requestPermissions { granted ->
                                    allPermissionsGranted.value = granted
                                }
                            }
                        )
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
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED) {
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
    
    private fun startSmsSync() {
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
                
                Log.d("MainActivity", "User data - Mobile: $mobileNumber, UserId: ${userId ?: "null (first install)"}, DeviceId: $deviceId")
                
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
                Log.d("MainActivity", "🌐 API URL: https://daf01b84975c.ngrok-free.app/api/user/auth/otp/verify")
                val response = RetrofitProvider.authApi.verifyOtp(request = request)
                
                Log.d("MainActivity", "API Response - Code: ${response.code()}, Success: ${response.isSuccessful}")
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val otpResponse = response.body()
                        if (otpResponse?.status == "OK") {
                            // Log successful API call
                            val analyticsService = AnalyticsService.getInstance(this@MainActivity)
                            analyticsService.logApiCall("otp_verify", "success")
                            onComplete(true)
                        } else {
                            val errorMessage = "Error: ${otpResponse?.message ?: "Verification failed"}"
                            Toast.makeText(
                                this@MainActivity,
                                errorMessage,
                                Toast.LENGTH_LONG
                            ).show()
                            
                            Log.e("MainActivity", errorMessage)
                            
                            // Log failed API call
                            val analyticsService = AnalyticsService.getInstance(this@MainActivity)
                            analyticsService.logApiCall("otp_verify", "failed")
                            onComplete(false)
                        }
                    } else {
                        val networkErrorMessage = "Network error: ${response.code()} message: ${response.message()}"
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
    
    fun generateOtp(email: String, phone: String, onComplete: (Boolean) -> Unit = {}, onError: () -> Unit = {}) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Add timeout for API call (30 seconds)
                withTimeout(30000L) {
                    Log.d("MainActivity", "Starting OTP generation for email: $email, phone: $phone")
                    
                    // Get or create device_id
                    val userPreferences = UserPreferences(this@MainActivity)
                    val deviceId = userPreferences.getOrCreateDeviceId()
                    
                    val request = OtpGenerateRequest(
                        mobileNumber = phone,
                        email = email,
                        deviceId = deviceId
                    )
                    
                    Log.d("MainActivity", "🔥 FIRING OTP GENERATE API - Email: $email, Phone: $phone")
                    Log.d("MainActivity", "🌐 API URL: https://daf01b84975c.ngrok-free.app/api/user/auth/otp/generate")
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
                                            val userPreferences = UserPreferences(this@MainActivity)
                                            userPreferences.saveUserId(userId)
                                        } catch (e: Exception) {
                                            Log.e("MainActivity", "Error saving userId: ${e.message}")
                                        }
                                    }
                                }
                                
                                // Log successful API call
                                val analyticsService = AnalyticsService.getInstance(this@MainActivity)
                                analyticsService.logApiCall("otp_generate", "success")
                                onComplete(true)
                            } else {
                                val errorMessage = "Error: ${otpResponse?.message ?: "Unknown error"}"
                                Toast.makeText(
                                    this@MainActivity,
                                    errorMessage,
                                    Toast.LENGTH_LONG
                                ).show()
                                
                                Log.e("MainActivity", errorMessage)
                                
                                // Log failed API call
                                val analyticsService = AnalyticsService.getInstance(this@MainActivity)
                                analyticsService.logApiCall("otp_generate", "failed")
                                onComplete(false)
                                onError()
                            }
                        } else {
                            val networkErrorMessage = "Network error: ${response.code()}, message: ${response.message()}"
                            Toast.makeText(
                                this@MainActivity,
                                networkErrorMessage,
                                Toast.LENGTH_LONG
                            ).show()
                            
                            Log.e("MainActivity", networkErrorMessage)
                            
                            // Log network error
                            val analyticsService = AnalyticsService.getInstance(this@MainActivity)
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