package com.wanderwildwood.enishi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class CallsTest {

    private val zone = ZoneId.of("America/New_York")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()
    private val now = at(2026, 10, 5, 18, 0)
    private fun said(t: Long) = whenSaid(t, now, "Yesterday", zone, Locale.US)

    @Test
    fun theSameNumberWrittenTwoWays() {
        assertEquals(numberKey("+1 (828) 555-0143"), numberKey("8285550143"))
        assertEquals("", numberKey("Unknown"))
    }

    @Test
    fun whenSaysTodayYesterdayWeekdayThenDate() {
        assertEquals("9:41 AM", said(at(2026, 10, 5, 9, 41)))
        assertEquals("Yesterday 11:05 PM", said(at(2026, 10, 4, 23, 5)))
        assertEquals("Wednesday 7:30 AM", said(at(2026, 9, 30, 7, 30)))
        assertEquals("Sep 12", said(at(2026, 9, 12, 12, 0)))
        assertEquals("Dec 24, 2025", said(at(2025, 12, 24, 12, 0)))
    }

    @Test
    fun howLongOnlyWhenAnswered() {
        assertNull(howLong(0))
        assertEquals("45 s", howLong(45))
        assertEquals("3 min", howLong(200))
        assertEquals("1 h", howLong(3600))
        assertEquals("1 h 5 min", howLong(3900))
    }
}
