package fr.axllvy.insane.ui.lineup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.axllvy.insane.data.SearchIndex
import fr.axllvy.insane.data.SetMatch
import fr.axllvy.insane.resources.Res
import fr.axllvy.insane.resources.cd_close
import fr.axllvy.insane.resources.search_empty_no_match
import fr.axllvy.insane.resources.search_empty_prompt
import fr.axllvy.insane.resources.search_placeholder
import fr.axllvy.insane.resources.search_title
import fr.axllvy.insane.ui.InsaneColors
import fr.axllvy.insane.ui.dayShortLabel
import fr.axllvy.insane.ui.stageMeta
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LineupSearchSheet(
    index: SearchIndex,
    onResult: (SetMatch) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val results = remember(query) { index.query(query) }

    Box(
        Modifier
            .fillMaxSize()
            .background(InsaneColors.DialogScrim)
            .clickable(onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(InsaneColors.BgMid)
                .border(1.dp, InsaneColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .clickable(enabled = false) {}
                .imePadding()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SearchHeader(onClose = onClose)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        stringResource(Res.string.search_placeholder),
                        color = InsaneColors.OnBgFaint,
                    )
                },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = InsaneColors.Accent,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable { query = "" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = null,
                                tint = InsaneColors.OnBgDim,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = InsaneColors.OnBg,
                    unfocusedTextColor = InsaneColors.OnBg,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = InsaneColors.Accent,
                    unfocusedIndicatorColor = InsaneColors.Border,
                    cursorColor = InsaneColors.Accent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
            Box(Modifier.fillMaxWidth().fillMaxHeight()) {
                when {
                    query.isBlank() -> EmptyMessage(stringResource(Res.string.search_empty_prompt))
                    results.isEmpty() -> EmptyMessage(stringResource(Res.string.search_empty_no_match))
                    else -> ResultsList(results = results, onResult = onResult)
                }
            }
        }
    }
}

@Composable
private fun SearchHeader(onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(Res.string.search_title),
            color = InsaneColors.OnBg,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose),
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
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.TopCenter) {
        Text(
            text,
            color = InsaneColors.OnBgDim,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ResultsList(results: List<SetMatch>, onResult: (SetMatch) -> Unit) {
    LazyColumn(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(results, key = { it.key() }) { m -> ResultRow(m, onResult) }
    }
}

@Composable
private fun ResultRow(match: SetMatch, onResult: (SetMatch) -> Unit) {
    val meta = stageMeta.getValue(match.stage)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(InsaneColors.Bg.copy(alpha = 0.5f))
            .border(1.dp, meta.color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable { onResult(match) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(meta.color))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                match.set.a,
                color = InsaneColors.OnBg,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dayShortLabel(match.day).uppercase(),
                    color = InsaneColors.OnBgDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.4.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text("·", color = InsaneColors.OnBgFaint, fontSize = 10.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    meta.label.uppercase(),
                    color = meta.color,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.4.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text("·", color = InsaneColors.OnBgFaint, fontSize = 10.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${match.set.s}–${match.set.e}",
                    color = InsaneColors.OnBgEmphasis,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
