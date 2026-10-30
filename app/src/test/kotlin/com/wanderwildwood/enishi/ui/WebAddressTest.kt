package com.wanderwildwood.enishi.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebAddressTest {
    @Test fun `a bare name gets https`() = assertEquals("https://example.org/a", webAddress("example.org/a"))
    @Test fun `http and https are kept`() {
        assertEquals("http://example.org", webAddress("http://example.org"))
        assertEquals("https://example.org", webAddress("https://example.org"))
    }
    @Test fun `a host with a port is a web address`() = assertEquals("https://example.org:8080", webAddress("example.org:8080"))
    @Test fun `other schemes are refused`() {
        assertNull(webAddress("intent://x#Intent;end"))
        assertNull(webAddress("file:///sdcard/a"))
        assertNull(webAddress("tel:555"))
        assertNull(webAddress("javascript:alert(1)"))
        assertNull(webAddress("   "))
    }
}
