package com.wanderwildwood.enishi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AskedVcfTest {
    @Test fun aFileEndingInVcfIsACard() {
        assertTrue(Asked.namesAVcfFile("com.android.externalstorage.documents", "card.vcf"))
        assertTrue(Asked.namesAVcfFile("mms", "CARD.VCF"))
    }

    @Test fun aSyncedContactsLookupKeyEndingInVcfIsAPerson() {
        assertFalse(Asked.namesAVcfFile("com.android.contacts", "3788i0f8a-uid.vcf"))
    }

    @Test fun anythingElseIsNotACard() {
        assertFalse(Asked.namesAVcfFile("com.android.contacts", "0r1-2F2D"))
        assertFalse(Asked.namesAVcfFile(null, null))
    }
}
