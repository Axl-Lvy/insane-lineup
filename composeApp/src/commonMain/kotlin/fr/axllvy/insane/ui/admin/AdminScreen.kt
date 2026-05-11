package fr.axllvy.insane.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.axllvy.insane.data.DayKey
import fr.axllvy.insane.data.Lineup
import fr.axllvy.insane.data.SetEntry
import fr.axllvy.insane.data.StageKey
import fr.axllvy.insane.ui.InsaneColors
import kotlinx.coroutines.launch

/**
 * Admin editor — overwrites the singleton lineup row. Reachable only after the 7-tap unlock and an
 * authenticated admin profile; RLS is the real gate (this UI is just convenience).
 */
@Composable
fun AdminScreen(initial: Lineup, onSave: suspend (Lineup) -> Unit, onClose: () -> Unit) {
    var working by remember { mutableStateOf(initial) }
    var day by remember { mutableStateOf(DayKey.JEU) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(InsaneColors.Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            AdminTopBar(
                day = day,
                onDayChange = { day = it },
                saving = saving,
                onClose = onClose,
                onSave = { confirm = true },
            )
            error?.let {
                Text(
                    "Error: $it",
                    color = Color(0xFFFFB4B4),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            StageList(
                lineup = working,
                day = day,
                onChange = { working = it },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Overwrite live lineup?") },
            text = { Text("All viewers will see these changes after their next refresh.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = false
                        scope.launch {
                            saving = true
                            error = null
                            runCatching { onSave(working) }
                                .onFailure { error = it.message ?: it::class.simpleName }
                            saving = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
            containerColor = InsaneColors.BgMid,
        )
    }
}

@Composable
private fun AdminTopBar(
    day: DayKey,
    onDayChange: (DayKey) -> Unit,
    saving: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(InsaneColors.HeaderBg).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = InsaneColors.OnBg)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "ADMIN · LINEUP",
                color = InsaneColors.OnBg,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onSave,
                enabled = !saving,
                colors = ButtonDefaults.buttonColors(containerColor = InsaneColors.Accent),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (d in DayKey.entries) {
                val active = d == day
                TextButton(
                    onClick = { onDayChange(d) },
                    colors =
                        ButtonDefaults.textButtonColors(
                            containerColor =
                                if (active) InsaneColors.Accent else InsaneColors.TabInactiveBg,
                            contentColor = if (active) Color.White else InsaneColors.OnBg,
                        ),
                ) {
                    Text(d.id.uppercase(), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StageList(
    lineup: Lineup,
    day: DayKey,
    onChange: (Lineup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    val stages = lineup[day] ?: emptyMap()
    LaunchedEffect(day) { state.scrollToItem(0) }
    LazyColumn(
        state = state,
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (stage in StageKey.entries) {
            val sets = stages[stage].orEmpty()
            item(key = "stage-${stage.name}") {
                StageHeader(
                    stage = stage,
                    onAdd = {
                        val withAdded = sets + SetEntry(s = "18:00", e = "19:00", a = "")
                        onChange(updateStage(lineup, day, stage, withAdded))
                    },
                )
            }
            items(sets.size, key = { idx -> "${stage.name}-$idx" }) { idx ->
                val entry = sets[idx]
                SetRow(
                    entry = entry,
                    onChange = { updated ->
                        val next = sets.toMutableList().also { it[idx] = updated }
                        onChange(updateStage(lineup, day, stage, next))
                    },
                    onDelete = {
                        val next = sets.toMutableList().also { it.removeAt(idx) }
                        onChange(updateStage(lineup, day, stage, next))
                    },
                )
            }
        }
    }
}

@Composable
private fun StageHeader(stage: StageKey, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stage.name,
            color = InsaneColors.Accent,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = "Add set", tint = InsaneColors.OnBg)
        }
    }
}

@Composable
private fun SetRow(entry: SetEntry, onChange: (SetEntry) -> Unit, onDelete: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(InsaneColors.BgMid, RoundedCornerShape(8.dp))
                .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeField(
            value = entry.s,
            onChange = { onChange(entry.copy(s = it)) },
            modifier = Modifier.width(78.dp),
        )
        TimeField(
            value = entry.e,
            onChange = { onChange(entry.copy(e = it)) },
            modifier = Modifier.width(78.dp),
        )
        OutlinedTextField(
            value = entry.a,
            onValueChange = { onChange(entry.copy(a = it)) },
            singleLine = true,
            modifier = Modifier.weight(1f).height(56.dp),
            colors = textFieldColors(),
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFFFFB4B4))
        }
    }
}

@Composable
private fun TimeField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        modifier = modifier.height(56.dp),
        colors = textFieldColors(),
    )
}

@Composable
private fun textFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = InsaneColors.OnBg,
        unfocusedTextColor = InsaneColors.OnBg,
        focusedBorderColor = InsaneColors.Accent,
        unfocusedBorderColor = InsaneColors.Border,
        cursorColor = InsaneColors.Accent,
    )

private fun updateStage(
    lineup: Lineup,
    day: DayKey,
    stage: StageKey,
    sets: List<SetEntry>,
): Lineup {
    val stages = (lineup[day] ?: emptyMap()).toMutableMap()
    if (sets.isEmpty()) stages.remove(stage) else stages[stage] = sets
    val next = lineup.toMutableMap()
    next[day] = stages
    return next
}
