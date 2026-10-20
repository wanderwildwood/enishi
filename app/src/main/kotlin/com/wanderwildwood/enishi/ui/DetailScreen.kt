package com.wanderwildwood.enishi.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.enishi.R
import com.wanderwildwood.enishi.data.Call
import com.wanderwildwood.enishi.data.Card
import com.wanderwildwood.enishi.data.Field
import com.wanderwildwood.enishi.data.callsWith
import com.wanderwildwood.enishi.data.howLong
import com.wanderwildwood.enishi.data.numberKey
import com.wanderwildwood.enishi.data.whenSaid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The person, read again whenever the store changes, which is how an edit anywhere shows. */
@Composable
internal fun rememberCard(model: BookModel, contactId: Long): Pair<Card?, Boolean> {
    var card by remember(contactId) { mutableStateOf<Card?>(null) }
    var missing by remember(contactId) { mutableStateOf(false) }
    LaunchedEffect(contactId, model.people, model.lastNameFirst) {
        val read = model.io { it.card(contactId, model.lastNameFirst) }.getOrNull()
        card = read
        missing = read == null
    }
    return card to missing
}

/**
 * Numbers as they should be shown: once each. A person joined from two places (a phone copy
 * and an account copy, or a contact added twice) often carries the same number twice, and the
 * phone's own app lists both; here one is enough. Editing still shows every row.
 */
internal fun distinctNumbers(phones: List<Field>): List<Field> =
    phones.distinctBy { p -> p.value.filter(Char::isDigit).takeLast(10).ifEmpty { p.value } }


/** How many of the calls with a person their page shows; All calls has the rest. */
private const val RECENT_CALLS = 5

/**
 * One person, as Phone and Messaging draw a page: their name in the bar, and everything they
 * have as plain rows down the page, the value over what kind it is.
 *
 * A press on a number calls it, through the phone app; the speech bubble at its end writes to
 * it. A press on an email address writes to it, on an address opens it in a map, on a website
 * opens it, and a long press on any of them copies it. Under those, the last few calls with
 * them, drawn as Phone's Recents are, and All calls when there are more. Sharing, handing them
 * to Field Kit when it is on the phone, and delete — which asks in its own face — come last.
 */
