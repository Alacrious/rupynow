package com.rupynow.application.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.content.Context

@Composable
fun AadhaarVerificationScreen(
    onContinue: (String) -> Unit,
    context: Context
) {
    var aadhaarNumber by remember { mutableStateOf("") }
    var isTermsAccepted by remember { mutableStateOf(false) }
    var isAadhaarValid by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // Header with title only
        Text(
            text = "Verify via Aadhaar",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Instructional text
        Text(
            text = "Enter your Aadhaar number as per your Aadhaar card",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Aadhaar number input field
        OutlinedTextField(
            value = aadhaarNumber,
            onValueChange = {
                aadhaarNumber = it.filter { char -> char.isDigit() }
                isAadhaarValid = true
            },
            label = { Text("Aadhaar Number") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            singleLine = true,
            isError = !isAadhaarValid && aadhaarNumber.isNotEmpty()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Helper text
        Text(
            text = "Enter the Aadhaar number in this field",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Terms and conditions checkbox
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isTermsAccepted,
                onCheckedChange = { isTermsAccepted = it }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "I agree to the ",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Terms and Conditions",
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = TextDecoration.Underline,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Continue button
        Button(
            onClick = {
                if (aadhaarNumber.length == 12) {
                    onContinue(aadhaarNumber)
                } else {
                    isAadhaarValid = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            enabled = aadhaarNumber.length == 12 && isTermsAccepted
        ) {
            Text(
                text = "Continue",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// Preview for AadhaarVerificationScreen - Empty state
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun AadhaarVerificationScreenPreview() {
    MaterialTheme {
        AadhaarVerificationScreen(
            onContinue = { /* Preview only */ },
            context = LocalContext.current
        )
    }
}

// Preview for AadhaarVerificationScreen - Filled state
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "AadhaarVerificationScreen - Filled")
@Composable
fun AadhaarVerificationScreenFilledPreview() {
    MaterialTheme {
        AadhaarVerificationScreen(
            onContinue = { /* Preview only */ },
            context = LocalContext.current
        )
    }
} 