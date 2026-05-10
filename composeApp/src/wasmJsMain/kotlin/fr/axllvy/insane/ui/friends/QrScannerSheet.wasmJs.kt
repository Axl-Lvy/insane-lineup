package fr.axllvy.insane.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.axllvy.insane.resources.Res
import fr.axllvy.insane.resources.general_ok
import fr.axllvy.insane.resources.scanner_web_message
import fr.axllvy.insane.resources.scanner_web_title
import fr.axllvy.insane.ui.InsaneColors
import org.jetbrains.compose.resources.stringResource

/**
 * Web QR scanning is intentionally not implemented yet — it would require
 * pulling in jsQR (or `BarcodeDetector` where available) plus a getUserMedia
 * stream + canvas pipeline. Instead, the web build asks the user to type the
 * code directly. The "Add a friend" section already supports that path, so
 * this is just a graceful fallback rather than a blocker.
 */
@Composable
actual fun QrScannerSheet(
    onResult: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(InsaneColors.DialogScrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(InsaneColors.BgMid)
                .border(1.dp, InsaneColors.Accent, RoundedCornerShape(14.dp))
                .clickable(enabled = false) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.QrCodeScanner,
                contentDescription = null,
                tint = InsaneColors.Accent,
                modifier = Modifier.size(40.dp),
            )
            Text(
                stringResource(Res.string.scanner_web_title),
                color = InsaneColors.Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.8.sp,
            )
            Text(
                stringResource(Res.string.scanner_web_message),
                color = InsaneColors.OnBgDim,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(InsaneColors.Accent)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(Res.string.general_ok),
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.4.sp,
                )
            }
        }
    }
}
