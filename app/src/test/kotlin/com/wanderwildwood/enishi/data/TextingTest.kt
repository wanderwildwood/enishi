package com.wanderwildwood.enishi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextingTest {
    private fun d(v: String, mobile: Boolean = false, primary: Boolean = false) = Dialable(v, mobile, primary)

    @Test fun noNumberIsNone() {
        assertEquals(Choice.None, chooseNumber(emptyList()))
        assertEquals(Choice.None, chooseNumber(listOf(d(" "), d("abc"))))
    }

    @Test fun oneNumberIsUsedWhateverItsType() {
        assertEquals(Choice.One("5551234567"), chooseNumber(listOf(d("(555) 123-4567"))))
    }

    @Test fun theSameNumberKeptTwiceIsOneNumber() {
        assertEquals(Choice.One("+15551234567"), chooseNumber(listOf(d("+1 555 123 4567", mobile = true), d("555-123-4567"))))
    }

    @Test fun primaryWinsOverMobile() {
        val c = chooseNumber(listOf(d("5550001", mobile = true), d("5550002", primary = true), d("5550003", mobile = true)))
        assertEquals(Choice.One("5550002"), c)
    }

    @Test fun theOnlyMobileWinsOverLandlines() {
        assertEquals(Choice.One("5550002"), chooseNumber(listOf(d("5550001"), d("5550002", mobile = true), d("5550003"))))
    }

    @Test fun twoMobilesAreAsked() {
        val c = chooseNumber(listOf(d("5550001", mobile = true), d("5550002", mobile = true)))
        assertTrue(c is Choice.Ask)
        assertEquals(listOf("5550001", "5550002"), (c as Choice.Ask).numbers.map { it.value })
    }

    @Test fun twoLandlinesAreAsked() {
        assertTrue(chooseNumber(listOf(d("5550001"), d("5550002"))) is Choice.Ask)
    }

    @Test fun twoPrimariesAreAsked() {
        assertTrue(chooseNumber(listOf(d("5550001", primary = true), d("5550002", primary = true))) is Choice.Ask)
    }

    @Test fun cleaningKeepsDigitsAndALeadingPlus() {
        assertEquals("+15551234567", cleanNumber(" +1 (555) 123-4567 "))
        assertEquals("5551234567", cleanNumber("555.123.4567"))
        assertEquals("", cleanNumber("call me"))
        assertEquals("15551234", cleanNumber("1,555;1234"))
    }

    @Test fun recipientsKeepOrderAndDropDuplicates() {
        val r = recipients(listOf("555 000 0001", "+1 555 000 0002", "5550000001", "", "x", "(555) 000-0002"))
        assertEquals(listOf("5550000001", "+15550000002"), r)
    }

    @Test fun theAddressPartIsCommaSeparated() {
        assertEquals("+15550000001,5550000002", smstoPart(listOf("+1 555-000-0001", "555 000 0002")))
        assertEquals("5550000001", smstoPart(listOf("5550000001")))
        assertEquals("", smstoPart(listOf("none")))
    }

    @Test fun typedNumbers() {
        assertEquals("5551234", typedNumber("555 1234"))
        assertEquals("+4412345", typedNumber("+44 12345"))
        assertNull(typedNumber("  "))
        assertNull(typedNumber("hello"))
    }

    @Test fun whoGetsTheAddress() {
        assertEquals(Target.CALLER, target(callerKnown = true, callerTakesIt = true, stockMessagesPresent = true))
        assertEquals(Target.STOCK_MESSAGES, target(true, false, true))
        assertEquals(Target.STOCK_MESSAGES, target(false, false, true))
        assertEquals(Target.ANY, target(true, false, false))
        assertEquals(Target.ANY, target(false, false, false))
    }
}
