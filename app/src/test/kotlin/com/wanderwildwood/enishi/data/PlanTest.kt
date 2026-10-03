package com.wanderwildwood.enishi.data

import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanTest {
    private val ada = Draft(
        given = "Ada", family = "Whitlock", nameDataId = 1,
        phones = listOf(Field("555 0101", Phone.TYPE_MOBILE, null, 10), Field("555 0102", Phone.TYPE_HOME, null, 11)),
        note = "Met at the river", noteDataId = 20,
        groups = mapOf(7L to 30L),
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

    @Test fun aChangedTypeAloneIsAnUpdate() {
        val after = ada.copy(phones = listOf(ada.phones[0].copy(type = Phone.TYPE_WORK), ada.phones[1]))
        val op = plan(ada, after).single() as Op.Update
        assertEquals(Phone.TYPE_WORK, op.values["data2"])
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
        val ops = plan(ada, ada.copy(groups = mapOf(8L to null)))
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
