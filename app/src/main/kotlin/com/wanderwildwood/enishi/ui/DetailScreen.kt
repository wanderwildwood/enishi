package com.wanderwildwood.enishi.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.enishi.R
import com.wanderwildwood.enishi.data.Card
import kotlinx.coroutines.launch
import java.io.File

/**
 * One person. A press on a number calls it, the word beside it writes to it; a press on an
 * address opens it. Everything a person has is one press from being used, which is the
 * whole job of a contacts app on a phone.
 */
@Composable
fun DetailScreen(
    model: BookModel,
    contactId: Long,
    onBack: () -> Unit,
    onEdit: (Card) -> Unit,
    onGone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var card by remember(contactId) { mutableStateOf<Card?>(null) }
    var missing by remember(contactId) { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    // Read again whenever the store changes, which is how an edit here or elsewhere shows.
    LaunchedEffect(contactId, model.people, model.lastNameFirst) {
        val read = model.io { it.card(contactId, model.lastNameFirst) }.getOrNull()
        card = read
        missing = read == null
    }

    // A call goes straight through once the phone has been allowed to place one; until then,
    // and if it is refused, the number is handed to the dialer to press call there.
    var calling by remember { mutableStateOf<String?>(null) }
    val askCall = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        calling?.let { number -> if (!place(context, number, granted)) notice = context.getString(R.string.nothing_opens) }
        calling = null
    }
    fun call(number: String) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            if (!place(context, number, true)) notice = context.getString(R.string.nothing_opens)
        } else {
            calling = number
            askCall.launch(Manifest.permission.CALL_PHONE)
        }
    }
    fun open(intent: Intent) {
        if (!start(context, intent)) notice = context.getString(R.string.nothing_opens)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = {},
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
                actions = {
                    card?.let { c ->
                        BarButton(
                            if (c.draft.starred) Icons.Star else Icons.StarBorder,
                            stringResource(if (c.draft.starred) R.string.cd_unstar else R.string.cd_star),
                        ) {
                            scope.launch {
                                model.io { it.star(c.id, !c.draft.starred) }
                                card = c.copy(draft = c.draft.copy(starred = !c.draft.starred))
                            }
                        }
                        BarWord(stringResource(R.string.edit)) { onEdit(c) }
                    }
                },
            )
        },
        bottomBar = { notice?.let { NoticeStrip(it) { notice = null } } },
    ) { padding ->
        val body = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface)
        val c = card
        if (c == null) {
            if (missing) Quiet(stringResource(R.string.contact_gone), body)
            return@Scaffold
        }
        val d = c.draft
        LazyColumnMMD(body) {
            item {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) {
                    TextMMD(
                        text = c.name.ifBlank { stringResource(R.string.no_name) },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    val under = listOfNotNull(
                        d.nickname.takeIf { it.isNotBlank() }?.let { "“$it”" },
                        listOf(d.jobTitle, d.company).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null },
                    )
                    under.forEach { TextMMD(text = it, style = MaterialTheme.typography.bodySmall) }
                }
                HorizontalDividerMMD()
            }
            d.phones.forEach { p ->
                item {
                    PlainRow(
                        title = p.value,
                        note = Labels.phone(context, p.type, p.label),
                        onPress = { call(p.value) },
                        trailing = {
                            FootButton(stringResource(R.string.text), Modifier.width(88.dp)) {
                                open(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", p.value, null)))
                            }
                        },
                    )
                }
            }
            d.emails.forEach { e ->
                item {
                    PlainRow(
                        title = e.value,
                        note = stringResource(R.string.kind_email, Labels.email(context, e.type, e.label)),
                        onPress = { open(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", e.value, null))) },
                    )
                }
            }
            d.addresses.forEach { a ->
                item {
                    PlainRow(
                        title = a.value,
                        titleLines = 4,
                        note = stringResource(R.string.kind_address, Labels.address(context, a.type, a.label)),
                        onPress = { open(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(a.value.replace('\n', ' '))))) },
                    )
                }
            }
            d.events.forEach { ev ->
                item { PlainRow(title = Labels.day(ev.value), note = Labels.event(context, ev.type, ev.label), onPress = null) }
            }
            d.websites.forEach { w ->
                item {
                    PlainRow(title = w.value, note = stringResource(R.string.kind_website), onPress = {
                        val url = if (w.value.contains("://")) w.value else "https://${w.value}"
                        open(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    })
                }
            }
            if (d.note.isNotBlank()) {
                item { PlainRow(title = d.note, titleLines = 20, note = stringResource(R.string.kind_note), onPress = null) }
            }
            val inGroups = model.groups.filter { it.id in d.groups.keys }.map { it.title }
            if (inGroups.isNotEmpty()) {
                item { PlainRow(title = inGroups.joinToString(", "), note = stringResource(R.string.kind_groups), onPress = null) }
            }

            item { Spacer(Modifier.padding(top = 18.dp)); HorizontalDividerMMD() }
            item {
                PlainRow(stringResource(R.string.share), onPress = {
                    scope.launch {
                        val shared = model.io { book -> shareFile(context, c.name, c.lookup, book) }.getOrNull()
                        if (shared == null) notice = context.getString(R.string.share_failed) else open(shared)
                    }
                })
            }
            // Where they are kept, worth knowing when an account syncs and the phone does not.
            item {
                TextMMD(
                    text = stringResource(R.string.kept_in, c.parts.map { Labels.account(context, it.account) }.distinct().joinToString(", ")),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
                HorizontalDividerMMD(thickness = 0.5.dp)
            }
            item {
                val (armed, press) = rememberArmed(c.id) {
                    scope.launch {
                        model.io { it.delete(c.id, c.lookup) }
                        onGone()
                    }
                }
                PlainRow(
                    stringResource(if (armed) R.string.delete_contact_armed else R.string.delete_contact),
                    bold = armed,
                    onPress = press,
                )
            }
        }
    }
}

/** Calls straight away, or hands the number to the dialer when calling was not allowed. */
private fun place(context: Context, number: String, direct: Boolean): Boolean {
    val uri = Uri.fromParts("tel", number, null)
    if (direct && start(context, Intent(Intent.ACTION_CALL, uri))) return true
    return start(context, Intent(Intent.ACTION_DIAL, uri))
}

/** A press that leads nowhere says so, rather than doing nothing. */
internal fun start(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

/**
 * The person as a .vcf, named for them, handed to whatever the reader shares it with —
 * Messaging sends it as a contact card. Written by the store itself, so nothing is lost on
 * the way.
 */
private fun shareFile(context: Context, name: String, lookup: String, book: com.wanderwildwood.enishi.data.Book): Intent {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifBlank { "contact" }
    val file = File(dir, "$safe.vcf")
    file.outputStream().use { book.vcardOf(lookup, it) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/x-vcard")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
