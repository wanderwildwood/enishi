package com.wanderwildwood.enishi.data

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone
import org.junit.Assert.assertEquals
import org.junit.Test

class VCardTest {
    private fun read(s: String, charset: java.nio.charset.Charset = Charsets.UTF_8) = VCard.read(s.toByteArray(charset))

    @Test fun readsAVersionThreeCard() {
        val cards = read(
            """
            BEGIN:VCARD
            VERSION:3.0
            N:Whitlock;Ada;;;
            FN:Ada Whitlock
            TEL;TYPE=CELL:+1 555 0101
            TEL;TYPE=WORK,VOICE:+1 555 0102
            EMAIL;TYPE=INTERNET,HOME:ada@example.org
            ADR;TYPE=HOME:;;12 Mill Lane;Hot Water;NC;28700;USA
            ORG:River Co;Upstream
            TITLE:Ferrier
            BDAY:1990-05-14
            NOTE:Likes\, among other things\, rivers.\nAnd hills.
            END:VCARD
            """.trimIndent().replace("\n", "\r\n"),
        )
        val c = cards.single()
        assertEquals("Ada", c.given)
        assertEquals("Whitlock", c.family)
        assertEquals("", c.wholeName)
        assertEquals(listOf(Field("+1 555 0101", Phone.TYPE_MOBILE), Field("+1 555 0102", Phone.TYPE_WORK)), c.phones)
        assertEquals(Field("ada@example.org", Email.TYPE_HOME), c.emails.single())
        assertEquals("12 Mill Lane\nHot Water, NC 28700\nUSA", c.addresses.single().value)
        assertEquals("River Co, Upstream", c.company)
        assertEquals("Ferrier", c.jobTitle)
        assertEquals(Field("1990-05-14", Event.TYPE_BIRTHDAY), c.events.single())
        assertEquals("Likes, among other things, rivers.\nAnd hills.", c.note)
    }

    @Test fun readsVersionTwoOneQuotedPrintableInItsOwnCharset() {
        val card = "BEGIN:VCARD\r\nVERSION:2.1\r\n" +
            "N;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:Garc=C3=ADa;Jos=C3=A9;;;\r\n" +
            "TEL;CELL;PREF:5550103\r\n" +
            "NOTE;ENCODING=QUOTED-PRINTABLE:one line=\r\n and the next\r\n" +
            "END:VCARD\r\n"
        val c = read(card).single()
        assertEquals("José", c.given)
        assertEquals("García", c.family)
        assertEquals(Phone.TYPE_MOBILE, c.phones.single().type)
        assertEquals("one line and the next", c.note)
    }

    @Test fun readsALatin1Card() {
        val card = "BEGIN:VCARD\r\nVERSION:2.1\r\nN;CHARSET=ISO-8859-1:Müller;Jörg\r\nEND:VCARD\r\n"
        val c = read(card, Charsets.ISO_8859_1).single()
        assertEquals("Jörg", c.given)
        assertEquals("Müller", c.family)
    }

    @Test fun unfoldsFoldedLines() {
        val c = read("BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Priya\r\n  Raman\r\nEND:VCARD\r\n").single()
        assertEquals("Priya Raman", c.wholeName)
    }

    @Test fun readsSeveralCardsAndSkipsEmptyOnes() {
        val book = "BEGIN:VCARD\nVERSION:3.0\nFN:One\nEND:VCARD\nBEGIN:VCARD\nVERSION:3.0\nEND:VCARD\nBEGIN:VCARD\nVERSION:4.0\nFN:Two\nTEL;VALUE=uri;TYPE=home:tel:+1-555-0104\nEND:VCARD\n"
        val cards = read(book)
        assertEquals(listOf("One", "Two"), cards.map { it.spokenName })
        assertEquals(Field("+1-555-0104", Phone.TYPE_HOME), cards[1].phones.single())
    }

    @Test fun aCardInsideACardIsNotASecondPerson() {
        val c = read("BEGIN:VCARD\nVERSION:2.1\nFN:Boss\nAGENT:\nBEGIN:VCARD\nFN:Assistant\nEND:VCARD\nTEL:5550105\nEND:VCARD\n")
        assertEquals("Boss", c.single().spokenName)
        assertEquals("5550105", c.single().phones.single().value)
    }

    @Test fun readsAndroidsOwnExportOfAYearlessBirthdayAndAnAnniversary() {
        val c = read(
            "BEGIN:VCARD\nVERSION:2.1\nN:Osei;Nina;;;\nBDAY:--05-14\n" +
                "X-ANDROID-CUSTOM:vnd.android.cursor.item/contact_event;2010-06-01;1;;;;;;;;;;;;;\n" +
                "X-ANDROID-CUSTOM:vnd.android.cursor.item/nickname;Neen;1;;;;;;;;;;;;;\nEND:VCARD\n",
        ).single()
        assertEquals(listOf(Field("--05-14", Event.TYPE_BIRTHDAY), Field("2010-06-01", Event.TYPE_ANNIVERSARY)), c.events)
        assertEquals("Neen", c.nickname)
    }

    @Test fun aColonInsideAQuotedParameterDoesNotEndIt() {
        val c = read("BEGIN:VCARD\nVERSION:4.0\nFN:Jonah\nEMAIL;PID=\"1:2\";TYPE=work:jonah@example.org\nEND:VCARD\n").single()
        assertEquals(Field("jonah@example.org", Email.TYPE_WORK), c.emails.single())
    }

    @Test fun aByteOrderMarkIsIgnored() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "BEGIN:VCARD\nFN:Mum\nEND:VCARD\n".toByteArray()
        assertEquals("Mum", VCard.read(bytes).single().spokenName)
    }
}
