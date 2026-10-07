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
        // Surnames, from each person's name row, for the bold half of a row.
        val family = mutableMapOf<Long, String>()
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.CONTACT_ID, StructuredName.FAMILY_NAME),
            "${Data.MIMETYPE}=?", arrayOf(StructuredName.CONTENT_ITEM_TYPE), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val f = c.getString(1)?.trim().orEmpty()
                if (f.isNotEmpty()) family.putIfAbsent(c.getLong(0), f)
            }
        }
        return out.map { it.copy(family = family[it.id].orEmpty()) }
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

    /**
     * One person, every part of them this app shows, with the row each came from and every
     * copy of it. A person Android has joined from several stores (the phone and an account,
     * usually) is read as one: the same number held twice is one number here, carrying both
     * rows, so an edit reaches both. The name is the one Android displays — its own choice of
     * copy, not the oldest.
     */
    fun card(contactId: Long, lastNameFirst: Boolean): Card? {
        val name = if (lastNameFirst) Contacts.DISPLAY_NAME_ALTERNATIVE else Contacts.DISPLAY_NAME_PRIMARY
        var lookup = ""
        var display = ""
        var starred = false
        var nameRaw = -1L
        resolver.query(
            ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId),
            arrayOf(Contacts.LOOKUP_KEY, name, Contacts.STARRED, Contacts.NAME_RAW_CONTACT_ID),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return null
            lookup = c.getString(0).orEmpty()
            display = c.getString(1).orEmpty()
            starred = c.getInt(2) != 0
            nameRaw = if (c.isNull(3)) -1L else c.getLong(3)
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

        // Only copies this app may write are edited; a messenger's copy (WhatsApp, Signal) is
        // that app's own and its sync would undo the change. A person kept only there is shown
        // as it is and cannot be edited.
        val syncing = syncingTypes()
        val writable = parts.filter { kept(it.account, syncing).writable }.map { it.rawId }.toSet()
        val readOnly = writable.isEmpty()

        class Row(val id: Long, val mime: String, val d: List<String>, val type: Int, val label: String?)
        val rows = mutableListOf<Row>()
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data._ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5, Data.DATA6, Data.RAW_CONTACT_ID),
            "${Data.CONTACT_ID}=?",
            arrayOf(contactId.toString()),
            // The displayed name's copy first, so its values lead and the rest are its copies.
            "CASE WHEN ${Data.RAW_CONTACT_ID}=$nameRaw THEN 0 ELSE 1 END, ${Data.IS_SUPER_PRIMARY} DESC, ${Data.IS_PRIMARY} DESC, ${Data._ID} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                if (!readOnly && c.getLong(8) !in writable) continue
                rows += Row(
                    c.getLong(0), c.getString(1).orEmpty(),
                    (2..7).map { c.getString(it).orEmpty() },
                    c.getInt(3), c.getString(4),
                )
            }
        }

        fun of(mime: String) = rows.filter { it.mime == mime }
        /** The first row, and every later row holding the same thing by [key]. */
        fun firstAndCopies(mime: String, key: (Row) -> List<String>): Pair<Row, List<Long>>? {
            val all = of(mime).filter { key(it).any { v -> v.isNotBlank() } }
            val first = all.firstOrNull() ?: return null
            return first to all.drop(1).filter { key(it) == key(first) }.map { it.id }
        }
        /** One field per distinct value, carrying every row that holds it. */
        fun fields(mime: String, norm: (String) -> String): List<Field> =
            of(mime).filter { it.d[0].isNotBlank() }
                .groupBy { norm(it.d[0]) }.values
                .map { same -> same.first().let { f -> Field(f.d[0], f.type, f.label, f.id, same.drop(1).map { it.id }) } }

        var d = Draft(starred = starred)
        // DATA1 display, DATA2 given, DATA3 family, DATA4 prefix, DATA5 middle, DATA6 suffix
        firstAndCopies(StructuredName.CONTENT_ITEM_TYPE) { it.d }?.let { (r, copies) ->
            d = d.copy(
                nameDataId = r.id, nameCopies = copies, wholeName = r.d[0], given = r.d[1], family = r.d[2],
                prefix = r.d[3], middle = r.d[4], suffix = r.d[5],
            )
        }
        firstAndCopies(Nickname.CONTENT_ITEM_TYPE) { listOf(it.d[0].trim()) }?.let { (r, copies) ->
            d = d.copy(nickname = r.d[0], nicknameDataId = r.id, nicknameCopies = copies)
        }
        // DATA1 company, DATA4 title
        firstAndCopies(Organization.CONTENT_ITEM_TYPE) { listOf(it.d[0].trim(), it.d[3].trim()) }?.let { (r, copies) ->
            d = d.copy(company = r.d[0], jobTitle = r.d[3], organisationDataId = r.id, organisationCopies = copies)
        }
        firstAndCopies(Note.CONTENT_ITEM_TYPE) { listOf(it.d[0].trim()) }?.let { (r, copies) ->
            d = d.copy(note = r.d[0], noteDataId = r.id, noteCopies = copies)
        }
        d = d.copy(
            phones = fields(Phone.CONTENT_ITEM_TYPE) { v -> v.filter(Char::isDigit).takeLast(10).ifEmpty { v.trim() } },
            emails = fields(Email.CONTENT_ITEM_TYPE) { it.trim().lowercase() },
            addresses = fields(StructuredPostal.CONTENT_ITEM_TYPE) { it.trim().lowercase().replace(Regex("\\s+"), " ") },
            events = fields(Event.CONTENT_ITEM_TYPE) { it.trim() },
            websites = fields(Website.CONTENT_ITEM_TYPE) { it.trim().lowercase().removeSuffix("/") },
            groups = of(GroupMembership.CONTENT_ITEM_TYPE)
                .filter { it.d[0].isNotBlank() }
                .groupBy { it.d[0].toLong() }
                .mapValues { (_, rs) -> rs.map { it.id } },
        )
        // Parts present: the editor shows those, and the whole name is the store's to rebuild.
        if (listOf(d.prefix, d.given, d.middle, d.family, d.suffix).any { it.isNotBlank() }) d = d.copy(wholeName = "")
        return Card(contactId, lookup, display, d, parts, readOnly)
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

    /** The address another app is handed for a person; the plain one if they have no lookup key. */
    fun lookupUri(contactId: Long, lookup: String): Uri =
        lookup.takeIf { it.isNotEmpty() }?.let { Contacts.getLookupUri(contactId, it) }
            ?: ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId)

    // ---------------------------------------------------------------- accounts

    private companion object {
        /**
         * Address books known to sync up to a server, counted as syncing even when the phone
         * does not say so: the sync services list leaves out what this app is not allowed to see.
         */
        val KNOWN_SYNCING = setOf("at.bitfire.davdroid.address_book", "com.google", "com.nextcloud.client", "com.etesync.syncadapter.address_book")
    }

    /** Account types whose contacts sync up to somewhere — the ones a new contact may go to. */
    private fun syncingTypes(): Set<String> = KNOWN_SYNCING + runCatching {
        ContentResolver.getSyncAdapterTypes()
            .filter { it.authority == ContactsContract.AUTHORITY && it.supportsUploading() }
            .map { it.accountType }.toSet()
    }.getOrDefault(emptySet())

    /**
     * Whether this app may write into a store, and whether it syncs. The phone itself, and a
     * phone's own local account (Mudita's "Phone"), take writes and keep them; an account that
     * syncs contacts up takes them and sends them on. Anything else — a messenger's account,
     * WhatsApp's, Signal's — is that app's own list, and its sync would remove whatever was
     * written there.
     */
    fun kept(account: Account, syncing: Set<String> = syncingTypes()): Kept {
        val type = account.type.orEmpty()
        val sync = type in syncing
        val local = account.isPhone || listOf("local", "phone", "device", "sim").any { type.lowercase().contains(it) }
        return Kept(writable = sync || local, syncing = sync)
    }

    /** How many people each store holds. */
    private fun counts(): Map<Account, Int> {
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
        return counts
    }

    /**
     * Where contacts can be kept: the phone itself, and every account this app may write to
     * that already holds some or has registered to sync them. Never a messenger's list.
     */
    fun accounts(): List<Account> {
        val syncing = syncingTypes()
        val found = linkedSetOf(Account.PHONE)
        counts().keys.forEach { found += it }
        runCatching {
            AccountManager.get(app).accounts
                .filter { it.type in syncing }
                .forEach { found += Account(it.name, it.type) }
        }
        return found.filter { kept(it, syncing).writable }
    }

    /**
     * Where a new contact most likely belongs: the syncing account holding the most people,
     * so it reaches the reader's other devices as the rest do; failing that, wherever this
     * phone keeps most of them; failing that, the phone.
     */
    fun busiestAccount(): Account {
        val syncing = syncingTypes()
        val counts = counts().filterKeys { kept(it, syncing).writable }
        return counts.filterKeys { kept(it, syncing).syncing }.maxByOrNull { it.value }?.key
            ?: counts.maxByOrNull { it.value }?.key
            ?: Account.PHONE
    }

    /**
     * Which of a person's copies gets anything new: the one in [prefer] if they have one there,
     * else a copy that syncs, else one this app may write to. Never a messenger's copy.
     */
    fun target(card: Card, prefer: Account?): Part? {
        val syncing = syncingTypes()
        val writable = card.parts.filter { kept(it.account, syncing).writable }
        return writable.firstOrNull { it.account == prefer }
            ?: writable.firstOrNull { kept(it.account, syncing).syncing }
            ?: writable.firstOrNull()
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
        val target = target(card, prefer) ?: error("no copy of this person can be written to")
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

    /** Every row of these raw contacts, every column, as the store keeps them. */
    private fun storeRows(rawIds: Collection<Long>): List<StoreRow> {
        if (rawIds.isEmpty()) return emptyList()
        val columns = (1..15).map { "data$it" }
        val out = mutableListOf<StoreRow>()
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data._ID, Data.MIMETYPE) + columns,
            "${Data.RAW_CONTACT_ID} IN (${rawIds.joinToString(",")})",
            null, "${Data.RAW_CONTACT_ID} ASC, ${Data.IS_SUPER_PRIMARY} DESC, ${Data._ID} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val values = mutableMapOf<String, Any?>()
                columns.forEachIndexed { i, name ->
                    val at = i + 2
                    when (c.getType(at)) {
                        android.database.Cursor.FIELD_TYPE_INTEGER -> values[name] = c.getLong(at)
                        android.database.Cursor.FIELD_TYPE_FLOAT -> values[name] = c.getDouble(at)
                        android.database.Cursor.FIELD_TYPE_BLOB -> values[name] = c.getBlob(at)
                        android.database.Cursor.FIELD_TYPE_STRING -> values[name] = c.getString(at)
                        else -> Unit
                    }
                }
                out += StoreRow(c.getLong(0), c.getString(1).orEmpty(), values)
            }
        }
        return out
    }

    /**
     * Makes one person of several, by hand: everything the others have that [keepId] lacks is
     * written into the kept person's own copy, and then the others' copies go. A copy kept by a
     * messenger (WhatsApp, Signal) is that app's to keep, so it is joined to the kept person
     * instead of deleted — Android then shows them as one, as it does for any two copies.
     *
     * One batch: either all of it happens or none of it does. Returns the kept person's id.
     */
    fun merge(keepId: Long, otherIds: Collection<Long>): Long {
        val keep = card(keepId, false) ?: error("the person to keep is gone")
        val target = target(keep, null) ?: error("the person to keep cannot be written to")
        val syncing = syncingTypes()
        val keptRows = storeRows(keep.parts.filter { kept(it.account, syncing).writable }.map { it.rawId })

        val othersWritable = mutableListOf<Long>()
        val othersJoined = mutableListOf<Long>()
        var starred = keep.draft.starred
        for (id in otherIds.filter { it != keepId }) {
            val other = card(id, false) ?: continue
            starred = starred || other.draft.starred
            for (part in other.parts) {
                if (kept(part.account, syncing).writable) othersWritable += part.rawId else othersJoined += part.rawId
            }
        }
        val groupsHere = groups().filter { it.account == target.account }.map { it.id }.toSet()
        val plan = planMerge(keptRows, storeRows(othersWritable), groupsHere)

        val ops = arrayListOf<ContentProviderOperation>()
        for (op in plan) {
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
        for (raw in othersJoined) {
            ops += ContentProviderOperation.newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI)
                .withValue(ContactsContract.AggregationExceptions.TYPE, ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER)
                .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, target.rawId)
                .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, raw)
                .build()
        }
        // Deleted as any app deletes: marked, so an account's sync removes the copy it holds too.
        for (raw in othersWritable) {
            ops += ContentProviderOperation.newDelete(ContentUris.withAppendedId(RawContacts.CONTENT_URI, raw)).build()
        }
        if (starred && !keep.draft.starred) {
            ops += ContentProviderOperation.newUpdate(ContentUris.withAppendedId(RawContacts.CONTENT_URI, target.rawId))
                .withValue(RawContacts.STARRED, 1)
                .build()
        }
        if (ops.isNotEmpty()) resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        return resolver.query(
            ContentUris.withAppendedId(RawContacts.CONTENT_URI, target.rawId),
            arrayOf(RawContacts.CONTACT_ID), null, null, null,
        )?.use { if (it.moveToFirst()) it.getLong(0) else null } ?: keepId
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

    /** Everyone with a copy kept in [account] — the only people a group there can take. */
    fun keptIn(account: Account): Set<Long> {
        val ids = mutableSetOf<Long>()
        resolver.query(
            RawContacts.CONTENT_URI, arrayOf(RawContacts.CONTACT_ID),
            "${RawContacts.DELETED}=0 AND " + accountWhere(account), accountArgs(account), null,
        )?.use { c -> while (c.moveToNext()) ids += c.getLong(0) }
        return ids
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
