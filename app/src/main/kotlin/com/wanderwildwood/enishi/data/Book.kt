package com.wanderwildwood.enishi.data

import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
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
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import java.io.OutputStream

/**
 * The phone's own contacts store, which is where everything this app shows lives. It keeps
 * no copy of its own: every other app that reads contacts — Messaging, the dialer, the
 * calendar's birthdays — sees exactly what is here, and nothing is lost if this app is.
 */
class Book(context: Context) {
    private val app = context.applicationContext
    private val resolver: ContentResolver = app.contentResolver

    // ---------------------------------------------------------------- reading

    /**
     * Everyone, in the store's own order: it sorts by the reader's language (Japanese by
     * reading, German with its umlauts), and says which letter each person files under.
     */
    fun people(lastNameFirst: Boolean, sortByLast: Boolean): List<Person> {
        val name = if (lastNameFirst) Contacts.DISPLAY_NAME_ALTERNATIVE else Contacts.DISPLAY_NAME_PRIMARY
        val sort = if (sortByLast) Contacts.SORT_KEY_ALTERNATIVE else Contacts.SORT_KEY_PRIMARY
        // The store's own index letter for each person. The columns are not in the public SDK,
        // but every Android since 4.4 answers to them; an older or odd store that does not is
        // asked again without, and the letter is taken from the name instead.
        val label = if (sortByLast) "phonebook_label_alt" else "phonebook_label"
        val out = mutableListOf<Person>()
        val cursor = runCatching {
            resolver.query(Contacts.CONTENT_URI, arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, name, label, Contacts.STARRED), null, null, "$sort COLLATE LOCALIZED ASC")
        }.getOrNull() ?: resolver.query(Contacts.CONTENT_URI, arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, name, Contacts.LOOKUP_KEY, Contacts.STARRED), null, null, "$sort COLLATE LOCALIZED ASC")
        val labelled = cursor?.getColumnName(3) == label
        cursor?.use { c ->
            while (c.moveToNext()) {
                out += Person(
                    id = c.getLong(0),
                    lookup = c.getString(1).orEmpty(),
                    name = c.getString(2)?.takeIf { it.isNotBlank() } ?: "",
                    section = (if (labelled) c.getString(3) else null)?.takeIf { it.isNotBlank() }
                        ?: c.getString(2)?.firstOrNull()?.takeIf { it.isLetter() }?.uppercase() ?: "#",
                    starred = c.getInt(4) != 0,
                )
            }
        }
        return out
    }

    /** Every number, address, company and nickname, by person, for search to look in. */
    fun findable(): Map<Long, Findable> {
        val numbers = mutableMapOf<Long, MutableList<String>>()
        val emails = mutableMapOf<Long, MutableList<String>>()
        val orgs = mutableMapOf<Long, MutableList<String>>()
        val nicks = mutableMapOf<Long, MutableList<String>>()
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.CONTACT_ID, Data.MIMETYPE, Data.DATA1),
            "${Data.MIMETYPE} IN (?,?,?,?)",
            arrayOf(Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, Organization.CONTENT_ITEM_TYPE, Nickname.CONTENT_ITEM_TYPE),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val value = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                val into = when (c.getString(1)) {
                    Phone.CONTENT_ITEM_TYPE -> numbers
                    Email.CONTENT_ITEM_TYPE -> emails
                    Organization.CONTENT_ITEM_TYPE -> orgs
                    else -> nicks
                }
                into.getOrPut(id) { mutableListOf() } += value
            }
        }
        return (numbers.keys + emails.keys + orgs.keys + nicks.keys).associateWith {
            Findable(numbers[it].orEmpty(), emails[it].orEmpty(), orgs[it].orEmpty(), nicks[it].orEmpty())
        }
    }

    /** One person, every part of them this app shows, with the row each came from. */
    fun card(contactId: Long, lastNameFirst: Boolean): Card? {
        val name = if (lastNameFirst) Contacts.DISPLAY_NAME_ALTERNATIVE else Contacts.DISPLAY_NAME_PRIMARY
        var lookup = ""
        var display = ""
        var starred = false
        resolver.query(
            ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId),
            arrayOf(Contacts.LOOKUP_KEY, name, Contacts.STARRED),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return null
            lookup = c.getString(0).orEmpty()
            display = c.getString(1).orEmpty()
            starred = c.getInt(2) != 0
        } ?: return null

        val parts = mutableListOf<Part>()
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
            "${RawContacts.CONTACT_ID}=? AND ${RawContacts.DELETED}=0",
            arrayOf(contactId.toString()),
            "${RawContacts._ID} ASC",
        )?.use { c ->
            while (c.moveToNext()) parts += Part(c.getLong(0), Account(c.getString(1), c.getString(2)))
        }

        var d = Draft(starred = starred)
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(
                Data._ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3,
                Data.DATA4, Data.DATA5, Data.DATA6, Data.IS_SUPER_PRIMARY,
            ),
            "${Data.CONTACT_ID}=?",
            arrayOf(contactId.toString()),
            // The primary name first, so a joined contact edits the name it is shown by.
            "${Data.IS_SUPER_PRIMARY} DESC, ${Data.IS_PRIMARY} DESC, ${Data._ID} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val s = { i: Int -> c.getString(i).orEmpty() }
                fun field(): Field = Field(s(2), c.getInt(3), c.getString(4), id)
                when (c.getString(1)) {
                    StructuredName.CONTENT_ITEM_TYPE -> if (d.nameDataId == null) {
                        // DATA1 display, DATA2 given, DATA3 family, DATA4 prefix, DATA5 middle, DATA6 suffix
                        d = d.copy(
                            nameDataId = id, wholeName = s(2), given = s(3), family = s(4),
                            prefix = s(5), middle = s(6), suffix = s(7),
                        )
                    }
                    Nickname.CONTENT_ITEM_TYPE -> if (d.nicknameDataId == null) d = d.copy(nickname = s(2), nicknameDataId = id)
                    Organization.CONTENT_ITEM_TYPE -> if (d.organisationDataId == null) {
                        // DATA1 company, DATA4 title
                        d = d.copy(company = s(2), jobTitle = s(5), organisationDataId = id)
                    }
                    Note.CONTENT_ITEM_TYPE -> if (d.noteDataId == null && s(2).isNotBlank()) d = d.copy(note = s(2), noteDataId = id)
                    Phone.CONTENT_ITEM_TYPE -> d = d.copy(phones = d.phones + field())
                    Email.CONTENT_ITEM_TYPE -> d = d.copy(emails = d.emails + field())
                    StructuredPostal.CONTENT_ITEM_TYPE -> d = d.copy(addresses = d.addresses + field())
                    Event.CONTENT_ITEM_TYPE -> d = d.copy(events = d.events + field())
                    Website.CONTENT_ITEM_TYPE -> d = d.copy(websites = d.websites + field())
                    GroupMembership.CONTENT_ITEM_TYPE -> d = d.copy(groups = d.groups + (c.getLong(2) to id))
                }
            }
        }
        // Parts present: the editor shows those, and the whole name is the store's to rebuild.
        if (listOf(d.prefix, d.given, d.middle, d.family, d.suffix).any { it.isNotBlank() }) d = d.copy(wholeName = "")
        return Card(contactId, lookup, display, d, parts)
    }

    /** The contact a link from another app points at: a lookup, a raw contact, or a row. */
    fun resolve(uri: Uri): Long? {
        val path = uri.path.orEmpty()
        return runCatching {
            when {
                path.contains("/raw_contacts/") -> resolver.query(uri, arrayOf(RawContacts.CONTACT_ID), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getLong(0) else null }
                path.contains("/data/") -> resolver.query(uri, arrayOf(Data.CONTACT_ID), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getLong(0) else null }
                else -> Contacts.lookupContact(resolver, uri)?.let(ContentUris::parseId)
                    ?: resolver.query(uri, arrayOf(Contacts._ID), null, null, null)
                        ?.use { if (it.moveToFirst()) it.getLong(0) else null }
            }
        }.getOrNull()
    }

    /** Who has this number or address, for "show or add" from another app. */
    fun findByNumber(number: String): Long? = runCatching {
        resolver.query(
            Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
            arrayOf(ContactsContract.PhoneLookup._ID), null, null, null,
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }
    }.getOrNull()

    fun findByEmail(address: String): Long? = runCatching {
        resolver.query(
            Data.CONTENT_URI, arrayOf(Data.CONTACT_ID),
            "${Data.MIMETYPE}=? AND ${Email.ADDRESS}=? COLLATE NOCASE",
            arrayOf(Email.CONTENT_ITEM_TYPE, address), null,
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }
    }.getOrNull()

    fun lookupUri(contactId: Long, lookup: String): Uri = Contacts.getLookupUri(contactId, lookup)

    // ---------------------------------------------------------------- accounts

    /**
     * Where contacts can be kept: the phone itself, and every account that already holds some
     * or has registered to sync them. On a phone with no accounts at all, only the phone.
     */
    fun accounts(): List<Account> {
        val found = linkedSetOf(Account.PHONE)
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
            "${RawContacts.DELETED}=0", null, null,
        )?.use { c -> while (c.moveToNext()) found += Account(c.getString(0), c.getString(1)) }
        runCatching {
            val syncing = ContentResolver.getSyncAdapterTypes()
                .filter { it.authority == ContactsContract.AUTHORITY && it.supportsUploading() }
                .map { it.accountType }.toSet()
            AccountManager.get(app).accounts
                .filter { it.type in syncing }
                .forEach { found += Account(it.name, it.type) }
        }
        return found.toList()
    }

    /** The account holding the most contacts, which is where a new one most likely belongs. */
    fun busiestAccount(): Account {
        val counts = mutableMapOf<Account, Int>()
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
            "${RawContacts.DELETED}=0", null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val a = Account(c.getString(0), c.getString(1))
                counts[a] = (counts[a] ?: 0) + 1
            }
        }
        return counts.maxByOrNull { it.value }?.key ?: Account.PHONE
    }

    // ---------------------------------------------------------------- writing

    /** Writes a new contact and returns its id, or null if the store would not take it. */
    fun create(draft: Draft, account: Account): Long? {
        val ops = arrayListOf<ContentProviderOperation>()
        ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            .withValue(RawContacts.ACCOUNT_NAME, account.name)
            .withValue(RawContacts.ACCOUNT_TYPE, account.type)
            .withValue(RawContacts.STARRED, if (draft.starred) 1 else 0)
            .build()
        for (op in planNew(draft)) {
            if (op is Op.Insert) {
                ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE, op.mime)
                    .also { b -> op.values.forEach { (k, v) -> b.withValue(k, v) } }
                    .build()
            }
        }
        val results = resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        val rawId = results.firstOrNull()?.uri?.let(ContentUris::parseId) ?: return null
        return resolver.query(
            ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId),
            arrayOf(RawContacts.CONTACT_ID), null, null, null,
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }
    }

    /**
     * Saves an edit: only what changed, and anything new goes into [card]'s first part kept in
     * [prefer] if there is one — so a contact joined from the phone and an account grows in the
     * place it was mostly kept, and never quietly spreads into a third.
     */
    fun save(card: Card, after: Draft, prefer: Account?) {
        val target = card.parts.firstOrNull { it.account == prefer } ?: card.parts.firstOrNull() ?: return
        val ops = arrayListOf<ContentProviderOperation>()
        for (op in plan(card.draft, after)) {
            ops += when (op) {
                is Op.Insert -> ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValue(Data.RAW_CONTACT_ID, target.rawId)
                    .withValue(Data.MIMETYPE, op.mime)
                    .also { b -> op.values.forEach { (k, v) -> b.withValue(k, v) } }
                    .build()
                is Op.Update -> ContentProviderOperation.newUpdate(ContentUris.withAppendedId(Data.CONTENT_URI, op.dataId))
                    .also { b -> op.values.forEach { (k, v) -> b.withValue(k, v) } }
                    .build()
                is Op.Delete -> ContentProviderOperation.newDelete(ContentUris.withAppendedId(Data.CONTENT_URI, op.dataId)).build()
            }
        }
        if (after.starred != card.draft.starred) {
            ops += ContentProviderOperation.newUpdate(ContentUris.withAppendedId(Contacts.CONTENT_URI, card.id))
                .withValue(Contacts.STARRED, if (after.starred) 1 else 0)
                .build()
        }
        if (ops.isNotEmpty()) resolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    fun star(contactId: Long, starred: Boolean) {
        resolver.update(
            ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId),
            android.content.ContentValues().apply { put(Contacts.STARRED, if (starred) 1 else 0) },
            null, null,
        )
    }

    /** Deletes a person from every account they are kept in, as the store does. */
    fun delete(contactId: Long, lookup: String) {
        resolver.delete(Contacts.getLookupUri(contactId, lookup) ?: ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId), null, null)
    }

    // ---------------------------------------------------------------- groups

    /** Groups a person can be put in. The store's own "starred" and "everyone" lists are not. */
    fun groups(): List<Group> {
        val sizes = mutableMapOf<Long, Int>()
        resolver.query(
            Data.CONTENT_URI, arrayOf(GroupMembership.GROUP_ROW_ID),
            "${Data.MIMETYPE}=?", arrayOf(GroupMembership.CONTENT_ITEM_TYPE), null,
        )?.use { c -> while (c.moveToNext()) c.getLong(0).let { sizes[it] = (sizes[it] ?: 0) + 1 } }
        val out = mutableListOf<Group>()
        resolver.query(
            Groups.CONTENT_URI,
            arrayOf(Groups._ID, Groups.TITLE, Groups.ACCOUNT_NAME, Groups.ACCOUNT_TYPE),
            "${Groups.DELETED}=0 AND ${Groups.AUTO_ADD}=0 AND ${Groups.FAVORITES}=0",
            null, "${Groups.TITLE} COLLATE LOCALIZED ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val title = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                out += Group(c.getLong(0), title, Account(c.getString(2), c.getString(3)), sizes[c.getLong(0)] ?: 0)
            }
        }
        return out
    }

    fun members(groupId: Long): Set<Long> {
        val ids = mutableSetOf<Long>()
        resolver.query(
            Data.CONTENT_URI, arrayOf(Data.CONTACT_ID),
            "${Data.MIMETYPE}=? AND ${GroupMembership.GROUP_ROW_ID}=?",
            arrayOf(GroupMembership.CONTENT_ITEM_TYPE, groupId.toString()), null,
        )?.use { c -> while (c.moveToNext()) ids += c.getLong(0) }
        return ids
    }

    fun createGroup(title: String, account: Account): Long? =
        resolver.insert(
            Groups.CONTENT_URI,
            android.content.ContentValues().apply {
                put(Groups.TITLE, title)
                put(Groups.ACCOUNT_NAME, account.name)
                put(Groups.ACCOUNT_TYPE, account.type)
                put(Groups.GROUP_VISIBLE, 1)
            },
        )?.let(ContentUris::parseId)

    fun renameGroup(groupId: Long, title: String) {
        resolver.update(
            ContentUris.withAppendedId(Groups.CONTENT_URI, groupId),
            android.content.ContentValues().apply { put(Groups.TITLE, title) }, null, null,
        )
    }

    /** The group goes; the people in it stay, as they are everywhere else. */
    fun deleteGroup(groupId: Long) {
        resolver.delete(ContentUris.withAppendedId(Groups.CONTENT_URI, groupId), null, null)
    }

    /**
     * Puts people in a group. A group belongs to one account, and only a person kept in that
     * account can join it; returns how many could not, so the reader is told rather than left
     * wondering why someone did not appear.
     */
    fun addToGroup(group: Group, contactIds: Collection<Long>): Int {
        var refused = 0
        val ops = arrayListOf<ContentProviderOperation>()
        for (id in contactIds) {
            val raw = resolver.query(
                RawContacts.CONTENT_URI, arrayOf(RawContacts._ID),
                "${RawContacts.CONTACT_ID}=? AND ${RawContacts.DELETED}=0 AND " +
                    accountWhere(group.account),
                arrayOf(id.toString()) + accountArgs(group.account), "${RawContacts._ID} ASC",
            )?.use { if (it.moveToFirst()) it.getLong(0) else null }
            if (raw == null) {
                refused++
                continue
            }
            ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValue(Data.RAW_CONTACT_ID, raw)
                .withValue(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE)
                .withValue(GroupMembership.GROUP_ROW_ID, group.id)
                .build()
        }
        if (ops.isNotEmpty()) resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        return refused
    }

    fun removeFromGroup(groupId: Long, contactId: Long) {
        resolver.delete(
            Data.CONTENT_URI,
            "${Data.MIMETYPE}=? AND ${GroupMembership.GROUP_ROW_ID}=? AND ${Data.CONTACT_ID}=?",
            arrayOf(GroupMembership.CONTENT_ITEM_TYPE, groupId.toString(), contactId.toString()),
        )
    }

    /** Every number of everyone in a group — the mobile where a person has one. */
    fun numbersFor(contactIds: Collection<Long>): List<String> = bestOf(contactIds, Phone.CONTENT_ITEM_TYPE, Phone.TYPE_MOBILE)

    fun emailsFor(contactIds: Collection<Long>): List<String> = bestOf(contactIds, Email.CONTENT_ITEM_TYPE, null)

    private fun bestOf(contactIds: Collection<Long>, mime: String, preferType: Int?): List<String> {
        if (contactIds.isEmpty()) return emptyList()
        val best = mutableMapOf<Long, Pair<Int, String>>()
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.CONTACT_ID, Data.DATA1, Data.DATA2, Data.IS_SUPER_PRIMARY),
            "${Data.MIMETYPE}=? AND ${Data.CONTACT_ID} IN (${contactIds.joinToString(",")})",
            arrayOf(mime), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val value = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                val score = when {
                    c.getInt(3) != 0 -> 3
                    preferType != null && c.getInt(2) == preferType -> 2
                    else -> 1
                }
                val id = c.getLong(0)
                if ((best[id]?.first ?: 0) < score) best[id] = score to value
            }
        }
        return best.values.map { it.second }
    }

    private fun accountWhere(a: Account): String =
        if (a.isPhone) "${RawContacts.ACCOUNT_NAME} IS NULL AND ${RawContacts.ACCOUNT_TYPE} IS NULL"
        else "${RawContacts.ACCOUNT_NAME}=? AND ${RawContacts.ACCOUNT_TYPE}=?"

    private fun accountArgs(a: Account): Array<String> =
        if (a.isPhone) emptyArray() else arrayOf(a.name.orEmpty(), a.type.orEmpty())

    // ---------------------------------------------------------------- vCards

    /**
     * Writes people as one .vcf, in the store's own words — Android composes every vCard
     * itself, photos and all, which is the surest way the file reads back into anything.
     */
    fun exportTo(lookups: List<String>, out: OutputStream) {
        // In batches: the lookups travel in the address, and an address has a length.
        for (batch in lookups.filter { it.isNotEmpty() }.chunked(100)) {
            val uri = Uri.withAppendedPath(Contacts.CONTENT_MULTI_VCARD_URI, Uri.encode(batch.joinToString(":")))
            resolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                fd.createInputStream().use { it.copyTo(out) }
            }
        }
    }

    fun vcardOf(lookup: String, out: OutputStream) {
        val uri = Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, lookup)
        resolver.openAssetFileDescriptor(uri, "r")?.use { fd -> fd.createInputStream().use { it.copyTo(out) } }
    }
}
