package com.wanderwildwood.enishi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.checkbox.CheckboxMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.enishi.R
import com.wanderwildwood.enishi.data.ContactsSync
import com.wanderwildwood.enishi.data.Group
import com.wanderwildwood.enishi.data.Person
import kotlinx.coroutines.delay

/** The list's tabs, in the order they stand. Groups is there only while there are groups. */
const val TAB_ALL = 0
const val TAB_FAVOURITES = 1
const val TAB_GROUPS = 2

/**
 * Where the app opens, in the house's look, as Phone and Messaging have it: the plain title, and
 * along the top right search, a + for someone new, the cog for settings and the `i` for About.
 * The + is up there rather than floating at the foot of the list, so no page of the list gives up
 * a row to keep clear of it. Under the bar, tabs: All, Favourites — the people starred here, who
 * are the Phone app's favourites too — and Groups, while the address book has any.
 *
 * A long press on anyone starts choosing several: a box beside every row, the count in the bar,
 * and Merge, Share and Delete along the foot. Back, or the cross, stops.
 */
@Composable
fun HomeScreen(
    model: BookModel,
    tab: Int,
    onTab: (Int) -> Unit,
    /** One place in the list for each tab, so going back to a tab finds it where it was left. */
    listStates: List<LazyListState>,
    onOpen: (Person) -> Unit,
    onGroup: (Group) -> Unit,
    onNew: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onImport: () -> Unit,
    chosen: Set<Long>,
    onChoose: (Person) -> Unit,
    onChosenDone: () -> Unit,
    onMerge: () -> Unit,
    onShareChosen: () -> Unit,
    onDeleteChosen: () -> Unit,
    notice: String?,
    onNoticeSeen: () -> Unit,
    removed: List<com.wanderwildwood.enishi.data.Removed> = emptyList(),
    onUndo: () -> Unit = {},
) {
    val choosing = chosen.isNotEmpty()
    // A tab chosen while there were groups, and there are none now, falls back to All.
    val hasGroups = model.groups.isNotEmpty()
    val shown = if (tab == TAB_GROUPS && !hasGroups) TAB_ALL else tab
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            if (choosing) {
                Bar(
                    title = { BarTitle(pluralStringResource(R.plurals.chosen_title, chosen.size, chosen.size)) },
                    navigationIcon = { BarButton(Icons.Close, stringResource(R.string.cd_close), onChosenDone) },
                )
            } else {
                Bar(
                    title = { BarTitle(stringResource(R.string.app_name)) },
                    actions = {
                        BarButton(Icons.Search, stringResource(R.string.cd_search), onSearch)
                        BarButton(Icons.Add, stringResource(R.string.cd_new_contact), onNew)
                        BarButton(Icons.Settings, stringResource(R.string.cd_settings), 22.dp, onSettings)
                        BarButton(Icons.Info, stringResource(R.string.cd_about), 22.dp, onAbout)
                    },
                )
            }
        },
        bottomBar = {
            Column {
                notice?.let { NoticeStrip(it, onNoticeSeen) }
                if (removed.isNotEmpty() && !choosing) UndoStrip(removed, onUndo)
                if (choosing) ChosenBar(chosen.size, onMerge, onShareChosen, onDeleteChosen)
            }
        },
    ) { padding ->
        val body = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface)
        when {
            !model.loaded -> Unit
            model.people.isEmpty() -> Column(body.padding(16.dp)) {
                TextMMD(text = stringResource(R.string.all_empty), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                FootButton(stringResource(R.string.all_empty_new), Modifier.fillMaxWidth(), onClick = onNew)
                Spacer(Modifier.height(10.dp))
                FootButton(stringResource(R.string.all_empty_import), Modifier.fillMaxWidth(), onClick = onImport)
            }
            else -> Column(body) {
                // A swipe down on the first page of any tab asks DAVx5 and its kind to sync; the
                // line under the tabs says so in words for a few seconds, and the rows change
                // when the contacts store does.
                var asked by remember { mutableStateOf(0) }
                LaunchedEffect(asked) { if (asked > 0) { delay(3000); asked = 0 } }
                val pull: () -> Unit = {
                    ContactsSync.requestNow()
                    asked++
                }
                val labels = listOfNotNull(
                    stringResource(R.string.tab_all),
                    stringResource(R.string.tab_favourites),
                    if (hasGroups) stringResource(R.string.tab_groups) else null,
                )
                StoneTabs(labels = labels, selected = shown, onSelect = onTab)
                if (asked > 0) {
                    TextMMD(
                        text = stringResource(R.string.sync_asked),
                        style = helperStyle(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                val rest = Modifier.weight(1f).fillMaxWidth()
                when (shown) {
                    TAB_GROUPS -> GroupList(model.groups, listStates[TAB_GROUPS], rest, onGroup, onPullAtTop = pull)
                    else -> {
                        val people = if (shown == TAB_FAVOURITES) model.people.filter { it.starred } else model.people
                        if (people.isEmpty()) {
                            Quiet(stringResource(R.string.favourites_none), rest)
                        } else {
                            PeopleList(
                                people = people,
                                state = listStates[shown],
                                modifier = rest,
                                chosen = if (choosing) chosen else null,
                                onLongPress = onChoose,
                                onOpen = { if (choosing) onChoose(it) else onOpen(it) },
                                onPullAtTop = pull,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The groups, one row each with how many are in it. Two groups of the same name kept in two
 * accounts are two rows, each saying where it is kept, since they are two lists.
 */
@Composable
private fun GroupList(
    groups: List<Group>,
    state: LazyListState,
    modifier: Modifier,
    onGroup: (Group) -> Unit,
    onPullAtTop: (() -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val twice = remember(groups) { groups.groupingBy { it.title }.eachCount().filterValues { it > 1 }.keys }
    PagedColumn(modifier, state = state, onPullAtTop = onPullAtTop) {
        items(groups, key = { it.id }) { g ->
            val count = pluralStringResource(R.plurals.group_size, g.size, g.size)
            NameRow(
                AnnotatedString(g.title),
                note = if (g.title in twice) "$count · ${Labels.account(context, g.account)}" else count,
            ) { onGroup(g) }
        }
    }
}

/** Everyone, one plain row each, with anything worth putting above them first. */
@Composable
internal fun PeopleList(
    people: List<Person>,
    state: LazyListState = rememberLazyListState(),
    modifier: Modifier,
    above: (@Composable () -> Unit)? = null,
    note: (Person) -> String? = { null },
    /** While several are being chosen: who is, and a box beside every row. */
    chosen: Set<Long>? = null,
    onLongPress: ((Person) -> Unit)? = null,
    /** A swipe down on the first page: asks for the contacts again, where the list wants that. */
    onPullAtTop: (() -> Unit)? = null,
    onOpen: (Person) -> Unit,
) {
    PagedColumn(modifier, state = state, onPullAtTop = onPullAtTop) {
        if (above != null) item(key = "above") { Column { above() } }
        items(people, key = { it.id }) { p ->
            PersonRow(p, note(p), chosen = chosen?.let { p.id in it }, onLongPress = onLongPress?.let { { it(p) } }) { onOpen(p) }
        }
    }
}

@Composable
internal fun PersonRow(
    person: Person,
    note: String? = null,
    chosen: Boolean? = null,
    onLongPress: (() -> Unit)? = null,
    onPress: () -> Unit,
) {
    val name = person.name.ifBlank { stringResource(R.string.no_name) }
    NameRow(
        // Regular weight, as every name in a list here is: bold is kept for what needs attention.
        AnnotatedString(name),
        note = note,
        leading = chosen?.let { on -> { CheckboxMMD(checked = on, onCheckedChange = { onPress() }) } },
        onLongPress = onLongPress,
        onPress = onPress,
    )
}

/**
 * What can be done to everyone chosen, along the foot: merge them into one (two or more), share
 * them as one card file, or delete them — that last asks first, as every delete here does.
 */
@Composable
private fun ChosenBar(count: Int, onMerge: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val (armed, press) = rememberArmed(count, onDelete)
    Column {
        HorizontalDividerMMD()
        if (armed) {
            FootButton(
                pluralStringResource(R.plurals.chosen_delete_armed, count, count),
                Modifier.fillMaxWidth().padding(10.dp),
                onClick = press,
            )
        } else {
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FootButton(stringResource(R.string.chosen_merge), Modifier.weight(1f), enabled = count >= 2, onClick = onMerge)
                FootButton(stringResource(R.string.chosen_share), Modifier.weight(1f), onClick = onShare)
                FootButton(stringResource(R.string.chosen_delete), Modifier.weight(1f), onClick = press)
            }
        }
    }
}

@Composable
internal fun Quiet(text: String, modifier: Modifier) {
    Box(modifier.padding(16.dp), contentAlignment = Alignment.TopStart) {
        TextMMD(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Everyone in one group, from the Groups tab: the group's name in the bar and its people as the
 * list draws them. Read again whenever the contacts change, so a person put in or taken out
 * elsewhere shows here.
 */
@Composable
fun GroupScreen(model: BookModel, groupId: Long, onBack: () -> Unit, onOpen: (Person) -> Unit) {
    val group = model.groups.firstOrNull { it.id == groupId }
    var members by remember(groupId) { mutableStateOf<Set<Long>?>(null) }
    LaunchedEffect(groupId, model.people) { members = model.io { it.members(groupId) }.getOrNull() ?: emptySet() }
    val state = rememberLazyListState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(
                title = { BarTitle(group?.title ?: "") },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
            )
        },
    ) { padding ->
        val body = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface)
        val ids = members ?: return@Scaffold
        val people = model.people.filter { it.id in ids }
        if (people.isEmpty()) Quiet(stringResource(R.string.group_empty), body)
        else PeopleList(people = people, state = state, modifier = body, onOpen = onOpen)
    }
}
