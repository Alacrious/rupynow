package com.rupynow.application.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.rupynow.application.data.UserPreferences
import com.rupynow.application.services.AnalyticsService
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import android.util.Log

@Composable
fun BasicDetailsScreen(
    onSubmitDetails: (String, String, (Boolean) -> Unit) -> Unit,
    context: android.content.Context
) {
    var fullName by remember { mutableStateOf("") }
    var panNumber by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf("User") }
    
    // For now, use a simple approach without complex Flow handling
    // TODO: Implement proper user data loading when needed
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        

        
        // Greeting
        Text(
            text = "Hi $userName!",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Updated message for basic details
        Text(
            text = "Just a few details to apply for loan. It takes less than 2 minutes.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Security assurance card
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
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = androidx.compose.ui.graphics.Color(0xFF81C784)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = "Your data is encrypted & RBI-compliant",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Full Name Input
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Full Name",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("Enter your full name")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // PAN Number Input
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "PAN Number",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = panNumber,
                onValueChange = { 
                    // Convert to uppercase and allow only alphanumeric characters
                    val filtered = it.uppercase().filter { char -> 
                        char.isLetterOrDigit() 
                    }
                    if (filtered.length <= 10) {
                        panNumber = filtered
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("Enter your PAN number")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Submit Button (updated CTA)
        Button(
            onClick = {
                if (fullName.isBlank()) {
                    return@Button
                }
                
                isLoading = true
                
                // Log analytics
                val analyticsService = AnalyticsService.getInstance(context)
                analyticsService.logButtonClick("submit_details", "basic_details_screen")
                analyticsService.logFeatureUsage("loan_application", "details_submitted")
                
                onSubmitDetails(fullName, panNumber) { success ->
                    isLoading = false
                    if (!success) {
                        // Handle error - could show a toast or error message
                        // For now, we'll just log the failure
                        analyticsService.logError("details_submission_failed", "Details submission failed")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            enabled = fullName.isNotBlank() && !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = "Submit",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// Extension function to capitalize first letter of each word
private fun String.capitalize(): String {
    return this.split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}