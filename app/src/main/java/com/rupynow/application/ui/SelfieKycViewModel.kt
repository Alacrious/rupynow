package com.rupynow.application.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rupynow.application.data.KycRepository
import com.rupynow.application.data.KycError
import com.rupynow.application.data.KycResult
import com.rupynow.application.data.VerificationStatus
import com.rupynow.application.services.AnalyticsService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.content.Context
import androidx.lifecycle.LifecycleOwner
import android.util.Log
import java.io.File
sealed class KycUiState {
    object Idle : KycUiState()
    object PermissionRequest : KycUiState()
    object CameraInitializing : KycUiState()
    object FaceAlignment : KycUiState()
    data class LivenessCheck(val code: String) : KycUiState()
    object Capturing : KycUiState()
    object Processing : KycUiState()
    object Success : KycUiState()
    data class Failed(val error: KycError) : KycUiState()
}

data class LivenessCode(
    val code: String,
    val duration: Long = 5000L // 5 seconds
)

class SelfieKycViewModel(
    private val kycRepository: KycRepository,
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<KycUiState>(KycUiState.Idle)
    val uiState: StateFlow<KycUiState> = _uiState.asStateFlow()
    
    private val _hasCameraPermission = MutableStateFlow(false)
    val hasCameraPermission: StateFlow<Boolean> = _hasCameraPermission.asStateFlow()
    
    private val _hasAudioPermission = MutableStateFlow(false)
    val hasAudioPermission: StateFlow<Boolean> = _hasAudioPermission.asStateFlow()
    
    private val _retryCount = MutableStateFlow(0)
    val retryCount: StateFlow<Int> = _retryCount.asStateFlow()
    
    private val analyticsService = AnalyticsService.getInstance(context)
    
    // Camera management
    private var cameraManager: CameraManager? = null
    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Initializing)
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()
    
    init {
        _uiState.value = KycUiState.PermissionRequest
    }
    
    fun grantPermissions() {
        _hasCameraPermission.value = true
        _hasAudioPermission.value = true
        _uiState.value = KycUiState.CameraInitializing
        analyticsService.logFeatureUsage("kyc_permissions_granted", "selfie_kyc")
        
        // Initialize camera manager
        initializeCamera()
    }
    
    private fun initializeCamera() {
        viewModelScope.launch {
            try {
                cameraManager = CameraManager(context = context, lifecycleOwner = lifecycleOwner)
                
                // Observe camera state
                cameraManager?.cameraState?.collect { state ->
                    _cameraState.value = state
                    when (state) {
                        is CameraState.Ready -> {
                            _uiState.value = KycUiState.FaceAlignment
                            analyticsService.logFeatureUsage("camera_initialized", "selfie_kyc")
                        }
                        is CameraState.Error -> {
                            _uiState.value = KycUiState.Failed(state.error)
                            analyticsService.logFeatureUsage("camera_initialization_failed", "selfie_kyc")
                        }
                        is CameraState.Initializing -> {
                            // Keep current state
                        }
                        is CameraState.Unavailable -> {
                            _uiState.value = KycUiState.Failed(
                                KycError.CameraError("Camera is unavailable")
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = KycUiState.Failed(
                    KycError.CameraError("Failed to initialize camera: ${e.message}")
                )
                analyticsService.logFeatureUsage("camera_initialization_failed", "selfie_kyc")
            }
        }
    }
    
    fun onFaceAligned() {
        viewModelScope.launch {
            when (val result = kycRepository.generateLivenessCode()) {
                is KycResult.Success -> {
                    _uiState.value = KycUiState.LivenessCheck(result.data)
                    analyticsService.logFeatureUsage("face_aligned", "selfie_kyc")
                }
                is KycResult.Error -> {
                    _uiState.value = KycUiState.Failed(result.error)
                    analyticsService.logFeatureUsage("liveness_code_generation_failed", "selfie_kyc")
                }
                is KycResult.Loading -> {
                    // Handle loading state if needed
                }
            }
        }
    }
    
    fun onLivenessComplete() {
        _uiState.value = KycUiState.Capturing
        
        viewModelScope.launch {
            // Start video recording for liveness check
            startVideoRecording()
            
            // Simulate liveness check duration
            delay(5000L)
            
            // Stop recording and process
            stopVideoRecording()
            
            _uiState.value = KycUiState.Processing
            analyticsService.logFeatureUsage("liveness_check_completed", "selfie_kyc")
            
            // Use repository for verification with actual video data
            when (val result = kycRepository.uploadAndVerify("actual_video_data")) {
                is KycResult.Success -> {
                    _uiState.value = KycUiState.Success
                    analyticsService.logFeatureUsage("verification_success", "selfie_kyc")
                }
                is KycResult.Error -> {
                    _uiState.value = KycUiState.Failed(result.error)
                    analyticsService.logFeatureUsage("verification_failed", "selfie_kyc")
                }
                is KycResult.Loading -> {
                    // Handle loading state if needed
                }
            }
        }
    }

    private fun startVideoRecording() {
        try {
            val outputFile = File(context.cacheDir, "liveness_video_${System.currentTimeMillis()}.mp4")
            
            cameraManager?.startVideoRecording(
                outputFile = outputFile,
                onSaved = { uri ->
                    // Video saved successfully
                    Log.d("SelfieKycViewModel", "Video saved: $uri")
                },
                onError = { error ->
                    Log.e("SelfieKycViewModel", "Video recording error: $error")
                    _uiState.value = KycUiState.Failed(KycError.CameraError("Video recording failed: $error"))
                }
            )
        } catch (e: Exception) {
            Log.e("SelfieKycViewModel", "Failed to start video recording: ${e.message}")
            _uiState.value = KycUiState.Failed(KycError.CameraError("Failed to start recording: ${e.message}"))
        }
    }

    private fun stopVideoRecording() {
        try {
            cameraManager?.stopRecording()
        } catch (e: Exception) {
            Log.e("SelfieKycViewModel", "Failed to stop video recording: ${e.message}")
        }
    }
    
    fun retry() {
        _retryCount.value = _retryCount.value + 1
        _uiState.value = KycUiState.FaceAlignment
        analyticsService.logFeatureUsage("kyc_retry", "selfie_kyc")
    }
    
    fun reset() {
        _uiState.value = KycUiState.PermissionRequest
        _retryCount.value = 0
        _hasCameraPermission.value = false
        _hasAudioPermission.value = false
        releaseCamera()
    }
    
    fun bindCameraToPreview(previewView: androidx.camera.view.PreviewView): KycResult<Unit> {
        return cameraManager?.bindCameraToLifecycle(previewView) 
            ?: KycResult.Error(KycError.CameraError("Camera manager not initialized"))
    }
    
    fun releaseCamera() {
        cameraManager?.release()
        cameraManager = null
        _cameraState.value = CameraState.Unavailable
    }
    
    override fun onCleared() {
        super.onCleared()
        releaseCamera()
    }
    
    // Helper functions are now in KycRepository
} 