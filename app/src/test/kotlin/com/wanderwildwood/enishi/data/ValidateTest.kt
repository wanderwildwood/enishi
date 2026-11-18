package com.wanderwildwood.enishi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateTest {
    @Test
    fun ordinaryNumbersPass() {
        assertTrue(validPhone("+1 555 010 0142"))
        assertTrue(validPhone("(555) 010-0142"))
        assertTrue(validPhone("555.010.0142,,2"))
        assertTrue(validPhone("  "))
    }

    @Test
    fun lettersAndTooFewDigitsFail() {
        assertFalse(validPhone("call me"))
        assertFalse(validPhone("55"))
        assertFalse(validPhone("+++"))
    }

    @Test
    fun onlyTypedOrChangedValuesAreChecked() {
        val before = listOf(Field("555 0100 ext 12", 2, null, 10), Field("555 0101", 2, null, 11))
        val after = listOf(Field(" 555 0100 ext 12 ", 2, null, 10), Field("555 0199", 2, null, 11), Field("555 0102", 2))
        assertEquals(listOf("555 0199", "555 0102"), typedOrChanged(before, after).map { it.value })
    }
}
