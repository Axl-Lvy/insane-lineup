package fr.axllvy.insane.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import fr.axllvy.insane.InsaneApplication

const val INSANE_NOTIFICATION_CHANNEL_ID = "insane.set_reminders"
private const val SCHEDULED_IDS_KEY = "scheduled_notification_ids_v1"

actual fun createNotificationScheduler(): NotificationScheduler =
    AndroidNotificationScheduler(InsaneApplication.appContext)

private class AndroidNotificationScheduler(
    private val context: Context,
) : NotificationScheduler {

    private val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val store: Settings = SharedPreferencesSettings(
        context.getSharedPreferences("insane.notifications", Context.MODE_PRIVATE),
    )

    override suspend fun isPermissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun requestPermission(): PermissionResult {
        if (isPermissionGranted()) return PermissionResult.Granted
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return PermissionResult.Granted
        return AndroidPermissionRequester.request()
    }

    override suspend fun replaceAll(
        items: List<ScheduledNotification>,
        channel: ChannelMetadata,
    ) {
        ensureChannel(channel)
        cancelAll()
        for (item in items) {
            val intent = buildAlarmIntent(item)
            // setAndAllowWhileIdle: doesn't require SCHEDULE_EXACT_ALARM. Can be
            // delayed by a few minutes inside Doze, but during a festival the
            // device is rarely idle, and we'd rather avoid the prompt.
            alarms.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                item.fireAtEpochMs,
                intent,
            )
        }
        store.putString(SCHEDULED_IDS_KEY, items.joinToString("\n") { it.id })
    }

    override suspend fun cancelAll() {
        val ids = store.getStringOrNull(SCHEDULED_IDS_KEY)
            ?.split("\n")
            ?.filter { it.isNotEmpty() }
            ?: return
        for (id in ids) {
            val pi = PendingIntent.getBroadcast(
                context,
                requestCodeFor(id),
                baseIntent(id),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pi != null) {
                alarms.cancel(pi)
                pi.cancel()
            }
        }
        store.remove(SCHEDULED_IDS_KEY)
    }

    private fun ensureChannel(meta: ChannelMetadata) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(INSANE_NOTIFICATION_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            INSANE_NOTIFICATION_CHANNEL_ID,
            meta.name,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = meta.description
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildAlarmIntent(item: ScheduledNotification): PendingIntent {
        val intent = baseIntent(item.id).apply {
            putExtra(InsaneNotificationReceiver.EXTRA_ID, item.id)
            putExtra(InsaneNotificationReceiver.EXTRA_TITLE, item.title)
            putExtra(InsaneNotificationReceiver.EXTRA_BODY, item.body)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(item.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun baseIntent(id: String): Intent =
        Intent(context, InsaneNotificationReceiver::class.java).apply {
            // Distinct data uri ensures PendingIntent matching keys per id.
            data = android.net.Uri.parse("insane://notify/$id")
            action = InsaneNotificationReceiver.ACTION_FIRE
        }

    private fun requestCodeFor(id: String): Int = id.hashCode()
}
