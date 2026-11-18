package com.wanderwildwood.enishi.ui

import com.wanderwildwood.enishi.FilesPicker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElsewhereTest {
    private val fieldKit = Elsewhere.FIELD_KIT
    private val other = "00".repeat(32)

    @Test fun onlyFieldKitsReleaseCertificateIsTrusted() {
        assertTrue(fieldKit.trusted(listOf(fieldKit.cert), debuggable = false))
        assertFalse(fieldKit.trusted(listOf(other), debuggable = false))
        assertFalse(fieldKit.trusted(listOf(fieldKit.cert, other), debuggable = false))
        assertFalse(fieldKit.trusted(emptyList(), debuggable = false))
    }

    @Test fun aDebugKeyIsTrustedOnlyByADebugBuild() {
        val debug = FilesPicker.DEBUG_CERTS_SHA256.first()
        assertFalse(fieldKit.trusted(listOf(debug), debuggable = false))
        assertTrue(fieldKit.trusted(listOf(debug), debuggable = true))
    }
}
