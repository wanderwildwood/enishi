package com.wanderwildwood.enishi.data

import java.text.Normalizer

/** A person search found, and the thing it found them by when that was not their name. */
data class Found(val person: Person, val by: String?)

/**
 * Finds people the way a person remembers them: the start of any of their names, a run of
 * digits from any of their numbers, or part of an address or a company. Accents and case do
 * not count — "jose" finds José.
 *
 * Those whose name matched come first, in list order; then those found by anything else.
 */
fun search(query: String, people: List<Person>, findable: Map<Long, Findable>): List<Found> {
    val words = fold(query).split(' ').filter { it.isNotEmpty() }
    val joined = fold(query).trim()
    if (words.isEmpty()) return emptyList()
    val digits = query.filter { it.isDigit() }
    // Three digits before a number is worth searching by: one or two match half the book.
    val byNumber = digits.length >= 3 && query.none { it.isLetter() }

    val byName = mutableListOf<Found>()
    val byOther = mutableListOf<Found>()
    for (person in people) {
        val names = fold(person.name).split(' ', '-', ',', '.').filter { it.isNotEmpty() }
        val extra = findable[person.id] ?: Findable()
        val nicknames = extra.nicknames.flatMap { fold(it).split(' ') }
        if (words.all { w -> (names + nicknames).any { it.startsWith(w) } }) {
            byName += Found(person, null)
            continue
        }
        if (byNumber) {
            val hit = extra.numbers.firstOrNull { it.filter(Char::isDigit).contains(digits) }
            if (hit != null) {
                byOther += Found(person, hit)
                continue
            }
        }
        val other = (extra.emails + extra.organisations).firstOrNull { fold(it).contains(joined) }
        if (other != null && joined.length >= 2) byOther += Found(person, other)
    }
    return byName + byOther
}

private val MARKS = Regex("\\p{Mn}+")

/** Lower case, accents off, so the reader need not type either. */
internal fun fold(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(MARKS, "")
        .lowercase()

/**
 * Who in the book this card already is, if anyone: the same name, and — when the card has
 * any — at least one number or address in common. Two people may share a name; a person and
 * their own card share a number too.
 */
fun alreadyHere(card: Draft, people: List<Person>, findable: Map<Long, Findable>): Person? {
    val name = fold(card.spokenName).trim()
    fun digits(s: String) = s.filter(Char::isDigit).takeLast(9)
    val numbers = card.phones.map { digits(it.value) }.filter { it.length >= 5 }.toSet()
    val emails = card.emails.map { it.value.trim().lowercase() }.toSet()
    // A card with only a number is matched by the number alone.
    if (name.isEmpty()) {
        if (numbers.isEmpty()) return null
        return people.firstOrNull { p -> findable[p.id]?.numbers.orEmpty().any { digits(it) in numbers } }
    }
    return people.firstOrNull { p ->
        if (fold(p.name).trim() != name && !fold(p.name).split(", ").reversed().joinToString(" ").equals(name)) return@firstOrNull false
        if (numbers.isEmpty() && emails.isEmpty()) return@firstOrNull true
        val f = findable[p.id] ?: return@firstOrNull false
        f.numbers.any { digits(it) in numbers } || f.emails.any { it.lowercase() in emails }
    }
}
