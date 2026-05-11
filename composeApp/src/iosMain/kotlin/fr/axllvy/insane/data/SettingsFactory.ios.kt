package fr.axllvy.insane.data

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults

actual fun createSettings(): Settings =
    NSUserDefaultsSettings(
        NSUserDefaults(suiteName = "fr.axllvy.insane") ?: NSUserDefaults.standardUserDefaults
    )
