package com.wanderwildwood.enishi.data

/**
 * A birthday or an anniversary as the contacts store keeps it. A year is often not known, and
 * that is not a gap to fill in: Android writes such a day as `--05-14`, and so does this.
 */
data class Day(val year: Int?, val month: Int, val day: Int) {
    /** The form Android's own editor writes, and every sync adapter reads. */
    fun stored(): String =
        if (year != null) "%04d-%02d-%02d".format(year, month, day) else "--%02d-%02d".format(month, day)

    companion object {
        private val DAYS_IN = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

        /**
         * Reads what is in the store, which is not always what Android wrote: vCards and sync
         * adapters bring `19900514`, `1990-05-14T00:00:00Z` and `--0514` too. Null when it is
         * none of those, and the editor then leaves the row exactly as it found it.
         */
        fun parse(text: String): Day? {
            val s = text.trim().substringBefore('T')
            Regex("""^(\d{4})-?(\d{2})-?(\d{2})$""").matchEntire(s)?.let { m ->
                val (y, mo, d) = m.destructured
                return of(y.toInt(), mo.toInt(), d.toInt())
            }
            Regex("""^--(\d{2})-?(\d{2})$""").matchEntire(s)?.let { m ->
                val (mo, d) = m.destructured
                return of(null, mo.toInt(), d.toInt())
            }
            return null
        }

        /** A day from what was typed, or null if it is not one. A year is optional. */
        fun of(year: Int?, month: Int, day: Int): Day? {
            if (month !in 1..12 || day < 1 || day > DAYS_IN[month - 1]) return null
            if (year != null) {
                if (year !in 1..9999) return null
                val leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
                if (month == 2 && day == 29 && !leap) return null
            }
            return Day(year, month, day)
        }

        /** The three boxes the editor shows, read back. Blank boxes say nothing was meant. */
        fun typed(year: String, month: String, day: String): Typed {
            val y = year.trim()
            val m = month.trim()
            val d = day.trim()
            if (y.isEmpty() && m.isEmpty() && d.isEmpty()) return Typed.Blank
            val mo = m.toIntOrNull() ?: return Typed.Wrong
            val da = d.toIntOrNull() ?: return Typed.Wrong
            val ye = if (y.isEmpty()) null else (y.toIntOrNull() ?: return Typed.Wrong)
            return of(ye, mo, da)?.let { Typed.Ok(it) } ?: Typed.Wrong
        }
    }
}

sealed interface Typed {
    data object Blank : Typed
    data object Wrong : Typed
    data class Ok(val day: Day) : Typed
}
