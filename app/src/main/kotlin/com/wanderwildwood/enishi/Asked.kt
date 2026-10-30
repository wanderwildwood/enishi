package com.wanderwildwood.enishi

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.Intents.Insert
import com.wanderwildwood.enishi.data.Draft
import com.wanderwildwood.enishi.data.Field
import org.json.JSONArray
import org.json.JSONObject

/** What a person picks when another app asks for "a contact", and what goes back. */
enum class PickKind { CONTACT, PHONE, EMAIL, ADDRESS }

/**
 * Mudita Messaging's "New message" is not a plain pick. It asks for a contact with
 * [EXTRA] set, and the phone's own Contacts app answers with its own screen: a box to type a
 * number, a "Create Group" row that ticks several people and opens one group conversation, and a
 * single person's number. None of that is in a standard pick, so when this app took the request
 * a group could not be started at all. A request carrying [EXTRA] goes to that screen, with the
 * answer going straight back to Messaging, whenever it is on the phone.
 */
object MessagingPick {
    const val EXTRA = "_is_msg_app_request_"
    const val STOCK_PACKAGE = "com.mudita.contacts"
    const val STOCK_PICKER = "com.android.contacts.activities.ContactSelectionActivity"

    /** Whether this request is to be handed over rather than answered here. */
    fun handOver(action: String?, flagged: Boolean, stockPresent: Boolean): Boolean =
        flagged && stockPresent && (action == Intent.ACTION_PICK || action == Intent.ACTION_GET_CONTENT)

    /**
     * Whether the phone's own Contacts screen can be handed the request right now: the screen
     * exists, may be started from outside, and neither it nor its app has been switched off.
     * Asked afresh for every request, because a person who replaces that app switches it off.
     */
    fun usable(exported: Boolean, componentEnabled: Boolean, appEnabled: Boolean): Boolean =
        exported && componentEnabled && appEnabled

    /** Whether this request is one for this app's own new-message screen. */
    fun answeredHere(action: String?, flagged: Boolean): Boolean =
        flagged && (action == Intent.ACTION_PICK || action == Intent.ACTION_GET_CONTENT)

    /** Result extras Messaging reads back (ConversationActivity, request code 60). */
    const val RESULT_NUMBER = "contact_picker_selected_number"
    const val RESULT_HAS_PLAIN = "_has_plain_number_"
    const val RESULT_PLAIN = "_plain_number_"
    const val MESSAGES_PACKAGE = "com.mudita.messages"
}

/**
 * What the app was opened to do. Messaging, Email and every other app reach a contacts app
 * through a handful of standard requests; each is one of these, read once from its intent.
 */
sealed interface Asked {
    data object Browse : Asked
    /** Show this person. A lookup, a contact, a raw contact or a row — whatever was sent. */
    data class Show(val uri: Uri) : Asked
    data class Edit(val uri: Uri) : Asked
    /** Add someone new, with whatever the other app already knows filled in. */
    data class Create(val seed: Draft) : Asked
    /** Add this to someone already here, or to someone new: the reader chooses. */
    data class AddTo(val seed: Draft) : Asked
    /** Show the person with this number or address, or offer to add them. */
    data class ShowOrCreate(val number: String?, val email: String?, val seed: Draft) : Asked
    /** [messaging]: Messaging's new-message request, answered with a number rather than a person. */
    data class Pick(val kind: PickKind, val messaging: Boolean = false) : Asked
    /** A .vcf to read — a card received in a message, or a file of a whole address book. */
    data class Cards(val uri: Uri) : Asked

