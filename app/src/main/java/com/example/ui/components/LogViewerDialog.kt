package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.NetworkInfo
import com.example.ui.theme.CatAmberPrimary
import com.example.ui.theme.CatCoralRed
import com.example.ui.theme.CatCyanSecondary
import com.example.ui.theme.CatNeonGreen
import com.example.ui.theme.CatSlateCard
import com.example.ui.theme.CatSlateSurface
import com.example.ui.theme.CatTextMuted
import com.example.ui.theme.CatTextSecondary
import com.example.util.AppLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerBottomSheet(
    networkInfo: NetworkInfo,
    isAccessibilityEnabled: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val logs by AppLogger.logs.collectAsStateWithLifecycle()
    var filterLevel by remember { mutableStateOf<String?>(null) } // null = All, "ERROR", "WARN", "INFO"
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val filteredLogs = remember(logs, filterLevel) {
        if (filterLevel == null) logs else logs.filter { it.level == filterLevel }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F151F),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = CatCyanSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Registro de Diagnósticos & Errores",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "Copia este reporte para compartir los errores",
                            style = MaterialTheme.typography.labelSmall.copy(color = CatTextMuted)
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // System specs card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = CatSlateCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, CatCyanSecondary.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                        style = MaterialTheme.typography.labelSmall.copy(color = CatCyanSecondary, fontFamily = FontFamily.Monospace)
                    )
                    Text(
                        text = "IP: ${networkInfo.ipAddress} (${networkInfo.ssid}) | Accesibilidad: ${if (isAccessibilityEnabled) "Activa" else "Inactiva"}",
                        style = MaterialTheme.typography.labelSmall.copy(color = CatTextSecondary, fontFamily = FontFamily.Monospace)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action row: Filter chips + Copy + Clear
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = filterLevel == null,
                        onClick = { filterLevel = null },
                        label = { Text("Todos (${logs.size})", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CatCyanSecondary,
                            selectedLabelColor = Color.Black
                        )
                    )
                    FilterChip(
                        selected = filterLevel == "ERROR",
                        onClick = { filterLevel = if (filterLevel == "ERROR") null else "ERROR" },
                        label = {
                            Text("Errores (${logs.count { it.level == "ERROR" }})", fontSize = 11.sp)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CatCoralRed,
                            selectedLabelColor = Color.White
                        )
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { AppLogger.clear() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Limpiar", tint = CatTextMuted, modifier = Modifier.size(18.dp))
                    }

                    Button(
                        onClick = {
                            val report = buildString {
                                appendLine("=== INFORME DE DIAGNÓSTICO CATMIRROR ===")
                                appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}")
                                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                                appendLine("IP: ${networkInfo.ipAddress} (${networkInfo.ssid})")
                                appendLine("Accesibilidad: ${if (isAccessibilityEnabled) "Activa" else "Inactiva"}")
                                appendLine("========================================")
                                appendLine(AppLogger.getAllLogsFormatted())
                            }

                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("catmirror_logs", report))
                            Toast.makeText(context, "Registro copiado al portapapeles", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CatAmberPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("copy_logs_button")
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copiar", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Log entries list
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay registros registrados en este momento.",
                        style = MaterialTheme.typography.bodySmall.copy(color = CatTextMuted)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF090D14), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { log ->
                        val levelColor = when (log.level) {
                            "ERROR" -> CatCoralRed
                            "WARN" -> CatAmberPrimary
                            else -> CatCyanSecondary
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(0.5.dp, levelColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .padding(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "[${log.timestamp}] [${log.tag}]",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = CatTextMuted
                                    )
                                )
                                Text(
                                    text = log.level,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = levelColor
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = log.message,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
