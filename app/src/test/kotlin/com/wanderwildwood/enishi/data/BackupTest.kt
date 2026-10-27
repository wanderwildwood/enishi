package com.wanderwildwood.enishi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    @Test fun aNameFromBeforeTheTimeWasAddedSortsUnderTheDaysLaterOnes() {
        val names = listOf(
            "contacts-2026-10-27.vcf",
            "contacts-2026-10-27-0930.vcf",
            "contacts-2026-10-26-2359.vcf",
            "contacts-2026-10-27-1815.vcf",
        )
        assertEquals(
            listOf("contacts-2026-10-27-1815.vcf", "contacts-2026-10-27-0930.vcf", "contacts-2026-10-27.vcf", "contacts-2026-10-26-2359.vcf"),
            names.sortedByDescending { Backups.stamp(it) },
        )
    }

    @Test fun stampsOfSameDayAreNotEqual() {
        assertTrue(Backups.stamp("contacts-2026-10-27-0001.vcf") > Backups.stamp("contacts-2026-10-27.vcf"))
    }
}
