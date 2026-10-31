package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NetworkInfo
import com.example.model.StreamStats
import com.example.ui.components.AccessibilityGuideDialog
import com.example.ui.components.LogViewerBottomSheet
import com.example.ui.components.PulseLiveIndicator
import com.example.ui.components.QrCodeCanvas
import com.example.ui.components.StatChip
import com.example.ui.theme.CatAmberPrimary
import com.example.ui.theme.CatCoralRed
import com.example.ui.theme.CatCyanSecondary
import com.example.ui.theme.CatNeonGreen
import com.example.ui.theme.CatSlateCard
import com.example.ui.theme.CatSlateSurface
import com.example.ui.theme.CatTextMuted
import com.example.ui.theme.CatTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmitterScreen(
    networkInfo: NetworkInfo,
    pin: String,
    isStreaming: Boolean,
    isAccessibilityEnabled: Boolean,
    connectedClient: String?,
    stats: StreamStats,
    isScreenDimmed: Boolean = false,
    isRemoteControlConfirmed: Boolean = false,
    onToggleScreenDim: () -> Unit = {},
    onConfirmRemoteControl: () -> Unit = {},
    onStartStreaming: (resultCode: Int, intentData: Intent?) -> Unit,
    onStopStreaming: () -> Unit,
    onRegeneratePin: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showAccessibilityGuideDialog by remember { mutableStateOf(false) }
    var showLogViewer by remember { mutableStateOf(false) }

    BackHandler {
        onNavigateBack()
    }

    val activity = context as? Activity

    // Control de brillo de pantalla para ahorro de batería
    androidx.compose.runtime.LaunchedEffect(isScreenDimmed) {
        activity?.let { act ->
            val lp = act.window.attributes
            if (isScreenDimmed) {
                lp.screenBrightness = 0.01f // Brillo al mínimo para no agotar la batería
            } else {
                lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            act.window.attributes = lp
        }
    }

    // Mantener la pantalla encendida mientras se transmite o espera para evitar bloqueo y pérdida de conexión
    androidx.compose.runtime.DisposableEffect(Unit) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.let { act ->
                val lp = act.window.attributes
                lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                act.window.attributes = lp
            }
        }
    }

    // Media projection launcher
    val projectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            onStartStreaming(result.resultCode, result.data)
        } else {
            com.example.util.AppLogger.w("EmitterScreen", "Permiso de captura de pantalla cancelado por el usuario o denegado por el sistema")
            android.widget.Toast.makeText(context, "Se requiere autorizar la captura de pantalla para transmitir", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Pantalla Emisor",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Transmitiendo esta pantalla",
                            style = MaterialTheme.typography.bodySmall.copy(color = CatAmberPrimary)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("emitter_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showLogViewer = true },
                        modifier = Modifier.testTag("emitter_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Registro de diagnósticos",
                            tint = CatAmberPrimary
                        )
                    }
                    PulseLiveIndicator(
                        isLive = isStreaming,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Accessibility status card
            if (!isAccessibilityEnabled) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2B1919)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CatCoralRed.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Alerta accesibilidad",
                                tint = CatCoralRed,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Permiso de Toque Remoto Requerido",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF8A80)
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Para que el receptor pueda hacer tap y controlar las apps de este teléfono, activa el servicio 'catmirror' en los Ajustes de Accesibilidad.",
                            style = MaterialTheme.typography.bodySmall.copy(color = CatTextSecondary)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                showAccessibilityGuideDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CatCoralRed),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cómo Activar en Android", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Pairing Credentials Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CatSlateSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, CatAmberPrimary.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "VINCULACIÓN DEL RECEPTOR",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = CatTextMuted,
                            letterSpacing = 1.2.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CatSlateCard)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "💡 Pantalla activa (evita suspensión y corte)",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = CatNeonGreen)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // QR Code
                    val pairingPayload = "catmirror://${networkInfo.ipAddress}:8888?pin=$pin"
                    QrCodeCanvas(payload = pairingPayload, size = 180.dp)

                    Spacer(modifier = Modifier.height(16.dp))

                    // PIN Code Display
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "PIN DE SEGURIDAD: ",
                            style = MaterialTheme.typography.labelMedium.copy(color = CatTextSecondary)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CatSlateCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, CatAmberPrimary.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = pin,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = CatAmberPrimary,
                                    letterSpacing = 4.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onRegeneratePin,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Regenerar PIN",
                                tint = CatTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Connection Endpoint details
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CatSlateCard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "DIRECCIÓN DE ENLACE",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = CatTextMuted)
                                )
                                Text(
                                    text = "${networkInfo.ipAddress}:8888",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = CatCyanSecondary
                                    )
                                )
                            }

                            Text(
                                text = if (networkInfo.isUsbTethered) "Cable USB" else "Wi-Fi Local",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = CatTextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Action Button
            if (!isStreaming) {
                Button(
                    onClick = {
                        val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                        if (mpManager != null) {
                            val intent = mpManager.createScreenCaptureIntent()
                            projectionLauncher.launch(intent)
                        } else {
                            onStartStreaming(0, null)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("start_streaming_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CatAmberPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Iniciar transmisión",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Iniciar Transmisión de Pantalla",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    )
                }
            } else {
                Button(
                    onClick = onStopStreaming,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("stop_streaming_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CatCoralRed)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Detener transmisión",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Detener Transmisión",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tarjeta de Verificación de Control Remoto y Ahorro de Batería
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("remote_control_confirmation_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isScreenDimmed) Color(0xFF13201D) else CatSlateSurface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.5.dp,
                    color = if (isScreenDimmed) CatNeonGreen else if (isRemoteControlConfirmed) CatCyanSecondary else CatAmberPrimary.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isScreenDimmed) Icons.Default.CheckCircle else Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = if (isScreenDimmed) CatNeonGreen else CatAmberPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CONTROL REMOTO Y AHORRO",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = if (isScreenDimmed) CatNeonGreen else CatTextSecondary
                                )
                            )
                        }

                        if (isScreenDimmed) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CatNeonGreen.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "1% Brillo",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(color = CatNeonGreen, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (isScreenDimmed) {
                            "✅ Control remoto verificado. La pantalla física está atenuada al mínimo para ahorrar batería, mientras la transmisión se emite con total claridad al receptor."
                        } else if (isRemoteControlConfirmed) {
                            "Control remoto verificado previamente. Puedes activar el modo ahorro en cualquier momento para atenuar la pantalla del dispositivo."
                        } else {
                            "Haz tap en el botón de abajo desde tu computadora o receptor remoto para confirmar el control táctil y activar la pantalla en brillo mínimo (modo ahorro)."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isScreenDimmed) Color(0xFFE2E8F0) else CatTextSecondary,
                            lineHeight = 18.sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (!isScreenDimmed) {
                        Button(
                            onClick = onConfirmRemoteControl,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("confirm_remote_touch_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRemoteControlConfirmed) CatCyanSecondary else CatAmberPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = "Confirmar",
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isRemoteControlConfirmed) "🌙 Activar Modo Ahorro (Atenuar)" else "🎯 Tocar aquí para Confirmar y Atenuar",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onToggleScreenDim,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("restore_brightness_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CatNeonGreen),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CatNeonGreen)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Restaurar",
                                tint = CatNeonGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "☀️ Restaurar Brillo Normal",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = CatNeonGreen
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Live Metrics Dashboard (when streaming)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CatSlateSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ESTADO DE TRANSMISIÓN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = CatTextMuted,
                                letterSpacing = 1.sp
                            )
                        )
                        Text(
                            text = if (connectedClient != null) "Receptor: $connectedClient" else "Esperando receptor…",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (connectedClient != null) CatNeonGreen else CatTextMuted
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatChip(
                            label = "FPS",
                            value = "${stats.fps}",
                            color = CatNeonGreen,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        StatChip(
                            label = "BITRATE",
                            value = "${stats.bitrateKbps} kbps",
                            color = CatCyanSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        StatChip(
                            label = "CLIENTES",
                            value = "${stats.activeClients}",
                            color = CatAmberPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showAccessibilityGuideDialog) {
        AccessibilityGuideDialog(
            onDismiss = { showAccessibilityGuideDialog = false }
        )
    }

    if (showLogViewer) {
        LogViewerBottomSheet(
            networkInfo = networkInfo,
            isAccessibilityEnabled = isAccessibilityEnabled,
            onDismiss = { showLogViewer = false }
        )
    }
}
