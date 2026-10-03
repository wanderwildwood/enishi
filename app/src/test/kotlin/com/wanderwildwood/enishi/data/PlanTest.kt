package com.wanderwildwood.enishi.data

import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanTest {
    private val ada = Draft(
        given = "Ada", family = "Whitlock", nameDataId = 1,
        phones = listOf(Field("555 0101", Phone.TYPE_MOBILE, null, 10), Field("555 0102", Phone.TYPE_HOME, null, 11)),
        note = "Met at the river", noteDataId = 20,
        groups = mapOf(7L to listOf(30L)),
    )

    @Test fun nothingChangedIsNothingWritten() {
        assertEquals(emptyList<Op>(), plan(ada, ada))
    }

    @Test fun onlyTheChangedNumberIsTouched() {
        val after = ada.copy(phones = listOf(ada.phones[0].copy(value = "555 0199"), ada.phones[1]))
        val ops = plan(ada, after)
        assertEquals(1, ops.size)
        val op = ops[0] as Op.Update
        assertEquals(10L, op.dataId)
        assertEquals("555 0199", op.values[Phone.NUMBER])
    }

    @Test fun aRemovedNumberIsDeletedAndANewOneInserted() {
        val after = ada.copy(phones = listOf(ada.phones[1], Field("555 0300", Phone.TYPE_WORK)))
        val ops = plan(ada, after)
        assertTrue(Op.Delete(10) in ops)
        assertTrue(ops.any { it is Op.Insert && it.mime == Phone.CONTENT_ITEM_TYPE && it.values[Phone.NUMBER] == "555 0300" && it.values["data2"] == Phone.TYPE_WORK })
        assertEquals(2, ops.size)
    }

    @Test fun anEmptiedValueDeletesItsRowRatherThanLeavingItBlank() {
        val after = ada.copy(phones = listOf(ada.phones[0].copy(value = "  "), ada.phones[1]), note = "")
        val ops = plan(ada, after)
        assertTrue(Op.Delete(10) in ops)
        assertTrue(Op.Delete(20) in ops)
        assertEquals(2, ops.size)
    }

    @Test fun aBlankNewRowIsNotWritten() {
        assertEquals(emptyList<Op>(), plan(ada, ada.copy(phones = ada.phones + Field("", Phone.TYPE_MOBILE))))
    }

    @Test fun aChangedTypeAloneWritesOnlyTheType() {
        val after = ada.copy(phones = listOf(ada.phones[0].copy(type = Phone.TYPE_WORK), ada.phones[1]))
        val op = plan(ada, after).single() as Op.Update
        assertEquals(Phone.TYPE_WORK, op.values["data2"])
        // The number itself is not handed back: for an address that would make the store
        // re-split the text and drop the town, postcode and country it kept beside it.
        assertTrue(Phone.NUMBER !in op.values)
    }

    @Test fun anAddressGivenANewKindKeepsItsText() {
        val home = Field("12 Mill Lane\nHot Water, NC 28700", StructuredPostal.TYPE_HOME, null, 40)
        val before = ada.copy(addresses = listOf(home))
        val op = plan(before, before.copy(addresses = listOf(home.copy(type = StructuredPostal.TYPE_WORK)))).single() as Op.Update
        assertEquals(setOf("data2", "data3"), op.values.keys)
    }

    @Test fun whitespaceAloneIsNotAChange() {
        val before = ada.copy(phones = listOf(Field(" 555 0101 ", Phone.TYPE_MOBILE, null, 10)))
        assertEquals(emptyList<Op>(), plan(before, before.copy(phones = listOf(Field("555 0101", Phone.TYPE_MOBILE, null, 10)))))
    }

    @Test fun aChangeReachesEveryCopyOfAJoinedPerson() {
        val joined = ada.copy(phones = listOf(Field("555 0101", Phone.TYPE_MOBILE, null, 10, copies = listOf(110))), nameCopies = listOf(101))
        val ops = plan(joined, joined.copy(phones = listOf(joined.phones[0].copy(value = "555 0199")), family = "Lovelace"))
        assertEquals(setOf(10L, 110L, 1L, 101L), ops.filterIsInstance<Op.Update>().map { it.dataId }.toSet())
        val gone = plan(joined, joined.copy(phones = emptyList()))
        assertEquals(setOf(10L, 110L), gone.filterIsInstance<Op.Delete>().map { it.dataId }.toSet())
    }

    @Test fun aRowThatShowsNothingHereIsLeftAlone() {
        // A company row holding only a department reads as empty; saving must not delete it.
        val dept = ada.copy(organisationDataId = 50)
        assertEquals(emptyList<Op>(), plan(dept, dept))
        assertEquals(emptyList<Op>(), plan(dept, dept.copy(note = "Met at the river")).filter { it == Op.Delete(50) })
    }

    @Test fun leavingAGroupLeavesItInEveryCopy() {
        val inTwice = ada.copy(groups = mapOf(7L to listOf(30L, 31L)))
        assertEquals(listOf(Op.Delete(30), Op.Delete(31)), plan(inTwice, inTwice.copy(groups = emptyMap())))
    }

    @Test fun aRenameClearsTheOldDisplayNameSoTheStoreRebuildsIt() {
        val op = plan(ada, ada.copy(family = "Lovelace")).single() as Op.Update
        assertEquals(1L, op.dataId)
        assertEquals("Lovelace", op.values[StructuredName.FAMILY_NAME])
        assertTrue(op.values.containsKey(StructuredName.DISPLAY_NAME))
        assertEquals(null, op.values[StructuredName.DISPLAY_NAME])
    }

    @Test fun aWholeNameWithNoPartsIsHandedOverWhole() {
        val op = plan(Draft(), Draft(wholeName = "Tomas Reyes")).single() as Op.Insert
        assertEquals(mapOf(StructuredName.DISPLAY_NAME to "Tomas Reyes"), op.values)
    }

    @Test fun groupsComeAndGoByTheirMembershipRow() {
        val ops = plan(ada, ada.copy(groups = mapOf(8L to emptyList())))
        assertTrue(Op.Delete(30) in ops)
        assertTrue(ops.any { it is Op.Insert && it.mime == GroupMembership.CONTENT_ITEM_TYPE && it.values[GroupMembership.GROUP_ROW_ID] == 8L })
    }

    @Test fun aNewContactWritesEverythingAndDeletesNothing() {
        val ops = planNew(ada)
        assertTrue(ops.all { it is Op.Insert })
        // name, note, two phones, one group
        assertEquals(5, ops.size)
    }
}
