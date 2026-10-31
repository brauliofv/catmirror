package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AppScreen
import com.example.ui.screens.EmitterScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ReceiverScreen
import com.example.ui.screens.RemoteStreamScreen
import com.example.ui.theme.CatMirrorTheme
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.util.AppLogger.initCrashHandler(applicationContext)
        enableEdgeToEdge()
        setContent {
            CatMirrorTheme(darkTheme = true) {
                CatMirrorApp()
            }
        }
    }
}

@Composable
fun CatMirrorApp(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val networkInfo by viewModel.networkInfo.collectAsStateWithLifecycle()
    val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsStateWithLifecycle()

    // Request notification permission on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        viewModel.refreshNetwork(context)
    }

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        when (currentScreen) {
            AppScreen.HOME -> {
                val isEmitterActive by viewModel.isEmitterStreaming.collectAsStateWithLifecycle()
                HomeScreen(
                    networkInfo = networkInfo,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    isEmitterActive = isEmitterActive,
                    onNavigate = { viewModel.navigateTo(it) },
                    onRefreshNetwork = { viewModel.refreshNetwork(context) },
                    modifier = Modifier
                        .statusBarsPadding()
                        .navigationBarsPadding()
                )
            }

            AppScreen.EMITTER -> {
                val pin by viewModel.emitterPin.collectAsStateWithLifecycle()
                val isStreaming by viewModel.isEmitterStreaming.collectAsStateWithLifecycle()
                val connectedClient by viewModel.emitterConnectedClient.collectAsStateWithLifecycle()
                val stats by viewModel.emitterStats.collectAsStateWithLifecycle()
                val isScreenDimmed by viewModel.isScreenDimmed.collectAsStateWithLifecycle()
                val isRemoteControlConfirmed by viewModel.isRemoteControlConfirmed.collectAsStateWithLifecycle()

                EmitterScreen(
                    networkInfo = networkInfo,
                    pin = pin,
                    isStreaming = isStreaming,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    connectedClient = connectedClient,
                    stats = stats,
                    isScreenDimmed = isScreenDimmed,
                    isRemoteControlConfirmed = isRemoteControlConfirmed,
                    onToggleScreenDim = { viewModel.toggleScreenDim() },
                    onConfirmRemoteControl = { viewModel.confirmRemoteControl() },
                    onStartStreaming = { resultCode, intentData ->
                        viewModel.startEmitter(context, resultCode, intentData)
                    },
                    onStopStreaming = {
                        viewModel.stopEmitter(context)
                    },
                    onRegeneratePin = {
                        viewModel.regeneratePin()
                    },
                    onNavigateBack = {
                        viewModel.navigateTo(AppScreen.HOME)
                    },
                    modifier = Modifier
                        .statusBarsPadding()
                        .navigationBarsPadding()
                )
            }

            AppScreen.RECEIVER -> {
                val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
                val manualIp by viewModel.manualIp.collectAsStateWithLifecycle()
                val manualPin by viewModel.manualPin.collectAsStateWithLifecycle()

                ReceiverScreen(
                    networkInfo = networkInfo,
                    discoveredDevices = discoveredDevices,
                    manualIp = manualIp,
                    manualPin = manualPin,
                    onUpdateManualIp = { viewModel.updateManualIp(it) },
                    onUpdateManualPin = { viewModel.updateManualPin(it) },
                    onConnect = { ip, port, pin ->
                        viewModel.connectToDevice(ip, port, pin)
                    },
                    onNavigateBack = {
                        viewModel.navigateTo(AppScreen.HOME)
                    },
                    modifier = Modifier
                        .statusBarsPadding()
                        .navigationBarsPadding()
                )
            }

            AppScreen.REMOTE_VIEW -> {
                val status by viewModel.receiverStatus.collectAsStateWithLifecycle()
                val frame by viewModel.receiverFrame.collectAsStateWithLifecycle()
                val stats by viewModel.receiverStats.collectAsStateWithLifecycle()
                val isFitScreen by viewModel.fitScreen.collectAsStateWithLifecycle()

                RemoteStreamScreen(
                    status = status,
                    frame = frame,
                    stats = stats,
                    isFitScreen = isFitScreen,
                    onTouch = { action, xRatio, yRatio ->
                        viewModel.sendTouch(action, xRatio, yRatio)
                    },
                    onGlobalAction = { actionCode ->
                        viewModel.sendGlobalAction(actionCode)
                    },
                    onToggleFit = {
                        viewModel.toggleFitScreen()
                    },
                    onDisconnect = {
                        viewModel.disconnectReceiver()
                    }
                )
            }
        }
    }
}
