package fr.axllvy.inase.data

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults

actual fun createSettings(): Settings =
    NSUserDefaultsSettings(NSUserDefaults(suiteName = "fr.axllvy.inase") ?: NSUserDefaults.standardUserDefaults)
