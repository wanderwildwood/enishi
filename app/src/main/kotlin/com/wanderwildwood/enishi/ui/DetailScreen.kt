package com.wanderwildwood.enishi.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.enishi.R
import com.wanderwildwood.enishi.data.Call
import com.wanderwildwood.enishi.data.Card
import com.wanderwildwood.enishi.data.Field
import com.wanderwildwood.enishi.data.callsWith
import com.wanderwildwood.enishi.data.howLong
import com.wanderwildwood.enishi.data.numberKey
import com.wanderwildwood.enishi.data.whenSaid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File

/** The person, read again whenever the store changes, which is how an edit anywhere shows. */
@Composable
private fun rememberCard(model: BookModel, contactId: Long): Pair<Card?, Boolean> {
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

/** What the buttons under a name act on: a mobile if there is one, else the first number. */
private fun mainNumber(phones: List<Field>): Field? =
    phones.firstOrNull { it.type == android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE } ?: phones.firstOrNull()

/**
 * One person, as the phone's own contacts app shows one: the name large in the middle, the
 * number under it, and three buttons — Call, Message and More. Everything else a person has
 * is on More, as it is there.
 */
@Composable
fun DetailScreen(
    model: BookModel,
    contactId: Long,
    onBack: () -> Unit,
    onEdit: (Card) -> Unit,
    onMore: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (card, missing) = rememberCard(model, contactId)
    var starredNow by remember(contactId) { mutableStateOf<Boolean?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var choosing by remember { mutableStateOf<Pair<Boolean, List<Field>>?>(null) }
    val dial = rememberDialer { notice = context.getString(R.string.nothing_opens) }

    fun open(intent: Intent) {
        if (!start(context, intent)) notice = context.getString(R.string.nothing_opens)
    }
    fun text(number: String) = open(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(
                title = { BarTitle(stringResource(R.string.details_title)) },
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
        val phones = distinctNumbers(d.phones)
        val main = mainNumber(phones)
        Column(body.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Sizes and gaps measured off the phone's own Details page: a 35px capital in the
                // name, the buttons well below it. Larger than the type scale goes, on purpose.
                Spacer(Modifier.height(100.dp))
                TextMMD(
                    text = c.name.ifBlank { stringResource(R.string.no_name) },
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp, lineHeight = 42.sp),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                )
                listOf(d.jobTitle, d.company).filter { it.isNotBlank() }.joinToString(", ").takeIf { it.isNotEmpty() }?.let {
                    TextMMD(text = it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(10.dp))
                // Under the name, the number the buttons act on, its kind first, as the phone does it.
                val line: AnnotatedString? = when {
                    main != null -> kindAndValue(Labels.phone(context, main.type, main.label).lowercase(), main.value)
                    d.emails.isNotEmpty() -> kindAndValue(stringResource(R.string.field_email).lowercase(), d.emails.first().value)
                    else -> null
                }
                line?.let { TextMMD(text = it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center) }

                Spacer(Modifier.height(100.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (phones.isNotEmpty()) {
                        ActionTile(Icons.Call, stringResource(R.string.call)) {
                            if (phones.size == 1) dial(phones[0].value) else choosing = true to phones
                        }
                        ActionTile(Icons.Sms, stringResource(R.string.message)) {
                            if (phones.size == 1) text(phones[0].value) else choosing = false to phones
                        }
                    } else if (d.emails.isNotEmpty()) {
                        ActionTile(Icons.Mail, stringResource(R.string.field_email)) {
                            open(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", d.emails.first().value, null)))
                        }
                    }
                    ActionTile(Icons.GridView, stringResource(R.string.more), outlined = false, onPress = onMore)
                }
            }
            if (phones.isNotEmpty()) RecentCalls(model, phones.map { it.value }, dial)
        }
    }

    // Which number, when a person has more than one.
    choosing?.let { (calling, numbers) ->
        EInkDialog(onDismiss = { choosing = null }) {
            numbers.forEach { p ->
                NameRow(
                    AnnotatedString(p.value),
                    note = Labels.phone(context, p.type, p.label),
                    onPress = {
                        choosing = null
                        if (calling) dial(p.value) else text(p.value)
                    },
                )
            }
        }
    }
}

/**
 * The calls with them, newest first, under the buttons, as the phone's own contacts app shows
 * them when a call's "i" opens a person. The call log is asked for the first time it would be
 * shown, from a row that says so; a no is remembered, and the row does not ask again.
 */
@Composable
private fun RecentCalls(model: BookModel, numbers: List<String>, dial: (String) -> Unit) {
    val context = LocalContext.current
    fun allowed() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(allowed()) }
    var declined by remember { mutableStateOf(model.prefs.callsDeclined) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { yes ->
        granted = yes
        if (!yes) {
            model.prefs.callsDeclined = true
            declined = true
        }
    }
    var calls by remember(numbers) { mutableStateOf<List<Call>?>(null) }
    LaunchedEffect(numbers, granted) {
        if (granted) calls = withContext(Dispatchers.IO) {
            runCatching { callsWith(context.contentResolver, numbers) }.getOrDefault(emptyList())
        }
    }
    if (!granted && declined) return

    Spacer(Modifier.height(28.dp))
    Column(Modifier.fillMaxWidth()) {
        TextMMD(
            text = stringResource(R.string.calls_title),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        DottedRule()
        if (!granted) {
            NameRow(AnnotatedString(stringResource(R.string.calls_show)), note = stringResource(R.string.calls_show_note)) {
                ask.launch(Manifest.permission.READ_CALL_LOG)
            }
            return@Column
        }
        val list = calls ?: return@Column
        if (list.isEmpty()) {
            TextMMD(
                text = stringResource(R.string.calls_none),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
            return@Column
        }
        val now = System.currentTimeMillis()
        val several = numbers.map(::numberKey).distinct().size > 1
        list.forEach { call ->
            val kind = stringResource(
                when (call.kind) {
                    Call.Kind.IN -> R.string.calls_in
                    Call.Kind.OUT -> R.string.calls_out
                    Call.Kind.MISSED -> R.string.calls_missed
                    Call.Kind.DECLINED -> R.string.calls_declined
                },
            )
            val title = buildAnnotatedString {
                if (call.kind == Call.Kind.MISSED) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(kind) } else append(kind)
                append("  ")
                append(whenSaid(call.at, now, stringResource(R.string.calls_yesterday)))
            }
            // Which number, only when they have more than one; how long, when it was answered.
            val note = listOfNotNull(call.number.takeIf { several }, howLong(call.seconds)).joinToString(" · ")
            NameRow(title, note = note.ifEmpty { null }) { dial(call.number) }
        }
    }
}

private fun kindAndValue(kind: String, value: String): AnnotatedString = buildAnnotatedString {
    append(kind)
    append("  ")
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(value) }
}

/** A big outlined square with an icon and its word under it — Call, Message, More. */
@Composable
private fun ActionTile(icon: ImageVector, label: String, outlined: Boolean = true, onPress: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp).clickable(onClick = onPress),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 80.dp, height = 64.dp)
                .let {
                    if (outlined) it.border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface), RoundedCornerShape(14.dp)) else it
                },
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(8.dp))
        TextMMD(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Calls straight away once the phone has been allowed to place one; until then, and if it is
 * refused, the number goes to the dialer to press call there.
 */
@Composable
private fun rememberDialer(onNothing: () -> Unit): (String) -> Unit {
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

/**
 * Everything a person has, a bold label over each value, as the phone's own app lists it.
 * A press on a number calls it, on an address opens it. Sharing, where they are kept, and
 * delete — which asks in its own face — come last.
 */
@Composable
fun MoreScreen(
    model: BookModel,
    contactId: Long,
    onBack: () -> Unit,
    onEdit: (Card) -> Unit,
    onGone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (card, missing) = rememberCard(model, contactId)
    var notice by remember { mutableStateOf<String?>(null) }
    val dial = rememberDialer { notice = context.getString(R.string.nothing_opens) }
    fun open(intent: Intent) {
        if (!start(context, intent)) notice = context.getString(R.string.nothing_opens)
    }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    fun copy(value: String) {
        clipboard.setText(AnnotatedString(value))
        notice = context.getString(R.string.copied)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(
                title = { BarTitle(stringResource(R.string.more)) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
                actions = { card?.takeIf { !it.readOnly }?.let { c -> BarButton(Icons.Edit, stringResource(R.string.edit)) { onEdit(c) } } },
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
        androidx.compose.runtime.CompositionLocalProvider(LocalCopy provides ::copy) { LazyColumnMMD(body) {
            distinctNumbers(d.phones).forEach { p ->
                item {
                    KindRow(Labels.phone(context, p.type, p.label), p.value, ::copy) { dial(p.value) }
                }
            }
            d.emails.forEach { e ->
                item {
                    KindRow(Labels.email(context, e.type, e.label), e.value, ::copy) {
                        open(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", e.value, null)))
                    }
                }
            }
            listOf(
                R.string.field_given to d.given, R.string.field_family to d.family,
                R.string.field_prefix to d.prefix, R.string.field_middle to d.middle,
                R.string.field_suffix to d.suffix, R.string.field_nickname to d.nickname,
            ).filter { it.second.isNotBlank() }.forEach { (label, value) ->
                item { LabelValue(stringResource(label), value) }
            }
            if (listOf(d.given, d.family, d.prefix, d.middle, d.suffix).all { it.isBlank() } && d.wholeName.isNotBlank()) {
                item { LabelValue(stringResource(R.string.field_name), d.wholeName) }
            }
            d.addresses.forEach { a ->
                item {
                    LabelValue(stringResource(R.string.kind_address, Labels.address(context, a.type, a.label)), a.value) {
                        open(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(a.value.replace('\n', ' ')))))
                    }
                }
            }
            d.events.forEach { ev -> item { LabelValue(Labels.event(context, ev.type, ev.label), Labels.day(ev.value)) } }
            if (d.company.isNotBlank()) item { LabelValue(stringResource(R.string.field_company), d.company) }
            if (d.jobTitle.isNotBlank()) item { LabelValue(stringResource(R.string.field_job), d.jobTitle) }
            d.websites.forEach { w ->
                item {
                    LabelValue(stringResource(R.string.kind_website), w.value) {
                        val url = if (w.value.contains("://")) w.value else "https://${w.value}"
                        open(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
            }
            if (d.note.isNotBlank()) item { LabelValue(stringResource(R.string.kind_note), d.note, lines = 30) }
            val inGroups = model.groups.filter { it.id in d.groups.keys }.map { it.title }
            if (inGroups.isNotEmpty()) item { LabelValue(stringResource(R.string.kind_groups), inGroups.joinToString(", ")) }
            item {
                LabelValue(
                    stringResource(R.string.kept_in_label),
                    c.parts.map { Labels.account(context, it.account) }.distinct().joinToString(", "),
                )
            }
            item {
                PlainRow(stringResource(R.string.share), onPress = {
                    scope.launch {
                        val shared = model.io { book -> shareFile(context, c.name, c.lookup, book) }.getOrNull()
                        if (shared == null) notice = context.getString(R.string.share_failed) else open(shared)
                    }
                })
            }
            item {
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
}

/**
 * "Mobile  ·  +1 555 010 1001", one line, as the phone's own More page lists a number. A press
 * uses it; a long press copies it.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun KindRow(kind: String, value: String, onCopy: (String) -> Unit, onPress: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onPress, onLongClick = { onCopy(value) })
            .padding(horizontal = 16.dp, vertical = 22.dp),
    ) {
        TextMMD(text = kind, style = MaterialTheme.typography.bodyLarge)
        TextMMD(text = "  ·  ", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        TextMMD(text = value, style = MaterialTheme.typography.bodyLarge, maxLines = 2, modifier = Modifier.weight(1f))
    }
    DottedRule()
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
