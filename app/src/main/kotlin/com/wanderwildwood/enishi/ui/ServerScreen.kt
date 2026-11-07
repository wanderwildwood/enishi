package com.wanderwildwood.enishi.ui

import android.accounts.Account
import android.content.ContentResolver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.wanderwildwood.davsync.DavSync
import com.wanderwildwood.davsync.R as DavR
import com.wanderwildwood.davsync.accounts.Elsewhere
import com.wanderwildwood.davsync.accounts.StatusText
import com.wanderwildwood.davsync.signin.Provider
import com.wanderwildwood.davsync.model.Collection
import com.wanderwildwood.davsync.servicedetection.DavResourceFinder
import com.wanderwildwood.davsync.servicedetection.CollectionMaker
import com.wanderwildwood.davsync.sync.SyncPolicy
import com.wanderwildwood.davsync.signin.Problem
import com.wanderwildwood.davsync.signin.SignIn
import com.wanderwildwood.davsync.signin.SignInResult
import com.wanderwildwood.davsync.sync.SyncNow
import com.wanderwildwood.enishi.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Settings > Contacts server: Connections' own sync with a CardDAV server, in place of DAVx5.
 *
 * With no account yet it is the typed sign-in, as in Memo: the server address, the user name
 * and an app password; nothing is saved before the server answers. Once signed in, each
 * address book is a row that turns its sync on or off; each synced one becomes its own account
 * on the phone (as DAVx5 does), so new people can be saved straight into it. Remove takes the
 * account and its people off this phone; the server keeps them.
 */
