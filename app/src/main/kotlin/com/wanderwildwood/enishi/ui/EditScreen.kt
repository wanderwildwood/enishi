package com.wanderwildwood.enishi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.checkbox.CheckboxMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.enishi.R
import com.wanderwildwood.enishi.data.Account
import com.wanderwildwood.enishi.data.Card
import com.wanderwildwood.enishi.data.Day
import com.wanderwildwood.enishi.data.Draft
import com.wanderwildwood.enishi.data.Field
import com.wanderwildwood.enishi.data.Typed
import com.wanderwildwood.enishi.with
import kotlinx.coroutines.launch

/** A birthday as the editor holds it: three boxes, read back into a day only on Save. */
private data class EventBox(val dataId: Long?, val type: Int, val label: String?, val year: String, val month: String, val day: String)

/**
 * A new person, or a change to one. Save sits in the top bar, where it can still be seen with
 * the keyboard up. Leaving with something changed asks first, in the bar's own words.
 */
@Composable
fun EditScreen(
    model: BookModel,
    card: Card?,
    seed: Draft,
    initialAccount: Account,
    onSaved: (Long) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val start = remember { if (card != null) card.draft.with(seed) else seed }
    var draft by remember { mutableStateOf(start) }

    // Days the store holds in a form it cannot read are carried through untouched, not shown.
    val (readable, unreadable) = remember { start.events.partition { Day.parse(it.value) != null } }
    val startBoxes = remember {
        readable.map { f ->
            val d = Day.parse(f.value)!!
            EventBox(f.dataId, f.type, f.label, d.year?.toString().orEmpty(), d.month.toString(), d.day.toString())
        }
    }
    var events by remember { mutableStateOf(startBoxes) }

    var account by remember { mutableStateOf(initialAccount) }
    var moreNames by remember { mutableStateOf(listOf(start.prefix, start.middle, start.suffix, start.nickname).any { it.isNotBlank() }) }
    var problem by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    // A refused Save says why at the top of the form, so the form goes there to say it.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(problem) { if (problem != null) listState.scrollToItem(0) }

    // An existing contact grows in the account it is mostly kept in; groups follow that account.
    val target: Account = card?.let { c -> c.parts.firstOrNull { it.account == model.saveTo }?.account ?: c.parts.firstOrNull()?.account } ?: account
    val groups = model.groups.filter { it.account == target }

    androidx.compose.runtime.LaunchedEffect(draft, events) { problem = null }

    val dirty = card == null && !draft.isEmpty || draft != start || events != startBoxes
    val (leaving, pressLeave) = rememberArmed(dirty) { onCancel() }
    val close = { if (dirty) pressLeave() else onCancel() }
    BackHandler(onBack = close)

    fun save() {
        val days = mutableListOf<Field>()
        for (e in events) {
            when (val t = Day.typed(e.year, e.month, e.day)) {
                Typed.Blank -> Unit
                Typed.Wrong -> {
                    problem = context.getString(R.string.edit_bad_date)
                    return
                }
                is Typed.Ok -> days += Field(t.day.stored(), e.type, e.label, e.dataId)
            }
        }
        val after = draft.copy(events = days + unreadable)
        if (after.isEmpty) {
            problem = context.getString(R.string.edit_empty)
            return
        }
        saving = true
        scope.launch {
            val result = model.io { book ->
                if (card == null) book.create(after, account) else { book.save(card, after, target); card.id }
            }
            saving = false
            val id = result.getOrNull()
            if (id == null) problem = context.getString(R.string.edit_failed) else onSaved(id)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = {
                    TextMMD(
                        text = stringResource(
                            when {
                                leaving -> R.string.edit_leave_armed
                                card == null -> R.string.edit_new_title
                                else -> R.string.edit_title
                            },
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = if (leaving) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = { BarButton(Icons.Close, stringResource(R.string.cd_close), close) },
                actions = { BarWord(stringResource(R.string.save), ready = !saving, onClick = ::save) },
            )
        },
    ) { padding ->
        LazyColumnMMD(Modifier.fillMaxSize().padding(padding).imePadding().background(MaterialTheme.colorScheme.surface), state = listState) {
            problem?.let {
                item {
                    TextMMD(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    HorizontalDividerMMD()
                }
            }

            // A name that came in whole, with no parts — from another app, or a vCard with only
            // a full name — is shown as it is; Android splits it into parts on saving.
            if (draft.wholeName.isNotBlank() && listOf(draft.given, draft.family, draft.middle, draft.prefix, draft.suffix).all { it.isBlank() }) {
                item { LabelledField(stringResource(R.string.field_name), draft.wholeName, { draft = draft.copy(wholeName = it) }) }
            } else {
                if (moreNames) item { LabelledField(stringResource(R.string.field_prefix), draft.prefix, { draft = draft.copy(prefix = it) }) }
                item { LabelledField(stringResource(R.string.field_given), draft.given, { draft = draft.copy(given = it) }) }
                if (moreNames) item { LabelledField(stringResource(R.string.field_middle), draft.middle, { draft = draft.copy(middle = it) }) }
                item { LabelledField(stringResource(R.string.field_family), draft.family, { draft = draft.copy(family = it) }) }
                if (moreNames) item { LabelledField(stringResource(R.string.field_suffix), draft.suffix, { draft = draft.copy(suffix = it) }) }
            }
            if (moreNames) {
                item { LabelledField(stringResource(R.string.field_nickname), draft.nickname, { draft = draft.copy(nickname = it) }) }
            } else {
                item { AddRow(stringResource(R.string.edit_more_names)) { moreNames = true } }
            }

            fieldList(
                title = R.string.field_phone, add = R.string.edit_add_phone,
                fields = draft.phones, types = Labels.PHONE_TYPES, keyboard = KeyboardType.Phone,
                label = { t, l -> Labels.phone(context, t, l) },
                newType = Labels.PHONE_TYPES.first(),
            ) { draft = draft.copy(phones = it) }
            fieldList(
                title = R.string.field_email, add = R.string.edit_add_email,
                fields = draft.emails, types = Labels.EMAIL_TYPES, keyboard = KeyboardType.Email,
                label = { t, l -> Labels.email(context, t, l) },
                newType = Labels.EMAIL_TYPES.first(),
            ) { draft = draft.copy(emails = it) }
            fieldList(
                title = R.string.field_address, add = R.string.edit_add_address,
                fields = draft.addresses, types = Labels.ADDRESS_TYPES, keyboard = KeyboardType.Text, multiLine = true,
                label = { t, l -> Labels.address(context, t, l) },
                newType = Labels.ADDRESS_TYPES.first(),
            ) { draft = draft.copy(addresses = it) }

            events.forEachIndexed { i, e ->
                item(key = "event$i") {
                    EventEditor(
                        box = e,
                        typeLabel = Labels.event(context, e.type, e.label),
                        onType = { events = events.toMutableList().also { l -> l[i] = e.copy(type = Labels.next(Labels.EVENT_TYPES, e.type), label = null) } },
                        onChange = { nb -> events = events.toMutableList().also { l -> l[i] = nb } },
                        onRemove = { events = events.toMutableList().also { l -> l.removeAt(i) } },
                    )
                }
            }
            item {
                AddRow(stringResource(if (events.none { it.type == android.provider.ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY }) R.string.edit_add_birthday else R.string.edit_add_date)) {
                    val birthday = android.provider.ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY
                    val type = if (events.none { it.type == birthday }) birthday else android.provider.ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY
                    events = events + EventBox(null, type, null, "", "", "")
                }
            }

            item { LabelledField(stringResource(R.string.field_company), draft.company, { draft = draft.copy(company = it) }) }
            item { LabelledField(stringResource(R.string.field_job), draft.jobTitle, { draft = draft.copy(jobTitle = it) }) }

            fieldList(
                title = R.string.field_website, add = R.string.edit_add_website,
                fields = draft.websites, types = emptyList(), keyboard = KeyboardType.Uri,
                label = { _, _ -> "" },
                newType = android.provider.ContactsContract.CommonDataKinds.Website.TYPE_HOMEPAGE,
            ) { draft = draft.copy(websites = it) }

            item { LabelledField(stringResource(R.string.field_note), draft.note, { draft = draft.copy(note = it) }, singleLine = false, words = false) }

            if (groups.isNotEmpty()) {
                item { Heading(stringResource(R.string.kind_groups)) }
                groups.forEach { g ->
                    item(key = "g${g.id}") {
                        val inIt = g.id in draft.groups
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    draft = draft.copy(groups = if (inIt) draft.groups - g.id else draft.groups + (g.id to start.groups[g.id]))
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            CheckboxMMD(checked = inIt, onCheckedChange = null)
                            Spacer(Modifier.width(14.dp))
                            TextMMD(text = g.title, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item {
                HorizontalDividerMMD(Modifier.padding(top = 10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { draft = draft.copy(starred = !draft.starred) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    TextMMD(text = stringResource(R.string.field_favourite), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    SwitchMMD(checked = draft.starred, onCheckedChange = null)
                }
                HorizontalDividerMMD(thickness = 0.5.dp)
            }

            // Only a new contact chooses; an existing one stays where it is.
            if (card == null && model.accounts.size > 1) {
                item {
                    PlainRow(
                        title = stringResource(R.string.edit_save_to),
                        note = Labels.account(context, account),
                        onPress = {
                            val all = model.accounts
                            account = all[(all.indexOf(account) + 1) % all.size]
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Numbers, addresses: each with its kind as a word that steps on when pressed. */
private fun LazyListScope.fieldList(
    title: Int,
    add: Int,
    fields: List<Field>,
    types: List<Int>,
    keyboard: KeyboardType,
    label: (Int, String?) -> String,
    newType: Int,
    multiLine: Boolean = false,
    onChange: (List<Field>) -> Unit,
) {
    fields.forEachIndexed { i, f ->
        item(key = "$title-$i") {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextMMD(text = stringResource(title), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    if (types.isNotEmpty()) {
                        TextMMD(
                            text = "  ·  " + label(f.type, f.label),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .clickable { onChange(fields.toMutableList().also { it[i] = f.copy(type = Labels.next(types, f.type), label = null) }) }
                                .padding(vertical = 8.dp, horizontal = 2.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    BarButton(Icons.Close, stringResource(R.string.cd_remove)) {
                        onChange(fields.toMutableList().also { it.removeAt(i) })
                    }
                }
                BareField(
                    value = f.value,
                    onChange = { v -> onChange(fields.toMutableList().also { it[i] = f.copy(value = v) }) },
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                    keyboard = keyboard,
                    words = multiLine,
                    singleLine = !multiLine,
                )
            }
        }
    }
    item(key = "$title-add") {
        AddRow(stringResource(add)) { onChange(fields + Field("", newType)) }
    }
}

@Composable
private fun EventEditor(box: EventBox, typeLabel: String, onType: () -> Unit, onChange: (EventBox) -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    // Day, month and year in the order the phone writes a date.
    val order = remember { android.text.format.DateFormat.getDateFormatOrder(context).toList() }
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextMMD(
                text = typeLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onType).padding(vertical = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            BarButton(Icons.Close, stringResource(R.string.cd_remove), onRemove)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(end = 12.dp)) {
            order.forEach { part ->
                when (part) {
                    'd' -> BareField(box.day, { onChange(box.copy(day = it.filter(Char::isDigit).take(2))) }, Modifier.weight(1f), KeyboardType.Number, hint = stringResource(R.string.date_day))
                    'M' -> BareField(box.month, { onChange(box.copy(month = it.filter(Char::isDigit).take(2))) }, Modifier.weight(1f), KeyboardType.Number, hint = stringResource(R.string.date_month))
                    'y' -> BareField(box.year, { onChange(box.copy(year = it.filter(Char::isDigit).take(4))) }, Modifier.weight(1.4f), KeyboardType.Number, hint = stringResource(R.string.date_year))
                }
            }
        }
    }
}

@Composable
private fun AddRow(text: String, onPress: () -> Unit) {
    TextMMD(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPress).padding(horizontal = 16.dp, vertical = 12.dp),
    )
    HorizontalDividerMMD(thickness = 0.5.dp)
}
