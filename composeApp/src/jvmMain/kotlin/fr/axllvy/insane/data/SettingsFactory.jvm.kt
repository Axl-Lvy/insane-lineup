package fr.axllvy.insane.data

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences

/**
 * JVM target only exists to run integration tests — back onto `java.util.prefs` under a dedicated
 * node so test runs don't collide with anything else on the developer's machine.
 */
actual fun createSettings(): Settings =
    PreferencesSettings(Preferences.userRoot().node("fr.axllvy.insane.test"))
