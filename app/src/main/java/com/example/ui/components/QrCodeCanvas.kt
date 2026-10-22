package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CatAmberPrimary
import kotlin.math.abs

@Composable
fun QrCodeCanvas(
    payload: String,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    tint: Color = Color.White,
    background: Color = Color(0xFF131A24)
) {
    // Generate deterministic 21x21 QR pattern based on payload hash
    val matrix = remember(payload) {
        val n = 21
        val grid = Array(n) { BooleanArray(n) }

        // Finder patterns in corners
        fun drawFinder(startX: Int, startY: Int) {
            for (r in 0 until 7) {
                for (c in 0 until 7) {
                    val isBorder = r == 0 || r == 6 || c == 0 || c == 6
                    val isCenter = r in 2..4 && c in 2..4
                    grid[startY + r][startX + c] = isBorder || isCenter
                }
            }
        }
        drawFinder(0, 0)
        drawFinder(n - 7, 0)
        drawFinder(0, n - 7)

        // Timing patterns
        for (i in 7 until n - 7) {
            grid[6][i] = i % 2 == 0
            grid[i][6] = i % 2 == 0
        }

        // Data payload pseudo-random modules based on string hash
        val bytes = payload.toByteArray()
        var byteIdx = 0
        var bitIdx = 0
        for (r in 0 until n) {
            for (c in 0 until n) {
                // Skip finders
                val inTopLeft = r < 8 && c < 8
                val inTopRight = r < 8 && c >= n - 8
                val inBottomLeft = r >= n - 8 && c < 8
                val inTiming = (r == 6 || c == 6)

                if (!inTopLeft && !inTopRight && !inBottomLeft && !inTiming) {
                    val b = if (bytes.isNotEmpty()) bytes[byteIdx % bytes.size].toInt() else 0
                    val isSet = ((b shr (bitIdx % 8)) and 1) == 1
                    val posHash = abs((r * 31 + c * 17) xor (payload.hashCode()))
                    grid[r][c] = (isSet xor (posHash % 3 == 0))

                    bitIdx++
                    if (bitIdx % 8 == 0) byteIdx++
                }
            }
        }
        grid
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(1.5.dp, CatAmberPrimary.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size - 24.dp)) {
            val n = matrix.size
            val cellSize = this.size.width / n

            for (r in 0 until n) {
                for (c in 0 until n) {
                    if (matrix[r][c]) {
                        drawRoundRect(
                            color = tint,
                            topLeft = Offset(c * cellSize, r * cellSize),
                            size = Size(cellSize * 0.95f, cellSize * 0.95f),
                            cornerRadius = CornerRadius(cellSize * 0.2f, cellSize * 0.2f)
                        )
                    }
                }
            }
        }
    }
}
