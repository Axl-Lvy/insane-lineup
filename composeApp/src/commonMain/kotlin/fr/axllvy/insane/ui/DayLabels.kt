package fr.axllvy.insane.ui

import androidx.compose.runtime.Composable
import fr.axllvy.insane.data.DayKey
import fr.axllvy.insane.resources.Res
import fr.axllvy.insane.resources.day_jeu_date
import fr.axllvy.insane.resources.day_jeu_full
import fr.axllvy.insane.resources.day_jeu_short
import fr.axllvy.insane.resources.day_sam_date
import fr.axllvy.insane.resources.day_sam_full
import fr.axllvy.insane.resources.day_sam_short
import fr.axllvy.insane.resources.day_ven_date
import fr.axllvy.insane.resources.day_ven_full
import fr.axllvy.insane.resources.day_ven_short
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private fun shortRes(day: DayKey): StringResource = when (day) {
    DayKey.JEU -> Res.string.day_jeu_short
    DayKey.VEN -> Res.string.day_ven_short
    DayKey.SAM -> Res.string.day_sam_short
}

private fun dateRes(day: DayKey): StringResource = when (day) {
    DayKey.JEU -> Res.string.day_jeu_date
    DayKey.VEN -> Res.string.day_ven_date
    DayKey.SAM -> Res.string.day_sam_date
}

private fun fullRes(day: DayKey): StringResource = when (day) {
    DayKey.JEU -> Res.string.day_jeu_full
    DayKey.VEN -> Res.string.day_ven_full
    DayKey.SAM -> Res.string.day_sam_full
}

@Composable
fun dayShortLabel(day: DayKey): String = stringResource(shortRes(day))

@Composable
fun dayDateLabel(day: DayKey): String = stringResource(dateRes(day))

@Composable
fun dayFullLabel(day: DayKey): String = stringResource(fullRes(day))
