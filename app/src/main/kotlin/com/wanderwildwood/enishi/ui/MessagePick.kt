package com.wanderwildwood.enishi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.enishi.R
import com.wanderwildwood.enishi.data.Choice
import com.wanderwildwood.enishi.data.Dialable
import com.wanderwildwood.enishi.data.Person
import com.wanderwildwood.enishi.data.chooseNumber
import com.wanderwildwood.enishi.data.recipients
import com.wanderwildwood.enishi.data.typedNumber
import kotlinx.coroutines.launch

/**
 * Messaging's "New message", answered here when the phone's own Contacts app is not there to
 * answer it: a box to type a number, a "Create Group" row that ticks several people and writes
 * to all of them at once, and one tap on a person for a single conversation. It is the same
 * three things that app offers, and what goes back to Messaging is the same.
 */
@Composable
fun MessagePickScreen(
    model: BookModel,
    onCancel: () -> Unit,
    /** One person, and the number of theirs to use. */
    onOne: (Person, String) -> Unit,
    /** A number typed in, not in the book. */
    onTyped: (String) -> Unit,
    /** Everyone ticked, as numbers. False if no messaging app could be started. */
    onWrite: (List<String>) -> Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var grouping by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    /** Who is ticked, with the number of theirs to write to, in the order ticked. */
    var ticked by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
    var choosing by remember { mutableStateOf<Pair<Person, List<Dialable>>?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    val people = remember(model.people, model.findable) {
        model.people.filter { model.findable[it.id]?.numbers?.isNotEmpty() == true }
    }

    BackHandler(enabled = grouping) { grouping = false }

    fun use(p: Person, number: String) {
        if (grouping) {
            ticked = ticked + (p.id to number)
        } else {
            onOne(p, number)
        }
    }

    fun tapped(p: Person) {
        searching = false
        if (grouping && ticked.any { it.first == p.id }) {
            ticked = ticked.filter { it.first != p.id }
            return
        }
        scope.launch {
            val numbers = model.io { it.dialablesOf(p.id) }.getOrDefault(emptyList())
            when (val c = chooseNumber(numbers)) {
                Choice.None -> notice = context.getString(R.string.pick_has_none, p.name)
                is Choice.One -> use(p, c.number)
                is Choice.Ask -> choosing = p to c.numbers
            }
        }
    }

    if (searching) {
        SearchScreen(
            model, stringResource(R.string.msg_title), { searching = false },
            only = { model.findable[it.id]?.numbers?.isNotEmpty() == true },
            onOpen = ::tapped,
        )
    } else Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(
                title = {
                    BarTitle(
                        if (grouping) pluralStringResource(R.plurals.chosen_title, ticked.size, ticked.size)
                        else stringResource(R.string.msg_title),
                    )
                },
                navigationIcon = {
                    BarButton(Icons.Close, stringResource(R.string.cd_close)) { if (grouping) grouping = false else onCancel() }
                },
                actions = { BarButton(Icons.Search, stringResource(R.string.cd_search)) { searching = true } },
            )
        },
        bottomBar = {
            Column {
                notice?.let { NoticeStrip(it) { notice = null } }
                if (grouping) {
                    HorizontalDividerMMD()
                    // The count of who the text goes to: two people who share a number are one
                    // address, and the conversation has one participant for it.
                    val going = recipients(ticked.map { it.second }).size
                    FootButton(
                        pluralStringResource(R.plurals.msg_write, going, going),
                        Modifier.fillMaxWidth().padding(10.dp),
                        enabled = ticked.isNotEmpty(),
                    ) {
                        if (!onWrite(ticked.map { it.second })) notice = context.getString(R.string.msg_failed)
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().background(MaterialTheme.colorScheme.surface)) {
            if (!grouping) {
                LabelledField(
                    label = "",
                    value = typed,
                    onChange = { typed = it },
                    keyboard = KeyboardType.Phone,
                    words = false,
                    hint = stringResource(R.string.msg_type_number),
                )
                typedNumber(typed)?.let { n ->
                    PlainRow(stringResource(R.string.msg_to_number, n), bold = true, onPress = { onTyped(n) })
                }
                if (people.isNotEmpty()) PlainRow(stringResource(R.string.msg_group), bold = true, onPress = { grouping = true })
            }
            val list = Modifier.weight(1f).fillMaxWidth()
            when {
                !model.loaded -> Spacer(list)
                people.isEmpty() -> Quiet(stringResource(R.string.msg_nobody), list)
                else -> PeopleList(
                    people = people,
                    modifier = list,
                    chosen = if (grouping) ticked.map { it.first }.toSet() else null,
                    onOpen = ::tapped,
                )
            }
        }
    }

    choosing?.let { (person, numbers) ->
        EInkDialog(onDismiss = { choosing = null }) {
            TextMMD(text = person.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            numbers.forEach { n ->
                PlainRow(
                    title = n.value,
                    titleLines = 2,
                    note = if (n.mobile) stringResource(R.string.msg_mobile) else null,
                    onPress = {
                        choosing = null
                        use(person, n.value)
                    },
                )
            }
        }
    }
}
