package com.wanderwildwood.enishi.data

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import com.wanderwildwood.enishi.with
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchTest {
    private val ada = Person(1, "a", "Ada Whitlock", "A", false)
    private val jose = Person(2, "b", "José García", "J", true)
    private val tomas = Person(3, "c", "Tomas Reyes", "T", false)
    private val people = listOf(ada, jose, tomas)
    private val findable = mapOf(
        1L to Findable(numbers = listOf("+1 (555) 010-1234"), emails = listOf("ada@river.example")),
        2L to Findable(organisations = listOf("Mill Co"), nicknames = listOf("Pepe")),
        3L to Findable(numbers = listOf("555 0300")),
    )

    @Test fun findsByTheStartOfAnyName() {
        assertEquals(listOf(ada), search("whit", people, findable).map { it.person })
        assertEquals(listOf(jose), search("jose gar", people, findable).map { it.person })
        assertEquals(listOf(jose), search("pepe", people, findable).map { it.person })
    }

    @Test fun findsByDigitsAndSaysWhichNumber() {
        val found = search("0101", people, findable).single()
        assertEquals(ada, found.person)
        assertEquals("+1 (555) 010-1234", found.by)
        // Two digits are not enough to search by.
        assertEquals(emptyList<Found>(), search("55", people, findable))
    }

    @Test fun findsByEmailAndCompanyAfterNames() {
        assertEquals(listOf(ada), search("river", people, findable).map { it.person })
        assertEquals(listOf(jose), search("mill", people, findable).map { it.person })
    }

    @Test fun aCardIsAlreadyHereByNameAndNumber() {
        val card = Draft(given = "Ada", family = "Whitlock", phones = listOf(Field("5550101234", Phone.TYPE_MOBILE)))
        assertEquals(ada, alreadyHere(card, people, findable))
        // Same name, different person.
        assertNull(alreadyHere(card.copy(phones = listOf(Field("5559999999", Phone.TYPE_MOBILE))), people, findable))
        // Last name first in the list still matches.
        assertEquals(1L, alreadyHere(card, listOf(ada.copy(name = "Whitlock, Ada")), findable)?.id)
    }

    @Test fun addingToSomeoneOnlyAddsWhatIsNew() {
        val here = Draft(given = "Ada", phones = listOf(Field("+1 555 010 1234", Phone.TYPE_MOBILE, null, 9)))
        val seed = Draft(
            wholeName = "Someone Else",
            phones = listOf(Field("(555) 010-1234", Phone.TYPE_MOBILE)),
            emails = listOf(Field("ada@river.example", Email.TYPE_OTHER)),
        )
        val merged = here.with(seed)
        assertEquals("Ada", merged.given)
        assertEquals("", merged.wholeName)
        assertEquals(1, merged.phones.size)
        assertEquals(1, merged.emails.size)
    }
}
