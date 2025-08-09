package com.rupynow.application.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import com.rupynow.application.R
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.camera.core.CameraSelector
import com.rupynow.application.data.KycRepositoryImpl
import com.rupynow.application.ui.SelfieKycViewModelFactory
import com.rupynow.application.data.KycError
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.rupynow.application.services.AnalyticsService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.compose.runtime.LaunchedEffect
import com.rupynow.application.data.KycResult
import com.rupynow.application.ui.KycUiState
import com.rupynow.application.ui.LivenessCode
import com.rupynow.application.ui.CameraState

// These are now defined in SelfieKycViewModel.kt

@Composable
fun SelfieKycScreen(
    onSuccess: () -> Unit,
    onManualReview: () -> Unit,
    onBackPressed: () -> Unit,
    context: Context,
    viewModel: SelfieKycViewModel = viewModel(
        factory = SelfieKycViewModelFactory(
            kycRepository = KycRepositoryImpl(),
            context = context
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val hasCameraPermission by viewModel.hasCameraPermission.collectAsState()
    val hasAudioPermission by viewModel.hasAudioPermission.collectAsState()
    val retryCount by viewModel.retryCount.collectAsState()
    val cameraState by viewModel.cameraState.collectAsState()
    
    // Permission launcher for camera and microphone
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        
        if (cameraGranted && audioGranted) {
            viewModel.onPermissionsGranted()
        } else {
            // Handle permission denied
            viewModel.onPermissionsDenied()
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        SelfieKycHeader(
            uiState = uiState,
            onBackPressed = onBackPressed
        )
        
        // Main Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            // Always-on camera surface - hoisted outside when to prevent recreation
            if (uiState !is KycUiState.PermissionRequest && uiState !is KycUiState.Processing && uiState !is KycUiState.Success && uiState !is KycUiState.Failed && uiState !is KycUiState.Idle) {
                CameraView(
                    uiState = uiState,
                    livenessCode = (uiState as? KycUiState.LivenessCheck)?.let { LivenessCode(it.code) },
                    onFaceAligned = { viewModel.onFaceAligned() },
                    onLivenessComplete = { viewModel.onLivenessComplete() },
                    viewModel = viewModel,
                    cameraState = cameraState
                )
            }

            // Overlays only - no more CameraView recreation
            when (uiState) {
                is KycUiState.PermissionRequest -> {
                    PermissionRequestContent(
                        onRequestPermissions = {
                            // Check if permissions are already granted
                            val hasCamera = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            
                            val hasAudio = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            
                            if (hasCamera && hasAudio) {
                                // Permissions already granted
                                viewModel.onPermissionsGranted()
                            } else {
                                // Request actual Android permissions
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.CAMERA,
                                        Manifest.permission.RECORD_AUDIO
                                    )
                                )
                            }
                        }
                    )
                }
                is KycUiState.CameraInitializing -> {
                    // Loading overlay only - camera view is already mounted above
                    CameraInitializingContent()
                }
                is KycUiState.FaceAlignment -> {
                    // Face alignment overlay already handled inside CameraView
                    // No additional content needed here
                }
                is KycUiState.LivenessCheck -> {
                    // Liveness check overlay already handled inside CameraView
                    // No additional content needed here
                }
                is KycUiState.Capturing -> {
                    // Capturing overlay already handled inside CameraView
                    // No additional content needed here
                }
                is KycUiState.Processing -> {
                    ProcessingContent()
                }
                is KycUiState.Success -> {
                    SuccessContent(
                        onContinue = onSuccess
                    )
                }
                is KycUiState.Failed -> {
                    val failedState = uiState as KycUiState.Failed
                    FailedContent(
                        error = failedState.error,
                        onRetry = {
                            viewModel.retry()
                        },
                        onManualReview = onManualReview
                    )
                }
                is KycUiState.Idle -> {
                    // Should not happen, but handle gracefully
                    PermissionRequestContent(
                        onRequestPermissions = {
                            viewModel.grantPermissions()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SelfieKycHeader(
    uiState: KycUiState,
    onBackPressed: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackPressed) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back_button),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = when (uiState) {
                is KycUiState.PermissionRequest -> stringResource(R.string.kyc_verification_test_mode)
                is KycUiState.CameraInitializing -> stringResource(R.string.setting_up_camera)
                            is KycUiState.FaceAlignment -> stringResource(R.string.face_alignment)
            is KycUiState.LivenessCheck -> stringResource(R.string.liveness_check)
            is KycUiState.Capturing -> stringResource(R.string.capturing)
            is KycUiState.Processing -> stringResource(R.string.processing)
            is KycUiState.Success -> stringResource(R.string.verification_complete)
            is KycUiState.Failed -> stringResource(R.string.verification_failed)
                is KycUiState.Idle -> stringResource(R.string.kyc_verification_test_mode)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Test mode indicator
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Text(
                text = stringResource(R.string.test),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PermissionRequestContent(
    onRequestPermissions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Camera & Microphone Access Required",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "We need access to your camera and microphone to perform the KYC verification. This includes:\n\n• Camera: To capture your photo for identity verification\n• Microphone: To perform liveness detection",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Button(
            onClick = onRequestPermissions,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = stringResource(R.string.grant_permissions),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun CameraInitializingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Setting up camera...",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Please wait while we initialize your camera for the verification process.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CameraView(
    uiState: KycUiState,
    livenessCode: LivenessCode? = null,
    onFaceAligned: () -> Unit = {},
    onLivenessComplete: () -> Unit = {},
    viewModel: SelfieKycViewModel,
    cameraState: CameraState
) {
    val hasCameraPermission by viewModel.hasCameraPermission.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Truly stable PreviewView instance - single instance across recompositions
    val stablePreview = remember { PreviewView(context) }
    
    // Camera binding with proper lifecycle management - bind on RESUMED, not on STARTED
    var hasBoundCamera by remember { mutableStateOf(false) }
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && 
                        hasCameraPermission && 
                        !hasBoundCamera) {
                        Log.d("CameraView", "ON_RESUME: Camera permission granted and not yet bound, ready for binding")
                        // Camera will be bound when stablePreview is available and camera state is ready
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    Log.d("CameraView", "${event}: Camera lifecycle destroyed")
                    viewModel.unbindCamera()
                    hasBoundCamera = false // Reset flag when camera is unbound
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            Log.d("CameraView", "Disposing camera effect, removing observer")
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Bind camera when stablePreview is available, camera state is ProviderReady, and lifecycle is RESUMED
    LaunchedEffect(stablePreview, cameraState, hasCameraPermission, hasBoundCamera) {
        if (hasCameraPermission && 
            !hasBoundCamera &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            
            when (cameraState) {
                is CameraState.ProviderReady -> {
                    Log.d("CameraView", "Camera provider ready, binding camera to lifecycle")
                    when (val result = viewModel.bindCamera(stablePreview, lifecycleOwner)) {
                        is KycResult.Success -> {
                            Log.d("CameraView", "Camera bound successfully")
                            hasBoundCamera = true // Mark as bound to prevent rebinding
                        }
                        is KycResult.Error -> {
                            Log.w("CameraView", "Camera binding failed: ${result.error}")
                        }
                        is KycResult.Loading -> {
                            Log.d("CameraView", "Camera binding in progress...")
                        }
                    }
                }
                is CameraState.Ready -> {
                    Log.d("CameraView", "Camera already bound and ready")
                    hasBoundCamera = true // Mark as bound to prevent rebinding
                }
                is CameraState.Error -> {
                    Log.w("CameraView", "Camera in error state: ${cameraState.error}")
                }
                else -> {
                    Log.d("CameraView", "Camera state: $cameraState, waiting...")
                }
            }
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Camera Preview with PreviewView
        // Keep preview visible; overlays already cover it.
        // This ensures the Surface is created and stays stable during session config
        AndroidView(
            factory = { stablePreview.apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }},
            modifier = Modifier.fillMaxSize(),
            update = { /* nothing: keep same instance/surface */ }
        )
        
        // Face alignment overlay
        if (uiState is KycUiState.FaceAlignment) {
            FaceAlignmentOverlay(
                onFaceAligned = onFaceAligned
            )
        }
        
        // Liveness check overlay
        if (uiState is KycUiState.LivenessCheck) {
            LivenessCheckOverlay(
                livenessCode = livenessCode,
                onComplete = onLivenessComplete
            )
        }
        
        // Capturing overlay - shows recording progress while keeping camera alive
        if (uiState is KycUiState.Capturing) {
            CapturingOverlay()
        }
        
        // Instructions overlay
        CameraInstructions(
            uiState = uiState,
            livenessCode = livenessCode
        )
        
        // Camera status indicator
        when (cameraState) {
            is CameraState.Error -> {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = stringResource(R.string.camera_error),
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            is CameraState.Initializing -> {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Text(
                        text = "Initializing...",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is CameraState.ProviderReady -> {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = "Camera Ready",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            is CameraState.Unavailable -> {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "Camera Unavailable",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is CameraState.Ready -> {
                // Camera is ready, no indicator needed
            }
        }
    }
}

@Composable
fun FaceAlignmentOverlay(
    onFaceAligned: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Face alignment frame
        Box(
            modifier = Modifier
                .size(280.dp, 360.dp)
                .border(
                    width = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                )
        )
        
        // Simulate face detection
        LaunchedEffect(Unit) {
            delay(2000) // Simulate face detection time
            onFaceAligned()
        }
    }
}

@Composable
fun LivenessCheckOverlay(
    livenessCode: LivenessCode?,
    onComplete: () -> Unit
) {
    var remainingMs by remember(livenessCode?.code) {
        mutableStateOf(livenessCode?.duration ?: 5000L)
    }

    // Start a one-shot timer when the code appears
    LaunchedEffect(livenessCode?.code) {
        val total = livenessCode?.duration ?: 5000L
        val step = 100L
        var left = total
        while (left > 0) {
            delay(step)
            left -= step
            remainingMs = left
        }
        onComplete() // 👉 triggers ViewModel.onLivenessComplete()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Say the code:", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = livenessCode?.code ?: "1234",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(16.dp))
                // countdown
                LinearProgressIndicator(
                    progress = 1f - (remainingMs / (livenessCode?.duration?.toFloat() ?: 5000f)),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Listening… ${"%.1f".format((remainingMs / 1000.0).coerceAtLeast(0.0))}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                // Optional manual advance if ASR fails
                TextButton(onClick = onComplete) { Text("I said the code") }
            }
        }
    }
}

@Composable
fun CapturingOverlay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Capturing...",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun CameraInstructions(
    uiState: KycUiState,
    livenessCode: LivenessCode?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Top instructions
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            )
        ) {
            Text(
                text = when (uiState) {
                    is KycUiState.FaceAlignment -> stringResource(R.string.align_face_frame)
                    is KycUiState.LivenessCheck -> stringResource(R.string.say_code_screen)
                    else -> ""
                },
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
        
        // Bottom progress indicator
        if (uiState is KycUiState.LivenessCheck || uiState is KycUiState.Capturing) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = when (uiState) {
                            is KycUiState.LivenessCheck -> "Recording..."
                            is KycUiState.Capturing -> "Capturing video..."
                            else -> "Processing..."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}



@Composable
fun ProcessingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Verifying identity...",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Please wait while we verify your identity with your Aadhaar photo.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SuccessContent(
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Verification Successful!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Your identity has been successfully verified. You can now proceed with your application.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = stringResource(R.string.btn_continue),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun FailedContent(
    error: KycError,
    onRetry: () -> Unit,
    onManualReview: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Error,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.verification_failed),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = when (error) {
                is KycError.NetworkError -> error.message
                is KycError.CameraError -> error.message
                is KycError.PermissionError -> error.message
                is KycError.VerificationError -> error.message
                is KycError.LivenessError -> error.message
                is KycError.UnknownError -> error.message
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Action buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.retry),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            OutlinedButton(
                onClick = onManualReview,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Support,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.contact_support),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// Helper function to get error message from KycError
private fun getErrorMessage(error: KycError): String {
    return when (error) {
        is KycError.NetworkError -> error.message
        is KycError.CameraError -> error.message
        is KycError.PermissionError -> error.message
        is KycError.VerificationError -> error.message
        is KycError.LivenessError -> error.message
        is KycError.UnknownError -> error.message
    }
}

// Helper functions are now in SelfieKycViewModel.kt

// Preview for SelfieKycScreen - Permission Request State
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "SelfieKycScreen - Permission Request")
@Composable
fun SelfieKycScreenPermissionPreview() {
    MaterialTheme {
        PermissionRequestContent(
            onRequestPermissions = { /* Preview only */ }
        )
    }
}

// Preview for SelfieKycScreen - Camera Initializing State
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "SelfieKycScreen - Camera Initializing")
@Composable
fun SelfieKycScreenCameraInitializingPreview() {
    MaterialTheme {
        CameraInitializingContent()
    }
}

// Preview for SelfieKycScreen - Face Alignment State
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "SelfieKycScreen - Face Alignment")
@Composable
fun SelfieKycScreenFaceAlignmentPreview() {
    MaterialTheme {
        // Note: In preview, we can't provide a real ViewModel, so we'll show a placeholder
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.camera_view_preview))
        }
    }
}

// Preview for SelfieKycScreen - Liveness Check State
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "SelfieKycScreen - Liveness Check")
@Composable
fun SelfieKycScreenLivenessCheckPreview() {
    MaterialTheme {
        // Note: In preview, we can't provide a real ViewModel, so we'll show a placeholder
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.liveness_check_preview))
        }
    }
}

