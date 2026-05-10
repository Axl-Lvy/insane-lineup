package fr.axllvy.insane.notifications

import com.russhwolf.settings.Settings
import fr.axllvy.insane.data.LineupState
import fr.axllvy.insane.data.StageKey
import fr.axllvy.insane.logE
import fr.axllvy.insane.resources.Res
import fr.axllvy.insane.resources.notification_body_starts_soon
import fr.axllvy.insane.resources.notification_channel_description
import fr.axllvy.insane.resources.notification_channel_name
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getString

private const val ENABLED_KEY = "notifications_enabled_v1"

/**
 * Reactive bridge between the favorites/lineup state and the platform scheduler.
 *
 * - [enabled] is the user-facing toggle, persisted.
 * - When enabled is true and lineup data is present, scheduled notifications
 *   are kept in sync (replaceAll) on every favorites or lineup change.
 * - Toggling off cancels everything.
 */
@OptIn(ExperimentalResourceApi::class)
class NotificationsController(
    private val scheduler: NotificationScheduler,
    private val settings: Settings,
    private val nowMs: () -> Long,
) {
    private val _enabled = MutableStateFlow(settings.getBoolean(ENABLED_KEY, defaultValue = false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun bind(
        scope: CoroutineScope,
        favorites: StateFlow<Set<String>>,
        lineup: StateFlow<LineupState?>,
    ) {
        scope.launch {
            combine(_enabled, favorites, lineup) { en, favs, state -> Triple(en, favs, state) }
                .distinctUntilChanged()
                .collect { (en, favs, state) ->
                    runCatching {
                        if (!en || state == null) {
                            scheduler.cancelAll()
                        } else {
                            val bodyByStage = StageKey.entries.associateWith { stage ->
                                getString(Res.string.notification_body_starts_soon, stageLabel(stage))
                            }
                            val items = computeScheduledNotifications(
                                lineup = state.lineup,
                                favKeys = favs,
                                nowMs = nowMs(),
                                formatBody = { stage -> bodyByStage.getValue(stage) },
                            )
                            val channel = ChannelMetadata(
                                name = getString(Res.string.notification_channel_name),
                                description = getString(Res.string.notification_channel_description),
                            )
                            scheduler.replaceAll(items, channel)
                        }
                    }.onFailure { logE("notification sync failed: ${it.message}") }
                }
        }
    }

    /** Tries to enable notifications. Returns the result so the UI can surface a message. */
    suspend fun enable(): EnableResult {
        val granted = scheduler.isPermissionGranted() ||
            scheduler.requestPermission() == PermissionResult.Granted
        if (!granted) return EnableResult.PermissionDenied
        settings.putBoolean(ENABLED_KEY, true)
        _enabled.value = true
        return EnableResult.Enabled
    }

    fun disable() {
        settings.putBoolean(ENABLED_KEY, false)
        _enabled.value = false
    }
}

sealed interface EnableResult {
    data object Enabled : EnableResult
    data object PermissionDenied : EnableResult
}
