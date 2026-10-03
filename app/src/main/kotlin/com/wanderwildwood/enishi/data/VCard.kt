package com.wanderwildwood.enishi.data

import android.provider.ContactsContract.CommonDataKinds.BaseTypes
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import java.nio.charset.Charset

/**
 * Reads a .vcf file into drafts, one per card.
 *
 * Writing one is Android's job — the contacts store hands out a vCard of any contact itself —
 * but reading one is not anything Android offers an app, so this does it. It reads what phones
 * and address books actually write: 2.1 with its quoted-printable and its per-line character
 * sets (old Nokias, many Android exports), 3.0 and 4.0. What it does not know it leaves out
 * rather than guessing at.
 */
object VCard {

    fun read(bytes: ByteArray): List<Draft> {
        // Each byte becomes one char, so the structure — all ASCII — can be read before any
        // value is decoded, and each value then in the character set its own line names.
        var text = String(bytes, Charsets.ISO_8859_1)
        if (text.startsWith("ï»¿")) text = text.substring(3)

        val cards = mutableListOf<Draft>()
        var card: Builder? = null
        var depth = 0
        var afterAgent = false
        for (line in unfold(text)) {
            val prop = parse(line) ?: continue
            when (prop.name) {
                "BEGIN" -> if (prop.raw.trim().equals("VCARD", true)) {
                    if (depth >= 1 && !afterAgent) {
                        // A card that never said END: it ends where the next one begins, rather
                        // than swallowing that one as if it were nested inside it.
                        card?.build()?.takeUnless { it.isEmpty }?.let { cards += it }
                        depth = 0
                    }
                    depth++
                    // A card inside a card (2.1's AGENT) is not a second person in the file.
                    if (depth == 1) card = Builder()
                }
                "END" -> if (prop.raw.trim().equals("VCARD", true)) {
                    if (depth == 1) card?.build()?.takeUnless { it.isEmpty }?.let { cards += it }
                    if (depth > 0) depth--
                    if (depth == 0) card = null
                }
                else -> if (depth == 1) card?.take(prop)
            }
            afterAgent = prop.name == "AGENT"
        }
        return cards
    }

    /**
     * Joins folded lines. 3.0 and 4.0 fold by starting the next line with a space or tab; 2.1's
     * quoted-printable instead ends a line with "=" and carries on at the start of the next.
     */
    internal fun unfold(text: String): List<String> {
        val raw = text.split("\r\n", "\n", "\r")
        val out = mutableListOf<String>()
        var i = 0
        while (i < raw.size) {
            var line = raw[i]
            i++
            while (i < raw.size) {
                if (isQuotedPrintable(line) && line.endsWith("=")) {
                    // A soft break: the next line carries on exactly where this one stopped.
                    line = line.dropLast(1) + raw[i]
                    i++
                } else if (raw[i].isNotEmpty() && (raw[i][0] == ' ' || raw[i][0] == '\t')) {
                    line += raw[i].substring(1)
                    i++
                } else {
                    break
                }
            }
            if (line.isNotBlank()) out += line
        }
        return out
    }

    private fun isQuotedPrintable(line: String): Boolean {
        val head = line.substringBefore(':').uppercase()
        return "QUOTED-PRINTABLE" in head
    }

    internal class Prop(
        val name: String,
        /** Every TYPE, upper case, however the line spelled them. */
        val types: Set<String>,
        val params: Map<String, String>,
        /** The value still undecoded: one char per byte. */
        val raw: String,
    ) {
        /** The value as the line meant it, in its own character set and encoding. */
        fun text(): String {
            var bytes = raw.toByteArray(Charsets.ISO_8859_1)
            if (params["ENCODING"]?.equals("QUOTED-PRINTABLE", true) == true) bytes = quotedPrintable(bytes)
            val charset = params["CHARSET"]?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
            return String(bytes, charset)
        }
    }

