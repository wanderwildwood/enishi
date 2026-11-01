package com.wanderwildwood.enishi.data

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
}
