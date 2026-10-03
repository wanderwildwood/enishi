package com.wanderwildwood.enishi.data

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website

/**
 * One change to the contacts store, before it becomes a `ContentProviderOperation`. Kept as
 * plain data so what a save will do can be worked out, and tested, without a phone.
 */
sealed interface Op {
    data class Insert(val mime: String, val values: Map<String, Any?>) : Op
    data class Update(val dataId: Long, val values: Map<String, Any?>) : Op
    data class Delete(val dataId: Long) : Op
}

/**
 * What it takes to turn [before] into [after]. Only rows the editor shows are looked at; a row
 * it does not know about — a photo, a messenger handle, a relation — is never in either draft,
 * so it is never in the plan, and it survives the save exactly as it was.
 *
 * A value emptied in the editor deletes its row rather than leaving an empty one behind, which
 * is what every other app reading the store would trip over.
 */
fun plan(before: Draft, after: Draft): List<Op> {
    val ops = mutableListOf<Op>()

    one(
        ops, before.nameDataId, StructuredName.CONTENT_ITEM_TYPE,
        old = nameValues(before), new = nameValues(after),
    )
    one(
        ops, before.nicknameDataId, Nickname.CONTENT_ITEM_TYPE,
        old = single(Nickname.NAME, before.nickname), new = single(Nickname.NAME, after.nickname),
    )
    one(
        ops, before.organisationDataId, Organization.CONTENT_ITEM_TYPE,
        old = organisationValues(before), new = organisationValues(after),
    )
    one(
        ops, before.noteDataId, Note.CONTENT_ITEM_TYPE,
        old = single(Note.NOTE, before.note), new = single(Note.NOTE, after.note),
    )

    many(ops, Phone.CONTENT_ITEM_TYPE, Phone.NUMBER, before.phones, after.phones)
    many(ops, Email.CONTENT_ITEM_TYPE, Email.ADDRESS, before.emails, after.emails)
    many(ops, StructuredPostal.CONTENT_ITEM_TYPE, StructuredPostal.FORMATTED_ADDRESS, before.addresses, after.addresses)
    many(ops, Event.CONTENT_ITEM_TYPE, Event.START_DATE, before.events, after.events)
    many(ops, Website.CONTENT_ITEM_TYPE, Website.URL, before.websites, after.websites)

    for ((group, row) in before.groups) {
        if (group !in after.groups && row != null) ops += Op.Delete(row)
    }
    for (group in after.groups.keys) {
        if (group !in before.groups) {
            ops += Op.Insert(GroupMembership.CONTENT_ITEM_TYPE, mapOf(GroupMembership.GROUP_ROW_ID to group))
        }
    }
    return ops
}

/** Everything a brand-new contact needs written, which is a plan from nothing. */
fun planNew(draft: Draft): List<Op> = plan(Draft(), draft.copy(
    nameDataId = null, nicknameDataId = null, organisationDataId = null, noteDataId = null,
    phones = draft.phones.map { it.copy(dataId = null) },
    emails = draft.emails.map { it.copy(dataId = null) },
    addresses = draft.addresses.map { it.copy(dataId = null) },
    events = draft.events.map { it.copy(dataId = null) },
    websites = draft.websites.map { it.copy(dataId = null) },
    groups = draft.groups.mapValues { null },
))

private fun nameValues(d: Draft): Map<String, Any?>? {
    val parts = listOf(d.prefix, d.given, d.middle, d.family, d.suffix).map { it.trim() }
    if (parts.all { it.isEmpty() }) {
        val whole = d.wholeName.trim()
        // Android splits a display name into parts itself when it is given no parts.
        return if (whole.isEmpty()) null else mapOf(StructuredName.DISPLAY_NAME to whole)
    }
    return mapOf(
        StructuredName.PREFIX to parts[0].ifEmpty { null },
        StructuredName.GIVEN_NAME to parts[1].ifEmpty { null },
        StructuredName.MIDDLE_NAME to parts[2].ifEmpty { null },
        StructuredName.FAMILY_NAME to parts[3].ifEmpty { null },
        StructuredName.SUFFIX to parts[4].ifEmpty { null },
        // Cleared on purpose: the store joins the parts into a display name only when it is
        // not handed one, and the old one would otherwise outlive the edit.
        StructuredName.DISPLAY_NAME to null,
    )
}

private fun organisationValues(d: Draft): Map<String, Any?>? {
    val company = d.company.trim()
    val title = d.jobTitle.trim()
    if (company.isEmpty() && title.isEmpty()) return null
    return mapOf(
        Organization.COMPANY to company.ifEmpty { null },
        Organization.TITLE to title.ifEmpty { null },
    )
}

private fun single(column: String, value: String): Map<String, Any?>? =
    value.trim().let { if (it.isEmpty()) null else mapOf(column to it) }

/** A kind a person has at most one of: written, rewritten, or taken away. */
private fun one(ops: MutableList<Op>, row: Long?, mime: String, old: Map<String, Any?>?, new: Map<String, Any?>?) {
    when {
        new == null -> if (row != null) ops += Op.Delete(row)
        row == null -> ops += Op.Insert(mime, new)
        new != old -> ops += Op.Update(row, new)
    }
}

/** A kind a person can have several of, matched up by the row each value came from. */
private fun many(ops: MutableList<Op>, mime: String, column: String, before: List<Field>, after: List<Field>) {
    val kept = after.mapNotNull { it.dataId }.toSet()
    for (field in before) {
        val row = field.dataId ?: continue
        if (row !in kept) ops += Op.Delete(row)
    }
    val was = before.associateBy { it.dataId }
    for (field in after) {
        val value = field.value.trim()
        val row = field.dataId
        val values = mapOf(
            column to value,
            "data2" to field.type, // TYPE, the same column for every kind
            "data3" to field.label?.takeIf { it.isNotBlank() }, // LABEL, likewise
        )
        when {
            row == null -> if (value.isNotEmpty()) ops += Op.Insert(mime, values)
            value.isEmpty() -> ops += Op.Delete(row)
            was[row] != field.copy(value = value) -> ops += Op.Update(row, values)
        }
    }
}
