package fr.axllvy.insane.notifications

data class ScheduledNotification(
    val id: String,
    val title: String,
    val body: String,
    val fireAtEpochMs: Long,
)

/**
 * Localized strings for the platform notification channel (Android exposes this
 * to the user in system Settings → Apps → Notifications). Other platforms
 * ignore these.
 */
data class ChannelMetadata(
    val name: String,
    val description: String,
)

enum class PermissionResult { Granted, Denied, Unavailable }

interface NotificationScheduler {
    suspend fun isPermissionGranted(): Boolean
    suspend fun requestPermission(): PermissionResult
    suspend fun replaceAll(items: List<ScheduledNotification>, channel: ChannelMetadata)
    suspend fun cancelAll()
}

expect fun createNotificationScheduler(): NotificationScheduler
