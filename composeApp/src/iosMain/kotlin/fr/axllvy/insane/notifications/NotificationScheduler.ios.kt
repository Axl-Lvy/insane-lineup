package fr.axllvy.insane.notifications

import fr.axllvy.insane.logE
import fr.axllvy.insane.nowMs
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

actual fun createNotificationScheduler(): NotificationScheduler = IosNotificationScheduler

private object IosNotificationScheduler : NotificationScheduler {

    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun isPermissionGranted(): Boolean = suspendCancellableCoroutine { cont ->
        center.getNotificationSettingsWithCompletionHandler { settings ->
            val s = settings?.authorizationStatus
            cont.resume(s == UNAuthorizationStatusAuthorized || s == UNAuthorizationStatusProvisional)
        }
    }

    override suspend fun requestPermission(): PermissionResult = suspendCancellableCoroutine { cont ->
        val opts = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        center.requestAuthorizationWithOptions(opts) { granted, error ->
            if (error != null) logE("ios notification auth error: ${error.localizedDescription}")
            cont.resume(if (granted) PermissionResult.Granted else PermissionResult.Denied)
        }
    }

    override suspend fun replaceAll(
        items: List<ScheduledNotification>,
        channel: ChannelMetadata,
    ) {
        // iOS has no notion of notification channels; channel metadata is ignored.
        center.removeAllPendingNotificationRequests()
        val now = nowMs()
        for (item in items) {
            val intervalSec = (item.fireAtEpochMs - now) / 1000.0
            if (intervalSec <= 0.0) continue
            val request = buildRequest(item, intervalSec)
            center.addNotificationRequest(request) { error ->
                if (error != null) logE("ios add notification failed: ${error.localizedDescription}")
            }
        }
    }

    override suspend fun cancelAll() {
        center.removeAllPendingNotificationRequests()
    }

    private fun buildRequest(item: ScheduledNotification, intervalSec: Double): UNNotificationRequest {
        val content = UNMutableNotificationContent().apply {
            setTitle(item.title)
            setBody(item.body)
        }
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
            timeInterval = intervalSec,
            repeats = false,
        )
        return UNNotificationRequest.requestWithIdentifier(
            identifier = item.id,
            content = content,
            trigger = trigger,
        )
    }
}
