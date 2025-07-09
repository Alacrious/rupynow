package com.rupynow.application.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rupynow.application.data.model.UserVerificationResult
import com.rupynow.application.domain.usecase.VerifyUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserVerificationViewModel @Inject constructor(
    private val verifyUserUseCase: VerifyUserUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserVerificationUiState())
    val uiState: StateFlow<UserVerificationUiState> = _uiState.asStateFlow()

    fun verifyUser(email: String, phone: String, deviceId: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )

            try {
                val result = verifyUserUseCase(email, phone, deviceId)
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    verificationResult = result,
                    error = if (!result.isSuccess) result.message else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "An unexpected error occurred: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun resetState() {
        _uiState.value = UserVerificationUiState()
    }
}

data class UserVerificationUiState(
    val isLoading: Boolean = false,
    val verificationResult: UserVerificationResult? = null,
    val error: String? = null
) 