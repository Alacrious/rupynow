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
import com.rupynow.application.services.AnalyticsService
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import com.rupynow.application.R

@Composable
fun AadhaarOtpScreen(
    onOtpVerified: (String, (Boolean) -> Unit) -> Unit,
    onBackPressed: () -> Unit,
    context: android.content.Context,
    aadhaarNumber: String = ""
) {
    var otp by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var verificationResult by remember { mutableStateOf<Boolean?>(null) }
    
    // Handle verification when OTP is complete
    LaunchedEffect(otp) {
        if (otp.length == 6 && !isLoading) {
            isLoading = true
            errorMessage = ""
            verificationResult = null
            onOtpVerified(otp) { success ->
                verificationResult = success
            }
        }
    }
    
    // Handle verification result
    LaunchedEffect(verificationResult) {
        verificationResult?.let { success ->
            isLoading = false
            if (!success) {
                errorMessage = "Invalid verification code. Please try again."
            }
        }
    }
    

    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        
        // Aadhaar icon
        Card(
            modifier = Modifier.size(80.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.VerifiedUser,
                    contentDescription = "Aadhaar Verification",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Title
        Text(
            text = stringResource(R.string.verify_your_aadhaar),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Subtitle
        Text(
            text = stringResource(R.string.aadhaar_otp_sent_message),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Aadhaar number display
        if (aadhaarNumber.isNotEmpty()) {
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.CreditCard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.aadhaar_number),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Text(
                            text = stringResource(R.string.aadhaar_number_display, aadhaarNumber.takeLast(4)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
        

        
        // OTP Input Field - Simple text field for continuous typing
        OutlinedTextField(
            value = otp,
            onValueChange = { newValue ->
                // Only allow digits and limit to 6 characters
                val filtered = newValue.filter { char -> char.isDigit() }
                if (filtered.length <= 6) {
                    otp = filtered
                    errorMessage = "" // Clear error when user types
                }
            },
            label = { Text(stringResource(R.string.enter_6_digit_otp)) },
            placeholder = { Text(stringResource(R.string.otp_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            singleLine = true,
            isError = errorMessage.isNotEmpty(),
            enabled = !isLoading,
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                letterSpacing = 8.sp,
                textAlign = TextAlign.Center
            )
        )
        
        // Loading indicator and message
        if (isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = stringResource(R.string.verifying_aadhaar_otp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Error message display
        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Info card about Aadhaar verification
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
                    text = stringResource(R.string.aadhaar_verification_info),
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
                    text = stringResource(R.string.verify_aadhaar),
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
                analyticsService.logButtonClick("resend_aadhaar_otp", "aadhaar_otp_screen")
            }
        ) {
            Text(
                text = stringResource(R.string.didnt_receive_code),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// Preview for AadhaarOtpScreen
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun AadhaarOtpScreenPreview() {
    MaterialTheme {
        AadhaarOtpScreen(
            onOtpVerified = { otp, onResult ->
                // Simulate OTP verification
                onResult(true)
            },
            onBackPressed = { /* Preview only */ },
            context = LocalContext.current,
            aadhaarNumber = "123456789012"
        )
    }
}

// Preview for AadhaarOtpScreen with error state
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "AadhaarOtpScreen - Error State")
@Composable
fun AadhaarOtpScreenErrorPreview() {
    MaterialTheme {
        AadhaarOtpScreen(
            onOtpVerified = { otp, onResult ->
                // Simulate failed OTP verification
                onResult(false)
            },
            onBackPressed = { /* Preview only */ },
            context = LocalContext.current,
            aadhaarNumber = "123456789012"
        )
    }
} 