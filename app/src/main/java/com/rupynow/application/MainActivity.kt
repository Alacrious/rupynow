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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import com.rupynow.application.services.AnalyticsService
import com.rupynow.application.services.NotificationService

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
                        UserInputScreen(
                            onVerify = { email, phone ->
                                // Handle verification logic here
                                val analyticsService = AnalyticsService.getInstance(this)
                                analyticsService.logUserRegistration(email, phone)
                                analyticsService.logConversion("user_registration")
                            },

                            initialPhoneNumber = getPhoneNumber(),
                            initialEmail = getGoogleAccountEmail()
                        )
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
    initialEmail: String = ""
) {
    var email by remember { mutableStateOf(initialEmail) }
    var phone by remember { mutableStateOf(initialPhoneNumber) }
    
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
                onVerify(email, phone)
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
                text = "Verify",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
} 