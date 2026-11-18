package com.wanderwildwood.enishi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemovedTest {
    private val card = """
        BEGIN:VCARD
        VERSION:3.0
        N:Example;Ada;;;
        FN:Ada Example
        TEL;TYPE=CELL:555-0100
        EMAIL:ada@example.org
        END:VCARD
    """.trimIndent().replace("\n", "\r\n").toByteArray()

    @Test fun `a deleted person reads back as a draft with name number and email`() {
        val d = Removed("Ada Example", card, null, Account("a", "t")).draft()
        assertNotNull(d)
        assertEquals("Ada", d!!.given)
        assertEquals("Example", d.family)
        assertEquals(1, d.phones.size)
        assertEquals(1, d.emails.size)
    }

    @Test fun `a card that reads as nobody gives nothing to put back`() {
        assertNull(Removed("x", "garbage".toByteArray(), null, null).draft())
    }

    @Test fun `the account and photo are kept as given`() {
        val r = Removed("Ada", card, byteArrayOf(1, 2, 3), Account("a", "t"))
        assertEquals(Account("a", "t"), r.account)
        assertTrue(r.photo!!.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test fun `the full picture read on its own wins over the thumbnail in the card`() {
        val thumb = java.util.Base64.getEncoder().encodeToString(byteArrayOf(9, 9, 9))
        val withThumb = String(card).replace("END:VCARD", "PHOTO;ENCODING=b;TYPE=JPEG:$thumb\r\nEND:VCARD").toByteArray()
        assertEquals(CardPhoto(byteArrayOf(1, 2, 3)), Removed("Ada", withThumb, byteArrayOf(1, 2, 3), null).draft()!!.photo)
        assertEquals(CardPhoto(byteArrayOf(9, 9, 9)), Removed("Ada", withThumb, null, null).draft()!!.photo)
    }
}