@Composable
fun ServerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val davSync = remember { DavSync(context) }
    var version by remember { mutableIntStateOf(0) }
    val accounts = remember(version) { davSync.accounts.accounts() }
    var adding by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var changing by remember { mutableStateOf<String?>(null) }
    // Held here, not in the row: a row scrolled off the page is thrown away, and with it a
    // check still asking the server.
    val passwordForm = remember { PasswordForm() }
    var making by remember { mutableStateOf<String?>(null) }
    val newForm = remember { NewForm() }
    val screenScope = rememberCoroutineScope()
    // DAVx5's address books on this phone, by name (their addresses are not readable).
    val davx5Books = remember(version) { Elsewhere.addressBooks(context) }

    // Address books made or removed in Nextcloud since: read the list again each time the page opens.
    LaunchedEffect(accounts.size) {
        for (account in accounts)
            CollectionMaker.refresh(davSync, account, DavResourceFinder.Service.CARDDAV)
        version++
    }

    // Syncs the system starts (on their schedule, after a change) redraw the "last synced" line too.
    DisposableEffect(Unit) {
        val main = Handler(Looper.getMainLooper())
        val handle = ContentResolver.addStatusChangeListener(
            ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE or ContentResolver.SYNC_OBSERVER_TYPE_PENDING,
        ) { main.post { version++ } }
        onDispose { ContentResolver.removeStatusChangeListener(handle) }
    }

    fun syncNow(account: Account) {
        syncing = true
        SyncNow.contacts(context, account) { Handler(Looper.getMainLooper()).post { syncing = false; version++ } }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(
                title = { BarTitle(stringResource(R.string.server_title)) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
            )
        },
    ) { padding ->
        if (accounts.isEmpty() || adding) {
            SignInForm(davSync, Modifier.padding(padding)) { account ->
                adding = false
                version++
                syncNow(account)
            }
        } else {
            PagedColumn(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface), showCutRow = true) {
                for (account in accounts) {
                    item { Heading(account.name, ruled = false) }
                    val books = davSync.accounts.collections(account, Collection.TYPE_ADDRESSBOOK)
                    item {
                        val active = syncing || davSync.accounts.addressBookAccounts(account).any { ContentResolver.isSyncActive(it, ContactsContract.AUTHORITY) }
                        TextMMD(
                            text = StatusText.of(context, davSync.accounts.status(account), active) +
                                (if (books.none { it.sync }) "\n" + stringResource(com.wanderwildwood.davsync.R.string.davsync_pick) else "") +
                                (if (books.any { Elsewhere.match(it, davx5Books) != Elsewhere.Match.NONE })
                                    "\n" + stringResource(com.wanderwildwood.davsync.R.string.davsync_moving_contacts) else ""),
                            style = helperStyle(),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    for (c in books) {
                        item {
                            val twice = Elsewhere.match(c, davx5Books) != Elsewhere.Match.NONE
                            PlainRow(
                                ruled = false,
                                title = c.title(),
                                note = stringResource(if (c.sync) R.string.server_on else R.string.server_off) +
                                    (if (c.readOnly()) " · " + stringResource(R.string.server_read_only) else "") +
                                    (if (twice) "\n" + stringResource(com.wanderwildwood.davsync.R.string.davsync_elsewhere_book) else ""),
                                onPress = {
                                    davSync.accounts.setSync(account, c.id, !c.sync)
                                    version++
                                    syncNow(account)
                                },
                            )
                        }
                    }
                    item {
                        if (making == account.name)
                            NewAddressBook(davSync, account, newForm, screenScope) { made ->
                                newForm.reset()
                                making = null
                                version++
                                if (made) syncNow(account)
                            }
                        else
                            PlainRow(
                                ruled = false,
                                title = stringResource(DavR.string.davsync_new_address_book),
                                note = stringResource(DavR.string.davsync_new_address_book_note),
                                onPress = { newForm.reset(); making = account.name },
                            )
                    }
                    item {
                        ChoiceRow(
                            label = stringResource(DavR.string.davsync_interval),
                            choices = listOf(
                                stringResource(DavR.string.davsync_interval_15m),
                                stringResource(DavR.string.davsync_interval_1h),
                                stringResource(DavR.string.davsync_interval_4h),
                            ),
                            chosen = SyncPolicy.INTERVALS.indexOf(davSync.accounts.interval(account)),
                            enabled = true,
                            perLine = 3,
                        ) {
                            davSync.accounts.setInterval(account, ContactsContract.AUTHORITY, SyncPolicy.INTERVALS[it])
                            version++
                        }
                    }
                    item {
                        val on = davSync.accounts.wifiOnly(account)
                        PlainRow(
                            ruled = false,
                            title = stringResource(DavR.string.davsync_wifi_only),
                            note = stringResource(DavR.string.davsync_wifi_only_note),
                            noteLines = 4,
                            trailing = { SwitchMMD(checked = on, onCheckedChange = null) },
                            onPress = {
                                davSync.accounts.setWifiOnly(account, !on)
                                version++
                            },
                        )
                    }
                    item {
                        PlainRow(
                            ruled = false,
                            title = stringResource(R.string.server_sync_now),
                            note = stringResource(R.string.server_sync_now_note),
                            onPress = { if (!syncing) syncNow(account) },
                        )
                    }
                    item {
                        if (changing == account.name)
                            ChangePassword(davSync, account, passwordForm, screenScope) { saved ->
                                passwordForm.reset()
                                changing = null
                                version++
                                if (saved) syncNow(account)
                            }
                        else
                            PlainRow(
                                ruled = false,
                                title = stringResource(com.wanderwildwood.davsync.R.string.davsync_change_password),
                                note = stringResource(com.wanderwildwood.davsync.R.string.davsync_change_password_note),
                                onPress = { changing = account.name },
                            )
                    }
                    item {
                        val (armed, press) = rememberArmed(account.name) {
                            davSync.accounts.remove(account)
                            version++
                        }
                        PlainRow(
                            ruled = false,
                            title = stringResource(if (armed) R.string.server_remove_armed else R.string.server_remove),
                            onPress = press,
                        )
                    }
                }
                item {
                    PlainRow(ruled = false, title = stringResource(R.string.server_add), onPress = { adding = true })
                }
            }
        }
    }
}

