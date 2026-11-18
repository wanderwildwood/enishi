package com.wanderwildwood.enishi.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.wanderwildwood.enishi.FilesPicker
import com.wanderwildwood.enishi.R
import java.security.MessageDigest

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
 * own package, as its README lists them. The app holding that name is trusted only when it is
 * signed with that app's release certificate ([cert]) and nothing else, so any other app
 * taking the name is never offered, nor handed a person.
 */
internal enum class Elsewhere(val pkg: String, val action: String, val label: Int, val cert: String) {
    FIELD_KIT(
        "com.wanderwildwood.zatsuno", "com.wanderwildwood.zatsuno.action.ADD_EMERGENCY_CONTACT", R.string.to_field_kit,
        "ee9ea93dd29e85ac4686b6318c790726d570e364af3cfa5f8bcd6ff09710a3f4",
    );

    /**
     * Whether certificates [signers] (SHA-256, lower-case hex) are this app's alone. A debuggable
     * build of this app also takes the debug keys test builds are signed with.
     */
    fun trusted(signers: List<String>, debuggable: Boolean): Boolean {
        val certs = if (debuggable) FilesPicker.DEBUG_CERTS_SHA256 + cert else setOf(cert)
        return signers.isNotEmpty() && signers.all { it in certs }
    }

    /** Whether the app holding [pkg] on this phone is the real one; see [trusted]. */
    fun isReal(context: Context): Boolean =
        trusted(signers(context.packageManager, pkg), context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)

    fun intent(id: Long, lookup: String, name: String, number: String): Intent =
        Intent(action).setPackage(pkg)
            .putExtra("$pkg.extra.NAME", name)
            .putExtra("$pkg.extra.NUMBER", number)

    companion object {
        /** The ones on this phone now, and the real apps. */
        fun installed(context: Context): List<Elsewhere> = entries.filter {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(Intent(it.action).setPackage(it.pkg), 0).isNotEmpty() && it.isReal(context)
        }

        /** The SHA-256 of each certificate [pkg] is signed with, lower-case hex; none when it cannot be read. */
        @Suppress("DEPRECATION")
        private fun signers(pm: PackageManager, pkg: String): List<String> = runCatching {
            val raw = if (Build.VERSION.SDK_INT >= 28) {
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
            } else {
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
            }
            raw.orEmpty().map { sig ->
                MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
            }
        }.getOrDefault(emptyList())
    }
}
