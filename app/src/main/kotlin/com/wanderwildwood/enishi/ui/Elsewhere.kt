package com.wanderwildwood.enishi.ui

import android.content.Context
import android.content.Intent
import com.wanderwildwood.enishi.R

/**
 * Other apps of this shop a person can be handed to from their page: Field Kit takes them as an
 * emergency contact. The row shows only while that app is on the phone and says it takes them;
 * the app itself asks before it keeps anything.
 *
 * Medicine used to have two rows here, pharmacy and doctor, on every person's page. They are
 * gone: a medicine's own page chooses its pharmacy and doctor from Contacts, which is where the
 * question belongs - two rows on every person for the few who are a pharmacy was clutter.
 *
 * A plain intent with the name and one number as string extras, named in the receiving app's
 * own package, as its README lists them.
 */
internal enum class Elsewhere(val pkg: String, val action: String, val label: Int) {
    FIELD_KIT("com.wanderwildwood.zatsuno", "com.wanderwildwood.zatsuno.action.ADD_EMERGENCY_CONTACT", R.string.to_field_kit);

    fun intent(id: Long, lookup: String, name: String, number: String): Intent =
        Intent(action).setPackage(pkg)
            .putExtra("$pkg.extra.NAME", name)
            .putExtra("$pkg.extra.NUMBER", number)

    companion object {
        /** The ones on this phone now. */
        fun installed(context: Context): List<Elsewhere> = entries.filter {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(Intent(it.action).setPackage(it.pkg), 0).isNotEmpty()
        }
    }
}
