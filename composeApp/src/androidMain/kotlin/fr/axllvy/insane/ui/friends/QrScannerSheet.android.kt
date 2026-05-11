package fr.axllvy.insane.ui.friends

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Uses Google Play Services Code Scanner — runs in a Play Services process so:
 * - we do NOT need android.permission.CAMERA in our manifest,
 * - we do NOT have to render a camera preview ourselves,
 * - it ships its own UI (overlay, autofocus, torch).
 *
 * Trade-off: requires Play Services on-device. Not a problem for Insane's audience but if we ever
 * need to ship a Play-Services-free build we'd swap this for a CameraX + ML Kit Vision
 * implementation.
 */
@Composable
actual fun QrScannerSheet(onResult: (String?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val options =
            GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(context, options)
            .startScan()
            .addOnSuccessListener { barcode -> onResult(barcode.rawValue) }
            .addOnCanceledListener { onDismiss() }
            .addOnFailureListener { onResult(null) }
    }
}
