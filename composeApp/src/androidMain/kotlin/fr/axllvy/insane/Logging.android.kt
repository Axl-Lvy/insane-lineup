package fr.axllvy.insane

import android.util.Log

actual fun logI(message: String) {
    Log.i("Insane", message)
}

actual fun logE(message: String) {
    Log.e("Insane", message)
}