@Composable
fun DetailScreen(
    model: BookModel,
    contactId: Long,
    onBack: () -> Unit,
    onEdit: (Card) -> Unit,
    onCalls: () -> Unit,
    onGone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (card, missing) = rememberCard(model, contactId)
    var starredNow by remember(contactId) { mutableStateOf<Boolean?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    // The name in the bar, cut short: then it is said in full at the top of the page.
    var nameCut by remember(contactId) { mutableStateOf(false) }
    val dial = rememberDialer { notice = context.getString(R.string.nothing_opens) }

    fun open(intent: Intent) {
        if (!start(context, intent)) notice = context.getString(R.string.nothing_opens)
    }
    fun text(number: String) = open(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))
    val clipboard = LocalClipboardManager.current
    fun copy(value: String) {
        clipboard.setText(AnnotatedString(value))
        notice = context.getString(R.string.copied)
    }

    // Looked at again on every return, so an app added or removed meanwhile shows or goes.
    var elsewhere by remember { mutableStateOf(Elsewhere.installed(context)) }
    var handing by remember { mutableStateOf<Elsewhere?>(null) }

    // The calls with them, read once the call log may be: one more than are shown, which is how
    // the page knows whether All calls has anything to add.
    fun mayReadCalls() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
    var callsAllowed by remember { mutableStateOf(mayReadCalls()) }
    val askCalls = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { callsAllowed = it }
    LifecycleResumeEffect(Unit) {
        elsewhere = Elsewhere.installed(context)
        callsAllowed = mayReadCalls()
        onPauseOrDispose { }
    }
    val numbers = card?.let { distinctNumbers(it.draft.phones) } ?: emptyList()
    var calls by remember(contactId) { mutableStateOf<List<Call>>(emptyList()) }
    LaunchedEffect(numbers, callsAllowed) {
        calls = if (!callsAllowed || numbers.isEmpty()) emptyList() else withContext(Dispatchers.IO) {
            runCatching { callsWith(context.contentResolver, numbers.map { it.value }, limit = RECENT_CALLS + 1) }.getOrDefault(emptyList())
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(
                title = {
                    card?.let { c -> BarTitle(c.name.ifBlank { stringResource(R.string.no_name) }) { nameCut = it } }
                },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
                actions = {
                    card?.let { c ->
                        val starred = starredNow ?: c.draft.starred
                        if (!c.readOnly) BarButton(Icons.Edit, stringResource(R.string.edit)) { onEdit(c) }
                        BarButton(
                            if (starred) Icons.Star else Icons.StarBorder,
                            stringResource(if (starred) R.string.cd_unstar else R.string.cd_star),
                        ) {
                            starredNow = !starred
                            scope.launch { model.io { it.star(c.id, !starred) } }
                        }
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
        val organisation = listOf(d.jobTitle, d.company).filter { it.isNotBlank() }.joinToString(", ")
        CompositionLocalProvider(LocalCopy provides ::copy) { PagedColumn(body) {
            // Who they are, under the bar: the name again only when the bar had to cut it.
            if (nameCut || organisation.isNotEmpty()) {
                item(key = "who") {
                    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp)) {
                        if (nameCut) {
                            TextMMD(text = c.name, style = MaterialTheme.typography.bodyLarge.copy(fontSize = rowText), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        if (organisation.isNotEmpty()) {
                            TextMMD(text = organisation, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    DottedRule()
                }
            }
            numbers.forEach { p ->
                item {
                    ValueRow(
                        value = p.value,
                        kind = Labels.phone(context, p.type, p.label),
                        onPress = { dial(p.value) },
                        trailing = { BarButton(Icons.Sms, stringResource(R.string.cd_message_number, p.value)) { text(p.value) } },
                    )
                }
            }
            d.emails.forEach { e ->
                item {
                    ValueRow(e.value, stringResource(R.string.kind_email, Labels.email(context, e.type, e.label))) {
                        open(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", e.value, null)))
                    }
                }
            }
            d.addresses.forEach { a ->
                item {
                    ValueRow(a.value, stringResource(R.string.kind_address, Labels.address(context, a.type, a.label)), lines = 4) {
                        val map = onMap(context, a.value)
                        if (map == null) notice = context.getString(R.string.nothing_opens) else open(map)
                    }
                }
            }
            if (d.nickname.isNotBlank()) item { ValueRow(d.nickname, stringResource(R.string.field_nickname)) }
            d.events.forEach { ev -> item { ValueRow(Labels.day(ev.value), Labels.event(context, ev.type, ev.label)) } }
            d.websites.forEach { w ->
                item {
                    ValueRow(w.value, stringResource(R.string.kind_website)) {
                        val url = if (w.value.contains("://")) w.value else "https://${w.value}"
                        open(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
            }
            if (d.note.isNotBlank()) item { ValueRow(d.note, stringResource(R.string.kind_note), lines = 30) }
            val inGroups = model.groups.filter { it.id in d.groups.keys }.map { it.title }
            if (inGroups.isNotEmpty()) item { ValueRow(inGroups.joinToString(", "), stringResource(R.string.kind_groups)) }

            // The calls with them, as Phone's Recents draws them; asked for the first time from
            // a row that says so, since the call log is not the contacts' to read.
            if (numbers.isNotEmpty()) {
                if (!callsAllowed) {
                    item(key = "calls-ask") {
                        PlainRow(stringResource(R.string.calls_show), note = stringResource(R.string.calls_show_note), onPress = {
                            askCalls.launch(Manifest.permission.READ_CALL_LOG)
                        })
                    }
                } else if (calls.isNotEmpty()) {
                    item(key = "calls-heading") { Heading(stringResource(R.string.calls_recent)) }
                    // Which of their numbers, by its kind, only when they have more than one.
                    val kinds = numbers.associate { numberKey(it.value) to Labels.phone(context, it.type, it.label) }
                    calls.take(RECENT_CALLS).forEachIndexed { i, call ->
                        item(key = "call-$i") { CallLine(call, kinds[numberKey(call.number)].takeIf { kinds.size > 1 }) }
                    }
                    if (calls.size > RECENT_CALLS) item(key = "calls-all") { PlainRow(stringResource(R.string.calls_all), onPress = onCalls) }
                }
            }

            item(key = "kept") {
                ValueRow(
                    c.parts.map { Labels.account(context, it.account) }.distinct().joinToString(", "),
                    stringResource(R.string.kept_in_label),
                )
            }
            item(key = "share") {
                PlainRow(stringResource(R.string.share), onPress = {
                    scope.launch {
                        val shared = model.io { book -> shareFile(context, c.name, c.lookup, book) }.getOrNull()
                        if (shared == null) notice = context.getString(R.string.share_failed) else open(shared)
                    }
                })
            }
            // Medicine and Field Kit, when they are on the phone: each wants a number.
            if (numbers.isNotEmpty()) {
                elsewhere.forEach { app ->
                    item(key = "elsewhere-${app.name}") {
                        PlainRow(stringResource(app.label), onPress = {
                            if (numbers.size == 1) open(app.intent(c.id, c.lookup, c.name, numbers[0].value))
                            else handing = app
                        })
                    }
                }
            }
            item(key = "delete") {
                val (armed, press) = rememberArmed(c.id) {
                    scope.launch {
                        // Gone only if it went: a store that refused says so here.
                        if (model.io { it.delete(c.id, c.lookup) }.isSuccess) onGone()
                        else notice = context.getString(R.string.delete_failed)
                    }
                }
                PlainRow(
                    stringResource(if (armed) R.string.delete_contact_armed else R.string.delete_contact),
                    bold = armed,
                    onPress = press,
                )
            }
        } }
    }

    // Which number goes to Medicine or Field Kit, when a person has more than one.
    val c = card
    handing?.let { app ->
        if (c == null) return@let
        EInkDialog(onDismiss = { handing = null }) {
            numbers.forEach { p ->
                NameRow(
                    AnnotatedString(p.value),
                    note = Labels.phone(context, p.type, p.label),
                    onPress = {
                        handing = null
                        open(app.intent(c.id, c.lookup, c.name, p.value))
                    },
                )
            }
        }
    }
}

/**
 * One thing a person has: the value, and under it what kind it is — "Mobile", "Email · Work",
 * "Birthday". A press uses it where it has a use; a long press copies it. [trailing] is a button
 * at the row's end, as a number's speech bubble.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ValueRow(
    value: String,
    kind: String,
    lines: Int = 2,
    trailing: (@Composable () -> Unit)? = null,
    onPress: (() -> Unit)? = null,
) {
    val copy = LocalCopy.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 65.dp)
            .combinedClickable(onClick = onPress ?: {}, onLongClick = copy?.let { c -> { c(value) } })
            .padding(start = 16.dp, end = if (trailing != null) 4.dp else 16.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            TextMMD(text = value, style = MaterialTheme.typography.bodyLarge.copy(fontSize = rowText), maxLines = lines, overflow = TextOverflow.Ellipsis)
            TextMMD(text = kind, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
    DottedRule()
}

/**
 * One call with them, as Phone's Recents draws a row: the arrow for the way it went, when it was,
 * and under that what kind of call and how long — and which number by its kind, "Mobile", when
 * they have more than one. A missed call is the one in bold. A press does nothing: the number's
 * row above calls.
 */
@Composable
private fun CallLine(call: Call, numberKind: String?) {
    val context = LocalContext.current
    val kind = stringResource(
        when (call.kind) {
            Call.Kind.IN -> R.string.calls_in
            Call.Kind.OUT -> R.string.calls_out
            Call.Kind.MISSED -> R.string.calls_missed
            Call.Kind.DECLINED -> R.string.calls_declined
        },
    )
    val now = System.currentTimeMillis()
    val hours24 = android.text.format.DateFormat.is24HourFormat(context)
    val title = whenSaid(call.at, now, stringResource(R.string.calls_today), stringResource(R.string.calls_yesterday), hours24)
    val note = listOfNotNull(kind, howLong(call.seconds), numberKind).joinToString(" · ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 17.5.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Icon(
            when (call.kind) {
                Call.Kind.IN -> Icons.CallIn
                Call.Kind.OUT -> Icons.CallOut
                else -> Icons.CallMissed
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(15.5.dp))
        Column(Modifier.weight(1f)) {
            TextMMD(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = rowText),
                fontWeight = if (call.kind == Call.Kind.MISSED) FontWeight.Bold else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextMMD(text = note, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    DottedRule(start = 59.dp, end = 0.dp)
}

/**
 * Calls straight away once the phone has been allowed to place one; until then, and if it is
 * refused, the number goes to the dialer to press call there.
 */
@Composable
internal fun rememberDialer(onNothing: () -> Unit): (String) -> Unit {
    val context = LocalContext.current
    var calling by remember { mutableStateOf<String?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        calling?.let { if (!place(context, it, granted)) onNothing() }
        calling = null
    }
    return { number ->
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            if (!place(context, number, true)) onNothing()
        } else {
            calling = number
            ask.launch(Manifest.permission.CALL_PHONE)
        }
    }
}


/** Calls straight away, or hands the number to the dialer when calling was not allowed. */
private fun place(context: Context, number: String, direct: Boolean): Boolean {
    val uri = Uri.fromParts("tel", number, null)
    if (direct && start(context, Intent(Intent.ACTION_CALL, uri))) return true
    return start(context, Intent(Intent.ACTION_DIAL, uri))
}

/**
 * An address shown in whatever map app the phone has, as a plain geo: search. Topo answers
 * geo: too, but only to mark a beacon at the place it is given — it cannot look up a street
 * address — so it is left out: when it is among the answers the rest are offered in the
 * phone's own chooser, and when it is the only one there is nothing to open the address.
 */
internal fun onMap(context: Context, address: String): Intent? {
    val view = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address.replace('\n', ' '))))
    val answers = context.packageManager.queryIntentActivities(view, PackageManager.MATCH_DEFAULT_ONLY)
    val topo = answers.filter { it.activityInfo.packageName.startsWith(TOPO) }
    if (topo.isEmpty()) return view
    if (topo.size == answers.size) return null
    val leftOut = topo.map { android.content.ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
    return Intent.createChooser(view, null).putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, leftOut.toTypedArray())
}

private const val TOPO = "com.wanderwildwood.chikei"

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
private fun shareFile(context: Context, name: String, lookup: String, book: com.wanderwildwood.enishi.data.Book): Intent =
    shareCards(context, name) { book.vcardOf(lookup, it) }

/** Several people as one .vcf, for sharing everyone chosen at once. */
internal fun shareMany(context: Context, lookups: List<String>, book: com.wanderwildwood.enishi.data.Book): Intent =
    shareCards(context, "contacts") { book.exportTo(lookups, it) }

private fun shareCards(context: Context, name: String, write: (java.io.OutputStream) -> Unit): Intent {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifBlank { "contact" }
    val file = File(dir, "$safe.vcf")
    file.outputStream().use(write)
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/x-vcard")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
