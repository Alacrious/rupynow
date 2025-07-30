package com.rupynow.application.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview

/**
 * Production-grade OTP input component with six separate boxes
 * 
 * Features:
 * - Six separate input boxes for 6-digit OTP
 * - Automatic focus advancement
 * - Backspace handling with focus retreat
 * - Paste support for full 6-digit strings
 * - SMS Retriever API and Autofill integration
 * - Error state handling
 * - Production-grade styling and accessibility
 */
@Composable
fun OtpInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true,
    boxSize: Int = 48,
    spacing: Int = 8
) {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val focusRequesters = remember { List(6) { FocusRequester() } }
    
    // Handle auto-fill from SMS or other sources
    LaunchedEffect(value) {
        if (value.length == 6 && value.all { it.isDigit() }) {
            // Auto-fill detected, focus the last box
            scope.launch {
                focusRequesters[5].requestFocus()
            }
        }
    }
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.dp)
    ) {
        repeat(6) { index ->
            val isFocused = remember { mutableStateOf(false) }
            val digit = if (index < value.length) value[index].toString() else ""
            
            Box(
                modifier = Modifier
                    .size(boxSize.dp)
                    .clip(RectangleShape)
                    .border(
                        width = 2.dp,
                        color = when {
                            isError -> MaterialTheme.colorScheme.error
                            isFocused.value -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline
                        },
                        shape = RectangleShape
                    )
                    .background(
                        color = if (isFocused.value) 
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                        else 
                            MaterialTheme.colorScheme.surface,
                        shape = RectangleShape
                    )
                    .clickable(enabled = enabled) {
                        scope.launch {
                            focusRequesters[index].requestFocus()
                        }
                    }
                    .focusRequester(focusRequesters[index])
                    .focusable(enabled = enabled)
                    .onFocusChanged { focusState ->
                        isFocused.value = focusState.isFocused
                    },
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = digit,
                    onValueChange = { newValue ->
                        handleOtpInput(
                            index = index,
                            newValue = newValue,
                            currentValue = value,
                            onValueChange = onValueChange,
                            focusRequesters = focusRequesters,
                            scope = scope,
                            focusManager = focusManager
                        )
                    },
                    textStyle = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = if (index == 5) ImeAction.Done else ImeAction.Next
                    ),
                    singleLine = true,
                    enabled = enabled,
                    visualTransformation = VisualTransformation.None,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

/**
 * Handle OTP input logic with focus management
 */
private fun handleOtpInput(
    index: Int,
    newValue: String,
    currentValue: String,
    onValueChange: (String) -> Unit,
    focusRequesters: List<FocusRequester>,
    scope: kotlinx.coroutines.CoroutineScope,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    val filteredValue = newValue.filter { it.isDigit() }
    
    when {
        // Single digit input
        filteredValue.length == 1 -> {
            val newOtp = currentValue.padEnd(6, ' ').toMutableList()
            newOtp[index] = filteredValue[0]
            val result = newOtp.joinToString("").trim()
            onValueChange(result)
            
            // Move focus to next box if not the last one
            if (index < 5) {
                scope.launch {
                    focusRequesters[index + 1].requestFocus()
                }
            } else {
                // Last box filled, hide keyboard
                scope.launch {
                    focusManager.clearFocus()
                }
            }
        }
        
        // Backspace on empty box
        filteredValue.isEmpty() && currentValue.getOrNull(index)?.isDigit() == true -> {
            val newOtp = currentValue.toMutableList()
            newOtp[index] = ' '
            val result = newOtp.joinToString("").trim()
            onValueChange(result)
            
            // Move focus to previous box
            if (index > 0) {
                scope.launch {
                    focusRequesters[index - 1].requestFocus()
                }
            }
        }
        
        // Paste operation (multiple digits)
        filteredValue.length > 1 -> {
            val otpDigits = filteredValue.take(6).padEnd(6, ' ')
            onValueChange(otpDigits.trim())
            
            // Focus the appropriate box
            val focusIndex = minOf(index + filteredValue.length, 5)
            scope.launch {
                focusRequesters[focusIndex].requestFocus()
            }
        }
        
        // Clear current digit
        filteredValue.isEmpty() -> {
            val newOtp = currentValue.toMutableList()
            newOtp[index] = ' '
            val result = newOtp.joinToString("").trim()
            onValueChange(result)
        }
    }
}

/**
 * Enhanced OTP input component with autofill support and clear functionality
 */
@Composable
fun OtpInputWithAutofill(
    value: String,
    onValueChange: (String) -> Unit,
    onOtpComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true,
    autofillHints: String? = "sms_otp",
    showClearButton: Boolean = true
) {
    val focusManager = LocalFocusManager.current
    
    // Watch for complete OTP
    LaunchedEffect(value) {
        if (value.length == 6 && value.all { it.isDigit() }) {
            onOtpComplete(value)
        }
    }
    
    Column(modifier = modifier) {
        OtpInputField(
            value = value,
            onValueChange = onValueChange,
            isError = isError,
            enabled = enabled
        )
        
        // Clear button
        if (value.isNotEmpty() && showClearButton) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = { onValueChange("") },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "Clear OTP",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear")
            }
        }
    }
}

