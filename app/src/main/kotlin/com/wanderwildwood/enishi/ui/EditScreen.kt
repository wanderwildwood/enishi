package com.wanderwildwood.enishi.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.checkbox.CheckboxMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.menus.DropdownMenuItemMMD
import com.mudita.mmd.components.menus.DropdownMenuMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.enishi.R
import com.wanderwildwood.enishi.data.Account
import com.wanderwildwood.enishi.data.Birthdays
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
 * A new person, or a change to one, laid out as the phone's own contacts app lays its editor
 * out: numbers first, each on one line with its kind as a small menu and an empty line always
 * waiting for the next; then a bold label over each field, dotted rules between. Save sits in
 * the top bar where the keyboard cannot cover it, and stays grey until there is something to
 * save. Leaving with something changed asks first, in the bar's own words.
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
    // Whether the birthday is in the calendar too. The calendar itself is the only record of
    // it, read once the form is open.
    var startCalendar by remember { mutableStateOf(false) }
    var calendar by remember { mutableStateOf(false) }
    LaunchedEffect(card) {
        if (card != null) model.io { model.birthdays.isOn(card.id) }.getOrNull()?.let {
            startCalendar = it
            calendar = it
        }
    }
    // A calendar the contacts' own server already fills with birthdays — Nextcloud's, Google's —
    // in which case the switch gives way to saying so, rather than show the birthday twice.
    var synced by remember { mutableStateOf<String?>(null) }
    val accountTypes = card?.parts?.map { it.account.type } ?: listOf(account.type)
    LaunchedEffect(accountTypes) {
        synced = model.io { model.birthdays.syncedCalendar(accountTypes) }.getOrNull()
    }
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) {
            scope.launch {
                val found = model.io { model.birthdays.syncedCalendar(accountTypes) }.getOrNull()
                synced = found
                if (found == null) calendar = true
            }
        } else {
            problem = context.getString(R.string.edit_calendar_refused)
        }
    }
    // The date the reader just added, which takes the cursor.
    var addedDate by remember { mutableStateOf<Int?>(null) }
    // A refused Save says why at the top of the form, so the form goes there to say it.
    val listState = rememberLazyListState()
    LaunchedEffect(problem) { if (problem != null) listState.scrollToItem(0) }

    // Anything new goes into the copy that syncs (or the chosen one); groups follow that copy,
    // since a group belongs to one account and only a copy kept there can join it.
    val cardTarget = remember(card) { card?.let { model.book.target(it, model.saveTo)?.account } }
    val target: Account = cardTarget ?: account
    val groups = model.groups.filter { it.account == target }
    // A group ticked under one "Save to" is not carried into another account's.
    LaunchedEffect(target) {
        val here = groups.map { it.id }.toSet()
        if (draft.groups.keys.any { it !in here && it !in start.groups }) {
            draft = draft.copy(groups = draft.groups.filterKeys { it in here || it in start.groups })
        }
    }

    LaunchedEffect(draft, events) { problem = null }

    // Measured against what is stored, not against the form as it opened: "add this address to
    // someone" opens with the address already in, and that is something to save.
    val dirty = if (card == null) !draft.isEmpty else draft.copy(events = emptyList()) != card.draft.copy(events = emptyList()) || events != startBoxes || calendar != startCalendar
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
                val id = if (card == null) book.create(after, account) else { book.save(card, after, target); card.id }
                // A calendar that could not be written to does not undo the contact.
                if (id != null) runCatching { if (calendar) model.birthdays.add(id) else if (startCalendar) model.birthdays.remove(id) }
                id
            }
            saving = false
            val id = result.getOrNull()
            if (id == null) problem = context.getString(R.string.edit_failed) else onSaved(id)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(
                title = {
                    if (leaving) {
                        TextMMD(text = stringResource(R.string.edit_leave_armed), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        BarTitle(stringResource(if (card == null) R.string.edit_new_title else R.string.edit_title))
                    }
                },
                navigationIcon = { BarButton(Icons.Close, stringResource(R.string.cd_close), close) },
                actions = { SaveButton(ready = dirty && !saving, onClick = ::save) },
            )
        },
    ) { padding ->
        LazyColumnMMD(
            Modifier.fillMaxSize().padding(padding).imePadding().background(MaterialTheme.colorScheme.surface),
            state = listState,
        ) {
            problem?.let {
                item {
                    TextMMD(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    DottedRule()
                }
            }

            kindLines(
                key = "phone", fields = draft.phones, types = Labels.PHONE_TYPES, keyboard = KeyboardType.Phone,
                label = { t, l -> Labels.phone(context, t, l) }, hint = context.getString(R.string.hint_phone),
            ) { draft = draft.copy(phones = it) }

            // A name that came in whole, with no parts — from another app, or a vCard with only
            // a full name — is shown as it is; Android splits it into parts on saving.
            if (draft.wholeName.isNotBlank() && listOf(draft.given, draft.family, draft.middle, draft.prefix, draft.suffix).all { it.isBlank() }) {
                item { LabelledLine(stringResource(R.string.field_name), draft.wholeName, { draft = draft.copy(wholeName = it) }) }
            } else {
                item { LabelledLine(stringResource(R.string.field_given), draft.given, { draft = draft.copy(given = it) }) }
                item { LabelledLine(stringResource(R.string.field_family), draft.family, { draft = draft.copy(family = it) }) }
                if (moreNames) {
                    item { LabelledLine(stringResource(R.string.field_prefix), draft.prefix, { draft = draft.copy(prefix = it) }) }
                    item { LabelledLine(stringResource(R.string.field_middle), draft.middle, { draft = draft.copy(middle = it) }) }
                    item { LabelledLine(stringResource(R.string.field_suffix), draft.suffix, { draft = draft.copy(suffix = it) }) }
                }
            }
            if (moreNames) {
                item { LabelledLine(stringResource(R.string.field_nickname), draft.nickname, { draft = draft.copy(nickname = it) }) }
            } else {
                item { AddRow(stringResource(R.string.edit_more_names)) { moreNames = true } }
            }

            kindLines(
                key = "email", fields = draft.emails, types = Labels.EMAIL_TYPES, keyboard = KeyboardType.Email,
                label = { t, l -> Labels.email(context, t, l) }, hint = context.getString(R.string.hint_email),
            ) { draft = draft.copy(emails = it) }
            kindLines(
                key = "address", fields = draft.addresses, types = Labels.ADDRESS_TYPES, keyboard = KeyboardType.Text,
                label = { t, l -> Labels.address(context, t, l) }, hint = context.getString(R.string.hint_address), multiLine = true,
            ) { draft = draft.copy(addresses = it) }

            events.forEachIndexed { i, e ->
                item(key = "event$i") {
                    EventLine(
                        box = e,
                        typeLabel = Labels.event(context, e.type, e.label),
                        types = Labels.EVENT_TYPES.map { it to Labels.event(context, it, null) },
                        onType = { t -> events = events.toMutableList().also { l -> l[i] = e.copy(type = t, label = null) } },
                        onChange = { nb -> events = events.toMutableList().also { l -> l[i] = nb } },
                        focusNow = addedDate == i,
                    )
                }
                if (e.type == android.provider.ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY &&
                    events.indexOfFirst { it.type == e.type } == i
                ) {
                    item(key = "calendar") {
                        val already = synced
                        // One already switched on stays a switch, so it can be switched off.
                        if (already != null && !calendar && !startCalendar) {
                            TextMMD(
                                text = stringResource(R.string.edit_birthday_synced, already),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                            )
                            DottedRule()
                            return@item
                        }
                        SwitchLine(stringResource(R.string.edit_birthday_calendar), calendar) {
                            when {
                                calendar -> calendar = false
                                model.birthdays.allowed() -> calendar = true
                                else -> askCalendar.launch(Birthdays.PERMISSIONS)
                            }
                        }
                    }
                }
            }
            item {
                val birthday = android.provider.ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY
                val noBirthday = events.none { it.type == birthday }
                AddRow(stringResource(if (noBirthday) R.string.edit_add_birthday else R.string.edit_add_date)) {
                    addedDate = events.size
                    val type = if (noBirthday) birthday else android.provider.ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY
                    events = events + EventBox(null, type, null, "", "", "")
                }
            }

            item { LabelledLine(stringResource(R.string.field_company), draft.company, { draft = draft.copy(company = it) }) }
            item { LabelledLine(stringResource(R.string.field_job), draft.jobTitle, { draft = draft.copy(jobTitle = it) }) }

            kindLines(
                key = "web", fields = draft.websites, types = emptyList(), keyboard = KeyboardType.Uri,
                label = { _, _ -> context.getString(R.string.kind_website) }, hint = context.getString(R.string.hint_website),
                newType = android.provider.ContactsContract.CommonDataKinds.Website.TYPE_HOMEPAGE,
            ) { draft = draft.copy(websites = it) }

            item { LabelledLine(stringResource(R.string.field_note), draft.note, { draft = draft.copy(note = it) }, singleLine = false, words = false) }

            if (groups.isNotEmpty()) {
                item {
                    TextMMD(
                        text = stringResource(R.string.kind_groups),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    )
                }
                groups.forEach { g ->
                    item(key = "g${g.id}") {
                        val inIt = g.id in draft.groups
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    draft = draft.copy(groups = if (inIt) draft.groups - g.id else draft.groups + (g.id to start.groups[g.id].orEmpty()))
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            CheckboxMMD(checked = inIt, onCheckedChange = null)
                            Spacer(Modifier.width(14.dp))
                            TextMMD(text = g.title, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                item { DottedRule() }
            }

            item {
                SwitchLine(stringResource(R.string.field_favourite), draft.starred) { draft = draft.copy(starred = !draft.starred) }
            }

            // Only a new contact chooses; an existing one stays where it is.
            if (card == null && model.accounts.size > 1) {
                item {
                    LabelValue(stringResource(R.string.edit_save_to), Labels.account(context, account)) {
                        val all = model.accounts
                        account = all[(all.indexOf(account) + 1) % all.size]
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Save, in the bar: filled when there is something to save, grey when there is not. */
@Composable
private fun SaveButton(ready: Boolean, onClick: () -> Unit) {
    ButtonMMD(
        onClick = onClick,
        enabled = ready,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = Color(0xFFE8E8E8),
            disabledContentColor = Color(0xFF9A9A9A),
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
        modifier = Modifier.padding(end = 10.dp).height(44.dp),
    ) {
        TextMMD(text = stringResource(R.string.save), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

/**
 * Numbers, addresses: one line each, its kind as a small menu in front, and an empty line at
 * the end always ready for the next, so there is no "add" to press. Emptying a line takes it
 * away on Save.
 */
private fun LazyListScope.kindLines(
    key: String,
    fields: List<Field>,
    types: List<Int>,
    keyboard: KeyboardType,
    label: (Int, String?) -> String,
    hint: String,
    multiLine: Boolean = false,
    newType: Int = types.firstOrNull() ?: 0,
    onChange: (List<Field>) -> Unit,
) {
    // The empty line waiting at the end is not one of the fields until something is typed in it.
    val shown = fields + Field("", newType)
    shown.forEachIndexed { i, f ->
        val isNew = i == fields.size
        item(key = "$key-$i") {
            Row(
                verticalAlignment = if (multiLine) Alignment.Top else Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 20.dp),
            ) {
                KindMenu(
                    current = label(f.type, f.label),
                    choices = types.map { it to label(it, null) },
                    onPick = { t ->
                        val changed = f.copy(type = t, label = null)
                        onChange(if (isNew) fields + changed else fields.toMutableList().also { it[i] = changed })
                    },
                )
                Spacer(Modifier.width(12.dp))
                PlainField(
                    value = f.value,
                    onChange = { v ->
                        onChange(if (isNew) fields + f.copy(value = v) else fields.toMutableList().also { it[i] = f.copy(value = v) })
                    },
                    hint = if (isNew) hint else null,
                    keyboard = keyboard,
                    singleLine = !multiLine,
                    words = multiLine,
                    modifier = Modifier.weight(1f),
                )
            }
            DottedRule()
        }
    }
}

/** "Mobile ⌄": the kind of a number, a press away from a short menu of the others. */
@Composable
private fun KindMenu(current: String, choices: List<Pair<Int, String>>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(enabled = choices.isNotEmpty()) { open = true }.padding(vertical = 2.dp),
        ) {
            TextMMD(text = current, style = MaterialTheme.typography.bodyLarge)
            if (choices.isNotEmpty()) {
                Icon(Icons.ChevronDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
            }
        }
        DropdownMenuMMD(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { (type, word) ->
                DropdownMenuItemMMD(text = { TextMMD(text = word, style = MaterialTheme.typography.bodyLarge) }, onClick = {
                    open = false
                    onPick(type)
                })
            }
        }
    }
}

/** A bold label over a box, as the phone's own editor shows a name. */
@Composable
private fun LabelledLine(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    singleLine: Boolean = true,
    words: Boolean = true,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        TextMMD(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        PlainField(value, onChange, keyboard = KeyboardType.Text, singleLine = singleLine, words = words, modifier = Modifier.fillMaxWidth())
    }
    DottedRule()
}

/** Text typed straight onto the page, no box round it: the dotted rule below is the line. */
@Composable
private fun PlainField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    keyboard: KeyboardType,
    singleLine: Boolean = true,
    words: Boolean = true,
    focusNow: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusNow) { if (focusNow) runCatching { focus.requestFocus() } }
    val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        minLines = 1,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            capitalization = if (words && keyboard == KeyboardType.Text) KeyboardCapitalization.Words else KeyboardCapitalization.None,
            autoCorrectEnabled = false,
        ),
        modifier = modifier.focusRequester(focus).padding(vertical = 4.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty() && hint != null) {
                    TextMMD(text = hint, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF8A8A8A))
                }
                inner()
            }
        },
    )
}

@Composable
private fun EventLine(
    box: EventBox,
    typeLabel: String,
    types: List<Pair<Int, String>>,
    onType: (Int) -> Unit,
    onChange: (EventBox) -> Unit,
    focusNow: Boolean,
) {
    val context = LocalContext.current
    // Day, month and year in the order the phone writes a date.
    val order = remember { android.text.format.DateFormat.getDateFormatOrder(context).toList() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 20.dp),
    ) {
        KindMenu(current = typeLabel, choices = types, onPick = onType)
        Spacer(Modifier.width(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            order.forEachIndexed { n, part ->
                val first = focusNow && n == 0
                when (part) {
                    'd' -> PlainField(box.day, { onChange(box.copy(day = it.filter(Char::isDigit).take(2))) }, Modifier.weight(1f), stringResource(R.string.date_day), KeyboardType.Number, focusNow = first)
                    'M' -> PlainField(box.month, { onChange(box.copy(month = it.filter(Char::isDigit).take(2))) }, Modifier.weight(1f), stringResource(R.string.date_month), KeyboardType.Number, focusNow = first)
                    'y' -> PlainField(box.year, { onChange(box.copy(year = it.filter(Char::isDigit).take(4))) }, Modifier.weight(1.3f), stringResource(R.string.date_year), KeyboardType.Number, focusNow = first)
                }
            }
        }
    }
    DottedRule()
}

/** A word and a switch, the whole line pressable. */
@Composable
private fun SwitchLine(text: String, on: Boolean, onPress: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPress)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        TextMMD(text = text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        SwitchMMD(checked = on, onCheckedChange = null)
    }
    DottedRule()
}

@Composable
private fun AddRow(text: String, onPress: () -> Unit) {
    TextMMD(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPress).padding(horizontal = 16.dp, vertical = 16.dp),
    )
    DottedRule()
}
