package com.wanderwildwood.enishi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayTest {
    @Test fun readsWhatAndroidAndOthersWrite() {
        assertEquals(Day(1990, 5, 14), Day.parse("1990-05-14"))
        assertEquals(Day(1990, 5, 14), Day.parse("19900514"))
        assertEquals(Day(1990, 5, 14), Day.parse("1990-05-14T00:00:00Z"))
        assertEquals(Day(null, 5, 14), Day.parse("--05-14"))
        assertEquals(Day(null, 5, 14), Day.parse("--0514"))
        assertNull(Day.parse("next spring"))
    }

    @Test fun writesWhatAndroidReads() {
        assertEquals("1990-05-14", Day(1990, 5, 14).stored())
        assertEquals("--02-29", Day(null, 2, 29).stored())
    }

    @Test fun aLeapDayNeedsALeapYearOnlyWhenThereIsAYear() {
        assertEquals(Typed.Ok(Day(null, 2, 29)), Day.typed("", "2", "29"))
        assertEquals(Typed.Ok(Day(2000, 2, 29)), Day.typed("2000", "2", "29"))
        assertEquals(Typed.Wrong, Day.typed("1900", "2", "29"))
    }

    @Test fun blankIsNotWrong() {
        assertEquals(Typed.Blank, Day.typed(" ", "", ""))
        assertEquals(Typed.Wrong, Day.typed("", "13", "1"))
        assertEquals(Typed.Wrong, Day.typed("", "4", "31"))
        assertEquals(Typed.Wrong, Day.typed("", "", "3"))
    }
}
