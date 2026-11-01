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
