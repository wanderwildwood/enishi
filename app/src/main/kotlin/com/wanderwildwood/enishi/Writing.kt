package com.wanderwildwood.enishi

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import com.wanderwildwood.enishi.data.Target
import com.wanderwildwood.enishi.data.recipients
import com.wanderwildwood.enishi.data.smstoPart
import com.wanderwildwood.enishi.data.target

/**
 * Starts a text to these numbers the way the phone's own Contacts app does for Messaging: a
 * `SENDTO` for one `smsto:` address holding every number, comma-separated, sent to the app that
 * asked for a contact. That app then makes one group conversation or several single ones, by
 * its own setting. Returns false if nothing could take it.
 */
fun writeTo(activity: ComponentActivity, numbers: List<String>): Boolean {
    if (recipients(numbers).isEmpty()) return false
    val uri = Uri.fromParts("smsto", smstoPart(numbers), null)
    fun send() = Intent(Intent.ACTION_SENDTO, uri)
    val pm = activity.packageManager
    val caller = (activity.callingPackage ?: activity.callingActivity?.packageName)?.takeIf { it != activity.packageName }
    fun takes(pkg: String) = runCatching { pm.queryIntentActivities(send().setPackage(pkg), 0).isNotEmpty() }.getOrDefault(false)
    val goes = target(caller != null, caller != null && takes(caller), takes(MessagingPick.MESSAGES_PACKAGE))
    val intent = send().also {
        when (goes) {
            Target.CALLER -> it.setPackage(caller)
            Target.STOCK_MESSAGES -> it.setPackage(MessagingPick.MESSAGES_PACKAGE)
            Target.ANY -> Unit
        }
    }
    return try {
        activity.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
