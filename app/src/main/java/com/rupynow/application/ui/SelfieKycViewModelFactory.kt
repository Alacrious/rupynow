package com.rupynow.application.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rupynow.application.data.KycRepository
import android.content.Context
import androidx.lifecycle.LifecycleOwner

class SelfieKycViewModelFactory(
    private val kycRepository: KycRepository,
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) : ViewModelProvider.Factory {
    
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SelfieKycViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SelfieKycViewModel(kycRepository, context, lifecycleOwner) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 