package com.rupynow.application.ui

import com.rupynow.application.data.BankAccountRepository
import com.rupynow.application.data.BankAccountVerificationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BankAccountVerificationViewModelTest {
    
    @Test
    fun `test initial state`() = runTest {
        val mockRepository = mock<BankAccountRepository>()
        val viewModel = BankAccountVerificationViewModel(mockRepository)
        
        assert(viewModel.uiState.value is BankAccountVerificationState.Initial)
        assert(!viewModel.isConfirmChecked.value)
    }
    
    @Test
    fun `test start verification`() = runTest {
        val mockRepository = mock<BankAccountRepository>()
        val viewModel = BankAccountVerificationViewModel(mockRepository)
        
        // Test that the view model can be initialized without errors
        assert(viewModel.uiState.value is BankAccountVerificationState.Initial)
    }
    
    @Test
    fun `test confirm checkbox state`() = runTest {
        val mockRepository = mock<BankAccountRepository>()
        val viewModel = BankAccountVerificationViewModel(mockRepository)
        
        viewModel.setConfirmChecked(true)
        assert(viewModel.isConfirmChecked.value)
        
        viewModel.setConfirmChecked(false)
        assert(!viewModel.isConfirmChecked.value)
    }
} 