package com.rupynow.application.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.rupynow.application.R
import android.content.Context
import androidx.compose.material.icons.filled.Fingerprint

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
                    imageVector = Icons.Filled.Fingerprint,
                    contentDescription = "Aadhaar Verification",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // Header with title only
        Text(
            text = stringResource(R.string.verify_via_aadhaar),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Instructional text
        Text(
            text = stringResource(R.string.enter_aadhaar_instruction),
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
            label = { Text(stringResource(R.string.aadhaar_number)) },
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
            text = stringResource(R.string.enter_aadhaar_field),
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
                text = stringResource(R.string.i_agree_to_terms),
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = stringResource(R.string.terms_and_conditions),
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
                text = stringResource(R.string.btn_continue),
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