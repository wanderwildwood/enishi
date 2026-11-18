package com.wanderwildwood.enishi.data

/**
 * Whether a typed phone number can be one: only digits and the marks numbers are written with
 * (+ - . ( ) space, and * # , ; for dialling extras), and at least three digits. A blank line is
 * not wrong; the editor drops it.
 */
fun validPhone(text: String): Boolean {
    val t = text.trim()
    if (t.isEmpty()) return true
    if (t.any { !(it.isDigit() && it.code < 128) && it !in "+-.() *#,;/" }) return false
    return t.count { it.isDigit() } >= 3
}

/**
 * The values in [after] typed or changed in the editor: new ones, and stored ones whose text is
 * not what [before] held. Only these are checked on Save, so a value kept as it came from
 * another app, a sync or a card (a number with "ext", an accented address) never stops an
 * edit of something else.
 */
fun typedOrChanged(before: List<Field>, after: List<Field>): List<Field> {
    val stored = before.filter { it.dataId != null }.associate { it.dataId to it.value.trim() }
    return after.filter { f -> f.dataId == null || stored[f.dataId] != f.value.trim() }
}