    companion object {
        fun of(intent: Intent?): Asked {
            if (intent == null) return Browse
            val type = intent.type?.lowercase()
            val data = intent.data
            return when (intent.action) {
                Intent.ACTION_VIEW -> when {
                    type in VCARD_TYPES || (data != null && namesAVcfFile(data)) ->
                        data?.let(::Cards) ?: Browse
                    data != null && isEncoded(data) -> stranger(data)
                    data != null -> Show(data)
                    else -> Browse
                }
                ContactsContract.QuickContact.ACTION_QUICK_CONTACT -> when {
                    data != null && isEncoded(data) -> stranger(data)
                    else -> data?.let(::Show) ?: Browse
                }
                Intent.ACTION_EDIT -> data?.let(::Edit) ?: Browse
                Intent.ACTION_INSERT -> Create(seed(intent))
                Intent.ACTION_INSERT_OR_EDIT -> AddTo(seed(intent))
                ContactsContract.Intents.SHOW_OR_CREATE_CONTACT -> {
                    val scheme = data?.scheme
                    val value = data?.schemeSpecificPart.orEmpty()
                    var s = seed(intent)
                    when (scheme) {
                        "tel" -> if (s.phones.none { it.value == value }) s = s.copy(phones = s.phones + Field(value, Phone.TYPE_MOBILE))
                        "mailto" -> if (s.emails.none { it.value == value }) s = s.copy(emails = s.emails + Field(value, Email.TYPE_OTHER))
                    }
                    ShowOrCreate(value.takeIf { scheme == "tel" }, value.takeIf { scheme == "mailto" }, s)
                }
                Intent.ACTION_PICK, Intent.ACTION_GET_CONTENT ->
                    Pick(kindOf(type, data), MessagingPick.answeredHere(intent.action, intent.getBooleanExtra(MessagingPick.EXTRA, false)))
                else -> Browse
            }
        }

        /**
         * A link that ends in ".vcf" is a card file, unless it points into the contacts database:
         * a contact synced from Nextcloud has a lookup key ending in ".vcf" (its source id), and
         * that link is a person to show, not a file to read.
         */
        private fun namesAVcfFile(uri: Uri): Boolean = namesAVcfFile(uri.authority, uri.lastPathSegment)

        fun namesAVcfFile(authority: String?, lastSegment: String?): Boolean =
            authority != ContactsContract.AUTHORITY && lastSegment?.endsWith(".vcf", true) == true

        /**
         * A Phone app's link for someone who is not in the book, such as a caller from an unsaved
         * number: contacts/lookup/encoded, with what is known about them as JSON in the fragment.
         * There is no one to look up, so it becomes "show or add" for the number it carries.
         */
        private fun isEncoded(uri: Uri): Boolean =
            uri.pathSegments.let { it.size >= 3 && it[it.size - 2] == "lookup" && it.last() == "encoded" }

        private fun stranger(uri: Uri): Asked {
            val json = runCatching { JSONObject(uri.fragment.orEmpty()) }.getOrNull() ?: return Browse
            val rows = json.optJSONObject(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
            fun fields(mime: String, otherwise: Int): List<Field> {
                val list = when (val v = rows?.opt(mime)) {
                    is JSONObject -> listOf(v)
                    is JSONArray -> (0 until v.length()).mapNotNull(v::optJSONObject)
                    else -> emptyList()
                }
                return list.mapNotNull { r ->
                    val value = r.optString(ContactsContract.Data.DATA1).trim()
                    val type = r.optInt(ContactsContract.Data.DATA2, 0).takeIf { it != 0 } ?: otherwise
                    value.takeIf { it.isNotEmpty() }?.let { Field(it, type) }
                }
            }
            val phones = fields(Phone.CONTENT_ITEM_TYPE, Phone.TYPE_MOBILE)
            val emails = fields(Email.CONTENT_ITEM_TYPE, Email.TYPE_OTHER)
            if (phones.isEmpty() && emails.isEmpty()) return Browse
            // An unknown caller's display name is usually just the number again.
            val name = json.optString(ContactsContract.Contacts.DISPLAY_NAME).trim().takeIf { n -> n.any(Char::isLetter) }.orEmpty()
            return ShowOrCreate(
                phones.firstOrNull()?.value,
                emails.firstOrNull()?.value.takeIf { phones.isEmpty() },
                Draft(wholeName = name, phones = phones, emails = emails),
            )
        }

        val VCARD_TYPES = setOf("text/x-vcard", "text/vcard", "text/directory")

        /** Which kind of thing the asking app wants back, from its type or its address. */
        private fun kindOf(type: String?, data: Uri?): PickKind {
            val t = type ?: data?.toString().orEmpty()
            return when {
                t.contains("phone") -> PickKind.PHONE
                t.contains("email") -> PickKind.EMAIL
                t.contains("postal") -> PickKind.ADDRESS
                else -> PickKind.CONTACT
            }
        }

        /** What another app knows about the person, in Android's standard extras. */
        private fun seed(intent: Intent): Draft {
            val x = intent.extras ?: return Draft()
            val phones = mutableListOf<Field>()
            val emails = mutableListOf<Field>()
            val addresses = mutableListOf<Field>()
            fun phone(key: String, typeKey: String) {
                val v = x.getCharSequence(key)?.toString()?.trim().orEmpty()
                @Suppress("DEPRECATION") val t = x.get(typeKey)
                if (v.isNotEmpty()) phones += Field(v, typeOf(t, Phone.TYPE_MOBILE), labelOf(t))
            }
            fun email(key: String, typeKey: String) {
                val v = x.getCharSequence(key)?.toString()?.trim().orEmpty()
                @Suppress("DEPRECATION") val t = x.get(typeKey)
                if (v.isNotEmpty()) emails += Field(v, typeOf(t, Email.TYPE_OTHER), labelOf(t))
            }
            phone(Insert.PHONE, Insert.PHONE_TYPE)
            phone(Insert.SECONDARY_PHONE, Insert.SECONDARY_PHONE_TYPE)
            phone(Insert.TERTIARY_PHONE, Insert.TERTIARY_PHONE_TYPE)
            email(Insert.EMAIL, Insert.EMAIL_TYPE)
            email(Insert.SECONDARY_EMAIL, Insert.SECONDARY_EMAIL_TYPE)
            email(Insert.TERTIARY_EMAIL, Insert.TERTIARY_EMAIL_TYPE)
            x.getCharSequence(Insert.POSTAL)?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                @Suppress("DEPRECATION") val t = x.get(Insert.POSTAL_TYPE)
                addresses += Field(it, typeOf(t, StructuredPostal.TYPE_HOME), labelOf(t))
            }
            // The newer way: whole rows, as ContentValues.
            @Suppress("DEPRECATION")
            x.getParcelableArrayList<ContentValues>(Insert.DATA)?.forEach { row ->
                val value = row.getAsString(ContactsContract.Data.DATA1)?.trim().orEmpty()
                if (value.isEmpty()) return@forEach
                val t = row.getAsInteger(ContactsContract.Data.DATA2) ?: 0
                val label = row.getAsString(ContactsContract.Data.DATA3)
                when (row.getAsString(ContactsContract.Data.MIMETYPE)) {
                    Phone.CONTENT_ITEM_TYPE -> phones += Field(value, t, label)
                    Email.CONTENT_ITEM_TYPE -> emails += Field(value, t, label)
                    StructuredPostal.CONTENT_ITEM_TYPE -> addresses += Field(value, t, label)
                }
            }
            return Draft(
                wholeName = x.getCharSequence(Insert.NAME)?.toString()?.trim().orEmpty(),
                phones = phones.distinctBy { it.value },
                emails = emails.distinctBy { it.value.lowercase() },
                addresses = addresses,
                company = x.getCharSequence(Insert.COMPANY)?.toString()?.trim().orEmpty(),
                jobTitle = x.getCharSequence(Insert.JOB_TITLE)?.toString()?.trim().orEmpty(),
                note = x.getCharSequence(Insert.NOTES)?.toString()?.trim().orEmpty(),
            )
        }

        /** A type extra is an int, or text — which Android's own apps send as a custom label. */
        private fun typeOf(value: Any?, otherwise: Int): Int = when (value) {
            is Int -> value
            is CharSequence -> if (value.isNotBlank()) ContactsContract.CommonDataKinds.BaseTypes.TYPE_CUSTOM else otherwise
            else -> otherwise
        }

        private fun labelOf(value: Any?): String? = (value as? CharSequence)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }
}

/**
 * Folds what another app sent into someone already here: numbers and addresses they do not
 * have yet are added, and a name is only used if they have none. Nothing is taken away.
 */
fun Draft.with(seed: Draft): Draft {
    fun digits(s: String) = s.filter(Char::isDigit)
    val newPhones = seed.phones.filter { p -> phones.none { digits(it.value).endsWith(digits(p.value).takeLast(9)) && digits(p.value).isNotEmpty() } }
    val newEmails = seed.emails.filter { e -> emails.none { it.value.equals(e.value, true) } }
    val newAddresses = seed.addresses.filter { a -> addresses.none { it.value == a.value } }
    val noName = spokenName.isBlank()
    return copy(
        wholeName = if (noName) seed.wholeName.ifBlank { seed.spokenName } else wholeName,
        phones = phones + newPhones,
        emails = emails + newEmails,
        addresses = addresses + newAddresses,
        company = company.ifBlank { seed.company },
        jobTitle = jobTitle.ifBlank { seed.jobTitle },
        note = note.ifBlank { seed.note },
    )
}
