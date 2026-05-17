package anwar.mlsa.eventsregistration

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import anwar.mlsa.eventsregistration.ui.SettingsScreen
import anwar.mlsa.eventsregistration.ui.theme.MLSAEgyptEventsRegistrationTheme
import anwar.mlsa.eventsregistration.viewmodel.AttendanceState
import anwar.mlsa.eventsregistration.viewmodel.AttendanceViewModel
import anwar.mlsa.eventsregistration.viewmodel.AttendanceViewModelFactory
import anwar.mlsa.eventsregistration.viewmodel.DarkModeConfig
import anwar.mlsa.eventsregistration.data.SettingsPreferences
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        SecurityManager.seedConfigIfNeeded(applicationContext)
        
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val settingsPreferences = remember {
                SettingsPreferences.from(context.applicationContext)
            }
            val viewModel: AttendanceViewModel = viewModel(
                factory = AttendanceViewModelFactory(application, settingsPreferences)
            )
            val settingsState by viewModel.settingsState.collectAsState()

            val darkTheme = when (settingsState.darkMode) {
                DarkModeConfig.SYSTEM -> isSystemInDarkTheme()
                DarkModeConfig.LIGHT -> false
                DarkModeConfig.DARK -> true
            }

            MLSAEgyptEventsRegistrationTheme(darkTheme = darkTheme) {
                AttendanceApp(viewModel = viewModel)
            }
        }
    }
}

enum class Screen {
    SCANNING, SETTINGS
}

