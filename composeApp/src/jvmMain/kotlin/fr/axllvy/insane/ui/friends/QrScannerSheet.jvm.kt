package fr.axllvy.insane.ui.friends

import androidx.compose.runtime.Composable

/** JVM target exists for tests only — no Compose UI is rendered. This actual is never invoked. */
@Composable
actual fun QrScannerSheet(onResult: (String?) -> Unit, onDismiss: () -> Unit) {
    error("QrScannerSheet is not implemented on JVM — this target is test-only")
}
