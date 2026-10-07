package com.wanderwildwood.enishi.ui

import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import com.wanderwildwood.enishi.R

/**
 * Other apps of this shop a person can be handed to from their More page: Medicine takes them
 * as a pharmacy, Field Kit as an emergency contact. Each row shows only while that app is on
 * the phone and says it takes them; the app itself asks before it keeps anything.
 *
 * Both are plain intents with the name and one number as string extras, each named in the
 * receiving app's own package, as its README lists them.
 */
internal enum class Elsewhere(val pkg: String, val action: String, val label: Int) {
    MEDICINE("com.wanderwildwood.fukuyaku", "com.wanderwildwood.fukuyaku.action.SET_PHARMACY", R.string.to_medicine),
    FIELD_KIT("com.wanderwildwood.zatsuno", "com.wanderwildwood.zatsuno.action.ADD_EMERGENCY_CONTACT", R.string.to_field_kit);

    fun intent(id: Long, lookup: String, name: String, number: String): Intent =
        Intent(action).setPackage(pkg)
            .putExtra("$pkg.extra.NAME", name)
            .putExtra("$pkg.extra.NUMBER", number)
            .also {
                // Medicine keeps the entry, for its "Open in Contacts".
                if (this == MEDICINE && lookup.isNotEmpty()) {
                    ContactsContract.Contacts.getLookupUri(id, lookup)?.let { uri -> it.putExtra("$pkg.extra.CONTACT", uri.toString()) }
                }
            }

    companion object {
        /** The ones on this phone now. */
        fun installed(context: Context): List<Elsewhere> = entries.filter {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(Intent(it.action).setPackage(it.pkg), 0).isNotEmpty()
        }
    }
}
