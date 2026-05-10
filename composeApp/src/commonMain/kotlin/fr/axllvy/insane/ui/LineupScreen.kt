package fr.axllvy.insane.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import fr.axllvy.insane.data.DayKey
import fr.axllvy.insane.data.FavoritesRepository
import fr.axllvy.insane.data.FriendCode
import fr.axllvy.insane.data.FriendsRepository
import fr.axllvy.insane.data.LineupState
import fr.axllvy.insane.data.RedeemResult
import fr.axllvy.insane.data.RefreshOutcome
import fr.axllvy.insane.data.StageKey
import fr.axllvy.insane.notifications.EnableResult
import fr.axllvy.insane.notifications.NotificationsController
import fr.axllvy.insane.resources.Res
import fr.axllvy.insane.resources.snackbar_friend_added
import fr.axllvy.insane.resources.snackbar_friend_added_default
import fr.axllvy.insane.resources.snackbar_lineup_refreshed
import fr.axllvy.insane.resources.snackbar_notifications_denied
import fr.axllvy.insane.resources.snackbar_notifications_disabled
import fr.axllvy.insane.resources.snackbar_notifications_enabled
import fr.axllvy.insane.resources.snackbar_offline
import fr.axllvy.insane.resources.snackbar_refresh_failed
import fr.axllvy.insane.ui.friends.FriendsSheet
import fr.axllvy.insane.ui.friends.FriendsSheetState
import fr.axllvy.insane.ui.friends.QrCodeView
import fr.axllvy.insane.ui.friends.QrScannerSheet
import fr.axllvy.insane.ui.lineup.DetailDialog
import fr.axllvy.insane.ui.lineup.Header
import fr.axllvy.insane.ui.lineup.Timeline
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

@Composable
fun LineupScreen(
    state: LineupState,
    favoritesRepo: FavoritesRepository,
    friendsRepo: FriendsRepository,
    notifications: NotificationsController,
    onRefresh: suspend () -> RefreshOutcome,
) {
    val scope = rememberCoroutineScope()
    var day by rememberSaveable { mutableStateOf(DayKey.JEU) }
    val favs by favoritesRepo.favorites.collectAsState()
    val notificationsEnabled by notifications.enabled.collectAsState()
    var favsOnly by rememberSaveable { mutableStateOf(false) }
    var hiddenStages by rememberSaveable { mutableStateOf(setOf<StageKey>()) }
    var selected by remember { mutableStateOf<String?>(null) }

    var showFriends by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var visibleFriends by rememberSaveable { mutableStateOf(setOf<String>()) }
    var myCode by remember { mutableStateOf<FriendCode?>(null) }

    val friends by friendsRepo.friends.collectAsState()
    val myDisplayName by friendsRepo.myDisplayName.collectAsState()
    val friendFavorites by friendsRepo.friendFavorites.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    val triggerRefresh: () -> Unit = {
        if (!state.refreshing) {
            scope.launch {
                val msg = when (onRefresh()) {
                    RefreshOutcome.Refreshed -> getString(Res.string.snackbar_lineup_refreshed)
                    RefreshOutcome.Offline -> getString(Res.string.snackbar_offline)
                    is RefreshOutcome.Error -> getString(Res.string.snackbar_refresh_failed)
                }
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    val toggleNotifications: () -> Unit = {
        scope.launch {
            val msg = if (notificationsEnabled) {
                notifications.disable()
                getString(Res.string.snackbar_notifications_disabled)
            } else {
                when (notifications.enable()) {
                    EnableResult.Enabled -> getString(Res.string.snackbar_notifications_enabled)
                    EnableResult.PermissionDenied -> getString(Res.string.snackbar_notifications_denied)
                }
            }
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg)
        }
    }

    Box(Modifier.fillMaxSize().background(verticalGradient())) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header(
                state = state,
                day = day,
                onDayChange = { day = it },
                hiddenStages = hiddenStages,
                onToggleStage = { s ->
                    hiddenStages = if (s in hiddenStages) hiddenStages - s else hiddenStages + s
                },
                favsOnly = favsOnly,
                onToggleFavsOnly = { favsOnly = !favsOnly },
                favCount = favs.size,
                notificationsEnabled = notificationsEnabled,
                onToggleNotifications = toggleNotifications,
                onRefresh = triggerRefresh,
                onOpenFriends = { showFriends = true },
            )

            Timeline(
                state = state,
                day = day,
                hiddenStages = hiddenStages,
                favs = favs,
                favsOnly = favsOnly,
                visibleFriends = visibleFriends,
                friendFavorites = friendFavorites,
                onSelect = { selected = it },
                onRefresh = triggerRefresh,
                modifier = Modifier.weight(1f),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            snackbar = { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = InsaneColors.BgMid,
                    contentColor = InsaneColors.OnBg,
                )
            },
        )

        selected?.let { key ->
            DetailDialog(
                selectedKey = key,
                day = day,
                state = state,
                isFav = key in favs,
                onToggleFav = { scope.launch { favoritesRepo.toggle(key) } },
                onDismiss = { selected = null },
            )
        }

        if (showFriends) {
            FriendsSheet(
                state = FriendsSheetState(
                    myDisplayName = myDisplayName,
                    myCode = myCode,
                    friends = friends,
                    visibleFriendIds = visibleFriends,
                ),
                onClose = { showFriends = false },
                onRotateCode = {
                    val rotated = friendsRepo.rotateCode()
                    if (rotated != null) myCode = rotated
                    rotated
                },
                onRedeem = { code -> friendsRepo.redeem(code) },
                onSetVisibility = { id, visible ->
                    visibleFriends = if (visible) visibleFriends + id else visibleFriends - id
                },
                onUnfriend = { id ->
                    friendsRepo.unfriend(id)
                    visibleFriends = visibleFriends - id
                },
                onSetDisplayName = { name -> friendsRepo.setDisplayName(name) },
                onLaunchScanner = { showScanner = true },
                qrRenderer = { data, sizeDp -> QrCodeView(data = data, sizeDp = sizeDp) },
            )
        }

        if (showScanner) {
            QrScannerSheet(
                onResult = { code ->
                    showScanner = false
                    if (code != null) {
                        scope.launch {
                            val result = friendsRepo.redeem(code)
                            snackbarHostState.showSnackbar(
                                when (result) {
                                    is RedeemResult.Added -> getString(
                                        Res.string.snackbar_friend_added,
                                        result.friend.displayName
                                            ?: getString(Res.string.snackbar_friend_added_default),
                                    )
                                    is RedeemResult.Failed -> result.message
                                },
                            )
                        }
                    }
                },
                onDismiss = { showScanner = false },
            )
        }
    }
}

@Composable
private fun verticalGradient() = Brush.verticalGradient(
    listOf(InsaneColors.BgTop, InsaneColors.BgMid, InsaneColors.Bg),
)
