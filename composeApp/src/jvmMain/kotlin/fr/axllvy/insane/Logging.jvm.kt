package fr.axllvy.insane

actual fun logI(message: String) {
    println("[I] $message")
}

actual fun logE(message: String) {
    System.err.println("[E] $message")
}
