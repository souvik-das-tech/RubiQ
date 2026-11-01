// Flat 2D net of the cube (U on top; L F R B across; D below). Handy for painting the
// back and bottom faces without rotating the 3D view.
package com.bbcl.rubiq

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.min

// (face, column, row) of each face in a 4-wide, 3-tall grid.
private val placement = listOf(Triple('U', 1, 0), Triple('L', 0, 1), Triple('F', 1, 1), Triple('R', 2, 1), Triple('B', 3, 1), Triple('D', 1, 2))

private class NetLayout(val faceSize: Float, val gap: Float, val left: Float, val top: Float)

private fun netLayout(width: Float, height: Float, density: Float): NetLayout {
    val gap = 6 * density
    val pad = 16 * density
    val faceSize = min((width - 3 * gap - pad) / 4, (height - 2 * gap - pad) / 3)
    return NetLayout(faceSize, gap, (width - (faceSize * 4 + gap * 3)) / 2, (height - (faceSize * 3 + gap * 2)) / 2)
}

@Composable
fun NetView(
    n: Int,
    facelets: String,
    highlight: List<Int>,
    paintEnabled: Boolean,
    onStickerTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hl = remember(highlight) { highlight.toSet() }
    val measurer = rememberTextMeasurer()
    val enabled = rememberUpdatedState(paintEnabled)
    val onTap = rememberUpdatedState(onStickerTap)
    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(n) {
                detectTapGestures { p ->
                    if (!enabled.value) return@detectTapGestures
                    val l = netLayout(size.width.toFloat(), size.height.toFloat(), density)
                    val cell = l.faceSize / n
                    for ((face, col, row) in placement) {
                        val x = p.x - l.left - col * (l.faceSize + l.gap)
                        val y = p.y - l.top - row * (l.faceSize + l.gap)
                        if (x < 0 || y < 0 || x >= l.faceSize || y >= l.faceSize) continue
                        val f = faceOrder.indexOf(face)
                        onTap.value(f * n * n + (y / cell).toInt() * n + (x / cell).toInt())
                        return@detectTapGestures
                    }
                }
            },
    ) {
        val l = netLayout(size.width, size.height, density)
        val cell = l.faceSize / n
        val inset = cell * 0.06f
        val radius = CornerRadius(cell * 0.16f)
        val letterStyle = TextStyle(fontSize = (cell * 0.7f / density / fontScale).sp, fontWeight = FontWeight.Bold, color = Color.Black.copy(alpha = 0.18f))
        for ((face, col, row) in placement) {
            val f = faceOrder.indexOf(face)
            val ox = l.left + col * (l.faceSize + l.gap)
            val oy = l.top + row * (l.faceSize + l.gap)
            for (r in 0 until n) {
                for (c in 0 until n) {
                    val index = f * n * n + r * n + c
                    val code = if (index < facelets.length) facelets[index] else 'X'
                    val topLeft = Offset(ox + c * cell + inset, oy + r * cell + inset)
                    val sz = Size(cell - 2 * inset, cell - 2 * inset)
                    drawRoundRect(stickerColor(code), topLeft, sz, radius)
                    if (index in hl) drawRoundRect(AppColors.rose, topLeft, sz, radius, style = Stroke(2.5f * density))
                }
            }
            // Face letter, faint, in the middle.
            val text = measurer.measure("$face", letterStyle)
            drawText(text, topLeft = Offset(ox + (l.faceSize - text.size.width) / 2, oy + (l.faceSize - text.size.height) / 2))
        }
    }
}
