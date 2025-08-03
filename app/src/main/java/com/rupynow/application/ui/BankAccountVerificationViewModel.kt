package com.rupynow.application.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rupynow.application.data.BankAccountRepository
import com.rupynow.application.data.BankAccountVerificationState
import com.rupynow.application.data.BankAccountDetailsData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BankAccountVerificationViewModel(
    private val repository: BankAccountRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<BankAccountVerificationState>(BankAccountVerificationState.Initial)
    val uiState: StateFlow<BankAccountVerificationState> = _uiState.asStateFlow()
    
    private val _isConfirmChecked = MutableStateFlow(false)
    val isConfirmChecked: StateFlow<Boolean> = _isConfirmChecked.asStateFlow()
    
    private var currentVerificationId: String? = null
    private var currentUserId: String? = null
    private var currentAuthorization: String? = null
    private var currentUpiHandle: String? = null
    
    fun startVerification(userId: String, upiHandle: String, authorization: String) {
        currentUserId = userId
        currentUpiHandle = upiHandle
        currentAuthorization = authorization
        
        viewModelScope.launch {
            repository.initiateBankAccountVerification(
                userId = userId,
                upiHandle = upiHandle,
                authorization = authorization
            ).collect { state ->
                _uiState.value = state
                
                if (state is BankAccountVerificationState.UpiReady) {
                    currentVerificationId = state.verificationId
                }
            }
        }
    }
    
    fun openUpiApp() {
        val upiHandle = currentUpiHandle ?: return
        val success = repository.openUpiApp(upiHandle)
        
        if (success) {
            // Start polling for transfer detection
            startTransferDetection()
        } else {
            _uiState.value = BankAccountVerificationState.Error(
                "No UPI app found. Please install a UPI app like Google Pay, Paytm, or PhonePe."
            )
        }
    }
    
    private fun startTransferDetection() {
        val verificationId = currentVerificationId ?: return
        val userId = currentUserId ?: return
        val authorization = currentAuthorization ?: return
        
        viewModelScope.launch {
            repository.waitForTransfer(
                verificationId = verificationId,
                userId = userId,
                authorization = authorization
            ).collect { state ->
                _uiState.value = state
            }
        }
    }
    
    fun confirmBankAccount() {
        val verificationId = currentVerificationId ?: return
        val userId = currentUserId ?: return
        val authorization = currentAuthorization ?: return
        
        viewModelScope.launch {
            repository.confirmBankAccount(
                verificationId = verificationId,
                userId = userId,
                authorization = authorization
            ).collect { state ->
                _uiState.value = state
            }
        }
    }
    
    fun setConfirmChecked(checked: Boolean) {
        _isConfirmChecked.value = checked
    }
    
    fun retryVerification() {
        val userId = currentUserId ?: return
        val upiHandle = currentUpiHandle ?: return
        val authorization = currentAuthorization ?: return
        
        startVerification(userId, upiHandle, authorization)
    }
    
    fun resetState() {
        _uiState.value = BankAccountVerificationState.Initial
        _isConfirmChecked.value = false
        currentVerificationId = null
    }
    
    fun copyUpiHandleToClipboard(): String? {
        return currentUpiHandle
    }
    
    fun shareUpiHandle(): String? {
        return currentUpiHandle?.let { handle ->
            "Send ₹1 to: $handle@bank"
        }
    }
} 