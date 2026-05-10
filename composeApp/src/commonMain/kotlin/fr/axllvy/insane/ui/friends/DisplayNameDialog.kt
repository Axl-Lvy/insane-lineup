package fr.axllvy.insane.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.axllvy.insane.ui.InsaneColors

/**
 * One-shot prompt that appears the first time a user shares or redeems a code.
 * Submitting persists the name on the profile; cancelling abandons whichever
 * action triggered it (the caller's `then` closure isn't invoked).
 */
@Composable
fun DisplayNameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    Box(
        Modifier
            .fillMaxSize()
            .background(InsaneColors.DialogScrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(InsaneColors.BgMid)
                .border(1.dp, InsaneColors.Accent, RoundedCornerShape(14.dp))
                .clickable(enabled = false) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "PICK A NAME",
                color = InsaneColors.Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.8.sp,
            )
            Text(
                "Your friends see this above your favorites. You can change it later.",
                color = InsaneColors.OnBgDim,
                fontSize = 12.sp,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                singleLine = true,
                placeholder = { Text("e.g. Camille", color = InsaneColors.OnBgFaint) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = InsaneColors.OnBg,
                    unfocusedTextColor = InsaneColors.OnBg,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = InsaneColors.Accent,
                    unfocusedIndicatorColor = InsaneColors.Border,
                    cursorColor = InsaneColors.Accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text("CANCEL", color = InsaneColors.OnBgDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp) }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (name.isNotBlank()) InsaneColors.Accent else InsaneColors.Accent.copy(alpha = 0.2f))
                        .clickable(enabled = name.isNotBlank()) { onConfirm(name.trim()) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        "SAVE",
                        color = if (name.isNotBlank()) Color.Black else InsaneColors.OnBgDim,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.4.sp,
                    )
                }
            }
        }
    }
}
