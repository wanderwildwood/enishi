package com.wanderwildwood.davsync.sync

import android.accounts.Account
import android.content.Context

/**
 * What the app checks before each sync of one of its accounts, here and in the sync adapter.
 * An account whose password comes from another app (Connections: Home's Nextcloud account) is
 * asked about first; [check] answers false when it has no password just now, and the sync is
 * skipped quietly: no error, nothing recorded, no retry. Called off the main thread.
 */
object SyncGate {
    @Volatile
    var check: ((Context, Account) -> Boolean)? = null

    fun mayRun(context: Context, account: Account): Boolean {
        val gate = check ?: return true
        return runCatching { gate(context, account) }.getOrDefault(true)
    }
}