@Composable
private fun SignInForm(davSync: DavSync, modifier: Modifier, onSignedIn: (Account) -> Unit) {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Int?>(null) }
    var said by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf(Provider.NEXTCLOUD) }
    var region by remember { mutableStateOf(Provider.ZOHO_REGIONS.first()) }

    fun choose(p: Provider, r: String = region) {
        val was = provider.address(DavResourceFinder.Service.CARDDAV, region)
        provider = p; region = r
        val now = p.address(DavResourceFinder.Service.CARDDAV, r)
        // A filled-in address follows the choice; a typed one is left alone.
        if (now != null) address = now else if (address == was) address = ""
        status = null
    }

    fun go(keep: Boolean) {
        busy = true
        status = R.string.server_checking
        scope.launch {
            val result = SignIn.check(davSync, address, user, password, DavResourceFinder.Service.CARDDAV)
            busy = false
            said = (result as? SignInResult.Bad)?.detail.orEmpty()
            status = when (result) {
                is SignInResult.Good -> if (keep) {
                    val account = SignIn.keep(davSync, result)
                    if (account != null) {
                        onSignedIn(account)
                        null
                    } else R.string.server_not_kept
                } else R.string.server_good
                is SignInResult.Bad -> when (result.problem) {
                    Problem.BAD_ADDRESS -> R.string.server_bad_address
                    Problem.PLAIN_HTTP -> R.string.server_plain_http
                    Problem.NOT_FOUND -> R.string.server_not_found
                    Problem.UNREACHABLE -> R.string.server_unreachable
                    Problem.CERTIFICATE -> R.string.server_certificate
                    Problem.WRONG_PASSWORD -> R.string.server_wrong_password
                    Problem.NO_SERVICE -> R.string.server_no_carddav
                    Problem.NOTHING_FOUND -> R.string.server_no_books
                    Problem.KEYSTORE -> R.string.server_not_kept
                    Problem.OTHER -> R.string.server_other
                }
            }
        }
    }

    val ready = address.isNotBlank() && user.isNotBlank() && password.isNotEmpty() && !busy

    PagedColumn(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), showCutRow = true) {
        item {
            TextMMD(
                text = stringResource(R.string.server_why),
                style = helperStyle(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        item {
            ChoiceRow(
                label = stringResource(com.wanderwildwood.davsync.R.string.davsync_kind),
                choices = Provider.entries.map { stringResource(it.label) },
                chosen = provider.ordinal,
                enabled = !busy,
            ) { choose(Provider.entries[it]) }
        }
        if (provider == Provider.ZOHO) item {
            ChoiceRow(
                label = stringResource(com.wanderwildwood.davsync.R.string.davsync_zoho_region),
                choices = Provider.ZOHO_REGIONS.map { ".$it" },
                chosen = Provider.ZOHO_REGIONS.indexOf(region),
                enabled = !busy,
                perLine = 3,
            ) { choose(Provider.ZOHO, Provider.ZOHO_REGIONS[it]) }
        }
        item {
            TextMMD(
                text = stringResource(provider.note),
                style = helperStyle(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        item {
            LabelledField(
                label = stringResource(R.string.server_address),
                value = address,
                onChange = { address = it.replace("\n", "").trim(); status = null },
                keyboard = KeyboardType.Uri,
                words = false,
                hint = "cloud.example.org",
            )
        }
        item {
            LabelledField(
                label = stringResource(R.string.server_user),
                value = user,
                onChange = { user = it.replace("\n", ""); status = null },
                keyboard = KeyboardType.Email,
                words = false,
            )
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                TextMMD(text = stringResource(R.string.server_password), style = helperStyle(), fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextFieldMMD(
                        value = password,
                        onValueChange = { password = it.replace("\n", ""); status = null },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onGo = { if (ready) go(keep = true) }),
                    )
                    Spacer(Modifier.width(8.dp))
                    FootButton(stringResource(if (shown) R.string.server_hide else R.string.server_show), Modifier.width(72.dp)) { shown = !shown }
                }
                TextMMD(text = stringResource(R.string.server_password_note), style = helperStyle(), modifier = Modifier.padding(top = 4.dp))
            }
        }
        status?.let { s ->
            item {
                TextMMD(
                    text = stringResource(s) + if (said.isNotBlank()) "\n" + stringResource(com.wanderwildwood.davsync.R.string.davsync_said, said.take(300)) else "",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        item {
            Column(Modifier.padding(16.dp)) {
                FootButton(stringResource(R.string.server_sign_in), Modifier.fillMaxWidth(), enabled = ready) { go(keep = true) }
                Spacer(Modifier.height(8.dp))
                FootButton(stringResource(R.string.server_test), Modifier.fillMaxWidth(), enabled = ready) { go(keep = false) }
            }
        }
    }
}

/** A label over a row of plain buttons, one of them chosen (filled). [perLine] to a line. */
@Composable
private fun ChoiceRow(label: String, choices: List<String>, chosen: Int, enabled: Boolean, perLine: Int = 2, onChoose: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        TextMMD(text = label, style = helperStyle(), fontWeight = FontWeight.Bold)
        for (line in choices.indices.chunked(perLine)) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                for (i in line) {
                    val mod = Modifier.weight(1f).height(44.dp).padding(end = if (i == line.last()) 0.dp else 6.dp)
                    val pad = PaddingValues(horizontal = 4.dp)
                    if (i == chosen)
                        ButtonMMD(onClick = { onChoose(i) }, enabled = enabled, modifier = mod, contentPadding = pad) {
                            TextMMD(text = choices[i], style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    else
                        OutlinedButtonMMD(onClick = { onChoose(i) }, enabled = enabled, modifier = mod, contentPadding = pad) {
                            TextMMD(text = choices[i], style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                }
                repeat(perLine - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A new app password for a signed-in account, kept only once the server has taken it. */
@Composable
private fun ChangePassword(davSync: DavSync, account: Account, form: PasswordForm, scope: CoroutineScope, onDone: (Boolean) -> Unit) {
    var password by form::password
    var busy by form::busy
    var status by form::status
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        TextMMD(text = stringResource(DavR.string.davsync_new_password), style = helperStyle(), fontWeight = FontWeight.Bold)
        TextFieldMMD(
            value = password,
            onValueChange = { password = it.replace("\n", ""); status = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        )
        status?.let { TextMMD(text = stringResource(it), style = helperStyle(), modifier = Modifier.padding(top = 6.dp)) }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            FootButton(stringResource(DavR.string.davsync_cancel), Modifier.weight(1f), enabled = !busy) { onDone(false) }
            Spacer(Modifier.width(8.dp))
            FootButton(stringResource(DavR.string.davsync_save), Modifier.weight(1f), enabled = !busy && password.isNotEmpty()) {
                busy = true
                status = DavR.string.davsync_checking
                scope.launch {
                    val result = SignIn.changePassword(davSync, account, password, DavResourceFinder.Service.CARDDAV)
                    busy = false
                    when {
                        result is SignInResult.Good -> onDone(true)
                        (result as SignInResult.Bad).problem == Problem.WRONG_PASSWORD -> status = DavR.string.davsync_password_refused
                        else -> status = DavR.string.davsync_password_unchecked
                    }
                }
            }
        }
    }
}

/** What the Change password row holds while it is open. */
private class PasswordForm {
    var password by mutableStateOf("")
    var busy by mutableStateOf(false)
    var status by mutableStateOf<Int?>(null)
    fun reset() { password = ""; busy = false; status = null }
}

/** A new address book, made on the server and turned on here. [onDone] says whether one was made. */
@Composable
private fun NewAddressBook(davSync: DavSync, account: Account, form: NewForm, scope: CoroutineScope, onDone: (Boolean) -> Unit) {
    var name by form::name
    var busy by form::busy
    var status by form::status
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        TextMMD(text = stringResource(DavR.string.davsync_new_name), style = helperStyle(), fontWeight = FontWeight.Bold)
        TextFieldMMD(
            value = name,
            onValueChange = { name = it.replace("\n", ""); status = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        status?.let { TextMMD(text = stringResource(it), style = helperStyle(), modifier = Modifier.padding(top = 6.dp)) }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            FootButton(stringResource(DavR.string.davsync_cancel), Modifier.weight(1f), enabled = !busy) { onDone(false) }
            Spacer(Modifier.width(8.dp))
            FootButton(stringResource(DavR.string.davsync_create), Modifier.weight(1f), enabled = !busy && name.isNotBlank()) {
                busy = true
                status = DavR.string.davsync_creating
                scope.launch {
                    val result = CollectionMaker.create(davSync, account, DavResourceFinder.Service.CARDDAV, name.trim(), null)
                    busy = false
                    when (result) {
                        is CollectionMaker.Result.Made -> onDone(true)
                        CollectionMaker.Result.NoHome -> status = DavR.string.davsync_create_no_home
                        CollectionMaker.Result.Password -> status = DavR.string.davsync_create_password
                        CollectionMaker.Result.Unreachable -> status = DavR.string.davsync_create_unreachable
                        is CollectionMaker.Result.Refused -> status = DavR.string.davsync_create_refused
                    }
                }
            }
        }
    }
}

/** What the New address book row holds while it is open (held above the row, as [PasswordForm]). */
private class NewForm {
    var name by mutableStateOf("")
    var busy by mutableStateOf(false)
    var status by mutableStateOf<Int?>(null)
    fun reset() { name = ""; busy = false; status = null }
}

