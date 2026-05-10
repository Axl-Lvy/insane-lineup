package fr.axllvy.insane

import platform.Foundation.NSLog

// NSLog has no level concept; we just prefix so consoles can filter on it.
actual fun logI(message: String) {
    NSLog("[Insane] %@", message)
}

actual fun logE(message: String) {
    NSLog("[Insane:E] %@", message)
}
