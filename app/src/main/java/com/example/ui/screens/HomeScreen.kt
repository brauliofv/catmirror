package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppScreen
import com.example.model.NetworkInfo
import com.example.ui.components.CatMirrorHeader
import com.example.ui.components.LogViewerBottomSheet
import com.example.ui.theme.CatAmberDark
import com.example.ui.theme.CatAmberPrimary
import com.example.ui.theme.CatCoralRed
import com.example.ui.theme.CatCyanSecondary
import com.example.ui.theme.CatNeonGreen
import com.example.ui.theme.CatSlateCard
import com.example.ui.theme.CatSlateSurface
import com.example.ui.theme.CatTextMuted
import com.example.ui.theme.CatTextSecondary

@Composable
fun HomeScreen(
    networkInfo: NetworkInfo,
    isAccessibilityEnabled: Boolean,
    isEmitterActive: Boolean,
    onNavigate: (AppScreen) -> Unit,
    onRefreshNetwork: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showLogViewer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onRefreshNetwork()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
    ) {
        CatMirrorHeader(networkInfo = networkInfo)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // Network Diagnostics Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("network_diagnostics_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CatSlateSurface),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (networkInfo.isWifiConnected || networkInfo.isUsbTethered)
                        CatCyanSecondary.copy(alpha = 0.35f)
                    else
                        CatCoralRed.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(CatCyanSecondary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (networkInfo.isUsbTethered) Icons.Default.Cable else Icons.Default.Wifi,
                                contentDescription = "Red",
                                tint = CatCyanSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = networkInfo.ssid,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "IP Local: ${networkInfo.ipAddress}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = CatTextSecondary
                                )
                            )
                        }
                    }

                    Row {
                        IconButton(
                            onClick = { showLogViewer = true },
                            modifier = Modifier.testTag("open_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Registro de errores",
                                tint = CatAmberPrimary
                            )
                        }
                        IconButton(
                            onClick = onRefreshNetwork,
                            modifier = Modifier.testTag("refresh_network_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Actualizar red",
                                tint = CatCyanSecondary
                            )
                        }
                    }
                }
            }

            // Accessibility Service Banner
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        try {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } catch (_: Exception) {}
                    }
                    .testTag("accessibility_banner"),
                color = if (isAccessibilityEnabled) CatSlateCard else Color(0xFF2C1919),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isAccessibilityEnabled) CatNeonGreen.copy(alpha = 0.4f) else CatCoralRed.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isAccessibilityEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = "Estado accesibilidad",
                            tint = if (isAccessibilityEnabled) CatNeonGreen else CatCoralRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isAccessibilityEnabled) "Control táctil habilitado" else "Control táctil deshabilitado",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isAccessibilityEnabled) CatNeonGreen else Color(0xFFFF8A80)
                                )
                            )
                            Text(
                                text = if (isAccessibilityEnabled) "Listo para inyectar toques remotos" else "Toca aquí para activar catmirror en Ajustes",
                                style = MaterialTheme.typography.labelSmall.copy(color = CatTextSecondary)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configurar accesibilidad",
                        tint = CatTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "SELECCIONA EL ROL DE ESTE DISPOSITIVO",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = CatTextMuted,
                    letterSpacing = 1.2.sp
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Role Card 1: Emitter (Pantalla Emisor)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { onNavigate(AppScreen.EMITTER) }
                    .testTag("emitter_mode_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CatSlateSurface),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (isEmitterActive) CatNeonGreen else CatAmberPrimary.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(CatAmberPrimary, CatAmberDark)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cast,
                                contentDescription = "Pantalla Emisor",
                                tint = Color.Black,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Pantalla Emisor",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                if (isEmitterActive) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = CatNeonGreen.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "ACTIVO",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = CatNeonGreen
                                            )
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Transmite la pantalla de este teléfono en tiempo real y recibe toques.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = CatTextSecondary,
                                    lineHeight = 16.sp
                                )
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Ir a modo emisor",
                        tint = CatAmberPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Role Card 2: Receiver (Pantalla Receptor)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { onNavigate(AppScreen.RECEIVER) }
                    .testTag("receiver_mode_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CatSlateSurface),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, CatCyanSecondary.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(CatCyanSecondary, Color(0xFF00838F))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = "Pantalla Receptor",
                                tint = Color.Black,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = "Pantalla Receptor",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Conéctate a otro dispositivo, visualiza su pantalla y haz tap en sus apps.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = CatTextSecondary,
                                    lineHeight = 16.sp
                                )
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Ir a modo receptor",
                        tint = CatCyanSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Instructions / Features Footer
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CatSlateCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CARACTERÍSTICAS CATMIRROR",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = CatAmberPrimary,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Wi-Fi Local & USB: Conecta en la misma red Wi-Fi o mediante cable USB con baja latencia.",
                        style = MaterialTheme.typography.bodySmall.copy(color = CatTextSecondary)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Control Táctil Completo: Toca, arrastra y usa atajos (Atrás, Inicio, Recientes) desde el receptor.",
                        style = MaterialTheme.typography.bodySmall.copy(color = CatTextSecondary)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Detección Automática & PIN: Emparejamiento instantáneo mediante código de 6 dígitos o QR.",
                        style = MaterialTheme.typography.bodySmall.copy(color = CatTextSecondary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showLogViewer) {
        LogViewerBottomSheet(
            networkInfo = networkInfo,
            isAccessibilityEnabled = isAccessibilityEnabled,
            onDismiss = { showLogViewer = false }
        )
    }
}
