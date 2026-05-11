package fr.axllvy.insane

/**
 * Diagnostic logging routed to platform-native sinks under the "Insane" tag.
 *
 * • Android → Log.i / Log.e visible in `adb logcat` • iOS → NSLog visible in Xcode console • wasmJs
 * → console.log / console.error visible in browser devtools
 *
 * Use [logI] for successful traffic (request/response, state transitions) and [logE] strictly for
 * failures (non-2xx, thrown exceptions, parse errors).
 */
expect fun logI(message: String)

expect fun logE(message: String)
