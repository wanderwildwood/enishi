package com.wanderwildwood.enishi.data

/**
 * Starting a text to several people, the way the phone's own Contacts app does it for
 * Messaging: the numbers are joined with commas into one `smsto:` address and handed to the
 * messaging app, which decides for itself whether that is one group conversation or several.
 * All of it is plain data, so it can be tested without a phone.
 */

/** One number a person has, with what is known about whether it is the one to use. */
data class Dialable(val value: String, val mobile: Boolean = false, val primary: Boolean = false)

/** Which number of a person to use. */
sealed interface Choice {
    /** They have no number at all. */
    data object None : Choice
    data class One(val number: String) : Choice
    /** Several different numbers and nothing to tell them apart: the reader is asked, once. */
    data class Ask(val numbers: List<Dialable>) : Choice
}

/**
 * A number as it goes into an address: digits, and a leading plus. Spaces, dashes and brackets
 * go, and so does anything that would break the comma-separated list or the address itself.
 * Empty if there is no digit in it.
 */
fun cleanNumber(raw: String): String {
    val t = raw.trim()
    val digits = t.filter(Char::isDigit)
    if (digits.isEmpty()) return ""
    return if (t.startsWith("+")) "+$digits" else digits
}

/**
 * The number to use for a person: the only one; the one marked primary; the only mobile. A
 * person with two different mobiles, or two landlines and no mobile, is asked about. The same
 * number kept twice (a phone copy and an account copy) counts once.
 */
fun chooseNumber(numbers: List<Dialable>): Choice {
    val distinct = numbers
        .filter { cleanNumber(it.value).isNotEmpty() }
        .groupBy { numberKey(it.value) }
        .map { (_, same) -> Dialable(cleanNumber(same.first().value), same.any { it.mobile }, same.any { it.primary }) }
    if (distinct.isEmpty()) return Choice.None
    if (distinct.size == 1) return Choice.One(distinct[0].value)
    distinct.filter { it.primary }.singleOrNull()?.let { return Choice.One(it.value) }
    distinct.filter { it.mobile }.singleOrNull()?.let { return Choice.One(it.value) }
    return Choice.Ask(distinct)
}

/** The numbers to write to, each once, in the order they were picked; nothing empty. */
fun recipients(numbers: List<String>): List<String> {
    val seen = mutableSetOf<String>()
    return numbers.map(::cleanNumber).filter { it.isNotEmpty() && seen.add(numberKey(it)) }
}

/**
 * The part of the address after `smsto:`, which is the numbers joined with commas. The
 * address itself is then built by Android's `Uri.fromParts("smsto", this, null)`, which
 * percent-encodes the plus and the commas exactly as the phone's own Contacts app's does.
 */
fun smstoPart(numbers: List<String>): String = recipients(numbers).joinToString(",")

/** What a typed number is worth: the number to message, or null if it has no digit. */
fun typedNumber(text: String): String? = cleanNumber(text).takeIf { it.isNotEmpty() }

/** Who receives the new conversation. */
enum class Target { CALLER, STOCK_MESSAGES, ANY }

/**
 * Which messaging app is sent the address: the one that asked for a contact if it can take it,
 * otherwise Mudita's Messaging if it is on the phone, otherwise whichever app takes `smsto:`.
 */
fun target(callerKnown: Boolean, callerTakesIt: Boolean, stockMessagesPresent: Boolean): Target = when {
    callerKnown && callerTakesIt -> Target.CALLER
    stockMessagesPresent -> Target.STOCK_MESSAGES
    else -> Target.ANY
}
