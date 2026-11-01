package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.changedToUp
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
        var renderedRect by remember { mutableStateOf<Rect?>(null) }
        var isControlBarExpanded by remember { mutableStateOf(false) }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            fun calculateNormalizedCoords(offset: Offset, canvasW: Float, canvasH: Float): Pair<Float, Float> {
                val rect = renderedRect
                return if (rect != null && rect.width > 0f && rect.height > 0f && isFitScreen) {
                    val xRatio = ((offset.x - rect.left) / rect.width).coerceIn(0f, 1f)
                    val yRatio = ((offset.y - rect.top) / rect.height).coerceIn(0f, 1f)
                    Pair(xRatio, yRatio)
                } else {
                    val xRatio = (offset.x / canvasW).coerceIn(0f, 1f)
                    val yRatio = (offset.y / canvasH).coerceIn(0f, 1f)
                    Pair(xRatio, yRatio)
                }
            }

            // Interactive Video Canvas with Unified Touch & Drag Detection
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("remote_video_canvas")
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val canvasW = size.width.toFloat()
                            val canvasH = size.height.toFloat()
                            val startPos = down.position
                            touchFeedbackPos = startPos

                            val (startX, startY) = calculateNormalizedCoords(startPos, canvasW, canvasH)
                            onTouch(Protocol.TOUCH_DOWN, startX, startY)

                            var isDragging = false
                            var lastPos = startPos

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                if (change.changedToUp()) {
                                    change.consume()
                                    val (upX, upY) = calculateNormalizedCoords(change.position, canvasW, canvasH)
                                    onTouch(Protocol.TOUCH_UP, upX, upY)
                                    break
                                }

                                val dist = (change.position - startPos).getDistance()
                                if (!isDragging && dist > viewConfiguration.touchSlop) {
                                    isDragging = true
                                }

                                if (isDragging) {
                                    change.consume()
                                    lastPos = change.position
                                    touchFeedbackPos = lastPos
                                    val (curX, curY) = calculateNormalizedCoords(lastPos, canvasW, canvasH)
                                    onTouch(Protocol.TOUCH_MOVE, curX, curY)
                                }
                            }
                        }
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

                    renderedRect = Rect(destX, destY, destX + destW, destY + destH)

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
                        radius = 28f,
                        center = pos
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 10f,
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

        // Floating Remote Navigation Bar (Collapsible / Non-intrusive)
        if (status == ConnectionStatus.CONNECTED) {
            if (isControlBarExpanded) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp)
                        .testTag("remote_control_bar_expanded"),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xEE151F2C),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FF9E2C))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Collapse button
                        IconButton(
                            onClick = { isControlBarExpanded = false },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Ocultar Controles",
                                tint = CatTextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Back button
                        IconButton(
                            onClick = { onGlobalAction(Protocol.GLOBAL_BACK) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("remote_back_action_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Atrás Remoto",
                                tint = CatAmberPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Home button
                        IconButton(
                            onClick = { onGlobalAction(Protocol.GLOBAL_HOME) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("remote_home_action_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Inicio Remoto",
                                tint = CatCyanSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Recents button
                        IconButton(
                            onClick = { onGlobalAction(Protocol.GLOBAL_RECENTS) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("remote_recents_action_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Apps Recientes",
                                tint = CatAmberPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Aspect ratio toggle
                        IconButton(
                            onClick = onToggleFit,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("remote_fit_toggle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = if (isFitScreen) "Modo Ajustar" else "Modo Llenar",
                                tint = if (isFitScreen) CatNeonGreen else CatTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Diagnostics log button
                        IconButton(
                            onClick = { showLogViewer = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("remote_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Registro de diagnósticos",
                                tint = CatAmberPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Disconnect button
                        IconButton(
                            onClick = onDisconnect,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("remote_disconnect_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Desconectar",
                                tint = CatCoralRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            } else {
                // Collapsed Minimal Floating Pill - Pinned at bottom right, completely out of the way
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 16.dp, bottom = 16.dp)
                        .testTag("remote_control_bar_collapsed"),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xCC151F2C),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x5500E5FF)),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { isControlBarExpanded = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Mostrar Controles",
                            tint = CatCyanSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Controles",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
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
