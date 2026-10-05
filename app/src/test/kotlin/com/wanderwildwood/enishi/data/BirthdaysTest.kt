package com.wanderwildwood.enishi.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class BirthdaysTest {
    @Test fun knownYearStartsOnTheDay() {
        assertEquals(LocalDate.of(1990, 5, 14) to "FREQ=YEARLY", Birthdays.yearly(Day(1990, 5, 14), 2026))
    }

    @Test fun unknownYearStartsThisYear() {
        assertEquals(LocalDate.of(2026, 5, 14) to "FREQ=YEARLY", Birthdays.yearly(Day(null, 5, 14), 2026))
    }

    @Test fun leapDayFallsOnFebruarysLastDay() {
        assertEquals(LocalDate.of(2000, 2, 29) to "FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=-1", Birthdays.yearly(Day(2000, 2, 29), 2026))
        assertEquals(LocalDate.of(2026, 2, 28) to "FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=-1", Birthdays.yearly(Day(null, 2, 29), 2026))
    }
}
