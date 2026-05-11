package fr.axllvy.insane

actual fun nowMs(): Long = System.currentTimeMillis()

actual val isPullToRefreshSupported: Boolean = true
