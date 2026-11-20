package com.wanderwildwood.enishi.data

import io.ktor.http.Url
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
    fun anOwnSignInToTheSameUserAtTheSameAddressIsHomes() {
        assertTrue(HomeLink.sameAccount("https://cloud.example.org/nextcloud", "ada", held))
        assertTrue(HomeLink.sameAccount("https://CLOUD.example.org/nextcloud/", "ada ", held))
        assertTrue(HomeLink.sameAccount("cloud.example.org/nextcloud", "ada", held))
        assertTrue(HomeLink.sameAccount("https://cloud.example.org:443/nextcloud", "ada", held))
    }

    @Test
    fun anyOtherSignInIsNot() {
        assertFalse(HomeLink.sameAccount("https://cloud.example.org/nextcloud", "bob", held))
        assertFalse(HomeLink.sameAccount("https://other.example.org/nextcloud", "ada", held))
        assertFalse(HomeLink.sameAccount(null, "ada", held))
        assertFalse(HomeLink.sameAccount("https://cloud.example.org/nextcloud", null, held))
    }

    @Test
    fun anotherServiceOnTheSameHostIsNot() {
        assertFalse(HomeLink.sameAccount("https://cloud.example.org", "ada", held))
        assertFalse(HomeLink.sameAccount("https://cloud.example.org/radicale", "ada", held))
        assertFalse(HomeLink.sameAccount("https://cloud.example.org:8443/nextcloud", "ada", held))
        assertFalse(HomeLink.sameAccount("http://cloud.example.org/nextcloud", "ada", held))
        assertFalse(HomeLink.sameAccount("https://cloud.example.org/Nextcloud", "ada", held))
    }

    @Test
    fun aPortCounts() {
        val onPort = HomeAccount.Held("http://nas.local:8080", "ada", "secret", "s1")
        assertTrue(HomeLink.sameAccount("http://nas.local:8080/", "ada", onPort))
        assertFalse(HomeLink.sameAccount("http://nas.local", "ada", onPort))
    }

    @Test
    fun aNewSignInGetsANameNotTaken() {
        assertEquals("ada@cloud.example.org", HomeLink.freeName("ada@cloud.example.org", listOf("bob@cloud.example.org")))
        assertEquals("ada@cloud.example.org (2)", HomeLink.freeName("ada@cloud.example.org", listOf("ada@cloud.example.org")))
        assertEquals(
            "ada@cloud.example.org (3)",
            HomeLink.freeName("ada@cloud.example.org", listOf("ada@cloud.example.org", "ada@cloud.example.org (2)")),
        )
    }

    @Test
    fun homesAccountIsNamedWithOneAtAndThePortWhenNotUsual() {
        assertEquals("ada@cloud.example.org", HomeLink.accountName("ada", Url("https://cloud.example.org/remote.php/dav/")))
        assertEquals("ada@cloud.example.org", HomeLink.accountName("ada", Url("https://cloud.example.org:443/nextcloud/")))
        assertEquals("ada@10.0.2.2:8080", HomeLink.accountName("ada", Url("http://10.0.2.2:8080/remote.php/dav/")))
        assertEquals("ada@nas.local", HomeLink.accountName("ada", Url("http://nas.local/")))
        assertEquals("ada@example.org on 10.0.2.2:8080", HomeLink.accountName("ada@example.org", Url("http://10.0.2.2:8080/")))
        assertEquals("ada@example.org on cloud.example.org", HomeLink.accountName("ada@example.org", Url("https://cloud.example.org/")))
    }
}
