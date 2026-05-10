package fr.axllvy.insane.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.UIKit.UIColor
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

/**
 * iOS QR scanner — AVFoundation preview wrapped in a Compose Column so we can
 * lay a close button above the camera surface (UIKitViewController doesn't
 * respect Compose z-order reliably on every Compose-Multiplatform version,
 * so we stack via layout, not via Box children).
 *
 * Requires `NSCameraUsageDescription` in iosApp/Info.plist. If the user
 * denies camera access, the preview stays black.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun QrScannerSheet(
    onResult: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val controller = remember { ScannerViewController(onResult) }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "SCAN A FRIEND CODE",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.6.sp,
            )
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
        }
        UIKitViewController(
            factory = { controller },
            modifier = Modifier.fillMaxSize(),
        )
    }
    DisposableEffect(Unit) {
        onDispose { controller.teardown() }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class ScannerViewController(
    private val onResult: (String?) -> Unit,
) : UIViewController(nibName = null, bundle = null) {

    private val session = AVCaptureSession()
    private var previewLayer: AVCaptureVideoPreviewLayer? = null
    private var alreadyHandled = false

    private val delegate = object : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
        override fun captureOutput(
            output: platform.AVFoundation.AVCaptureOutput,
            didOutputMetadataObjects: List<*>,
            fromConnection: platform.AVFoundation.AVCaptureConnection,
        ) {
            if (alreadyHandled) return
            val first = didOutputMetadataObjects.firstOrNull() as? AVMetadataMachineReadableCodeObject ?: return
            val value = first.stringValue ?: return
            alreadyHandled = true
            onResult(value)
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.blackColor

        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        if (device == null) {
            onResult(null); return
        }
        @Suppress("UNCHECKED_CAST")
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, error = null) as? AVCaptureDeviceInput
        if (input == null || !session.canAddInput(input)) {
            onResult(null); return
        }
        session.addInput(input)

        val output = AVCaptureMetadataOutput()
        if (!session.canAddOutput(output)) {
            onResult(null); return
        }
        session.addOutput(output)
        output.setMetadataObjectsDelegate(delegate, queue = dispatch_get_main_queue())
        output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)

        session.sessionPreset = AVCaptureSessionPresetHigh

        val layer = AVCaptureVideoPreviewLayer(session = session)
        layer.videoGravity = AVLayerVideoGravityResizeAspectFill
        layer.frame = view.bounds
        view.layer.addSublayer(layer)
        previewLayer = layer
    }

    override fun viewWillAppear(animated: Boolean) {
        super.viewWillAppear(animated)
        if (!session.isRunning()) session.startRunning()
    }

    override fun viewWillDisappear(animated: Boolean) {
        if (session.isRunning()) session.stopRunning()
        super.viewWillDisappear(animated)
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
    }

    fun teardown() {
        if (session.isRunning()) session.stopRunning()
        previewLayer?.removeFromSuperlayer()
        previewLayer = null
    }
}
