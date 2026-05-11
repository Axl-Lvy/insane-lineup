package fr.axllvy.insane

actual fun logI(message: String) {
    consoleLog("[Insane] $message")
}

actual fun logE(message: String) {
    consoleError("[Insane] $message")
}

@JsFun("(msg) => { console.log(msg); }") private external fun consoleLog(msg: String)

@JsFun("(msg) => { console.error(msg); }") private external fun consoleError(msg: String)
