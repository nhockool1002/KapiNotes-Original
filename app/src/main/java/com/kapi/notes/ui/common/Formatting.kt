package com.kapi.notes.ui.common

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Mirrors the wireframe's "Hôm qua, 14:30" / "20 Thg 10, 2026" relative-ish date labels. */
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

    val timeFormat = SimpleDateFormat("HH:mm", Locale("vi"))
    return when {
        sameDay -> "Hôm nay, ${timeFormat.format(Date(epochMillis))}"
        wasYesterday -> "Hôm qua, ${timeFormat.format(Date(epochMillis))}"
        else -> SimpleDateFormat("d 'Thg' M, yyyy", Locale("vi")).format(Date(epochMillis))
    }
}
