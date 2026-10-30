package com.wanderwildwood.enishi.ui

/**
 * The address a contact's website field should open: a bare name gets https://, an http or https
 * address is kept, and any other scheme (tel:, intent:, file:, ...) is refused, since the field
 * is typed by hand or arrives in a vCard and must only ever lead to a web page.
 */
fun webAddress(value: String): String? {
    val v = value.trim()
    if (v.isEmpty()) return null
    val scheme = Regex("^([A-Za-z][A-Za-z0-9+.-]*):").find(v)?.groupValues?.get(1)?.lowercase()
    return when {
        v.startsWith("http://", true) || v.startsWith("https://", true) -> v
        v.contains("://") -> null
        // "example.org:8080" reads as a scheme to the regex; a dot in it means it is a host.
        scheme != null && !scheme.contains('.') -> null
        else -> "https://$v"
    }
}
