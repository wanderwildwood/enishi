package com.wanderwildwood.enishi.data

import android.accounts.Account
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.compose.runtime.mutableIntStateOf
import com.wanderwildwood.davsync.DavSync
import com.wanderwildwood.davsync.servicedetection.DavResourceFinder
import com.wanderwildwood.davsync.signin.Problem
import com.wanderwildwood.davsync.signin.SignIn
import com.wanderwildwood.davsync.signin.SignInResult
import com.wanderwildwood.davsync.sync.SyncGate
import com.wanderwildwood.davsync.sync.SyncNow
import com.wanderwildwood.davsync.sync.SyncResult
import io.ktor.http.Url
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Contacts sync with the phone's one Nextcloud account, the one Home holds (HomeAccount.kt,
 * shared-homeaccount's README).
 *
 * What is kept here is only the choice: [Link.who] (server and user, no password), the stamp last
 * taken from Home, and the name of the sync account made for it. That sync account is an ordinary
 * davsync account, and the password sealed in it is only a mirror of Home's: written from Home on
 * each start, resume and change notice, and before each sync ([SyncGate]); forgotten when Home
 * signs out, goes, or holds another account. Then the account stays with its address books and
 * people, its syncing stops, and the screen shows it signed out. The same account from Home again
 * picks it up where it was.
 *
 * Nothing here is backed up (the app's manifest keeps everything out of backups) or logged.
 */
object HomeLink {

    /** The choice to use Home's account: [account] is the name of the sync account made for it. */
    data class Link(val who: String, val stamp: String, val account: String) {
        /** Home has the account and the mirror holds its password. */
        val holding: Boolean get() = stamp.isNotEmpty()
    }

    /** Bumped on the main thread whenever the link changes, so a screen can redraw. */
    val changes = mutableIntStateOf(0)

    private val lock = Any()
    private val worker by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "HomeLink").apply { isDaemon = true } } }
    private val main by lazy { Handler(Looper.getMainLooper()) }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("enishi", Context.MODE_PRIVATE)

    fun link(context: Context): Link? {
        val p = prefs(context)
        val account = p.getString(KEY_ACCOUNT, null).orEmpty()
        if (account.isEmpty()) return null
        return Link(p.getString(KEY_WHO, null).orEmpty(), p.getString(KEY_STAMP, null).orEmpty(), account)
    }

    private fun save(context: Context, link: Link?) {
        prefs(context).edit().apply {
            if (link == null) {
                remove(KEY_WHO); remove(KEY_STAMP); remove(KEY_ACCOUNT)
            } else {
                putString(KEY_WHO, link.who); putString(KEY_STAMP, link.stamp); putString(KEY_ACCOUNT, link.account)
            }
        }.commit()
        main.post { changes.intValue++ }
    }

    /** The sync account that uses Home's Nextcloud account, if there is one. */
    fun account(context: Context, davSync: DavSync, link: Link? = link(context)): Account? =
        link?.let { Account(it.account, davSync.accounts.accountType) }?.takeIf { davSync.accounts.exists(it) }

    /**
     * Asks Home and does what [HomeAccount.follow] says. Off the main thread. Returns whether the
     * account may sync now: true for any account that is not Home's, and for Home's only while
     * its password is held.
     */
    fun refresh(context: Context, only: Account? = null): Boolean {
        val app = context.applicationContext
        var took: Account? = null
        val mayRun = synchronized(lock) {
            val link = link(app) ?: return true
            val davSync = DavSync(app)
            val account = Account(link.account, davSync.accounts.accountType)
            if (only != null && only != account) return true
            if (!davSync.accounts.exists(account)) {
                // Taken off the phone elsewhere (Android's settings): the choice goes with it.
                save(app, null)
                return false
            }
            when (val step = HomeAccount.follow(link.who, link.stamp, HomeAccount.ask(app))) {
                is HomeAccount.Step.Keep -> link.holding
                is HomeAccount.Step.Take -> {
                    if (davSync.accounts.updatePassword(account, step.held.password)) {
                        save(app, link.copy(stamp = step.held.stamp))
                        took = account
                        true
                    } else {
                        false
                    }
                }
                is HomeAccount.Step.Drop -> {
                    // Let go already (Home holds another account, asked again): nothing more to do.
                    if (!link.holding && (step.following || link.who.isEmpty())) return false
                    davSync.accounts.forgetPassword(account)
                    davSync.accounts.pauseSync(account, ContactsContract.AUTHORITY)
                    save(app, link.copy(stamp = "", who = if (step.following) link.who else ""))
                    false
                }
            }
        }
        // Back after a sign-out, or a new password: sync now, which also turns the address books' own syncing on again.
        took?.let { if (only == null) SyncNow.contacts(app, it) }
        return mayRun
    }

    /** [refresh] on a thread of its own. */
    fun refreshSoon(context: Context) {
        val app = context.applicationContext
        if (link(app) == null) return
        worker.execute { runCatching { refresh(app) } }
    }

    /** What [use] came to. */
    sealed class Used {
        object Done : Used()
        /** Home's account is in use, but an own sign-in stayed: its changes could not be sent. */
        object OwnKept : Used()
        /** Home has no account for this app just now. */
        object NoAccount : Used()
        class Bad(val problem: Problem, val detail: String = "") : Used()
    }

    /**
     * Switches contacts sync to Home's account. An own sign-in to the same user on the same server
     * (the same address, path and all) is kept and takes Home's password; any other own sign-in
     * sends its waiting changes and then leaves the phone with its people (the server keeps them).
     * One whose changes could not be sent stays, and so does one with no password to send them
     * with (a link left over from an earlier Home account, or a password the phone can no longer
     * open): the user removes those, and Remove says what is lost.
     */
    suspend fun use(context: Context): Used {
        val app = context.applicationContext
        val davSync = DavSync(app)
        val held = HomeAccount.read(app) ?: return Used.NoAccount
        val before = davSync.accounts.accounts()
        val same = before.firstOrNull { sameAccount(davSync.accounts.server(it)?.toString(), davSync.accounts.user(it), held) }
        val target = if (same != null) {
            if (!davSync.accounts.updatePassword(same, held.password)) return Used.Bad(Problem.KEYSTORE)
            same
        } else {
            when (val result = SignIn.check(davSync, held.server, held.user, held.password, DavResourceFinder.Service.CARDDAV)) {
                is SignInResult.Bad -> return Used.Bad(result.problem, result.detail)
                is SignInResult.Good -> keep(davSync, result, before.map { it.name }) ?: return Used.Bad(Problem.KEYSTORE)
            }
        }
        synchronized(lock) { save(app, Link(held.who, held.stamp, target.name)) }
        var kept = false
        for (other in before) {
            if (other == target) continue
            // No password: nothing can be sent, so it stays rather than lose what was not sent.
            val sent = davSync.accounts.credentials(other) != null && sendWaiting(app, other)
            if (sent) davSync.accounts.remove(other) else kept = true
        }
        SyncNow.contacts(app, target)
        return if (kept) Used.OwnKept else Used.Done
    }

    /**
     * [SignIn.keep], under a name no account has yet ([accountName]): an own sign-in to the same
     * user on the same host, but another address (a port or a path), may already have it.
     */
    private fun keep(davSync: DavSync, good: SignInResult.Good, taken: List<String>): Account? {
        val name = freeName(accountName(good.credentials.username, good.server), taken)
        val collections = good.collections.map { it.copy(sync = false) }
        return davSync.accounts.create(name, good.server, good.credentials, collections)
    }

    /**
     * The name of the sync account made from Home's: "user@host" as an own sign-in's
     * ([SignIn.keep]), with the port when it is not the usual one, and "user on host" when the
     * user is an email address, so the name never has two @. Only an account made from now on
     * gets it; one made before keeps its name, since its calendars, address books and choice
     * are kept under that name.
     */
    fun accountName(user: String, server: Url): String {
        val host = if (server.port != server.protocol.defaultPort) "${server.host}:${server.port}" else server.host
        return if ('@' in user) "$user on $host" else "$user@$host"
    }

    /** [name], or "name (2)", "name (3)" and so on, whichever is not [taken]. */
    fun freeName(name: String, taken: List<String>): String =
        generateSequence(1) { it + 1 }.map { if (it == 1) name else "$name ($it)" }.first { it !in taken }

    /**
     * Stops using Home's account: the choice, the stamp and the sync account (with its people) go.
     * Done on the worker, since a refresh may hold the lock while it waits for Home; [done] runs on
     * the main thread after.
     */
    fun stop(context: Context, done: () -> Unit = {}) {
        val app = context.applicationContext
        worker.execute {
            runCatching {
                synchronized(lock) {
                    val davSync = DavSync(app)
                    account(app, davSync)?.let { davSync.accounts.remove(it) }
                    save(app, null)
                }
            }
            main.post(done)
        }
    }

    private suspend fun sendWaiting(context: Context, account: Account): Boolean =
        suspendCancellableCoroutine { cont ->
            SyncNow.contacts(context, account) { result: SyncResult ->
                cont.resume(com.wanderwildwood.davsync.accounts.AccountStore.Status.problemOf(result) == null)
            }
        }

    /**
     * Asks Home on every start and resume of the app, and on each change notice while the app is
     * on screen; and before each sync of Home's account ([SyncGate]). Once, at start-up.
     */
    fun install(app: Application) {
        SyncGate.check = { context, account -> refresh(context, only = account) }
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var started = 0
            private var stopWatching: (() -> Unit)? = null

            override fun onActivityStarted(activity: Activity) {
                if (started++ == 0) stopWatching = HomeAccount.observe(app) { refreshSoon(app) }
            }

            override fun onActivityResumed(activity: Activity) = refreshSoon(app)

            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (started == 0) {
                    stopWatching?.invoke()
                    stopWatching = null
                }
            }

            override fun onActivityCreated(activity: Activity, state: Bundle?) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /** The server as the screen shows it: no https://, no slash at the end. */
    fun shown(server: String): String =
        server.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')

    /**
     * Whether an own sign-in ([server], [user]) is the same account as Home's [held]: the same
     * user at the same address (scheme, host, port and path; a slash at the end, the case of the
     * host and a port that is the scheme's own do not count).
     */
    fun sameAccount(server: String?, user: String?, held: HomeAccount.Held): Boolean {
        if (server.isNullOrEmpty() || user.isNullOrEmpty()) return false
        val mine = address(server) ?: return false
        return mine == address(held.server) && user.trim() == held.user.trim()
    }

    /** [server] as one string to compare: "https://host:443/path" for any way of writing it. */
    fun address(server: String): String? {
        val uri = runCatching { java.net.URI(SignIn.normalise(server)) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val port = when {
            uri.port != -1 -> uri.port
            scheme == "https" -> 443
            scheme == "http" -> 80
            else -> return null
        }
        return "$scheme://$host:$port" + uri.rawPath.orEmpty().trimEnd('/')
    }

    private const val KEY_WHO = "home_account_who"
    private const val KEY_STAMP = "home_account_stamp"
    private const val KEY_ACCOUNT = "home_account_name"
}