// Preview for SelfieKycScreen - Capturing State
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "SelfieKycScreen - Capturing")
@Composable
fun SelfieKycScreenCapturingPreview() {
    MaterialTheme {
        CapturingOverlay()
    }
}

// Preview for SelfieKycScreen - Processing State
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "SelfieKycScreen - Processing")
@Composable
fun SelfieKycScreenProcessingPreview() {
    MaterialTheme {
        ProcessingContent()
    }
}

// Preview for SelfieKycScreen - Success State
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "SelfieKycScreen - Success")
@Composable
fun SelfieKycScreenSuccessPreview() {
    MaterialTheme {
        SuccessContent(
            onContinue = { /* Preview only */ }
        )
    }
}

// Preview for SelfieKycScreen - Failed State
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "SelfieKycScreen - Failed")
@Composable
fun SelfieKycScreenFailedPreview() {
    MaterialTheme {
        FailedContent(
            error = KycError.VerificationError("Your face doesn't match with your Aadhaar photo."),
            onRetry = { /* Preview only */ },
            onManualReview = { /* Preview only */ }
        )
    }
}

// Preview for SelfieKycScreen - Header
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "SelfieKycScreen - Header")
@Composable
fun SelfieKycScreenHeaderPreview() {
    MaterialTheme {
        SelfieKycHeader(
            uiState = KycUiState.FaceAlignment,
            onBackPressed = { /* Preview only */ }
        )
    }
} 