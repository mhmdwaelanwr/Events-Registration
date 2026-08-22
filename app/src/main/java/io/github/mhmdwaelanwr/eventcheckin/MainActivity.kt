package io.github.mhmdwaelanwr.eventcheckin

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
import androidx.compose.foundation.BorderStroke
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
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mhmdwaelanwr.eventcheckin.ui.SettingsScreen
import io.github.mhmdwaelanwr.eventcheckin.ui.theme.EventCheckInTheme
import io.github.mhmdwaelanwr.eventcheckin.viewmodel.AttendanceState
import io.github.mhmdwaelanwr.eventcheckin.viewmodel.AttendanceViewModel
import io.github.mhmdwaelanwr.eventcheckin.viewmodel.AttendanceViewModelFactory
import io.github.mhmdwaelanwr.eventcheckin.viewmodel.DarkModeConfig
import io.github.mhmdwaelanwr.eventcheckin.data.SettingsPreferences
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

            EventCheckInTheme(darkTheme = darkTheme) {
                AttendanceApp(viewModel = viewModel)
            }
        }
    }
}

enum class Screen {
    SCANNING, SETTINGS
}

@Composable
fun AccessKeyDialog(onAuthorized: (isMaster: Boolean, keyUsed: String) -> Unit) {
    var key by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 32.dp)
    ) {
        Spacer(Modifier.height(52.dp))
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Color(0xFFDDEFFC), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
                Icon(
                    Icons.Default.VpnKey,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = Color(0xFF0078D4)
                )
        }

        Spacer(Modifier.height(36.dp))
        Text(
            "Staff access",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter the event access key to open the check-in scanner.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 326.dp)
        )

        Spacer(Modifier.height(52.dp))

                OutlinedTextField(
                    value = key,
                    onValueChange = { 
                        key = it
                        isError = false 
                    },
                    label = { Text("Access key") },
                    placeholder = { Text("Enter access key") },
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
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                    shape = RoundedCornerShape(6.dp)
                )

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            isError = false
                            
                            val masterKey = SecurityManager.getConfig(context, "APP_ACCESS_KEY")
                            val remoteUrl = SecurityManager.getConfig(context, "REMOTE_CONFIG_URL")
                            
                            var isMasterUsed = false
                            var isAuthorizedSuccess = false
                            
                            // 1. Check against Master Key first (Provides permanent immunity)
                            if (key == masterKey) {
                                isAuthorizedSuccess = true
                                isMasterUsed = true
                            } else {
                                // 2. If not master, check Remote Config via Pastebin
                                try {
                                    require(remoteUrl.startsWith("https://"))
                                    val remoteKey = withContext(Dispatchers.IO) {
                                        URL(remoteUrl).readText().trim()
                                    }
                                    if (key == remoteKey) {
                                        isAuthorizedSuccess = true
                                        isMasterUsed = false
                                    }
                                } catch (e: Exception) {
                                    Log.e("Auth", "Remote config fetch failed", e)
                                    // If remote is unreachable, we don't authorize a regular key for the first time.
                                }
                            }
                            
                            if (isAuthorizedSuccess) {
                                onAuthorized(isMasterUsed, key)
                            } else {
                                isError = true
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(6.dp),
                    enabled = !isLoading && key.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Verify and continue")
                    }
                }

        Spacer(Modifier.weight(1f))
        Text(
            "Authorized event staff only",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 40.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceApp(viewModel: AttendanceViewModel) {
    val context = LocalContext.current
    var isAuthorized by remember { mutableStateOf(SecurityManager.isAuthorized(context)) }

    // Kill Switch Logic: Every time the app opens, verify remote key consistency
    // EXCEPT if the user is authorized via the Master Key.
    LaunchedEffect(Unit) {
        if (isAuthorized && !SecurityManager.isMasterKeyUsed(context)) {
            val remoteUrl = SecurityManager.getConfig(context, "REMOTE_CONFIG_URL")
            val lastKeyUsed = SecurityManager.getLastKey(context)
            try {
                val remoteKey = withContext(Dispatchers.IO) {
                    URL(remoteUrl).readText().trim()
                }
                if (remoteKey.isNotBlank() && lastKeyUsed != remoteKey) {
                    // Remote key changed! Kick user out and force re-verification
                    SecurityManager.setAuthorized(context, false)
                    isAuthorized = false
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Access key expired. Please re-verify.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                // If offline or server error, allow staying authorized since they passed once before.
                Log.e("Security", "Could not verify remote key, staying authorized offline")
            }
        }
    }

    if (!isAuthorized) {
        AccessKeyDialog(onAuthorized = { isMaster, keyUsed ->
            SecurityManager.setAuthorized(context, true, isMaster, keyUsed)
            isAuthorized = true
        })
    }

    if (isAuthorized) {
        val uiState by viewModel.uiState.collectAsState()
        val settingsState by viewModel.settingsState.collectAsState()
        var currentScreen by remember { mutableStateOf(Screen.SCANNING) }

        LaunchedEffect(Unit) {
            viewModel.retryPendingCheckIns()
        }

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
                onBack = { currentScreen = Screen.SCANNING },
                onLock = {
                    SecurityManager.setAuthorized(context, false)
                    isAuthorized = false
                }
            )
        } else if (uiState is AttendanceState.Success) {
            SuccessScreen(
                state = uiState as AttendanceState.Success,
                onScanNext = viewModel::resetState
            )
        } else {
            if (hasCameraPermission) {
                ScanningScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    hapticEnabled = settingsState.hapticEnabled,
                    onSettings = { currentScreen = Screen.SETTINGS }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Camera permission is required to scan QR codes.")
                }
            }

            when (val state = uiState) {
                is AttendanceState.Success -> Unit
                is AttendanceState.AlreadyRegistered -> {
                    ResultDialog(
                        type = ResultType.ALREADY_REGISTERED,
                        message = "${state.message}\nID: ${state.registrationId}",
                        onDismiss = { viewModel.resetState() }
                    )
                }
                is AttendanceState.PendingSync -> {
                    ResultDialog(
                        type = ResultType.PENDING_SYNC,
                        message = "Saved securely for automatic sync.\nID: ${state.registrationId}\nPending: ${state.pendingCount}",
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

@Composable
fun SuccessScreen(
    state: AttendanceState.Success,
    onScanNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 32.dp)
    ) {
        Spacer(Modifier.height(56.dp))
        Box(
            modifier = Modifier.size(72.dp).background(Color(0xFFDFF6DD), RoundedCornerShape(36.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF107C34), modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(36.dp))
        Text("CHECK-IN COMPLETE", color = Color(0xFF107C34), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Attendance confirmed",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "The attendee is now marked as present.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(36.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Registration ID", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text(state.registrationId, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                HorizontalDivider(Modifier.padding(vertical = 18.dp))
                Text("Checked in  •  Just now", color = Color(0xFF147A57), fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onScanNext,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text("Scan next attendee", fontWeight = FontWeight.SemiBold)
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun ScanningScreen(
    viewModel: AttendanceViewModel,
    uiState: AttendanceState,
    hapticEnabled: Boolean,
    onSettings: () -> Unit
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

        ScannerOverlay(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 216.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .background(Color(0xF5202020))
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text("EVENT CHECK-IN", color = Color(0xFF60BAFF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("Scan attendee QR", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        }

        IconButton(
            onClick = onSettings,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 12.dp)
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
        }

        FloatingActionButton(
            onClick = { torchEnabled = !torchEnabled },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 176.dp, end = 24.dp)
                .size(44.dp),
            containerColor = if (torchEnabled) Color(0xFFFFD700) else Color.White,
            contentColor = if (torchEnabled) Color.Black else Color.Gray,
            shape = RoundedCornerShape(12.dp)
        ) {
            val icon = if (torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff
            Icon(imageVector = icon, contentDescription = "Toggle Flash")
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 136.dp)
                .background(
                    color = when (uiState) {
                        is AttendanceState.Idle -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                        is AttendanceState.Loading -> Color(0xFFFFD700).copy(alpha = 0.9f)
                        is AttendanceState.Success -> Color(0xFF4CAF50).copy(alpha = 0.9f)
                        is AttendanceState.AlreadyRegistered -> Color(0xFF2196F3).copy(alpha = 0.9f)
                        is AttendanceState.PendingSync -> Color(0xFFFFB900).copy(alpha = 0.95f)
                        is AttendanceState.Error -> Color(0xFFF44336).copy(alpha = 0.9f)
                    },
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 24.dp, vertical = 10.dp)
        ) {
            @Suppress("UNUSED_VARIABLE")
            Text(
                text = when (uiState) {
                    is AttendanceState.Idle -> "Ready to scan"
                    is AttendanceState.Loading -> "Verifying..."
                    is AttendanceState.Success -> "Verified!"
                    is AttendanceState.AlreadyRegistered -> "Registered Before"
                    is AttendanceState.PendingSync -> "Saved for sync"
                    is AttendanceState.Error -> "Error"
                },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Text(
            "Hold the code inside the frame",
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 506.dp)
        )

        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Manual check-in",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
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
                        shape = RoundedCornerShape(6.dp)
                    )
                    Button(
                        onClick = {
                            if (manualId.isNotBlank()) {
                                viewModel.markAttendance(manualId)
                                manualId = ""
                            }
                        },
                        enabled = uiState is AttendanceState.Idle,
                        shape = RoundedCornerShape(6.dp),
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
            .size(264.dp)
            .background(Color.Transparent)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 4.dp.toPx()
            val cornerLength = 30.dp.toPx()
            val color = Color(0xFF60BAFF)

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
    SUCCESS, ERROR, ALREADY_REGISTERED, PENDING_SYNC
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
        ResultType.PENDING_SYNC -> Color(0xFFFFF4CE)
    }

    val iconColor = when(type) {
        ResultType.SUCCESS -> Color(0xFF2E7D32)
        ResultType.ERROR -> Color(0xFFC62828)
        ResultType.ALREADY_REGISTERED -> Color(0xFF1565C0)
        ResultType.PENDING_SYNC -> Color(0xFF8A5700)
    }

    val icon = when(type) {
        ResultType.SUCCESS -> Icons.Default.CheckCircle
        ResultType.ERROR -> Icons.Default.Warning
        ResultType.ALREADY_REGISTERED -> Icons.Default.Info
        ResultType.PENDING_SYNC -> Icons.Default.Info
    }

    val title = when(type) {
        ResultType.SUCCESS -> "Attendance confirmed"
        ResultType.ERROR -> "Check-in failed"
        ResultType.ALREADY_REGISTERED -> "Already checked in"
        ResultType.PENDING_SYNC -> "Saved for sync"
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
