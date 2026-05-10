package fr.axllvy.insane.data

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import fr.axllvy.insane.InsaneApplication

actual fun createSettings(): Settings {
    val prefs = InsaneApplication.appContext.getSharedPreferences("insane", Context.MODE_PRIVATE)
    return SharedPreferencesSettings(prefs)
}