    /** `item1.TEL;TYPE=cell,voice;PREF:+1 555` — a name, its parameters, and its value. */
    internal fun parse(line: String): Prop? {
        // The first colon outside quotes ends the parameters; a value may hold more colons.
        var quoted = false
        var colon = -1
        for ((i, c) in line.withIndex()) {
            if (c == '"') quoted = !quoted
            if (c == ':' && !quoted) {
                colon = i
                break
            }
        }
        if (colon < 0) return null
        val head = line.substring(0, colon).split(';')
        val name = head[0].substringAfterLast('.').trim().uppercase()
        if (name.isEmpty()) return null
        val types = mutableSetOf<String>()
        val params = mutableMapOf<String, String>()
        for (p in head.drop(1)) {
            val eq = p.indexOf('=')
            if (eq < 0) {
                // 2.1 writes bare parameters: TEL;CELL;PREF, and ADR;QUOTED-PRINTABLE.
                val v = p.trim().uppercase()
                when (v) {
                    "QUOTED-PRINTABLE", "BASE64", "B" -> params["ENCODING"] = v
                    else -> types += v
                }
            } else {
                val key = p.substring(0, eq).trim().uppercase()
                val value = p.substring(eq + 1).trim().trim('"')
                if (key == "TYPE") value.split(',').forEach { types += it.trim().uppercase() } else params[key] = value
            }
        }
        return Prop(name, types, params, line.substring(colon + 1))
    }