@Composable
fun AccessKeyDialog(onAuthorized: () -> Unit) {
    var key by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.VpnKey,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    "Security Verification",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    "Please enter your Access key",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = key,
                    onValueChange = { 
                        key = it
                        isError = false 
                    },
                    label = { Text("Access Key") },
                    singleLine = true,
                    isError = isError,
                    enabled = !isLoading,
                    trailingIcon = {
                        if (key.isEmpty()) {
                            IconButton(onClick = {
                                val clipData = clipboardManager.primaryClip
                                if (clipData != null && clipData.itemCount > 0) {
                                    val textToPaste = clipData.getItemAt(0).text?.toString() ?: ""
                                    if (textToPaste.isNotBlank()) {
                                        key = textToPaste
                                        isError = false
                                    }
                                }
                            }) {
                                Icon(imageVector = Icons.Filled.ContentPaste, contentDescription = "Paste")
                            }
                        } else {
                            IconButton(onClick = {
                                key = ""
                                isError = false
                            }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    supportingText = {
                        if (isError) {
                            Text("Incorrect access key. Please try again.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            isError = false
                            
                            val masterKey = SecurityManager.getConfig(context, "APP_ACCESS_KEY")
                            val remoteUrl = SecurityManager.getConfig(context, "REMOTE_CONFIG_URL")
                            
                            var isAuthorizedSuccess = false
                            
                            // 1. Try checking against Remote Config first
                            try {
                                val remoteKey = withContext(Dispatchers.IO) {
                                    URL(remoteUrl).readText().trim()
                                }
                                if (key == remoteKey) {
                                    isAuthorizedSuccess = true
                                }
                            } catch (e: Exception) {
                                Log.e("Auth", "Remote config fetch failed", e)
                                // If remote fails, we skip to master key check below
                            }
                            
                            // 2. Check against Master Key (Always works as fallback or bypass)
                            if (!isAuthorizedSuccess && key == masterKey) {
                                isAuthorizedSuccess = true
                            }
                            
                            if (isAuthorizedSuccess) {
                                onAuthorized()
                            } else {
                                isError = true
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading && key.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Verify & Enter")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceApp(viewModel: AttendanceViewModel) {
    val context = LocalContext.current
    var isAuthorized by remember { mutableStateOf(SecurityManager.isAuthorized(context)) }

    if (!isAuthorized) {
        AccessKeyDialog(onAuthorized = {
            SecurityManager.setAuthorized(context, true)
            isAuthorized = true
        })
    }

    if (isAuthorized) {
        val uiState by viewModel.uiState.collectAsState()
        val settingsState by viewModel.settingsState.collectAsState()
        val isSystemDark = isSystemInDarkTheme()
        val isDarkTheme = when (settingsState.darkMode) {
            DarkModeConfig.SYSTEM -> isSystemDark
            DarkModeConfig.LIGHT -> false
            DarkModeConfig.DARK -> true
        }
        var currentScreen by remember { mutableStateOf(Screen.SCANNING) }

        var hasCameraPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            )
        }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted ->
                hasCameraPermission = granted
            }
        )

        LaunchedEffect(Unit) {
            if (!hasCameraPermission) {
                launcher.launch(Manifest.permission.CAMERA)
            }
        }

        if (currentScreen == Screen.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                settingsState = settingsState,
                onBack = { currentScreen = Screen.SCANNING }
            )
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("MLSA Egypt Attendance") },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        actions = {
                            IconButton(onClick = {
                                val newMode = if (isDarkTheme) {
                                    DarkModeConfig.LIGHT
                                } else {
                                    DarkModeConfig.DARK
                                }
                                viewModel.updateDarkMode(newMode)
                            }) {
                                Icon(
                                    imageVector = when (settingsState.darkMode) {
                                        DarkModeConfig.DARK -> Icons.Filled.DarkMode
                                        DarkModeConfig.LIGHT -> Icons.Filled.LightMode
                                        DarkModeConfig.SYSTEM -> if (isSystemDark) {
                                            Icons.Filled.DarkMode
                                        } else {
                                            Icons.Filled.LightMode
                                        }
                                    },
                                    contentDescription = "Toggle Theme"
                                )
                            }
                            IconButton(onClick = { currentScreen = Screen.SETTINGS }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (hasCameraPermission) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            ScanningScreen(
                                viewModel = viewModel,
                                uiState = uiState,
                                hapticEnabled = settingsState.hapticEnabled
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Camera permission is required to scan QR codes.")
                        }
                    }
                }
            }

            when (val state = uiState) {
                is AttendanceState.Success -> {
                    ResultDialog(
                        type = ResultType.SUCCESS,
                        message = "Success: ${state.message}\nID: ${state.registrationId}",
                        onDismiss = { viewModel.resetState() }
                    )
                }
                is AttendanceState.AlreadyRegistered -> {
                    ResultDialog(
                        type = ResultType.ALREADY_REGISTERED,
                        message = "${state.message}\nID: ${state.registrationId}",
                        onDismiss = { viewModel.resetState() }
                    )
                }
                is AttendanceState.Error -> {
                    ResultDialog(
                        type = ResultType.ERROR,
                        message = state.message,
                        onDismiss = { viewModel.resetState() }
                    )
                }
                else -> {}
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun ScanningScreen(
    viewModel: AttendanceViewModel,
    uiState: AttendanceState,
    hapticEnabled: Boolean
) {
    var torchEnabled by remember { mutableStateOf(false) }
    var manualId by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(uiState) {
        if (hapticEnabled && uiState !is AttendanceState.Idle && uiState !is AttendanceState.Loading) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            torchEnabled = torchEnabled,
            onBarcodeDetected = { code ->
                if (uiState is AttendanceState.Idle && code.isNotBlank()) {
                    viewModel.markAttendance(code)
                }
            }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        )

        ScannerOverlay(modifier = Modifier.align(Alignment.Center))

        FloatingActionButton(
            onClick = { torchEnabled = !torchEnabled },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .size(50.dp),
            containerColor = if (torchEnabled) Color(0xFFFFD700) else Color.White,
            contentColor = if (torchEnabled) Color.Black else Color.Gray
        ) {
            val icon = if (torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff
            Icon(imageVector = icon, contentDescription = "Toggle Flash")
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
                .background(
                    color = when (uiState) {
                        is AttendanceState.Idle -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                        is AttendanceState.Loading -> Color(0xFFFFD700).copy(alpha = 0.9f)
                        is AttendanceState.Success -> Color(0xFF4CAF50).copy(alpha = 0.9f)
                        is AttendanceState.AlreadyRegistered -> Color(0xFF2196F3).copy(alpha = 0.9f)
                        is AttendanceState.Error -> Color(0xFFF44336).copy(alpha = 0.9f)
                    },
                    shape = RoundedCornerShape(30.dp)
                )
                .padding(horizontal = 24.dp, vertical = 10.dp)
        ) {
            Text(
                text = when (uiState) {
                    is AttendanceState.Idle -> "Scan QR Code"
                    is AttendanceState.Loading -> "Verifying..."
                    is AttendanceState.Success -> "Verified!"
                    is AttendanceState.AlreadyRegistered -> "Registered Before"
                    is AttendanceState.Error -> "Error"
                },
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Or enter ID manually",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualId,
                        onValueChange = { manualId = it },
                        placeholder = { Text("Registration ID") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = {
                            if (manualId.isNotBlank()) {
                                viewModel.markAttendance(manualId)
                                manualId = ""
                            }
                        },
                        enabled = uiState is AttendanceState.Idle,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                    }
                }
            }
        }
        
        if (uiState is AttendanceState.Loading) {
             Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.6f)), contentAlignment = Alignment.Center) {
                 CircularProgressIndicator(color = Color.White)
             }
        }
    }
}

