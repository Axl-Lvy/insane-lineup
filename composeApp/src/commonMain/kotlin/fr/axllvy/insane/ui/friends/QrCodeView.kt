package fr.axllvy.insane.ui.friends

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.axllvy.insane.resources.Res
import fr.axllvy.insane.resources.cd_qr_code
import io.github.alexzhirkevich.qrose.options.QrBrush
import io.github.alexzhirkevich.qrose.options.QrErrorCorrectionLevel
import io.github.alexzhirkevich.qrose.options.solid
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.jetbrains.compose.resources.stringResource

/**
 * Renders [data] as a QR code via qrose, which is purpose-built for Compose Multiplatform and ships
 * proper iOS/wasmJs artifacts.
 *
 * `Medium` ECC level gives ~15% damage tolerance — enough for a screen photo without inflating the
 * matrix needlessly for a 6-char payload.
 */
@Composable
fun QrCodeView(
    data: String,
    sizeDp: Int,
    foreground: Color = Color.Black,
    background: Color = Color.White,
) {
    val painter =
        rememberQrCodePainter(data) {
            errorCorrectionLevel = QrErrorCorrectionLevel.Medium
            colors {
                dark = QrBrush.solid(foreground)
                light = QrBrush.solid(background)
            }
        }
    Image(
        painter = painter,
        contentDescription = stringResource(Res.string.cd_qr_code, data),
        modifier = Modifier.size(sizeDp.dp),
    )
}
