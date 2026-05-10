package fr.axllvy.insane.notifications

actual fun createNotificationScheduler(): NotificationScheduler = NoopNotificationScheduler

private object NoopNotificationScheduler : NotificationScheduler {
    override suspend fun isPermissionGranted(): Boolean = false
    override suspend fun requestPermission(): PermissionResult = PermissionResult.Unavailable
    override suspend fun replaceAll(
        items: List<ScheduledNotification>,
        channel: ChannelMetadata,
    ) = Unit
    override suspend fun cancelAll() = Unit
}
