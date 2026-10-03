package com.wanderwildwood.enishi.data

/**
 * One person in the list: what it takes to draw a row and to find them again. Everything else
 * is read when they are opened, so the list of a few hundred stays one query.
 */
data class Person(
    val id: Long,
    val lookup: String,
    val name: String,
    /** The letter the phone's own contacts store files them under, in the reader's language. */
    val section: String,
    val starred: Boolean,
)

/** What search looks in beyond the name: every number, address and company a person has. */
data class Findable(
    val numbers: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val organisations: List<String> = emptyList(),
    val nicknames: List<String> = emptyList(),
)

/**
 * Where a contact is kept. A null name and type is the phone itself, which is where a phone
 * with no Google account keeps everything.
 */
data class Account(val name: String?, val type: String?) {
    val isPhone: Boolean get() = name == null && type == null

    companion object {
        val PHONE = Account(null, null)
    }
}

/**
 * One value of a kind a person can have several of — a number, an address. [dataId] is the
 * row it came from, null for one added in the editor; [type] and [label] are Android's own,
 * so a custom label written by another app survives a round trip untouched.
 */
data class Field(
    val value: String,
    val type: Int,
    val label: String? = null,
    val dataId: Long? = null,
)

/**
 * A contact as the editor holds it, and as a vCard reads into. Loaded from the phone it carries
 * the row each value came from, which is what lets a save change only what was changed: the
 * photo, a ringtone, a messenger handle — everything this app does not edit — is never touched.
 */
data class Draft(
    val prefix: String = "",
    val given: String = "",
    val middle: String = "",
    val family: String = "",
    val suffix: String = "",
    /** A name with no parts, as a vCard with only FN gives one. Android splits it itself. */
    val wholeName: String = "",
    val nameDataId: Long? = null,
    val nickname: String = "",
    val nicknameDataId: Long? = null,
    val phones: List<Field> = emptyList(),
    val emails: List<Field> = emptyList(),
    val addresses: List<Field> = emptyList(),
    val events: List<Field> = emptyList(),
    val websites: List<Field> = emptyList(),
    val company: String = "",
    val jobTitle: String = "",
    val organisationDataId: Long? = null,
    val note: String = "",
    val noteDataId: Long? = null,
    /** Group id to the membership row that puts them there (null for one added here). */
    val groups: Map<Long, Long?> = emptyMap(),
    val starred: Boolean = false,
) {
    /** A name, in the order it is spoken, for a vCard with parts and no FN. */
    val spokenName: String
        get() = listOf(prefix, given, middle, family).filter { it.isNotBlank() }.joinToString(" ")
            .let { if (suffix.isNotBlank()) "$it, $suffix" else it }
            .ifBlank { wholeName }

    /** Nothing worth saving: no name, no number, nothing a person could be found by. */
    val isEmpty: Boolean
        get() = spokenName.isBlank() && nickname.isBlank() && company.isBlank() &&
            (phones + emails + addresses + websites + events).all { it.value.isBlank() } &&
            note.isBlank()
}

/** A contact opened from the list: the editable part, and what it is made of. */
data class Card(
    val id: Long,
    val lookup: String,
    val name: String,
    val draft: Draft,
    /** The raw contacts this one is joined from, each in its own account. */
    val parts: List<Part>,
)

data class Part(val rawId: Long, val account: Account)

data class Group(
    val id: Long,
    val title: String,
    val account: Account,
    val size: Int,
)