@Composable
fun ScannerOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    
    val laserOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 250.dp.value,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "laser"
    )

    Box(
        modifier = modifier
            .size(250.dp)
            .background(Color.Transparent)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 4.dp.toPx()
            val cornerLength = 30.dp.toPx()
            val color = Color(0xFF00BCF2)

            drawPath(
                path = Path().apply {
                    moveTo(0f, cornerLength)
                    lineTo(0f, 0f)
                    lineTo(cornerLength, 0f)
                },
                color = color,
                style = Stroke(width = strokeWidth)
            )
            drawPath(
                path = Path().apply {
                    moveTo(size.width - cornerLength, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, cornerLength)
                },
                color = color,
                style = Stroke(width = strokeWidth)
            )
            drawPath(
                path = Path().apply {
                    moveTo(0f, size.height - cornerLength)
                    lineTo(0f, size.height)
                    lineTo(cornerLength, size.height)
                },
                color = color,
                style = Stroke(width = strokeWidth)
            )
            drawPath(
                path = Path().apply {
                    moveTo(size.width - cornerLength, size.height)
                    lineTo(size.width, size.height)
                    lineTo(size.width, size.height - cornerLength)
                },
                color = color,
                style = Stroke(width = strokeWidth)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.TopCenter)
                .offset(y = laserOffset.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF00BCF2), Color.Transparent)
                    )
                )
        )
    }
}

enum class ResultType {
    SUCCESS, ERROR, ALREADY_REGISTERED
}

@Composable
fun ResultDialog(
    type: ResultType,
    message: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    if (type != ResultType.ERROR) {
        LaunchedEffect(Unit) {
            delay(2500)
            onDismiss()
        }
    }

    val backgroundColor = when(type) {
        ResultType.SUCCESS -> Color(0xFFE8F5E9)
        ResultType.ERROR -> Color(0xFFFFEBEE)
        ResultType.ALREADY_REGISTERED -> Color(0xFFE3F2FD)
    }

    val iconColor = when(type) {
        ResultType.SUCCESS -> Color(0xFF2E7D32)
        ResultType.ERROR -> Color(0xFFC62828)
        ResultType.ALREADY_REGISTERED -> Color(0xFF1565C0)
    }

    val icon = when(type) {
        ResultType.SUCCESS -> Icons.Default.CheckCircle
        ResultType.ERROR -> Icons.Default.Warning
        ResultType.ALREADY_REGISTERED -> Icons.Default.Info
    }

    val title = when(type) {
        ResultType.SUCCESS -> "Success!"
        ResultType.ERROR -> "Failed!"
        ResultType.ALREADY_REGISTERED -> "Attention"
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = iconColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                if (type == ResultType.ERROR) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = "mailto:".toUri()
                                    putExtra(Intent.EXTRA_EMAIL, arrayOf("moanwarpcz@gmail.com"))
                                    putExtra(Intent.EXTRA_SUBJECT, "EventSync Error Report")
                                    putExtra(Intent.EXTRA_TEXT, "Error Details:\n$message")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) { }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                        ) {
                            Text("Email")
                        }

                        Button(
                            onClick = {
                                val url = "https://api.whatsapp.com/send?phone=+201010373387&text=${Uri.encode("Error Report:\n$message")}"
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = url.toUri()
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) { }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                        ) {
                            Text("WhatsApp")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("Dismiss", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun CameraPreview(
    torchEnabled: Boolean,
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    LaunchedEffect(torchEnabled, camera) {
        camera?.cameraControl?.enableTorch(torchEnabled)
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val executor = Executors.newSingleThreadExecutor()
            
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(executor, QrCodeAnalyzer { result ->
                            onBarcodeDetected(result)
                        })
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (exc: Exception) {
                    Log.e("CameraPreview", "Use case binding failed", exc)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

class QrCodeAnalyzer(
    private val onQrCodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        barcode.rawValue?.let { value ->
                             onQrCodeDetected(value)
                        }
                    }
                }
                .addOnFailureListener { }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
