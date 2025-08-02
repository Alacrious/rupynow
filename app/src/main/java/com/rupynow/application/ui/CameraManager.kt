package com.rupynow.application.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.video.*
import androidx.camera.video.VideoRecordEvent.Finalize
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
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
    object Ready : CameraState()
    data class Error(val error: KycError) : CameraState()
    object Unavailable : CameraState()
}

class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: androidx.camera.core.Camera? = null

    private var recorder: Recorder? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: Recording? = null

    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Initializing)
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private val _isCameraAvailable = MutableStateFlow(false)
    val isCameraAvailable: StateFlow<Boolean> = _isCameraAvailable.asStateFlow()

    init {
        initializeCamera()
    }

    private fun initializeCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                _cameraState.value = CameraState.Ready
                _isCameraAvailable.value = true
            } catch (e: Exception) {
                _cameraState.value = CameraState.Error(
                    KycError.CameraError("Failed to initialize camera: ${e.message ?: "Unknown error"}")
                )
                _isCameraAvailable.value = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun bindCameraToLifecycle(previewView: PreviewView): KycResult<Unit> {
        return try {
            val provider = cameraProvider ?: return KycResult.Error(
                KycError.CameraError("Camera provider not initialized")
            )

            provider.unbindAll()

            val preview = androidx.camera.core.Preview.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()

            videoCapture = VideoCapture.withOutput(recorder!!)

            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                videoCapture
            )

            preview.setSurfaceProvider(previewView.surfaceProvider)

            _cameraState.value = CameraState.Ready
            _isCameraAvailable.value = true

            KycResult.Success(Unit)
        } catch (e: Exception) {
            _cameraState.value = CameraState.Error(
                KycError.CameraError("Failed to bind camera: ${e.message ?: "Unknown error"}")
            )
            _isCameraAvailable.value = false
            KycResult.Error(KycError.CameraError("Camera binding failed: ${e.message ?: "Unknown error"}"))
        }
    }

    fun startVideoRecording(outputFile: File, onSaved: (Uri) -> Unit, onError: (String) -> Unit) {
        try {
            val vc = videoCapture ?: return onError("VideoCapture not initialized")
            val mediaOutput = FileOutputOptions.Builder(outputFile).build()
            val pendingRecording = vc.output.prepareRecording(context, mediaOutput)
                .withAudioEnabled()

            currentRecording = pendingRecording.start(
                ContextCompat.getMainExecutor(context)
            ) { event ->
                when (event) {
                    is Finalize -> {
                        if (event.hasError()) {
                            onError("Recording failed: ${event.error}")
                        } else {
                            onSaved(event.outputResults.outputUri)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            onError("Recording exception: ${e.message}")
        }
    }

    fun stopRecording() {
        try {
            currentRecording?.stop()
            currentRecording = null
        } catch (e: Exception) {
            Log.e("CameraManager", "Failed to stop recording: ${e.message}")
        }
    }

    fun unbindCamera() {
        try {
            cameraProvider?.unbindAll()
            camera = null
            _cameraState.value = CameraState.Unavailable
            _isCameraAvailable.value = false
        } catch (e: Exception) {
            // Log error but don't throw
        }
    }

    fun release() {
        try {
            stopRecording()
            unbindCamera()
            cameraExecutor.shutdown()
        } catch (e: Exception) {
            // Log error but don't throw
        }
    }

    fun getCameraInfo(): String {
        return when (val state = _cameraState.value) {
            is CameraState.Ready -> "Camera ready"
            is CameraState.Error -> "Camera error: ${state.error.toString()}"
            is CameraState.Initializing -> "Camera initializing"
            is CameraState.Unavailable -> "Camera unavailable"
        }
    }
} 