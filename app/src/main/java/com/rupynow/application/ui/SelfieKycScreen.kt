package com.rupynow.application.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
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
            context = context,
            lifecycleOwner = LocalLifecycleOwner.current
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val hasCameraPermission by viewModel.hasCameraPermission.collectAsState()
    val hasAudioPermission by viewModel.hasAudioPermission.collectAsState()
    val retryCount by viewModel.retryCount.collectAsState()
    val cameraState by viewModel.cameraState.collectAsState()
    
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
            when (uiState) {
                is KycUiState.PermissionRequest -> {
                    PermissionRequestContent(
                        onRequestPermissions = {
                            viewModel.grantPermissions()
                        }
                    )
                }
                is KycUiState.CameraInitializing -> {
                    CameraInitializingContent()
                }
                is KycUiState.FaceAlignment -> {
                    CameraView(
                        uiState = uiState,
                        onFaceAligned = {
                            viewModel.onFaceAligned()
                        },
                        viewModel = viewModel,
                        cameraState = cameraState
                    )
                }
                is KycUiState.LivenessCheck -> {
                    val livenessState = uiState as KycUiState.LivenessCheck
                    CameraView(
                        uiState = uiState,
                        livenessCode = LivenessCode(livenessState.code),
                        onLivenessComplete = {
                            viewModel.onLivenessComplete()
                        },
                        viewModel = viewModel,
                        cameraState = cameraState
                    )
                }
                is KycUiState.Capturing -> {
                    CapturingContent(
                        onCaptureComplete = {
                            // This will be handled by the ViewModel
                        }
                    )
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
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = when (uiState) {
                is KycUiState.PermissionRequest -> "KYC Verification"
                is KycUiState.CameraInitializing -> "Setting up camera..."
                is KycUiState.FaceAlignment -> "Face Alignment"
                is KycUiState.LivenessCheck -> "Liveness Check"
                is KycUiState.Capturing -> "Capturing..."
                is KycUiState.Processing -> "Processing..."
                is KycUiState.Success -> "Verification Complete"
                is KycUiState.Failed -> "Verification Failed"
                is KycUiState.Idle -> "KYC Verification"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.weight(1f))
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
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onRequestPermissions,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = "Grant Permissions",
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var isCameraBound by remember { mutableStateOf(false) }
    
    DisposableEffect(Unit) {
        onDispose {
            // Camera cleanup is handled by ViewModel
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Camera Preview with Improved Management
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                if (!isCameraBound && previewView != null) {
                    when (val result = viewModel.bindCameraToPreview(view)) {
                        is KycResult.Success -> {
                            isCameraBound = true
                        }
                        is KycResult.Error -> {
                            // Fallback for emulator/demo
                            view.setBackgroundColor(android.graphics.Color.DKGRAY)
                            view.post {
                                val textView = android.widget.TextView(context)
                                textView.text = "📷 Camera Preview (Demo Mode)\n\nTap here to simulate camera interaction"
                                textView.setTextColor(android.graphics.Color.WHITE)
                                textView.textSize = 18f
                                textView.gravity = android.view.Gravity.CENTER
                                textView.setPadding(32, 32, 32, 32)
                                textView.setOnClickListener {
                                    // Simulate camera working for demo
                                    when (uiState) {
                                        is KycUiState.FaceAlignment -> onFaceAligned()
                                        is KycUiState.LivenessCheck -> onLivenessComplete()
                                        else -> {}
                                    }
                                }
                                view.addView(textView)
                            }
                        }
                        is KycResult.Loading -> {
                            // Handle loading state
                        }
                    }
                }
            }
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
                        text = "Camera Error",
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
            is CameraState.Unavailable -> {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Text(
                        text = "Demo Mode",
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
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Code display
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
                Text(
                    text = "Say the code:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = livenessCode?.code ?: "1234",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Speak clearly into the microphone",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
                    is KycUiState.FaceAlignment -> "Align your face in the frame"
                    is KycUiState.LivenessCheck -> "Say the code on screen"
                    else -> ""
                },
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
        
        // Bottom progress indicator
        if (uiState is KycUiState.LivenessCheck) {
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
                        text = "Recording...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun CapturingContent(
    onCaptureComplete: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(1000) // Simulate capture time
        onCaptureComplete()
    }
    
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
            text = "Capturing...",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
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
                text = "Continue",
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
            text = "Verification Failed",
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
                    text = "Retry",
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
                    text = "Contact Support",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
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
            Text("Camera View Preview")
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
            Text("Liveness Check Preview")
        }
    }
}

// Preview for SelfieKycScreen - Capturing State
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "SelfieKycScreen - Capturing")
@Composable
fun SelfieKycScreenCapturingPreview() {
    MaterialTheme {
        CapturingContent(
            onCaptureComplete = { /* Preview only */ }
        )
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