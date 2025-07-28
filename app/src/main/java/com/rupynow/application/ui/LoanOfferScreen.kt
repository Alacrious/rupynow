package com.rupynow.application.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rupynow.application.services.AnalyticsService
import kotlinx.coroutines.launch

@Composable
fun LoanOfferScreen(
    onContinueToApply: () -> Unit,
    context: android.content.Context
) {
    var userName by remember { mutableStateOf("User") }
    var loanAmount by remember { mutableStateOf(12000f) }
    val scope = rememberCoroutineScope()
    
    // Load user name from DataStore
    LaunchedEffect(Unit) {
        try {
            val userPreferences = com.rupynow.application.data.UserPreferences(context)
            // For now, use a default name since Flow handling is complex
            userName = "User"
        } catch (e: Exception) {
            userName = "User"
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(30.dp))
        // Celebration Icon
        Icon(
            imageVector = Icons.Filled.Celebration,
            contentDescription = "Celebration",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Congratulations Message
        Text(
            text = "Congratulations $userName!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "You're eligible for up to ₹50,000 instantly!",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Loan Amount Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Loan amount",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Slider
                Slider(
                    value = loanAmount,
                    onValueChange = { loanAmount = it },
                    valueRange = 12000f..50000f,
                    steps = 37, // (50000 - 12000) / 1000 - 1
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Min and Max values
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "₹12,000",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "₹50,000",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Selected amount
                Text(
                    text = "₹${String.format("%,.0f", loanAmount)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Loan Parameters
                LoanParameterRow("Number of months", "12")
                LoanParameterRow("Interest", "16%")
                LoanParameterRow("Processing fee", "2.5%")
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Estimated EMI
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Estimated EMI:",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "₹${calculateEMI(loanAmount)}/month",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Continue to Apply Button
        Button(
            onClick = {
                scope.launch {
                    val analyticsService = AnalyticsService.getInstance(context)
                    analyticsService.logButtonClick("continue_to_apply", "loan_offer_screen")
                    analyticsService.logFeatureUsage("loan_application", "started")
                }
                onContinueToApply()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF424242) // Dark grey color
            )
        ) {
            Text(
                text = "Continue to Apply",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Disclaimer
        Text(
            text = "Subject to final KYC verification",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun LoanParameterRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun calculateEMI(loanAmount: Float): String {
    // Simple EMI calculation: P * r * (1 + r)^n / ((1 + r)^n - 1)
    // Where P = principal, r = monthly interest rate, n = number of months
    val principal = loanAmount
    val annualRate = 0.16 // 16%
    val monthlyRate = annualRate / 12
    val months = 12
    
    val emi = principal * monthlyRate * Math.pow(1 + monthlyRate, months.toDouble()) / 
               (Math.pow(1 + monthlyRate, months.toDouble()) - 1)
    
    return String.format("%,.0f", emi)
} 