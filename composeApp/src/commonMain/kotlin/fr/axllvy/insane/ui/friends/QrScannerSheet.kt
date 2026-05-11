package fr.axllvy.insane.ui.friends

import androidx.compose.runtime.Composable

/**
 * Cross-platform QR scanner overlay.
 *
 * Each platform owns its own preview UI and permission flow — the contract here is just: produce a
 * scanned string (or null on failure) via [onResult], or dismiss without a result via [onDismiss].
 *
 * The scanned string is the raw QR payload — the caller decides how to interpret it (typically a
 * 6-char `friend_code`, but we don't enforce that at this layer so future invite formats are easy
 * to add).
 */
@Composable expect fun QrScannerSheet(onResult: (String?) -> Unit, onDismiss: () -> Unit)
