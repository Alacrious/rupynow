package com.rupynow.application.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rupynow.application.services.SmsRetrieverService
import com.rupynow.application.services.AnalyticsService
import kotlinx.coroutines.delay

@Composable
fun OtpInputScreen(
    onOtpVerified: (String) -> Unit,
    onBackPressed: () -> Unit,
    context: android.content.Context,
    phoneNumber: String = ""
) {
    var otp by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showAutoFillButton by remember { mutableStateOf(false) }
    
    // Get OTP detection service (SMS Retriever API only)
    val smsRetrieverService = remember { SmsRetrieverService.getInstance() }
    val detectedOtp by smsRetrieverService.detectedOtp.collectAsStateWithLifecycle()
    
    // Start listening for OTP SMS when screen is shown
    LaunchedEffect(Unit) {
        // Start SMS Retriever service
        smsRetrieverService.startListening(context)
        
        // Check for recent OTP in SMS
        delay(1000) // Give time for any pending OTP detection
        val lastOtp = smsRetrieverService.getLastDetectedOtp()
        if (lastOtp != null) {
            showAutoFillButton = true
        }
        
        // Check for any existing detected OTP
        val existingOtp = smsRetrieverService.getLastDetectedOtp()
        if (existingOtp != null) {
            showAutoFillButton = true
        }
    }
    
    // Auto-fill OTP when detected
    LaunchedEffect(detectedOtp) {
        detectedOtp?.let { value ->
            if (value.length in 4..6) {
                otp = value
                showAutoFillButton = false
                
                // Log auto-fill event
                val analyticsService = AnalyticsService.getInstance(context)
                analyticsService.logOtpAutoFill(value, "otp_input_screen")
                
                // Clear the detected OTP to avoid re-filling
                smsRetrieverService.clearDetectedOtp()
            }
        }
    }
    
    // Handle verification delay
    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(1500) // Simulate API call
            isLoading = false
            onOtpVerified(otp)
        }
    }
    
    // Cleanup when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            smsRetrieverService.stopListening(context)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        
        // Back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(onClick = onBackPressed) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Title
        Text(
            text = "Verify Your Number",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Subtitle
        Text(
            text = "We've sent a verification code to\n$phoneNumber",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Auto-fill button (shown when OTP is detected)
        if (showAutoFillButton) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "OTP Detected!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        
                        Text(
                            text = "Tap to auto-fill the verification code",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    TextButton(
                        onClick = {
                            detectedOtp?.let { value ->
                                otp = value
                                showAutoFillButton = false
                                
                                // Log auto-fill event
                                val analyticsService = AnalyticsService.getInstance(context)
                                analyticsService.logOtpAutoFill(value, "otp_input_screen")
                                
                                // Clear the detected OTP
                                smsRetrieverService.clearDetectedOtp()
                            }
                        }
                    ) {
                        Text(
                            text = "Auto-fill",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
        
        // OTP Input Field
        OutlinedTextField(
            value = otp,
            onValueChange = { 
                // Only allow digits and limit to 6 characters
                val filtered = it.filter { char -> char.isDigit() }
                if (filtered.length <= 6) {
                    otp = filtered
                    errorMessage = "" // Clear error when user types
                }
            },
            label = { Text("Enter 6-digit code") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "OTP"
                )
            },
            trailingIcon = {
                if (otp.isNotEmpty()) {
                    IconButton(
                        onClick = { otp = "" }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Clear"
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            singleLine = true,
            isError = errorMessage.isNotEmpty(),
            supportingText = {
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Info card about SMS detection
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(top = 2.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = "We'll automatically detect and fill the OTP from SMS messages sent to your number.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Verify Button
        Button(
            onClick = {
                if (otp.length < 4) {
                    errorMessage = "Please enter a valid verification code"
                    return@Button
                }
                
                isLoading = true
                errorMessage = ""
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            enabled = otp.length >= 4 && !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = "Verify Code",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Resend option
        TextButton(
            onClick = {
                // Handle resend OTP
                val analyticsService = AnalyticsService.getInstance(context)
                analyticsService.logButtonClick("resend_otp", "otp_input_screen")
            }
        ) {
            Text(
                text = "Didn't receive the code? Resend",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
} 