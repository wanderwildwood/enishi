package com.wanderwildwood.enishi.data

import android.content.ContentResolver
import android.provider.CallLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** One call with a person, from the phone's call log. */
data class Call(val number: String, val kind: Kind, val at: Long, val seconds: Long) {
    enum class Kind { IN, OUT, MISSED, DECLINED }
}

/**
 * The part of a number that says whose it is: its last ten digits, so "+1 (828) 555-0143" and
 * "8285550143" are the same person, as [com.wanderwildwood.enishi.ui.distinctNumbers] has it.
 */
internal fun numberKey(number: String): String = number.filter(Char::isDigit).takeLast(10)

/**
 * The calls with any of [numbers], newest first, at most [limit]. The log is read newest first
 * and only as far as it takes to find them, so a long log costs little for a recent caller.
 */
fun callsWith(resolver: ContentResolver, numbers: List<String>, limit: Int = 20): List<Call> {
    val keys = numbers.map(::numberKey).filter { it.isNotEmpty() }.toSet()
    if (keys.isEmpty()) return emptyList()
    val out = mutableListOf<Call>()
    resolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
        null,
        null,
        "${CallLog.Calls.DATE} DESC",
    )?.use { c ->
        while (out.size < limit && c.moveToNext()) {
            val number = c.getString(0) ?: continue
            if (numberKey(number) !in keys) continue
            out += Call(number, kindOf(c.getInt(1)), c.getLong(2), c.getLong(3))
        }
    }
    return out
}

private fun kindOf(type: Int): Call.Kind = when (type) {
    CallLog.Calls.OUTGOING_TYPE -> Call.Kind.OUT
    CallLog.Calls.MISSED_TYPE -> Call.Kind.MISSED
    CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> Call.Kind.DECLINED
    else -> Call.Kind.IN
}

/**
 * When a call was, as the phone's own call log says it: the time alone today, "Yesterday" and
 * the time, the weekday within the week, and the date before that.
 */
internal fun whenSaid(
    at: Long,
    now: Long,
    yesterday: String,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val then = Instant.ofEpochMilli(at).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day: LocalDate = then.toLocalDate()
    // Newer Java puts a narrow no-break space before "AM"; a plain one reads the same.
    val time = then.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)).replace('\u202F', ' ')
    return when {
        day == today -> time
        day == today.minusDays(1) -> "$yesterday $time"
        day.isAfter(today.minusDays(7)) -> then.format(DateTimeFormatter.ofPattern("EEEE", locale)) + " " + time
        day.year == today.year -> then.format(DateTimeFormatter.ofPattern("MMM d", locale))
        else -> then.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }
}

/** How long a call went on: "45 s", "3 min", "1 h 5 min". Nothing for one never answered. */
internal fun howLong(seconds: Long): String? = when {
    seconds <= 0 -> null
    seconds < 60 -> "$seconds s"
    seconds < 3600 -> "${seconds / 60} min"
    else -> "${seconds / 3600} h" + ((seconds % 3600) / 60).let { if (it > 0) " $it min" else "" }
}
