package com.rupynow.application.network

import com.rupynow.application.data.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface BankAccountApi {
    @POST("api/bank-account/verification/initiate")
    suspend fun initiateBankAccountVerification(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: BankAccountVerificationRequest
    ): Response<BankAccountVerificationResponse>
    
    @POST("api/bank-account/verification/details")
    suspend fun getBankAccountDetails(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: BankAccountDetailsRequest
    ): Response<BankAccountDetailsResponse>
    
    @POST("api/bank-account/verification/confirm")
    suspend fun confirmBankAccount(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: BankAccountConfirmationRequest
    ): Response<BankAccountConfirmationResponse>
} 