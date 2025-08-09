package com.rupynow.application.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.video.*
import androidx.camera.video.VideoRecordEvent.Finalize
import androidx.camera.video.FallbackStrategy
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Lifecycle
import com.rupynow.application.data.KycError
import com.rupynow.application.data.KycResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed class CameraState {
    object Initializing : CameraState()
    object ProviderReady : CameraState() // Camera provider is ready but not bound
    object Ready : CameraState() // Camera is bound and ready to use
    data class Error(val error: KycError) : CameraState()
    object Unavailable : CameraState()
}

class CameraManager(
    private val context: Context
) {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: androidx.camera.core.Camera? = null
    private var preview: androidx.camera.core.Preview? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recorder: Recorder? = null
    private var isRecording = false
    private var currentRecording: Recording? = null
    private var isBound = false

    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Initializing)
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private val _isCameraAvailable = MutableStateFlow(false)
    val isCameraAvailable: StateFlow<Boolean> = _isCameraAvailable.asStateFlow()

    // Helper method to check if lifecycle is at least RESUMED for camera binding
    // CameraX requires lifecycle to be at least RESUMED for optimal camera performance
    // This prevents binding attempts during STARTED state which can cause performance issues
    private fun isLifecycleActive(lifecycleOwner: LifecycleOwner): Boolean {
        val currentState = lifecycleOwner.lifecycle.currentState
        val isActive = currentState.isAtLeast(Lifecycle.State.RESUMED)
        Log.d("CameraManager", "Lifecycle state check: current=$currentState, isActive=$isActive")
        return isActive
    }
    
    // Public method to check if camera manager is safe to use
    fun isSafeToUse(): Boolean {
        return cameraProvider != null && isBound && _cameraState.value is CameraState.Ready
    }
    
    // Public method to check if camera is already bound
    fun isCameraBound(): Boolean {
        return isBound && preview != null && camera != null && videoCapture != null
    }
    
    // Public method to check if camera is currently being bound
    fun isBindingInProgress(): Boolean {
        return _cameraState.value is CameraState.Initializing
    }
    
    // Public method to check if lifecycle state allows binding
    fun canBindToLifecycle(lifecycleOwner: LifecycleOwner): Boolean {
        return isLifecycleActive(lifecycleOwner)
    }

    // Initialize camera provider (only once)
    fun initializeCamera() {
        // Don't reinitialize if already done
        if (cameraProvider != null) {
            Log.d("CameraManager", "Camera provider already initialized, skipping...")
            return
        }
        
        Log.d("CameraManager", "Starting camera provider initialization...")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                Log.d("CameraManager", "Camera provider future completed, getting provider...")
                cameraProvider = cameraProviderFuture.get()
                Log.d("CameraManager", "Camera provider obtained: ${cameraProvider != null}")
                _cameraState.value = CameraState.ProviderReady
                _isCameraAvailable.value = true
                Log.d("CameraManager", "Camera provider initialized successfully, state updated to ProviderReady")
            } catch (e: Exception) {
                Log.e("CameraManager", "Failed to initialize camera provider", e)
                _cameraState.value = CameraState.Error(
                    KycError.CameraError("Failed to initialize camera: ${e.message ?: "Unknown error"}")
                )
                _isCameraAvailable.value = false
            }
        }, ContextCompat.getMainExecutor(context))
        Log.d("CameraManager", "Camera provider initialization listener added")
    }

    // Bind camera to lifecycle with PreviewView
    fun bindCameraToLifecycle(previewView: PreviewView, lifecycleOwner: LifecycleOwner): KycResult<Unit> {
        return try {
            // ✅ ENFORCE RESUMED state: CameraX requires lifecycle to be at least RESUMED for optimal binding
            val currentState = lifecycleOwner.lifecycle.currentState
            Log.d("CameraManager", "Attempting to bind camera, lifecycle state: $currentState")
            
            if (!isLifecycleActive(lifecycleOwner)) {
                Log.w("CameraManager", "Cannot bind camera: lifecycle is not at least RESUMED (current: $currentState)")
                return KycResult.Error(
                    KycError.CameraError("Cannot bind camera: lifecycle is not at least RESUMED (current: $currentState)")
                )
            }
            
            // Wait for camera provider to be initialized
            if (cameraProvider == null) {
                Log.d("CameraManager", "Camera provider not ready yet, waiting...")
                return KycResult.Loading
            }
            
            val provider = cameraProvider!!
            Log.d("CameraManager", "Camera provider ready, proceeding with binding")

            // ✅ IDEMPOTENT BINDING: Don't rebind if already bound and ready
            if (isCameraBound()) {
                Log.d("CameraManager", "Camera already bound and ready, skipping rebind")
                return KycResult.Success(Unit)
            }
            
            // ✅ PREVENT MULTIPLE BINDING: Don't bind if binding is already in progress
            if (isBindingInProgress()) {
                Log.d("CameraManager", "Camera binding already in progress, skipping duplicate request")
                return KycResult.Loading
            }

            // ✅ OPTIMIZED UNBINDING: Only unbindAll() when transitioning from unbound -> bound
            // This prevents unnecessary surface recreation and binding loops
            if (isBound) {
                Log.d("CameraManager", "Camera was previously bound, cleaning up before rebinding")
                provider.unbindAll()
            }
            
            // ✅ EARLY SURFACE ATTACHMENT: Create preview and attach surface BEFORE binding
            // This reduces the window where CameraX is waiting for a surface
            preview = androidx.camera.core.Preview.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            
            // Create recorder and video capture
            recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
                )
                .setExecutor(cameraExecutor)
                .build()
            
            videoCapture = VideoCapture.withOutput(recorder!!)
            
            // Bind use cases to lifecycle
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview!!,
                videoCapture!!
            )
            
            isBound = true
            _cameraState.value = CameraState.Ready
            _isCameraAvailable.value = true
            
            Log.d("CameraManager", "Camera bound successfully to PreviewView")
            KycResult.Success(Unit)
        } catch (e: Exception) {
            Log.e("CameraManager", "Camera binding failed: ${e.message}", e)
            isBound = false
            _cameraState.value = CameraState.Error(
                KycError.CameraError("Failed to bind camera: ${e.message ?: "Unknown error"}")
            )
            _isCameraAvailable.value = false
            KycResult.Error(KycError.CameraError("Failed to bind camera: ${e.message ?: "Unknown error"}"))
        }
    }

    // Check if recording is currently active
    fun isRecordingActive(): Boolean = isRecording

    // Start video recording
    fun startVideoRecording(): KycResult<Unit> {
        return try {
            if (!isSafeToUse()) {
                return KycResult.Error(KycError.CameraError("Camera not ready for recording"))
            }

            val vc = videoCapture ?: return KycResult.Error(
                KycError.CameraError("Video capture not initialized")
            )

            // Create video file
            val videoFile = createVideoFile()
            val fileOutputOptions = FileOutputOptions.Builder(videoFile).build()

            // Start recording
            currentRecording = vc.output.prepareRecording(context, fileOutputOptions)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            isRecording = true
                            Log.d("CameraManager", "Recording started successfully")
                        }
                        is Finalize -> {
                            isRecording = false
                            if (event.hasError()) {
                                Log.e("CameraManager", "Recording failed: ${event.error}")
                                _cameraState.value = CameraState.Error(
                                    KycError.CameraError("Recording failed: ${event.error}")
                                )
                            } else {
                                Log.d("CameraManager", "Recording completed: ${event.outputResults.outputUri}")
                            }
                        }
                        else -> {
                            Log.d("CameraManager", "Recording event: $event")
                        }
                    }
                }

            Log.d("CameraManager", "Video recording started")
            KycResult.Success(Unit)
        } catch (e: Exception) {
            Log.e("CameraManager", "Failed to start recording: ${e.message}", e)
            KycResult.Error(KycError.CameraError("Failed to start recording: ${e.message}"))
        }
    }

    // Stop video recording
    fun stopVideoRecording() {
        try {
            if (isRecording && currentRecording != null) {
                currentRecording?.stop()
                isRecording = false
                Log.d("CameraManager", "Video recording stopped")
            }
        } catch (e: Exception) {
            Log.e("CameraManager", "Error stopping recording: ${e.message}", e)
        }
    }

    // Create video file
    private fun createVideoFile(): File {
        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val storageDir = context.getExternalFilesDir(null)
        return File.createTempFile("VIDEO_${timeStamp}_", ".mp4", storageDir)
    }

    // Unbind camera from lifecycle
    fun unbindCamera() {
        try {
            // ✅ Don't unbind if already unbound
            if (!isBound) {
                Log.d("CameraManager", "Camera already unbound, skipping")
                return
            }
            
            if (isRecording) {
                stopVideoRecording()
            }
            
            cameraProvider?.unbindAll()
            camera = null
            preview = null
            videoCapture = null
            recorder = null
            isBound = false
            
            // Reset to provider ready state if provider is still available
            if (cameraProvider != null) {
                _cameraState.value = CameraState.ProviderReady
            } else {
                _cameraState.value = CameraState.Initializing
            }
            
            Log.d("CameraManager", "Camera unbound successfully")
        } catch (e: Exception) {
            Log.e("CameraManager", "Error unbinding camera", e)
        }
    }

    // Release resources
    fun release() {
        try {
            Log.d("CameraManager", "Releasing camera manager resources...")
            
            // Stop any active recording
            if (isRecording) {
                stopVideoRecording()
            }
            
            // Unbind camera
            unbindCamera()
            
            // Shutdown executor
            cameraExecutor.shutdown()
            
            Log.d("CameraManager", "Camera manager resources released successfully")
        } catch (e: Exception) {
            Log.e("CameraManager", "Error releasing camera manager: ${e.message}", e)
        }
    }
} 