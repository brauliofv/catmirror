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
    onStartStreaming: (resultCode: Int, intentData: Intent?) -> Unit,
    onStopStreaming: () -> Unit,
    onRegeneratePin: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    BackHandler {
        onNavigateBack()
    }

    // Media projection launcher
    val projectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            onStartStreaming(result.resultCode, result.data)
        } else {
            // Start simulated stream generator so user can preview even without device prompt
            onStartStreaming(0, null)
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
                                try {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CatCoralRed),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Activar en Ajustes de Android", style = MaterialTheme.typography.labelMedium)
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

                    Spacer(modifier = Modifier.height(16.dp))

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
}
