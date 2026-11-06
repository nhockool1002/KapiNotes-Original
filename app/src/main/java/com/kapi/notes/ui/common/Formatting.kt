package com.kapi.notes.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kapi.notes.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Mirrors the wireframe's "Hôm qua, 14:30" / "20 Thg 10, 2026" relative-ish date labels.
 * Uses the device's own locale (not a hardcoded one) for both the "Today/Yesterday" wording and
 * the month-name format, so an English-locale device reads "Oct 20, 2026" and a Vietnamese one
 * reads "20 Thg 10, 2026" automatically. */
@Composable
fun formatNoteTimestamp(epochMillis: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = epochMillis }

    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = now.timeInMillis
        add(Calendar.DAY_OF_YEAR, -1)
    }
    val wasYesterday = yesterday.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        yesterday.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)

    val locale = Locale.getDefault()
    val timeFormat = SimpleDateFormat("HH:mm", locale)
    return when {
        sameDay -> stringResource(R.string.date_today, timeFormat.format(Date(epochMillis)))
        wasYesterday -> stringResource(R.string.date_yesterday, timeFormat.format(Date(epochMillis)))
        else -> SimpleDateFormat("d MMM, yyyy", locale).format(Date(epochMillis))
    }
}
