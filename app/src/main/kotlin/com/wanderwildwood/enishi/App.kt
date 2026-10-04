package com.wanderwildwood.enishi

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.enishi.data.Account
import com.wanderwildwood.enishi.data.Card
import com.wanderwildwood.enishi.data.Draft
import com.wanderwildwood.enishi.ui.AboutDialog
import com.wanderwildwood.enishi.ui.BookModel
import com.wanderwildwood.enishi.ui.CardsScreen
import com.wanderwildwood.enishi.ui.DetailScreen
import com.wanderwildwood.enishi.ui.EditScreen
import com.wanderwildwood.enishi.ui.MoreScreen
import com.wanderwildwood.enishi.ui.HomeScreen
import com.wanderwildwood.enishi.ui.MergeScreen
import com.wanderwildwood.enishi.ui.shareMany
import com.wanderwildwood.enishi.ui.start
import com.wanderwildwood.enishi.data.Backups
import com.wanderwildwood.enishi.ui.PickScreen
import com.wanderwildwood.enishi.ui.SearchScreen
import com.wanderwildwood.enishi.ui.SettingsScreen
import com.wanderwildwood.enishi.ui.has
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where the reader is. The stack is a list of these; Back takes the top one off. */
sealed interface Route {
    /** Working out where a request from another app leads, before showing anything. */
    data class Opening(val asked: Asked) : Route
    data object Home : Route
    data object Search : Route
    data class Detail(val id: Long) : Route
    data class More(val id: Long) : Route
    data class Edit(val card: Card?, val seed: Draft, val account: Account) : Route
    data object Settings : Route
    /** Choosing whose name stays when several people are made one. */
    data class Merge(val ids: List<Long>) : Route
    data class Cards(val uri: Uri) : Route
    data class Pick(val kind: PickKind) : Route
    data class PickSearch(val kind: PickKind) : Route
    /** Choosing who to add something to, from Messaging's or Email's "add to contacts". */
    data class AddTo(val seed: Draft, val searching: Boolean = false) : Route
    data object Gone : Route
}

/**
 * The whole app, for the launcher and for every request another app makes of it.
 *
 * Opened from the launcher it is an address book. Opened by another app it does the one thing
 * asked and then gives the reader back to that app: Back from the contact Messaging showed
 * returns to the conversation, and a contact added from an email goes back to the email.
 */
