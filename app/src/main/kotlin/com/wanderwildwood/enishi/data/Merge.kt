package com.wanderwildwood.enishi.data

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website

/**
 * One row of the store exactly as it is kept: every column, so a row moved from one person to
 * another arrives as it left — a photo, a messenger handle, a label another app wrote, all of it.
 * [values] holds `data1` to `data15`; a column the row leaves empty is not in it.
 */
data class StoreRow(val id: Long, val mime: String, val values: Map<String, Any?>) {
    val text: String get() = (values["data1"] as? String).orEmpty()
}

/**
 * What it takes to make one person of several: everything the others have that the one kept
 * does not, written into the kept one. Nothing the kept person already has is written twice —
 * the same number, however it is spaced, is one number — and nothing the others have is left
 * behind, whatever kind of row it is.
 *
 * The kept person's name stays. A note on both is joined into one, since the store shows only
 * one. A group is carried over only when the kept copy is in the same account as the group,
 * which is the only way a group can hold someone.
 */
fun planMerge(keep: List<StoreRow>, others: List<StoreRow>, groupsHere: Set<Long>): List<Op> {
    val ops = mutableListOf<Op>()
    val have = keep.mapNotNull { key(it) }.toMutableSet()
    val single = keep.map { it.mime }.filter { it in ONE_ONLY || it.startsWith(DAVX5) }.toMutableSet()

    val keptNote = keep.firstOrNull { it.mime == Note.CONTENT_ITEM_TYPE && it.text.isNotBlank() }
    val notes = mutableListOf<String>()

    for (row in others) {
        val mime = row.mime
        if (mime == Note.CONTENT_ITEM_TYPE) {
            val text = row.text.trim()
            if (text.isNotEmpty() && text != keptNote?.text?.trim() && text !in notes) notes += text
            continue
        }
        if (mime == GroupMembership.CONTENT_ITEM_TYPE) {
            val group = (row.values["data1"] as? Number)?.toLong() ?: row.text.toLongOrNull() ?: continue
            if (group !in groupsHere) continue
        }
        if (mime in ONE_ONLY || mime.startsWith(DAVX5)) {
            if (mime in single) continue
            if (mime != Photo.CONTENT_ITEM_TYPE && row.values.values.none { it is String && it.isNotBlank() }) continue
            single += mime
        } else {
            val k = key(row) ?: continue
            if (!have.add(k)) continue
        }
        // A photo's file number is the store's own bookkeeping for the copy it came from; the
        // store files the picture afresh from the bytes.
        ops += Op.Insert(mime, if (mime == Photo.CONTENT_ITEM_TYPE) row.values - Photo.PHOTO_FILE_ID else row.values)
    }

    if (notes.isNotEmpty()) {
        if (keptNote == null) ops += Op.Insert(Note.CONTENT_ITEM_TYPE, mapOf(Note.NOTE to notes.joinToString("\n\n")))
        else ops += Op.Update(keptNote.id, mapOf(Note.NOTE to (listOf(keptNote.text.trim()) + notes).joinToString("\n\n")))
    }
    return ops
}

/** Kinds a person has one of: the kept person's stays, or the first of the others' fills it. */
private val ONE_ONLY = setOf(StructuredName.CONTENT_ITEM_TYPE, Photo.CONTENT_ITEM_TYPE)

/** DAVx5's own bookkeeping rows: one per copy, and the kept copy's are its own. */
private const val DAVX5 = "x.davdroid/"

/** What makes two rows of a kind the same thing, or null for a row with nothing in it. */
internal fun key(row: StoreRow): String? {
    val v = row.text.trim()
    val norm = when (row.mime) {
        Phone.CONTENT_ITEM_TYPE -> v.filter(Char::isDigit).takeLast(10).ifEmpty { v }
        Email.CONTENT_ITEM_TYPE -> v.lowercase()
        StructuredPostal.CONTENT_ITEM_TYPE -> v.lowercase().replace(Regex("\\s+"), " ")
        Website.CONTENT_ITEM_TYPE -> v.lowercase().removeSuffix("/")
        Organization.CONTENT_ITEM_TYPE -> listOf(v, (row.values["data4"] as? String).orEmpty().trim()).joinToString("|").lowercase()
        GroupMembership.CONTENT_ITEM_TYPE -> (row.values["data1"] ?: v).toString()
        else -> v.lowercase()
    }
    return if (norm.isBlank() || norm == "|") null else "${row.mime}:$norm"
}