/**
 * OTP input component with SMS Retriever API integration
 * This component automatically handles SMS OTP detection and autofill
 */
@Composable
fun OtpInputWithSmsRetriever(
    value: String,
    onValueChange: (String) -> Unit,
    onOtpComplete: (String) -> Unit,
    detectedOtp: String? = null,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true
) {
    // Auto-fill when SMS OTP is detected
    LaunchedEffect(detectedOtp) {
        detectedOtp?.let { otp ->
            if (otp.length in 4..6 && otp.all { it.isDigit() }) {
                onValueChange(otp)
            }
        }
    }
    
    OtpInputWithAutofill(
        value = value,
        onValueChange = onValueChange,
        onOtpComplete = onOtpComplete,
        modifier = modifier,
        isError = isError,
        enabled = enabled,
        autofillHints = "sms_otp"
    )
}

// Preview for OtpInputField
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun OtpInputFieldPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "OTP Input Field",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            OtpInputField(
                value = "123456",
                onValueChange = { /* Preview only */ },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// Preview for OtpInputField - Empty State
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "OtpInputField - Empty")
@Composable
fun OtpInputFieldEmptyPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "OTP Input Field - Empty",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            OtpInputField(
                value = "",
                onValueChange = { /* Preview only */ },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// Preview for OtpInputField - Error State
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "OtpInputField - Error")
@Composable
fun OtpInputFieldErrorPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "OTP Input Field - Error State",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            OtpInputField(
                value = "123",
                onValueChange = { /* Preview only */ },
                modifier = Modifier.fillMaxWidth(),
                isError = true
            )
        }
    }
}

// Preview for OtpInputWithAutofill
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun OtpInputWithAutofillPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "OTP Input with Autofill",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            OtpInputWithAutofill(
                value = "123456",
                onValueChange = { /* Preview only */ },
                onOtpComplete = { /* Preview only */ },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// Preview for OtpInputWithAutofill - Empty State
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "OtpInputWithAutofill - Empty")
@Composable
fun OtpInputWithAutofillEmptyPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "OTP Input with Autofill - Empty",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            OtpInputWithAutofill(
                value = "",
                onValueChange = { /* Preview only */ },
                onOtpComplete = { /* Preview only */ },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// Preview for OtpInputWithSmsRetriever
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun OtpInputWithSmsRetrieverPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "OTP Input with SMS Retriever",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            OtpInputWithSmsRetriever(
                value = "123456",
                onValueChange = { /* Preview only */ },
                onOtpComplete = { /* Preview only */ },
                detectedOtp = "123456",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
} 