@Composable
fun App(activity: ComponentActivity, asked: Asked, fromElsewhere: Boolean) {
    val context = LocalContext.current
    val model: BookModel = viewModel()
    val scope = rememberCoroutineScope()

    // Asked again every time the app comes back, since it can be granted from Android's settings.
    var allowed by remember { mutableStateOf(hasAccess(activity)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) allowed = hasAccess(activity) }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    LaunchedEffect(allowed) { if (allowed) model.start() }

    if (!allowed) {
        AccessScreen(activity) { allowed = hasAccess(activity) }
        return
    }

    val stack = remember { mutableStateListOf<Route>(if (asked == Asked.Browse) Route.Home else Route.Opening(asked)) }
    val listState = rememberLazyListState()
    var about by remember { mutableStateOf(false) }
    var settingsNotice by remember { mutableStateOf<String?>(null) }
    var homeNotice by remember { mutableStateOf<String?>(null) }
    /** Everyone chosen on the list, while several are being chosen; empty the rest of the time. */
    var chosen by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val backups = remember { Backups(context) }
    var backupsSeen by remember { mutableStateOf(0) }
    /** A schedule asked for before there was a folder: set once one is chosen. */
    var everyOnceChosen by remember { mutableStateOf(0) }

    fun push(r: Route) { stack.add(r) }
    fun replace(r: Route) { stack.removeAt(stack.lastIndex); stack.add(r) }
    fun finish(result: Uri? = null) {
        if (result != null) {
            activity.setResult(Activity.RESULT_OK, Intent().setData(result).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } else if (fromElsewhere) {
            activity.setResult(Activity.RESULT_CANCELED)
        }
        activity.finish()
    }
    fun back() {
        when {
            stack.lastOrNull() == Route.Home && chosen.isNotEmpty() -> chosen = emptySet()
            stack.size <= 1 -> finish()
            else -> stack.removeAt(stack.lastIndex)
        }
    }
    fun contactUri(id: Long): Uri =
        ContactsContract.Contacts.getLookupUri(context.contentResolver, ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id))
            ?: ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)

    // Another app is waiting for an answer while it is open for one of these.
    val answering = asked is Asked.Create || asked is Asked.AddTo || asked is Asked.Edit || asked is Asked.ShowOrCreate

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) push(Route.Cards(uri))
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val lookups = model.people.map { it.lookup }
            val ok = model.io { book ->
                context.contentResolver.openOutputStream(uri, "wt")?.use { book.exportTo(lookups, it) } ?: error("no stream")
            }.isSuccess
            settingsNotice = if (ok) context.resources.getQuantityString(R.plurals.export_done, lookups.size, lookups.size)
            else context.getString(R.string.export_failed)
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backups.choose(uri)
        if (everyOnceChosen > 0) backups.every = everyOnceChosen
        everyOnceChosen = 0
        backupsSeen++
    }
    val importTypes = arrayOf("text/x-vcard", "text/vcard", "text/directory", "application/octet-stream", "text/plain")
    fun newContact(seed: Draft = Draft()) {
        scope.launch { push(Route.Edit(null, seed, model.newContactAccount())) }
    }

    BackHandler(enabled = stack.isNotEmpty()) { back() }

    val body = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
    when (val top = stack.lastOrNull() ?: Route.Gone) {
        Route.Gone -> Unit
        is Route.Opening -> {
            LaunchedEffect(top) {
                replace(open(model, top.asked) ?: Route.Gone)
                if (stack.lastOrNull() == Route.Gone) finish()
            }
        }
        Route.Home -> HomeScreen(
            model = model, listState = listState,
            onOpen = { push(Route.Detail(it.id)) },
            onNew = { newContact() },
            onSearch = { push(Route.Search) },
            onSettings = { push(Route.Settings) },
            onAbout = { about = true },
            onImport = { pickFile.launch(importTypes) },
            chosen = chosen,
            onChoose = { p -> chosen = if (p.id in chosen) chosen - p.id else chosen + p.id },
            onChosenDone = { chosen = emptySet() },
            onMerge = { push(Route.Merge(model.people.filter { it.id in chosen }.map { it.id })) },
            onShareChosen = {
                scope.launch {
                    val lookups = model.people.filter { it.id in chosen }.map { it.lookup }
                    val shared = model.io { shareMany(context, lookups, it) }.getOrNull()
                    if (shared == null || !start(context, shared)) homeNotice = context.getString(R.string.share_failed)
                }
            },
            onDeleteChosen = {
                scope.launch {
                    val going = model.people.filter { it.id in chosen }
                    val failed = going.count { p -> model.io { it.delete(p.id, p.lookup) }.isFailure }
                    chosen = emptySet()
                    if (failed > 0) homeNotice = context.resources.getQuantityString(R.plurals.chosen_deleted_some, failed, failed)
                }
            },
            notice = homeNotice,
            onNoticeSeen = { homeNotice = null },
        )
        is Route.Merge -> MergeScreen(model, top.ids, ::back) { id ->
            chosen = emptySet()
            replace(Route.Detail(id))
        }
        Route.Search -> SearchScreen(model, stringResource(R.string.search_title), ::back) { replace(Route.Detail(it.id)) }
        is Route.Detail -> DetailScreen(
            model, top.id,
            onBack = ::back,
            onEdit = { card -> scope.launch { push(Route.Edit(card, Draft(), model.newContactAccount())) } },
            onMore = { push(Route.More(top.id)) },
        )
        is Route.More -> MoreScreen(
            model, top.id,
            onBack = ::back,
            onEdit = { card -> scope.launch { push(Route.Edit(card, Draft(), model.newContactAccount())) } },
            // Deleted: off the More page and the person both, back to where they were found.
            onGone = { back(); back() },
        )
        is Route.Edit -> EditScreen(
            model, top.card, top.seed, top.account,
            onSaved = { id ->
                when {
                    // Saved for another app: hand back where the person now is, and go back to it.
                    answering -> finish(contactUri(id))
                    top.card == null -> replace(Route.Detail(id))
                    else -> back()
                }
            },
            onCancel = ::back,
        )
        Route.Settings -> SettingsScreen(
            model, ::back,
            onImport = { pickFile.launch(importTypes) },
            onExport = {
                val day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                saveFile.launch("contacts-$day.vcf")
            },
            notice = settingsNotice,
            onNoticeSeen = { settingsNotice = null },
            backups = backups,
            backupsSeen = backupsSeen,
            onBackupEvery = { days ->
                if (days > 0 && backups.folder == null) {
                    everyOnceChosen = days
                    pickFolder.launch(null)
                } else {
                    backups.every = days
                    backupsSeen++
                }
            },
            onBackupFolder = { pickFolder.launch(backups.folder) },
            onBackupNow = {
                if (backups.folder == null) pickFolder.launch(null)
                else scope.launch {
                    val saved = model.io { backups.run().getOrThrow() }
                    backupsSeen++
                    settingsNotice = saved.fold(
                        { context.resources.getQuantityString(R.plurals.export_done, it, it) },
                        { context.getString(R.string.export_failed) },
                    )
                }
            },
        )
        is Route.Cards -> CardsScreen(
            model, top.uri, ::back,
            onAddOne = { newContact(it) },
            onOpen = { push(Route.Detail(it.id)) },
        )
        is Route.Pick -> PickScreen(
            model, top.kind,
            onSearch = { push(Route.PickSearch(top.kind)) },
            onCancel = { finish() },
            onPicked = { finish(it) },
        )
        is Route.PickSearch -> PickScreen(
            model, top.kind,
            searching = true,
            onSearch = {},
            onCancel = ::back,
            onPicked = { finish(it) },
        )
        is Route.AddTo -> PickScreen(
            model, PickKind.CONTACT,
            newRow = stringResource(R.string.pick_new_contact),
            onNew = { newContact(top.seed) },
            onSearch = { push(Route.AddTo(top.seed, searching = true)) },
            searching = top.searching,
            onCancel = { if (top.searching) back() else finish() },
            onPerson = { p ->
                scope.launch {
                    val card = model.io { it.card(p.id, model.lastNameFirst) }.getOrNull() ?: return@launch
                    push(Route.Edit(card, top.seed, model.newContactAccount()))
                }
            },
            onPicked = {},
        )
    }

    if (about) AboutDialog { about = false }
}

