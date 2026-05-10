package fr.axllvy.insane.ui.friends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.goquati.qr.QrCode

/**
 * Single-pass Canvas QR renderer. Beats nested Box grids on every metric:
 * one composable, one draw call, no per-cell layout overhead.
 *
 * Encodes [data] verbatim — the friend code is short enough to use Ecc.MEDIUM
 * comfortably, which gives us better damage tolerance for screen photos.
 */
@Composable
fun QrCodeView(
    data: String,
    sizeDp: Int,
    foreground: Color = Color.White,
    background: Color = Color.Black,
) {
    val matrix = remember(data) { QrCode.encodeText(data, QrCode.Ecc.MEDIUM) }
    val modules = matrix.size
    Canvas(Modifier.size(sizeDp.dp)) {
        drawRect(color = background, topLeft = Offset.Zero, size = this.size)
        // Quiet zone: 1 module border (instead of the spec's 4) to maximize
        // payload area in a small UI tile. Scanners we care about (ML Kit /
        // jsQR) handle this fine.
        val modulesWithQuiet = modules + 2
        val cell = this.size.width / modulesWithQuiet
        val originX = cell
        val originY = cell
        for (y in 0 until modules) {
            for (x in 0 until modules) {
                if (matrix[x, y]) {
                    drawRect(
                        color = foreground,
                        topLeft = Offset(originX + x * cell, originY + y * cell),
                        size = Size(cell + 0.5f, cell + 0.5f), // hairline overdraw avoids gaps from rounding
                    )
                }
            }
        }
    }
}
