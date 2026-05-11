package fr.axllvy.insane.ui.friends

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.axllvy.insane.data.Friend
import fr.axllvy.insane.data.FriendCode
import fr.axllvy.insane.data.RedeemResult
import fr.axllvy.insane.nowMs
import fr.axllvy.insane.resources.Res
import fr.axllvy.insane.resources.cd_close
import fr.axllvy.insane.resources.cd_remove_friend
import fr.axllvy.insane.resources.cd_scan_qr
import fr.axllvy.insane.resources.friends_add_friend_label
import fr.axllvy.insane.resources.friends_btn_add
import fr.axllvy.insane.resources.friends_btn_generate
import fr.axllvy.insane.resources.friends_btn_rotate
import fr.axllvy.insane.resources.friends_code_expired
import fr.axllvy.insane.resources.friends_code_expires_in
import fr.axllvy.insane.resources.friends_code_placeholder
import fr.axllvy.insane.resources.friends_count_label
import fr.axllvy.insane.resources.friends_empty_state
import fr.axllvy.insane.resources.friends_generate_help
import fr.axllvy.insane.resources.friends_my_invite_code
import fr.axllvy.insane.resources.friends_title
import fr.axllvy.insane.resources.friends_visibility_off
import fr.axllvy.insane.resources.friends_visibility_on
import fr.axllvy.insane.resources.snackbar_friend_added
import fr.axllvy.insane.resources.snackbar_friend_added_default
import fr.axllvy.insane.ui.InsaneColors
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Friends bottom-sheet-style overlay. Sections:
 * 1. "My code" — current 6-char code (rotate button + QR), or empty state.
 * 2. "Add a friend" — manual text entry + scan-QR button.
 * 3. Friends list with per-friend visibility toggle + unfriend.
 */
@Composable
fun FriendsSheet(
    state: FriendsSheetState,
    onClose: () -> Unit,
    onRotateCode: suspend () -> FriendCode?,
    onRedeem: suspend (String) -> RedeemResult,
    onSetVisibility: (String, Boolean) -> Unit,
    onUnfriend: suspend (String) -> Unit,
    onSetDisplayName: suspend (String) -> Unit,
    onLaunchScanner: () -> Unit,
    qrRenderer: @Composable (data: String, sizeDp: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var displayNameDialog by remember { mutableStateOf<DisplayNameDialogState?>(null) }
    var redeemFeedback by remember { mutableStateOf<String?>(null) }

    fun ensureDisplayName(after: () -> Unit) {
        if (state.myDisplayName.isNullOrBlank()) {
            displayNameDialog = DisplayNameDialogState(after)
        } else {
            after()
        }
    }

    Box(
        Modifier.fillMaxSize().background(InsaneColors.DialogScrim).clickable(onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(InsaneColors.BgMid)
                .border(
                    1.dp,
                    InsaneColors.Accent.copy(alpha = 0.4f),
                    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                )
                .clickable(enabled = false) {}
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            FriendsHeader(onClose = onClose)

            MyCodeSection(
                code = state.myCode,
                onRotate = { ensureDisplayName { scope.launch { onRotateCode() } } },
                qrRenderer = qrRenderer,
            )

            AddFriendSection(
                feedback = redeemFeedback,
                onRedeem = { code ->
                    ensureDisplayName {
                        scope.launch {
                            val result = onRedeem(code)
                            redeemFeedback =
                                when (result) {
                                    is RedeemResult.Added ->
                                        getString(
                                            Res.string.snackbar_friend_added,
                                            result.friend.displayName
                                                ?: getString(
                                                    Res.string.snackbar_friend_added_default
                                                ),
                                        )
                                    is RedeemResult.Failed -> result.message
                                }
                        }
                    }
                },
                onScan = { ensureDisplayName { onLaunchScanner() } },
            )

            Text(
                stringResource(
                    Res.string.friends_count_label,
                    state.friends.size.toString().padStart(2, '0'),
                ),
                color = InsaneColors.OnBgDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.6.sp,
            )

            FriendsList(
                friends = state.friends,
                visible = state.visibleFriendIds,
                onToggleVisibility = onSetVisibility,
                onUnfriend = { id -> scope.launch { onUnfriend(id) } },
                modifier = Modifier.weight(1f, fill = true),
            )
        }
    }

    displayNameDialog?.let { dialog ->
        DisplayNameDialog(
            initial = state.myDisplayName.orEmpty(),
            onDismiss = { displayNameDialog = null },
            onConfirm = { name ->
                scope.launch {
                    onSetDisplayName(name)
                    displayNameDialog = null
                    dialog.then()
                }
            },
        )
    }
}

data class FriendsSheetState(
    val myDisplayName: String?,
    val myCode: FriendCode?,
    val friends: List<Friend>,
    val visibleFriendIds: Set<String>,
)

private data class DisplayNameDialogState(val then: () -> Unit)

@Composable
private fun FriendsHeader(onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(Res.string.friends_title),
            color = InsaneColors.OnBg,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(Res.string.cd_close),
                tint = InsaneColors.OnBgDim,
            )
        }
    }
}