    private fun quotedPrintable(bytes: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(bytes.size)
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            if (b == '='.code && i + 2 < bytes.size) {
                val hex = String(bytes, i + 1, 2, Charsets.ISO_8859_1)
                val v = hex.toIntOrNull(16)
                if (v != null) {
                    out.write(v)
                    i += 3
                    continue
                }
            }
            out.write(b)
            i++
        }
        return out.toByteArray()
    }

    /** Splits a structured value on its unescaped separator, then unescapes each part. */
    internal fun split(value: String, separator: Char): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                current.append(c).append(value[i + 1])
                i += 2
                continue
            }
            if (c == separator) {
                parts += unescape(current.toString())
                current.clear()
            } else {
                current.append(c)
            }
            i++
        }
        parts += unescape(current.toString())
        return parts
    }

    internal fun unescape(value: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val n = value[i + 1]) {
                    'n', 'N' -> out.append('\n')
                    else -> out.append(n)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    private class Builder {
        var draft = Draft()

        fun take(prop: Prop) {
            val types = prop.types
            when (prop.name) {
                "FN" -> draft = draft.copy(wholeName = unescape(prop.text()).trim())
                "N" -> {
                    val n = split(prop.text(), ';').map { it.trim() } + List(5) { "" }
                    draft = draft.copy(family = n[0], given = n[1], middle = n[2], prefix = n[3], suffix = n[4])
                }
                "NICKNAME" -> if (draft.nickname.isEmpty()) {
                    draft = draft.copy(nickname = split(prop.text(), ',').joinToString(", ") { it.trim() })
                }
                "TEL" -> {
                    val number = unescape(prop.text()).trim().removePrefix("tel:")
                    if (number.isNotEmpty()) draft = draft.copy(phones = draft.phones + phone(number, types))
                }
                "EMAIL" -> {
                    val address = unescape(prop.text()).trim().removePrefix("mailto:")
                    if (address.isNotEmpty()) draft = draft.copy(emails = draft.emails + email(address, types))
                }
                "ADR" -> {
                    val a = split(prop.text(), ';').map { it.trim() } + List(7) { "" }
                    val formatted = address(a)
                    if (formatted.isNotEmpty()) {
                        draft = draft.copy(addresses = draft.addresses + Field(formatted, homeWorkOther(types, StructuredPostal.TYPE_HOME, StructuredPostal.TYPE_WORK, StructuredPostal.TYPE_OTHER), custom(types)))
                    }
                }
                "ORG" -> {
                    val o = split(prop.text(), ';').map { it.trim() }.filter { it.isNotEmpty() }
                    if (o.isNotEmpty()) draft = draft.copy(company = o.joinToString(", "))
                }
                "TITLE" -> draft = draft.copy(jobTitle = unescape(prop.text()).trim())
                "NOTE" -> {
                    val note = unescape(prop.text()).trim()
                    if (note.isNotEmpty()) draft = draft.copy(note = listOf(draft.note, note).filter { it.isNotEmpty() }.joinToString("\n\n"))
                }
                "BDAY" -> event(prop.text(), Event.TYPE_BIRTHDAY, null)
                "ANNIVERSARY", "X-ANNIVERSARY" -> event(prop.text(), Event.TYPE_ANNIVERSARY, null)
                "URL" -> {
                    val url = unescape(prop.text()).trim()
                    if (url.isNotEmpty()) draft = draft.copy(websites = draft.websites + Field(url, Website.TYPE_HOMEPAGE))
                }
                // Android's own export writes kinds vCard has no word for this way.
                "X-ANDROID-CUSTOM" -> {
                    val f = split(prop.text(), ';')
                    when (f.firstOrNull()) {
                        "vnd.android.cursor.item/contact_event" -> {
                            val type = f.getOrNull(2)?.toIntOrNull() ?: Event.TYPE_OTHER
                            event(f.getOrNull(1).orEmpty(), type, f.getOrNull(3)?.takeIf { it.isNotBlank() })
                        }
                        "vnd.android.cursor.item/nickname" -> if (draft.nickname.isEmpty()) {
                            draft = draft.copy(nickname = f.getOrNull(1).orEmpty().trim())
                        }
                    }
                }
            }
        }

        private fun event(text: String, type: Int, label: String?) {
            val day = Day.parse(unescape(text)) ?: return
            if (draft.events.any { it.value == day.stored() && it.type == type }) return
            draft = draft.copy(events = draft.events + Field(day.stored(), type, label))
        }

        fun build(): Draft {
            // An FN that only repeats the parts is dropped, so the parts decide the name and a
            // later edit of them is not undone by a stale whole.
            val d = draft
            val hasParts = listOf(d.prefix, d.given, d.middle, d.family, d.suffix).any { it.isNotBlank() }
            return if (hasParts) d.copy(wholeName = "") else d
        }
    }

    private fun custom(types: Set<String>): String? =
        types.firstOrNull { it.startsWith("X-") && it.length > 2 }?.substring(2)?.lowercase()?.replaceFirstChar { it.uppercase() }

    private fun phone(number: String, types: Set<String>): Field {
        val fax = "FAX" in types
        val type = when {
            fax && "HOME" in types -> Phone.TYPE_FAX_HOME
            fax -> Phone.TYPE_FAX_WORK
            "CELL" in types || "MOBILE" in types -> Phone.TYPE_MOBILE
            "PAGER" in types -> Phone.TYPE_PAGER
            "MAIN" in types -> Phone.TYPE_MAIN
            "HOME" in types -> Phone.TYPE_HOME
            "WORK" in types -> Phone.TYPE_WORK
            custom(types) != null -> BaseTypes.TYPE_CUSTOM
            // A bare TEL is most often the one number a person has, which is a mobile.
            types.none { it != "VOICE" && it != "PREF" } -> Phone.TYPE_MOBILE
            else -> Phone.TYPE_OTHER
        }
        return Field(number, type, if (type == BaseTypes.TYPE_CUSTOM) custom(types) else null)
    }

    private fun email(address: String, types: Set<String>): Field {
        val type = when {
            "HOME" in types -> Email.TYPE_HOME
            "WORK" in types -> Email.TYPE_WORK
            "CELL" in types || "MOBILE" in types -> Email.TYPE_MOBILE
            custom(types) != null -> BaseTypes.TYPE_CUSTOM
            else -> Email.TYPE_OTHER
        }
        return Field(address, type, if (type == BaseTypes.TYPE_CUSTOM) custom(types) else null)
    }

    private fun homeWorkOther(types: Set<String>, home: Int, work: Int, other: Int): Int = when {
        "HOME" in types -> home
        "WORK" in types -> work
        else -> other
    }

    /** po box; extended; street; town; region; postcode; country — written as it is posted. */
    private fun address(a: List<String>): String {
        val street = listOf(a[0], a[1], a[2]).filter { it.isNotEmpty() }.joinToString("\n")
        val town = listOf(a[3], listOf(a[4], a[5]).filter { it.isNotEmpty() }.joinToString(" "))
            .filter { it.isNotEmpty() }.joinToString(", ")
        return listOf(street, town, a[6]).filter { it.isNotEmpty() }.joinToString("\n")
    }
}
