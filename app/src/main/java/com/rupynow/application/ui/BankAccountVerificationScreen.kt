package com.rupynow.application.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rupynow.application.data.BankAccountVerificationState
import com.rupynow.application.data.BankAccountDetailsData
import com.rupynow.application.services.AnalyticsService

@Composable
fun BankAccountVerificationScreen(
    viewModel: BankAccountVerificationViewModel,
    onVerificationComplete: () -> Unit,
    context: Context
) {
    val uiState by viewModel.uiState.collectAsState()
    val isConfirmChecked by viewModel.isConfirmChecked.collectAsState()
    val localContext = LocalContext.current
    
    LaunchedEffect(uiState) {
        when (uiState) {
            is BankAccountVerificationState.Confirmed -> {
                val analyticsService = AnalyticsService.getInstance(context)
                analyticsService.logButtonClick("bank_account_verified", "bank_verification_screen")
                onVerificationComplete()
            }
            else -> {}
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
        
        // Header
        Text(
            text = "Verify Your Bank Account",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Verify your bank account to receive the loan",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        when (uiState) {
            is BankAccountVerificationState.Initial -> {
                InitialStateContent(
                    onStartVerification = {
                        // This would typically be called with actual data
                        viewModel.startVerification(
                            userId = "user123",
                            upiHandle = "rupynow@bank",
                            authorization = "Bearer token"
                        )
                    }
                )
            }
            
            is BankAccountVerificationState.Loading -> {
                LoadingStateContent()
            }
            
            is BankAccountVerificationState.UpiReady -> {
                val upiReadyState = uiState as BankAccountVerificationState.UpiReady
                UpiReadyStateContent(
                    upiHandle = upiReadyState.upiHandle,
                    onCopyUpiHandle = {
                        val clipboard = localContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("UPI Handle", upiReadyState.upiHandle)
                        clipboard.setPrimaryClip(clip)
                    },
                    onShareUpiHandle = {
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Send ₹1 to: ${upiReadyState.upiHandle}")
                        }
                        localContext.startActivity(Intent.createChooser(shareIntent, "Share UPI Handle"))
                    },
                    onOpenUpiApp = {
                        viewModel.openUpiApp()
                    }
                )
            }
            
            is BankAccountVerificationState.WaitingForTransfer -> {
                WaitingForTransferContent()
            }
            
            is BankAccountVerificationState.TransferDetected -> {
                // This state is handled internally, show waiting content
                WaitingForTransferContent()
            }
            
            is BankAccountVerificationState.AccountDetailsFetched -> {
                val accountDetailsState = uiState as BankAccountVerificationState.AccountDetailsFetched
                AccountDetailsContent(
                    details = accountDetailsState.details,
                    isConfirmChecked = isConfirmChecked,
                    onConfirmCheckedChange = { viewModel.setConfirmChecked(it) },
                    onConfirmBankAccount = {
                        if (isConfirmChecked) {
                            viewModel.confirmBankAccount()
                        }
                    }
                )
            }
            
            is BankAccountVerificationState.Confirmed -> {
                ConfirmedStateContent()
            }
            
            is BankAccountVerificationState.Error -> {
                val errorState = uiState as BankAccountVerificationState.Error
                ErrorStateContent(
                    message = errorState.message,
                    onRetry = { viewModel.retryVerification() }
                )
            }
            
            is BankAccountVerificationState.Timeout -> {
                TimeoutStateContent(
                    onRetry = { viewModel.retryVerification() }
                )
            }
        }
    }
}

@Composable
private fun InitialStateContent(
    onStartVerification: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.AccountBalance,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Send ₹1 from the bank account you want the loan disbursed to.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "We will auto-fetch and verify your account details.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onStartVerification,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Verification")
            }
        }
    }
}

@Composable
private fun LoadingStateContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Initializing verification...",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun UpiReadyStateContent(
    upiHandle: String,
    onCopyUpiHandle: () -> Unit,
    onShareUpiHandle: () -> Unit,
    onOpenUpiApp: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // UPI Handle Display
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
                    text = "Please send ₹1 to:",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // UPI Handle with copy/share buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = upiHandle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Row {
                        IconButton(onClick = onCopyUpiHandle) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy UPI Handle"
                            )
                        }
                        
                        IconButton(onClick = onShareUpiHandle) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share UPI Handle"
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Button(
                    onClick = onOpenUpiApp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Payment,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send ₹1 Using UPI")
                }
            }
        }
    }
}

@Composable
private fun WaitingForTransferContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Waiting for ₹1 transfer...",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Please complete the UPI transfer. We'll automatically detect it.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AccountDetailsContent(
    details: BankAccountDetailsData,
    isConfirmChecked: Boolean,
    onConfirmCheckedChange: (Boolean) -> Unit,
    onConfirmBankAccount: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Account Details Card
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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "Account Details Verified",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Account Details
                AccountDetailRow("Account Holder", details.accountHolderName)
                AccountDetailRow("Account Number", maskAccountNumber(details.accountNumber))
                AccountDetailRow("IFSC Code", details.ifscCode)
                AccountDetailRow("Bank Name", details.bankName)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Confirmation Checkbox
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = isConfirmChecked,
                onCheckedChange = onConfirmCheckedChange
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = "I confirm the above bank details are correct and belong to me.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Confirm Button
        Button(
            onClick = onConfirmBankAccount,
            enabled = isConfirmChecked,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Confirm & Proceed")
        }
    }
}

@Composable
private fun AccountDetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ConfirmedStateContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Bank Account Verified Successfully!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Your bank account has been verified and is ready for loan disbursal.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorStateContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Verification Failed",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Retry")
        }
    }
}

@Composable
private fun TimeoutStateContent(
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Schedule,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Verification Timeout",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "We couldn't verify your account. Please ensure you sent ₹1 from the correct UPI-linked account.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Retry")
        }
    }
}

private fun maskAccountNumber(accountNumber: String): String {
    return if (accountNumber.length > 4) {
        "XXXX XXXX ${accountNumber.takeLast(4)}"
    } else {
        accountNumber
    }
} 