package com.wanderwildwood.enishi.data

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeTest {
    private fun row(id: Long, mime: String, vararg values: Pair<String, Any?>) = StoreRow(id, mime, mapOf(*values))

    private val kept = listOf(
        row(1, StructuredName.CONTENT_ITEM_TYPE, "data1" to "Ada Whitlock", "data2" to "Ada", "data3" to "Whitlock"),
        row(2, Phone.CONTENT_ITEM_TYPE, "data1" to "+1 555-010-1001", "data2" to 2L),
        row(3, Email.CONTENT_ITEM_TYPE, "data1" to "ada@example.org", "data2" to 1L),
    )

    @Test fun theSameNumberSpacedDifferentlyIsNotWrittenTwice() {
        val other = listOf(row(10, Phone.CONTENT_ITEM_TYPE, "data1" to "(555) 010 1001", "data2" to 1L))
        assertEquals(emptyList<Op>(), planMerge(kept, other, emptySet()))
    }

    @Test fun whatTheKeptPersonLacksIsCopiedWithEveryColumn() {
        val other = listOf(
            row(10, Phone.CONTENT_ITEM_TYPE, "data1" to "555 010 2002", "data2" to 0L, "data3" to "Boat"),
            row(11, "vnd.android.cursor.item/relation", "data1" to "Grace", "data2" to 14L),
        )
        val ops = planMerge(kept, other, emptySet())
        assertEquals(2, ops.size)
        val phone = ops[0] as Op.Insert
        assertEquals(Phone.CONTENT_ITEM_TYPE, phone.mime)
        assertEquals("Boat", phone.values["data3"])
        assertEquals("vnd.android.cursor.item/relation", (ops[1] as Op.Insert).mime)
    }

    @Test fun theKeptNameStays() {
        val other = listOf(row(10, StructuredName.CONTENT_ITEM_TYPE, "data1" to "A. Whitlock"))
        assertEquals(emptyList<Op>(), planMerge(kept, other, emptySet()))
    }

    @Test fun aNamelessKeptPersonTakesTheFirstName() {
        val other = listOf(
            row(10, StructuredName.CONTENT_ITEM_TYPE, "data1" to "Ada Whitlock"),
            row(11, StructuredName.CONTENT_ITEM_TYPE, "data1" to "Ada W"),
        )
        val ops = planMerge(kept.drop(1), other, emptySet())
        assertEquals(1, ops.size)
        assertEquals("Ada Whitlock", (ops[0] as Op.Insert).values["data1"])
    }

    @Test fun aPhotoComesOnlyWhenTheKeptPersonHasNone() {
        val photo = row(10, Photo.CONTENT_ITEM_TYPE, "data15" to byteArrayOf(1, 2, 3))
        assertEquals(1, planMerge(kept, listOf(photo), emptySet()).size)
        val withPhoto = kept + row(9, Photo.CONTENT_ITEM_TYPE, "data15" to byteArrayOf(9))
        assertEquals(0, planMerge(withPhoto, listOf(photo), emptySet()).size)
    }

    @Test fun notesOnBothAreJoinedIntoTheKeptOne() {
        val withNote = kept + row(4, Note.CONTENT_ITEM_TYPE, "data1" to "Met at the river")
        val other = listOf(row(10, Note.CONTENT_ITEM_TYPE, "data1" to "Owes us a pie"))
        val ops = planMerge(withNote, other, emptySet())
        assertEquals(listOf(Op.Update(4, mapOf(Note.NOTE to "Met at the river\n\nOwes us a pie"))), ops)
    }

    @Test fun theSameNoteIsNotRepeated() {
        val withNote = kept + row(4, Note.CONTENT_ITEM_TYPE, "data1" to "Met at the river")
        val other = listOf(row(10, Note.CONTENT_ITEM_TYPE, "data1" to " Met at the river "))
        assertEquals(emptyList<Op>(), planMerge(withNote, other, emptySet()))
    }

    @Test fun aGroupComesOnlyFromTheKeptCopysAccount() {
        val other = listOf(
            row(10, GroupMembership.CONTENT_ITEM_TYPE, "data1" to 7L),
            row(11, GroupMembership.CONTENT_ITEM_TYPE, "data1" to 8L),
        )
        val ops = planMerge(kept, other, setOf(7L))
        assertEquals(1, ops.size)
        assertEquals(7L, (ops[0] as Op.Insert).values["data1"])
    }

    @Test fun twoOthersWithTheSameNewNumberAddItOnce() {
        val other = listOf(
            row(10, Phone.CONTENT_ITEM_TYPE, "data1" to "555 010 3003"),
            row(20, Phone.CONTENT_ITEM_TYPE, "data1" to "5550103003"),
        )
        assertEquals(1, planMerge(kept, other, emptySet()).size)
    }

    @Test fun davx5BookkeepingStaysTheKeptCopys() {
        val withDav = kept + row(5, "x.davdroid/unknown-properties", "data1" to "X-A:1")
        val other = listOf(row(10, "x.davdroid/unknown-properties", "data1" to "X-B:2"))
        assertEquals(emptyList<Op>(), planMerge(withDav, other, emptySet()))
        assertTrue(planMerge(kept, other, emptySet()).isNotEmpty())
    }
}