@Composable
private fun MyCodeSection(
    code: FriendCode?,
    onRotate: () -> Unit,
    qrRenderer: @Composable (String, Int) -> Unit,
) {
    val ttlSec = useTtlCountdown(code?.expiresAtMs)
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(InsaneColors.Bg.copy(alpha = 0.5f))
            .border(1.dp, InsaneColors.Border, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.QrCode,
                contentDescription = null,
                tint = InsaneColors.Accent,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(Res.string.friends_my_invite_code),
                color = InsaneColors.OnBgDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.6.sp,
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(6.dp))
                    .background(InsaneColors.Accent.copy(alpha = 0.18f))
                    .border(1.dp, InsaneColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .clickable(onClick = onRotate)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = InsaneColors.Accent,
                        modifier = Modifier.size(11.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(
                            if (code == null) Res.string.friends_btn_generate
                            else Res.string.friends_btn_rotate
                        ),
                        color = InsaneColors.Accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                    )
                }
            }
        }

        if (code == null) {
            Text(
                stringResource(Res.string.friends_generate_help),
                color = InsaneColors.OnBgDim,
                fontSize = 12.sp,
            )
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                qrRenderer(code.code, 110)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        code.code,
                        color = InsaneColors.OnBg,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 6.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    val mins = (ttlSec / 60).toString().padStart(2, '0')
                    val secs = (ttlSec % 60).toString().padStart(2, '0')
                    val expiredColor = if (ttlSec <= 0) InsaneColors.Warn else InsaneColors.OnBgDim
                    Text(
                        if (ttlSec <= 0) stringResource(Res.string.friends_code_expired)
                        else stringResource(Res.string.friends_code_expires_in, mins, secs),
                        color = expiredColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.4.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun useTtlCountdown(expiresAtMs: Long?): Long {
    if (expiresAtMs == null) return 0
    var now by remember(expiresAtMs) { mutableStateOf(nowMs()) }
    LaunchedEffect(expiresAtMs) {
        while (true) {
            delay(1000)
            now = nowMs()
        }
    }
    return max(0L, (expiresAtMs - now) / 1000)
}

@Composable
private fun AddFriendSection(feedback: String?, onRedeem: (String) -> Unit, onScan: () -> Unit) {
    var input by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(InsaneColors.Bg.copy(alpha = 0.5f))
            .border(1.dp, InsaneColors.Border, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(Res.string.friends_add_friend_label),
            color = InsaneColors.OnBgDim,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.6.sp,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { v ->
                    input = v.uppercase().filter { it.isLetterOrDigit() }.take(6)
                },
                placeholder = {
                    Text(
                        stringResource(Res.string.friends_code_placeholder),
                        letterSpacing = 4.sp,
                        color = InsaneColors.OnBgFaint,
                    )
                },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done,
                    ),
                colors =
                    TextFieldDefaults.colors(
                        focusedTextColor = InsaneColors.OnBg,
                        unfocusedTextColor = InsaneColors.OnBg,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = InsaneColors.Accent,
                        unfocusedIndicatorColor = InsaneColors.Border,
                        cursorColor = InsaneColors.Accent,
                    ),
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(InsaneColors.Accent.copy(alpha = 0.12f))
                    .border(1.dp, InsaneColors.Accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onScan),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.QrCodeScanner,
                    contentDescription = stringResource(Res.string.cd_scan_qr),
                    tint = InsaneColors.Accent,
                )
            }
        }
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (input.length == 6) InsaneColors.Accent
                    else InsaneColors.Accent.copy(alpha = 0.2f)
                )
                .clickable(enabled = input.length == 6) {
                    onRedeem(input)
                    input = ""
                }
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(Res.string.friends_btn_add),
                color = if (input.length == 6) Color.Black else InsaneColors.OnBgDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.6.sp,
            )
        }
        AnimatedVisibility(feedback != null) {
            Text(feedback.orEmpty(), color = InsaneColors.OnBgDim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun FriendsList(
    friends: List<Friend>,
    visible: Set<String>,
    onToggleVisibility: (String, Boolean) -> Unit,
    onUnfriend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (friends.isEmpty()) {
        Box(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(InsaneColors.Bg.copy(alpha = 0.4f))
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(Res.string.friends_empty_state),
                color = InsaneColors.OnBgDim,
                fontSize = 12.sp,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(friends, key = { it.id }) { friend ->
            FriendRow(
                friend = friend,
                isVisible = friend.id in visible,
                onToggleVisibility = { onToggleVisibility(friend.id, !(friend.id in visible)) },
                onUnfriend = { onUnfriend(friend.id) },
            )
        }
    }
}

@Composable
private fun FriendRow(
    friend: Friend,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onUnfriend: () -> Unit,
) {
    val color = friendColor(friend.id)
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(InsaneColors.Bg.copy(alpha = 0.5f))
            .border(1.dp, if (isVisible) color else InsaneColors.Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onToggleVisibility)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(10.dp)
                .clip(CircleShape)
                .background(if (isVisible) color else color.copy(alpha = 0.25f))
                .border(1.dp, color, CircleShape)
        )
        Text(
            friend.displayName ?: friend.id.take(8),
            color = InsaneColors.OnBg,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(
                if (isVisible) Res.string.friends_visibility_on
                else Res.string.friends_visibility_off
            ),
            color = if (isVisible) color else InsaneColors.OnBgDim,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.4.sp,
        )
        Box(
            Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onUnfriend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(Res.string.cd_remove_friend),
                tint = InsaneColors.OnBgFaint,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