/** Where a request from another app leads, once the person it names has been looked up. */
private suspend fun open(model: BookModel, asked: Asked): Route? = when (asked) {
    Asked.Browse -> Route.Home
    is Asked.Show -> model.io { it.resolve(asked.uri) }.getOrNull()?.let { Route.Detail(it) }
    is Asked.Edit -> model.io { b -> b.resolve(asked.uri)?.let { b.card(it, model.lastNameFirst) } }.getOrNull()
        ?.let { Route.Edit(it, Draft(), model.newContactAccount()) }
    is Asked.Create -> Route.Edit(null, asked.seed, model.newContactAccount())
    is Asked.AddTo -> Route.AddTo(asked.seed)
    is Asked.ShowOrCreate -> {
        val id = model.io { b -> asked.number?.let(b::findByNumber) ?: asked.email?.let(b::findByEmail) }.getOrNull()
        if (id != null) Route.Detail(id) else Route.Edit(null, asked.seed, model.newContactAccount())
    }
    is Asked.Pick -> Route.Pick(asked.kind)
    is Asked.Cards -> Route.Cards(asked.uri)
}

private val NEEDED = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS, Manifest.permission.GET_ACCOUNTS)

private fun hasAccess(activity: Activity): Boolean =
    listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS).all {
        ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
    }

/**
 * Before Android has been told this app may see the contacts. One page, one button; if the
 * question was refused for good, the button opens the app's page in Android's settings instead,
 * because asking again would do nothing.
 */
@Composable
private fun AccessScreen(activity: Activity, onAnswered: () -> Unit) {
    var refused by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refused = !hasAccess(activity) && !activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)
        onAnswered()
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(20.dp)) {
        Spacer(Modifier.height(40.dp))
        TextMMD(text = stringResource(R.string.access_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        TextMMD(text = stringResource(R.string.access_body), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(20.dp))
        OutlinedButtonMMD(
            onClick = {
                if (refused) {
                    activity.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
                    )
                } else {
                    ask.launch(NEEDED)
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            TextMMD(text = stringResource(if (refused) R.string.access_settings else R.string.access_allow), style = MaterialTheme.typography.bodySmall)
        }
    }
}
