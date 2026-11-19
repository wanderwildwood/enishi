package com.wanderwildwood.enishi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLinkTest {

    private val held = HomeAccount.Held("https://Cloud.example.org/nextcloud", "ada", "secret", "s1")

    @Test
    fun serverIsShownWithoutHttps() {
        assertEquals("cloud.example.org", HomeLink.shown("https://cloud.example.org/"))
        assertEquals("cloud.example.org/nextcloud", HomeLink.shown("https://cloud.example.org/nextcloud"))
        assertEquals("nas.local:8080", HomeLink.shown("http://nas.local:8080"))
    }

    @Test
    fun anOwnSignInToTheSameUserOnTheSameServerIsHomes() {
        assertTrue(HomeLink.sameAccount("cloud.example.org", "ada", held))
        assertTrue(HomeLink.sameAccount("CLOUD.example.org", "ada ", held))
    }

    @Test
    fun anyOtherSignInIsNot() {
        assertFalse(HomeLink.sameAccount("cloud.example.org", "bob", held))
        assertFalse(HomeLink.sameAccount("other.example.org", "ada", held))
        assertFalse(HomeLink.sameAccount(null, "ada", held))
        assertFalse(HomeLink.sameAccount("cloud.example.org", null, held))
    }

    @Test
    fun aPortIsNotPartOfTheHost() {
        val onPort = HomeAccount.Held("http://nas.local:8080", "ada", "secret", "s1")
        assertTrue(HomeLink.sameAccount("nas.local", "ada", onPort))
    }
}
