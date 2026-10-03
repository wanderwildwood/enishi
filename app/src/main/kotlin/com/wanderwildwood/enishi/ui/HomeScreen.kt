package com.wanderwildwood.enishi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.tabs.PrimaryTabRowMMD
import com.mudita.mmd.components.tabs.TabMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.enishi.R
import com.wanderwildwood.enishi.data.Person
import kotlinx.coroutines.launch

enum class Tab { ALL, FAVOURITES, GROUPS }

/**
 * Where the app opens: everyone, the favourites, and the groups, one press apart. A cog for
 * the few settings and an `i` for what the app is, both top right, as in every app here.
 */
@Composable
fun HomeScreen(
    model: BookModel,
    tab: Tab,
    onTab: (Tab) -> Unit,
    listState: LazyListState,
    onOpen: (Person) -> Unit,
    onNew: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onGroup: (Long) -> Unit,
    onNewGroup: () -> Unit,
    onImport: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column {
                TopAppBarMMD(
                    title = { TextMMD(text = stringResource(R.string.app_name)) },
                    actions = {
                        BarButton(Icons.Search, stringResource(R.string.cd_search), onSearch)
                        BarButton(Icons.Add, stringResource(R.string.cd_new_contact), onNew)
                        BarButton(Icons.Settings, stringResource(R.string.cd_settings), onSettings)
                        BarButton(Icons.Info, stringResource(R.string.cd_about), onAbout)
                    },
                )
                PrimaryTabRowMMD(selectedTabIndex = tab.ordinal) {
                    Tab.entries.forEach { t ->
                        TabMMD(
                            selected = tab == t,
                            onClick = { onTab(t) },
                            text = {
                                TextMMD(
                                    text = stringResource(
                                        when (t) {
                                            Tab.ALL -> R.string.tab_all
                                            Tab.FAVOURITES -> R.string.tab_favourites
                                            Tab.GROUPS -> R.string.tab_groups
                                        },
                                    ),
                                    // A third of the panel is just enough for "Favourites" on one line.
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val body = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface)
        when {
            !model.loaded -> Unit
            tab == Tab.GROUPS -> GroupsList(model, body, onGroup, onNewGroup)
            tab == Tab.FAVOURITES -> {
                val starred = model.people.filter { it.starred }
                if (starred.isEmpty()) {
                    Quiet(stringResource(R.string.favourites_empty), body)
                } else {
                    LazyColumnMMD(body) { items(starred, key = { it.id }) { PersonRow(it) { onOpen(it) } } }
                }
            }
            model.people.isEmpty() -> Column(body.padding(16.dp)) {
                TextMMD(text = stringResource(R.string.all_empty), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                FootButton(stringResource(R.string.all_empty_new), Modifier.fillMaxWidth(), onClick = onNew)
                Spacer(Modifier.height(10.dp))
                FootButton(stringResource(R.string.all_empty_import), Modifier.fillMaxWidth(), onClick = onImport)
            }
            else -> Lettered(model.people, listState, body, onOpen = onOpen)
        }
    }
}

/**
 * Everyone, under the letter the phone files them by. Pressing a letter opens every letter at
 * once, which on a list that moves a page at a time is the quick way to the far end of it.
 */
@Composable
internal fun Lettered(
    people: List<Person>,
    state: LazyListState = rememberLazyListState(),
    modifier: Modifier,
    above: (@Composable () -> Unit)? = null,
    note: (Person) -> String? = { null },
    onOpen: (Person) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var jumping by remember { mutableStateOf(false) }
    val sections = remember(people) { people.groupBy { it.section }.toList() }
    // Where each letter's heading sits in the list, counting whatever is above it.
    val aboveCount = if (above != null) 1 else 0
    val at = remember(sections, aboveCount) {
        var i = aboveCount
        sections.associate { (letter, group) -> (letter to i).also { i += 1 + group.size } }
    }

    LazyColumnMMD(modifier, state = state) {
        if (above != null) item(key = "above") { above() }
        sections.forEach { (letter, group) ->
            item(key = "h:$letter") { Heading(letter) { jumping = true } }
            items(group, key = { it.id }) { p -> PersonRow(p, note(p)) { onOpen(p) } }
        }
    }

    if (jumping) {
        LetterDialog(sections.map { it.first }, onDismiss = { jumping = false }) { letter ->
            jumping = false
            at[letter]?.let { scope.launch { state.scrollToItem(it) } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LetterDialog(letters: List<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    EInkDialog(onDismiss) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            letters.forEach { letter ->
                OutlinedButtonMMD(
                    onClick = { onPick(letter) },
                    modifier = Modifier.width(52.dp).height(48.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) { TextMMD(text = letter, style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}

@Composable
internal fun PersonRow(person: Person, note: String? = null, onPress: () -> Unit) {
    PlainRow(
        title = person.name.ifBlank { stringResource(R.string.no_name) },
        note = note,
        onPress = onPress,
    )
}

@Composable
private fun GroupsList(model: BookModel, modifier: Modifier, onGroup: (Long) -> Unit, onNewGroup: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // The account is named only when there is more than one, where it tells two "Family"s apart.
    val many = model.groups.map { it.account }.distinct().size > 1
    LazyColumnMMD(modifier) {
        items(model.groups, key = { it.id }) { g ->
            val size = pluralStringResource(R.plurals.group_size, g.size, g.size)
            PlainRow(
                title = g.title,
                note = if (many) "$size · ${Labels.account(context, g.account)}" else size,
                onPress = { onGroup(g.id) },
            )
        }
        item { PlainRow(stringResource(R.string.group_new), onPress = onNewGroup) }
    }
}

@Composable
internal fun Quiet(text: String, modifier: Modifier) {
    Column(modifier.padding(16.dp)) {
        TextMMD(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}
