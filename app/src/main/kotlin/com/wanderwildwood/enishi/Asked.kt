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

/** What a person picks when another app asks for "a contact", and what goes back. */
enum class PickKind { CONTACT, PHONE, EMAIL, ADDRESS }

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
    data class Pick(val kind: PickKind) : Asked
    /** A .vcf to read — a card received in a message, or a file of a whole address book. */
    data class Cards(val uri: Uri) : Asked

    companion object {
        fun of(intent: Intent?): Asked {
            if (intent == null) return Browse
            val type = intent.type?.lowercase()
            val data = intent.data
            return when (intent.action) {
                Intent.ACTION_VIEW -> when {
                    type in VCARD_TYPES || data?.lastPathSegment?.endsWith(".vcf", true) == true ->
                        data?.let(::Cards) ?: Browse
                    data != null -> Show(data)
                    else -> Browse
                }
                ContactsContract.QuickContact.ACTION_QUICK_CONTACT -> data?.let(::Show) ?: Browse
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
                Intent.ACTION_PICK, Intent.ACTION_GET_CONTENT -> Pick(kindOf(type, data))
                else -> Browse
            }
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
                if (v.isNotEmpty()) phones += Field(v, typeOf(x.get(typeKey), Phone.TYPE_MOBILE))
            }
            fun email(key: String, typeKey: String) {
                val v = x.getCharSequence(key)?.toString()?.trim().orEmpty()
                if (v.isNotEmpty()) emails += Field(v, typeOf(x.get(typeKey), Email.TYPE_OTHER))
            }
            phone(Insert.PHONE, Insert.PHONE_TYPE)
            phone(Insert.SECONDARY_PHONE, Insert.SECONDARY_PHONE_TYPE)
            phone(Insert.TERTIARY_PHONE, Insert.TERTIARY_PHONE_TYPE)
            email(Insert.EMAIL, Insert.EMAIL_TYPE)
            email(Insert.SECONDARY_EMAIL, Insert.SECONDARY_EMAIL_TYPE)
            email(Insert.TERTIARY_EMAIL, Insert.TERTIARY_EMAIL_TYPE)
            x.getCharSequence(Insert.POSTAL)?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                addresses += Field(it, typeOf(x.get(Insert.POSTAL_TYPE), StructuredPostal.TYPE_HOME))
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

        /** A type extra is an int, or the word for one ("work"), or a custom label. */
        private fun typeOf(value: Any?, otherwise: Int): Int = when (value) {
            is Int -> value
            else -> otherwise
        }
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
