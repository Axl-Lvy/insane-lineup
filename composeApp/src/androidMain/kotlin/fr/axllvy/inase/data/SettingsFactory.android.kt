package fr.axllvy.inase.data

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import fr.axllvy.inase.InaseApplication

actual fun createSettings(): Settings {
    val prefs = InaseApplication.appContext.getSharedPreferences("inase", Context.MODE_PRIVATE)
    return SharedPreferencesSettings(prefs)
}
