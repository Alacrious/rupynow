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
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
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
    private val context: Context
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
    
    // Camera management - no longer stores lifecycle references
    private var cameraManager: CameraManager? = null
    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Initializing)
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()
    
    init {
        _uiState.value = KycUiState.PermissionRequest
    }
    
    fun onPermissionsGranted() {
        _hasCameraPermission.value = true
        _hasAudioPermission.value = true
        _uiState.value = KycUiState.CameraInitializing
        analyticsService.logFeatureUsage("kyc_permissions_granted", "selfie_kyc")
        
        // Initialize camera manager (without lifecycle)
        initializeCamera()
    }
    
    fun onPermissionsDenied() {
        _hasCameraPermission.value = false
        _hasAudioPermission.value = false
        _uiState.value = KycUiState.Failed(
            KycError.PermissionError("Camera and microphone permissions are required for KYC verification")
        )
        analyticsService.logFeatureUsage("kyc_permissions_denied", "selfie_kyc")
    }
    
    // Keep the old method for backward compatibility
    fun grantPermissions() {
        onPermissionsGranted()
    }
    
    private fun initializeCamera() {
        viewModelScope.launch {
            try {
                // Create camera manager without lifecycle dependency
                cameraManager = CameraManager(context = context)
                
                // ✅ Kick off provider init immediately to break the deadlock
                cameraManager?.initializeCamera()
                
                // Observe camera state
                cameraManager?.cameraState?.collect { state ->
                    _cameraState.value = state
                    when (state) {
                        is CameraState.Initializing -> {
                            Log.d("SelfieKycViewModel", "Camera initializing...")
                        }
                        is CameraState.ProviderReady -> {
                            // Camera provider is ready, but we need to bind from UI layer
                            Log.d("SelfieKycViewModel", "Camera provider ready, waiting for UI binding")
                        }
                        is CameraState.Ready -> {
                            Log.d("SelfieKycViewModel", "Camera ready, proceeding to face alignment")
                            // tiny delay to avoid tearing down PreviewView during session finalize
                            viewModelScope.launch {
                                kotlinx.coroutines.delay(16) // ~1 frame
                                _uiState.value = KycUiState.FaceAlignment
                            }
                        }
                        is CameraState.Error -> {
                            Log.e("SelfieKycViewModel", "Camera error: ${state.error}")
                            _uiState.value = KycUiState.Failed(state.error)
                        }
                        is CameraState.Unavailable -> {
                            Log.e("SelfieKycViewModel", "Camera unavailable")
                            _uiState.value = KycUiState.Failed(
                                KycError.CameraError("Camera is unavailable")
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SelfieKycViewModel", "Failed to initialize camera", e)
                _uiState.value = KycUiState.Failed(
                    KycError.CameraError("Failed to initialize camera: ${e.message}")
                )
            }
        }
    }
    
    // Method to bind camera from UI layer when lifecycle is available
    fun bindCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner): KycResult<Unit> {
        return try {
            val manager = cameraManager ?: return KycResult.Error(
                KycError.CameraError("Camera manager not initialized")
            )
            
            // Camera provider is already initialized in initializeCamera()
            // Just bind camera to lifecycle
            val result = manager.bindCameraToLifecycle(previewView, lifecycleOwner)
            
            when (result) {
                is KycResult.Success -> {
                    Log.d("SelfieKycViewModel", "Camera bound successfully")
                    KycResult.Success(Unit)
                }
                is KycResult.Error -> {
                    Log.e("SelfieKycViewModel", "Camera binding failed: ${result.error}")
                    _uiState.value = KycUiState.Failed(result.error)
                    result
                }
                is KycResult.Loading -> {
                    Log.d("SelfieKycViewModel", "Camera binding in progress...")
                    result
                }
            }
        } catch (e: Exception) {
            Log.e("SelfieKycViewModel", "Exception during camera binding", e)
            val error = KycError.CameraError("Camera binding failed: ${e.message}")
            _uiState.value = KycUiState.Failed(error)
            KycResult.Error(error)
        }
    }
    
    // Method to unbind camera when lifecycle stops
    fun unbindCamera() {
        try {
            Log.d("SelfieKycViewModel", "Unbinding camera from lifecycle")
            cameraManager?.unbindCamera()
        } catch (e: Exception) {
            Log.e("SelfieKycViewModel", "Error unbinding camera", e)
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
        Log.d("SelfieKycViewModel", "Starting liveness check recording...")
        _uiState.value = KycUiState.Capturing
        
        viewModelScope.launch {
            try {
                // Start video recording for liveness check
                Log.d("SelfieKycViewModel", "Calling startVideoRecording()...")
                startVideoRecording()
                
                // Wait for recording to actually start
                Log.d("SelfieKycViewModel", "Waiting for recording to start...")
                var recordingStarted = false
                for (i in 0 until 20) { // Wait up to 2 seconds for recording to start
                    val isActive = cameraManager?.isRecordingActive() ?: false
                    Log.d("SelfieKycViewModel", "Recording active: $isActive")
                    if (isActive) {
                        recordingStarted = true
                        break
                    }
                    delay(100L)
                }
                
                if (!recordingStarted) {
                    Log.e("SelfieKycViewModel", "Recording failed to start")
                    _uiState.value = KycUiState.Failed(
                        KycError.CameraError("Recording failed to start")
                    )
                    return@launch
                }
                
                Log.d("SelfieKycViewModel", "Recording started successfully, recording for 5 seconds...")
                
                // Wait for recording duration (5 seconds) with simple delay
                Log.d("SelfieKycViewModel", "Recording for 5 seconds...")
                delay(5000L)
                
                // Stop recording and process
                Log.d("SelfieKycViewModel", "Stopping recording...")
                stopVideoRecording()
                
                // Simple delay to allow recording to finalize
                Log.d("SelfieKycViewModel", "Waiting for recording finalization...")
                delay(1000L)
                
                Log.d("SelfieKycViewModel", "Transitioning to Processing state...")
                _uiState.value = KycUiState.Processing
                analyticsService.logFeatureUsage("liveness_check_completed", "selfie_kyc")
                
                // Simulate processing time
                Log.d("SelfieKycViewModel", "Processing for 2 seconds...")
                delay(2000L)
                
                // For testing, always succeed
                Log.d("SelfieKycViewModel", "Processing complete, showing success")
                _uiState.value = KycUiState.Success
                analyticsService.logFeatureUsage("verification_success", "selfie_kyc")
                
            } catch (e: Exception) {
                Log.e("SelfieKycViewModel", "Error during liveness check: ${e.message}", e)
                _uiState.value = KycUiState.Failed(
                    KycError.CameraError("Liveness check failed: ${e.message}")
                )
                analyticsService.logFeatureUsage("verification_failed", "selfie_kyc")
            }
        }
    }

    private fun startVideoRecording() {
        Log.d("SelfieKycViewModel", "Starting video recording...")
        
        // Check if camera is available
        if (cameraManager?.isCameraAvailable?.value != true) {
            Log.e("SelfieKycViewModel", "Camera not available for recording")
            return
        }
        
        try {
            // Use external files directory instead of cache directory for better permissions and space
            val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
            val timestamp = System.currentTimeMillis()
            val outputFile = File(outputDir, "liveness_video_${timestamp}.mp4")
            Log.d("SelfieKycViewModel", "Starting video recording to: ${outputFile.absolutePath}")
            Log.d("SelfieKycViewModel", "Output directory exists: ${outputDir.exists()}, writable: ${outputDir.canWrite()}")
            Log.d("SelfieKycViewModel", "Output file will be: ${outputFile.name}")
            
            // Ensure the output directory exists
            if (!outputDir.exists()) {
                outputDir.mkdirs()
                Log.d("SelfieKycViewModel", "Created output directory: ${outputDir.absolutePath}")
            }
            
            // Check file permissions
            if (!outputDir.canWrite()) {
                Log.e("SelfieKycViewModel", "Cannot write to output directory: ${outputDir.absolutePath}")
                _uiState.value = KycUiState.Failed(KycError.CameraError("Cannot write to output directory"))
                return
            }
            
            // Check if we can create a test file
            try {
                val testFile = File(outputDir, "test_${timestamp}.tmp")
                testFile.createNewFile()
                testFile.delete()
                Log.d("SelfieKycViewModel", "File permissions test passed")
            } catch (e: Exception) {
                Log.e("SelfieKycViewModel", "File permissions test failed: ${e.message}")
                _uiState.value = KycUiState.Failed(KycError.CameraError("File permissions test failed: ${e.message}"))
                return
            }
            
            // Ensure camera is ready
            if (cameraManager?.isCameraAvailable?.value != true) {
                Log.e("SelfieKycViewModel", "Camera is not available")
                _uiState.value = KycUiState.Failed(KycError.CameraError("Camera is not available"))
                return
            }
            
            Log.d("SelfieKycViewModel", "Starting video recording...")
            when (val result = cameraManager?.startVideoRecording()) {
                is KycResult.Success -> {
                    Log.d("SelfieKycViewModel", "Video recording started successfully")
                }
                is KycResult.Error -> {
                    Log.e("SelfieKycViewModel", "Video recording failed: ${result.error}")
                    _uiState.value = KycUiState.Failed(result.error)
                    return
                }
                is KycResult.Loading -> {
                    Log.d("SelfieKycViewModel", "Video recording in progress...")
                }
                null -> {
                    Log.e("SelfieKycViewModel", "Camera manager is null")
                    _uiState.value = KycUiState.Failed(KycError.CameraError("Camera manager not available"))
                    return
                }
            }
            
            Log.d("SelfieKycViewModel", "Video recording start command sent")
            Log.d("SelfieKycViewModel", "Waiting for recording to begin...")
        } catch (e: Exception) {
            Log.e("SelfieKycViewModel", "Failed to start video recording: ${e.message}", e)
            _uiState.value = KycUiState.Failed(KycError.CameraError("Failed to start recording: ${e.message}"))
        }
    }

    private fun stopVideoRecording() {
        try {
            cameraManager?.stopVideoRecording()
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
    

    
    fun releaseCamera() {
        try {
            Log.d("SelfieKycViewModel", "Releasing camera resources...")
            
            // Check if lifecycle is still active before cleanup
            // if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED) { // Removed LifecycleOwner
            //     Log.w("SelfieKycViewModel", "Lifecycle is destroyed, forcing camera cleanup")
            // }
            
        // Stop any active recording first
        if (cameraManager?.isRecordingActive() == true) {
            Log.d("SelfieKycViewModel", "Stopping active recording before release")
            cameraManager?.stopVideoRecording()
            // Wait a bit for recording to stop
            Thread.sleep(200)
        }
            
            cameraManager?.release()
            cameraManager = null
            _cameraState.value = CameraState.Initializing
            
            Log.d("SelfieKycViewModel", "Camera resources released successfully")
        } catch (e: Exception) {
            Log.e("SelfieKycViewModel", "Error releasing camera: ${e.message}", e)
            // Force cleanup even if there's an error
            cameraManager = null
            _cameraState.value = CameraState.Initializing
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d("SelfieKycViewModel", "ViewModel being cleared, releasing camera resources")
        try {
            // Stop any active recording first
            if (cameraManager?.isRecordingActive() == true) {
                Log.d("SelfieKycViewModel", "Stopping active recording before cleanup")
                cameraManager?.stopVideoRecording()
                // Wait a bit for recording to stop
                Thread.sleep(200)
            }
            
            // Release camera resources
            releaseCamera()
        } catch (e: Exception) {
            Log.e("SelfieKycViewModel", "Error during camera cleanup: ${e.message}", e)
        }
    }
    
    // Helper functions are now in KycRepository
} 