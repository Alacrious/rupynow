package com.rupynow.application.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.rupynow.application.network.BankAccountApi
import com.rupynow.application.network.RetrofitProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

class BankAccountRepository(
    private val context: Context,
    private val bankAccountApi: BankAccountApi = RetrofitProvider.provideRetrofit().create(BankAccountApi::class.java)
) {
    
    fun initiateBankAccountVerification(
        userId: String,
        upiHandle: String,
        authorization: String
    ): Flow<BankAccountVerificationState> = flow {
        emit(BankAccountVerificationState.Loading)
        
        try {
            val deviceId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: UUID.randomUUID().toString()
            
            val request = BankAccountVerificationRequest(
                userId = userId,
                upiHandle = upiHandle,
                deviceId = deviceId
            )
            
            val response = bankAccountApi.initiateBankAccountVerification(
                authorization = authorization,
                request = request
            )
            
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody?.status == "success" && responseBody.data != null) {
                    emit(BankAccountVerificationState.UpiReady(
                        upiHandle = responseBody.data!!.upiHandle,
                        verificationId = responseBody.data!!.verificationId
                    ))
                } else {
                    emit(BankAccountVerificationState.Error(
                        responseBody?.message ?: "Failed to initiate verification"
                    ))
                }
            } else {
                emit(BankAccountVerificationState.Error(
                    "Network error: ${response.code()}"
                ))
            }
        } catch (e: Exception) {
            emit(BankAccountVerificationState.Error(
                "Exception: ${e.message ?: "Unknown error"}"
            ))
        }
    }
    
    fun waitForTransfer(
        verificationId: String,
        userId: String,
        authorization: String,
        timeoutSeconds: Int = 120
    ): Flow<BankAccountVerificationState> = flow {
        emit(BankAccountVerificationState.WaitingForTransfer)
        
        var attempts = 0
        val maxAttempts = timeoutSeconds / 5 // Check every 5 seconds
        
        while (attempts < maxAttempts) {
            try {
                val request = BankAccountDetailsRequest(
                    verificationId = verificationId,
                    userId = userId
                )
                
                val response = bankAccountApi.getBankAccountDetails(
                    authorization = authorization,
                    request = request
                )
                
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody?.status == "success" && responseBody.data != null) {
                        val details = responseBody.data!!
                        if (details.isVerified) {
                            emit(BankAccountVerificationState.AccountDetailsFetched(details))
                            return@flow
                        }
                    }
                }
                
                attempts++
                delay(5000) // Wait 5 seconds before next check
                
            } catch (e: Exception) {
                attempts++
                delay(5000)
            }
        }
        
        emit(BankAccountVerificationState.Timeout)
    }
    
    fun confirmBankAccount(
        verificationId: String,
        userId: String,
        authorization: String
    ): Flow<BankAccountVerificationState> = flow {
        emit(BankAccountVerificationState.Loading)
        
        try {
            val request = BankAccountConfirmationRequest(
                verificationId = verificationId,
                userId = userId,
                isConfirmed = true
            )
            
            val response = bankAccountApi.confirmBankAccount(
                authorization = authorization,
                request = request
            )
            
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody?.status == "success" && responseBody.data != null) {
                    emit(BankAccountVerificationState.Confirmed(responseBody.data!!.confirmationId))
                } else {
                    emit(BankAccountVerificationState.Error(
                        responseBody?.message ?: "Failed to confirm bank account"
                    ))
                }
            } else {
                emit(BankAccountVerificationState.Error(
                    "Network error: ${response.code()}"
                ))
            }
        } catch (e: Exception) {
            emit(BankAccountVerificationState.Error(
                "Exception: ${e.message ?: "Unknown error"}"
            ))
        }
    }
    
    fun openUpiApp(upiHandle: String): Boolean {
        return try {
            // Try to open UPI apps in order of preference
            val upiApps = listOf(
                "com.google.android.apps.nbu.paisa.user", // Google Pay
                "net.one97.paytm", // Paytm
                "in.org.npci.upiapp", // BHIM
                "com.phonepe.app", // PhonePe
                "com.whatsapp" // WhatsApp Pay
            )
            
            for (packageName in upiApps) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("upi://pay?pa=$upiHandle&pn=RupyNow&am=1&cu=INR")
                        setPackage(packageName)
                    }
                    
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                        return true
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            
            // Fallback to generic UPI intent
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("upi://pay?pa=$upiHandle&pn=RupyNow&am=1&cu=INR")
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
} 