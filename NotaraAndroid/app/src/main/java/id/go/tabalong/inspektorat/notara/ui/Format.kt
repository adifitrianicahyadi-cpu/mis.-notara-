package id.go.tabalong.inspektorat.notara.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmtDuration(totalSec: Int): String {
    val s = if (totalSec < 0) 0 else totalSec
    val m = s / 60
    val r = s % 60
    return "%02d:%02d".format(m, r)
}

fun fmtDurationMs(ms: Int): String = fmtDuration(ms / 1000)

fun fmtDate(t: Long): String {
    val date = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date(t))
    val time = SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date(t))
    return "$date · $time"
}
