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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                        
                        when (currentScreen.value) {
                            "user_input" -> {
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
                                        
                                        // Store user data and navigate to OTP screen
                                        userEmail.value = email
                                        userPhone.value = phone
                                        currentScreen.value = "otp_input"
                                    },
                                    initialPhoneNumber = getPhoneNumber(),
                                    initialEmail = getGoogleAccountEmail(),
                                    context = this
                                )
                            }
                            "otp_input" -> {
                                OtpInputScreen(
                                    onOtpVerified = { otp ->
                                        // Handle OTP verification
                                        val analyticsService = AnalyticsService.getInstance(this)
                                        analyticsService.logConversion("otp_verification_success")
                                        
                                        // Navigate to success screen or main app
                                        currentScreen.value = "success"
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
    
    fun generateOtp(email: String, phone: String, onComplete: (Boolean) -> Unit = {}) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = OtpGenerateRequest(
                    mobileNumber = phone,
                    email = email
                )
                
                val response = RetrofitProvider.authApi.generateOtp(
                    authorization = "Basic YWRtaW46YWRtaW4=",
                    request = request
                )
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val otpResponse = response.body()
                        if (otpResponse?.status == "OK") {
                            
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
                        }
                    } else {
                        val networkErrorMessage = "Network error: ${response.code()}"
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
                    analyticsService.logApiCall("otp_generate", "exception")
                    onComplete(false)
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
    context: MainActivity
) {
    var email by remember { mutableStateOf(initialEmail) }
    var phone by remember { mutableStateOf(initialPhoneNumber) }
    var isLoading by remember { mutableStateOf(false) }
    
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
                isLoading = true
                onVerify(email, phone)
                
                // Make API call to generate OTP
                context.generateOtp(email, phone) { success ->
                    isLoading = false
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