package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.model.NetworkInfo
import com.example.ui.components.LogViewerBottomSheet
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionStatus
import com.example.model.StreamStats
import com.example.network.Protocol
import com.example.network.VideoFrame
import com.example.ui.theme.CatAmberPrimary
import com.example.ui.theme.CatCoralRed
import com.example.ui.theme.CatCyanSecondary
import com.example.ui.theme.CatNeonGreen
import com.example.ui.theme.CatSlateCard
import com.example.ui.theme.CatTextMuted
import com.example.ui.theme.CatTextSecondary
import kotlinx.coroutines.delay

@Composable
fun RemoteStreamScreen(
    status: ConnectionStatus,
    frame: VideoFrame?,
    stats: StreamStats,
    isFitScreen: Boolean,
    lastError: String? = null,
    networkInfo: NetworkInfo = NetworkInfo(),
    onTouch: (action: Byte, xRatio: Float, yRatio: Float) -> Unit,
    onGlobalAction: (actionCode: Byte) -> Unit,
    onToggleFit: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onDisconnect()
    }

    var showLogViewer by remember { mutableStateOf(false) }
    // Touch ripple visualization
    var touchFeedbackPos by remember { mutableStateOf<Offset?>(null) }

    // Decode latest frame to bitmap safely
    val currentBitmap = remember(frame?.timestamp) {
        frame?.let {
            try {
                BitmapFactory.decodeByteArray(it.jpegData, 0, it.jpegData.size)
            } catch (_: Exception) {
                null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("remote_stream_screen")
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val containerWidth = maxWidth.value
            val containerHeight = maxHeight.value

            fun mapToRatio(offset: Offset, canvasW: Float, canvasH: Float): Pair<Float, Float>? {
                val bmp = currentBitmap
                val imgW = bmp?.width?.toFloat() ?: frame?.width?.toFloat() ?: canvasW
                val imgH = bmp?.height?.toFloat() ?: frame?.height?.toFloat() ?: canvasH
                if (imgW <= 0f || imgH <= 0f || canvasW <= 0f || canvasH <= 0f) return null

                val (destW, destH, destX, destY) = if (isFitScreen) {
                    val scale = (canvasW / imgW).coerceAtMost(canvasH / imgH)
                    val w = imgW * scale
                    val h = imgH * scale
                    listOf(w, h, (canvasW - w) / 2f, (canvasH - h) / 2f)
                } else {
                    listOf(canvasW, canvasH, 0f, 0f)
                }

                if (offset.x < destX || offset.x > destX + destW || offset.y < destY || offset.y > destY + destH) {
                    return null
                }
                val rx = ((offset.x - destX) / destW).coerceIn(0f, 1f)
                val ry = ((offset.y - destY) / destH).coerceIn(0f, 1f)
                return Pair(rx, ry)
            }

            // Interactive Video Canvas with Touch Injection
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("remote_video_canvas")
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                touchFeedbackPos = offset
                                val coords = mapToRatio(offset, size.width.toFloat(), size.height.toFloat())
                                if (coords != null) {
                                    onTouch(Protocol.TOUCH_DOWN, coords.first, coords.second)
                                    onTouch(Protocol.TOUCH_UP, coords.first, coords.second)
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                touchFeedbackPos = offset
                                val coords = mapToRatio(offset, size.width.toFloat(), size.height.toFloat())
                                if (coords != null) {
                                    onTouch(Protocol.TOUCH_DOWN, coords.first, coords.second)
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                touchFeedbackPos = change.position
                                val coords = mapToRatio(change.position, size.width.toFloat(), size.height.toFloat())
                                if (coords != null) {
                                    onTouch(Protocol.TOUCH_MOVE, coords.first, coords.second)
                                }
                            },
                            onDragEnd = {
                                touchFeedbackPos?.let { pos ->
                                    val coords = mapToRatio(pos, size.width.toFloat(), size.height.toFloat())
                                    if (coords != null) {
                                        onTouch(Protocol.TOUCH_UP, coords.first, coords.second)
                                    }
                                }
                            },
                            onDragCancel = {
                                touchFeedbackPos?.let { pos ->
                                    val coords = mapToRatio(pos, size.width.toFloat(), size.height.toFloat())
                                    if (coords != null) {
                                        onTouch(Protocol.TOUCH_UP, coords.first, coords.second)
                                    }
                                }
                            }
                        )
                    }
            ) {
                val canvasW = size.width
                val canvasH = size.height

                if (currentBitmap != null && !currentBitmap.isRecycled) {
                    val imgW = currentBitmap.width.toFloat()
                    val imgH = currentBitmap.height.toFloat()

                    val (destW, destH, destX, destY) = if (isFitScreen) {
                        val scale = (canvasW / imgW).coerceAtMost(canvasH / imgH)
                        val w = imgW * scale
                        val h = imgH * scale
                        val x = (canvasW - w) / 2f
                        val y = (canvasH - h) / 2f
                        listOf(w, h, x, y)
                    } else {
                        listOf(canvasW, canvasH, 0f, 0f)
                    }

                    drawImage(
                        image = currentBitmap.asImageBitmap(),
                        dstOffset = IntOffset(destX.toInt(), destY.toInt()),
                        dstSize = IntSize(destW.toInt(), destH.toInt())
                    )
                } else {
                    // Placeholder background grid
                    drawRect(color = Color(0xFF0F141C))
                }

                // Render touch circle feedback
                touchFeedbackPos?.let { pos ->
                    drawCircle(
                        color = CatAmberPrimary.copy(alpha = 0.5f),
                        radius = 32f,
                        center = pos
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 12f,
                        center = pos
                    )
                }
            }
        }

        // Auto fade out touch ripple
        LaunchedEffect(touchFeedbackPos) {
            if (touchFeedbackPos != null) {
                delay(300)
                touchFeedbackPos = null
            }
        }

        // Connecting / Error Overlay
        if (status != ConnectionStatus.CONNECTED) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xDD0F141C)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (status == ConnectionStatus.CONNECTING || status == ConnectionStatus.AUTHENTICATING) {
                        CircularProgressIndicator(color = CatCyanSecondary, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = if (status == ConnectionStatus.CONNECTING) "Estableciendo conexión socket…" else "Verificando PIN de seguridad…",
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Conectando al puerto 8888...",
                            style = MaterialTheme.typography.bodySmall.copy(color = CatTextSecondary)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onDisconnect,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF243242)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancelar", color = Color.White)
                        }
                    } else if (status == ConnectionStatus.ERROR) {
                        Text(
                            text = "Error de conexión con el emisor",
                            style = MaterialTheme.typography.titleMedium.copy(color = CatCoralRed, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (!lastError.isNullOrBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x33FF5252),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CatCoralRed.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = lastError,
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                )
                            }
                        } else {
                            Text(
                                text = "Asegúrate de que el emisor esté activo en la misma red y el PIN sea correcto.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = CatTextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onDisconnect,
                                colors = ButtonDefaults.buttonColors(containerColor = CatCyanSecondary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Volver", color = Color.Black, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { showLogViewer = true },
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CatAmberPrimary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = CatAmberPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ver Registro", color = CatAmberPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Floating Bottom Remote Navigation Bar (Back, Home, Recents, Aspect Ratio)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xDD151F2C),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FF9E2C))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Back button
                IconButton(
                    onClick = { onGlobalAction(Protocol.GLOBAL_BACK) },
                    modifier = Modifier.testTag("remote_back_action_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Atrás Remoto",
                        tint = CatAmberPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Home button
                IconButton(
                    onClick = { onGlobalAction(Protocol.GLOBAL_HOME) },
                    modifier = Modifier.testTag("remote_home_action_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Inicio Remoto",
                        tint = CatCyanSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Recents button
                IconButton(
                    onClick = { onGlobalAction(Protocol.GLOBAL_RECENTS) },
                    modifier = Modifier.testTag("remote_recents_action_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Apps Recientes",
                        tint = CatAmberPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Aspect ratio toggle
                IconButton(
                    onClick = onToggleFit,
                    modifier = Modifier.testTag("remote_fit_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = if (isFitScreen) "Modo Ajustar" else "Modo Llenar",
                        tint = if (isFitScreen) CatNeonGreen else CatTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Diagnostics log button
                IconButton(
                    onClick = { showLogViewer = true },
                    modifier = Modifier.testTag("remote_logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Registro de diagnósticos",
                        tint = CatAmberPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Disconnect button
                IconButton(
                    onClick = onDisconnect,
                    modifier = Modifier.testTag("remote_disconnect_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Desconectar",
                        tint = CatCoralRed,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        if (showLogViewer) {
            LogViewerBottomSheet(
                networkInfo = networkInfo,
                isAccessibilityEnabled = false,
                onDismiss = { showLogViewer = false }
            )
        }
    }
}
