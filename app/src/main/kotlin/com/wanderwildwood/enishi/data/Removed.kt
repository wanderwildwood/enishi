package com.wanderwildwood.enishi.data

/**
 * A person just deleted, kept in memory until the user moves on so that Undo can put them back:
 * the vCard the store itself composes, their photo, and the account they were kept in. It is
 * never written anywhere.
 */
class Removed(
    val name: String,
    val vcard: ByteArray,
    val photo: ByteArray?,
    val account: Account?,
) {
    /** The person as a draft, or null when the card reads as nobody (then there is nothing to undo). */
    fun draft(): Draft? = VCard.read(vcard).firstOrNull()
}
