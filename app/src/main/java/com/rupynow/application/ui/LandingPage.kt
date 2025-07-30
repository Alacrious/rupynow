package com.rupynow.application.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

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

// Preview for LandingPage
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun LandingPagePreview() {
    MaterialTheme {
        LandingPage(
            onAcceptAll = { /* Preview only */ }
        )
    }
} 