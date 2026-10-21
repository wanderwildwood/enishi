package com.wanderwildwood.enishi.data

import android.content.ContentResolver
import android.os.Bundle
import android.provider.ContactsContract

/**
 * Asks whatever syncs the contacts to do it now.
 *
 * This app reads and writes the phone's contacts store and never talks to a server; the apps
 * that do -- DAVx5 and its kind -- register with the phone as the contacts store's sync
 * adapters. A request on the contacts authority with no account named reaches every one of
 * them, for every account they keep, marked as the person's own asking so it is not deferred
 * the way a scheduled sync may be. Nothing is promised in return: an adapter whose sync is
 * switched off, or whose app has been stopped, will not answer. The list changes when the
 * store does, as it always has.
 */
object ContactsSync {
    fun requestNow() {
        val extras = Bundle().apply {
            putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        }
        runCatching { ContentResolver.requestSync(null, ContactsContract.AUTHORITY, extras) }
    }
}
