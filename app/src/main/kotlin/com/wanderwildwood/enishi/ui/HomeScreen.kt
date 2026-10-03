package com.wanderwildwood.enishi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.FloatingActionButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.enishi.R
import com.wanderwildwood.enishi.data.Person

/**
 * Where the app opens, drawn as the phone's own contacts app is: a bold title, everyone in one
 * plain list with the surname in bold, and a round + for someone new. Favourites and groups are
 * two rows above everyone rather than tabs, so the list itself looks like the one the phone
 * already had. A cog and an `i`, top right, as in every app here.
 */
@Composable
fun HomeScreen(
    model: BookModel,
    listState: LazyListState,
    onOpen: (Person) -> Unit,
    onNew: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onFavourites: () -> Unit,
    onGroups: () -> Unit,
    onImport: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(
                title = { BarTitle(stringResource(R.string.app_name)) },
                actions = {
                    BarButton(Icons.SearchLight, stringResource(R.string.cd_search), onSearch)
                    BarButton(Icons.SettingsLight, stringResource(R.string.cd_settings), onSettings)
                    BarButton(Icons.InfoLight, stringResource(R.string.cd_about), onAbout)
                },
            )
        },
        floatingActionButton = { NewButton(onNew) },
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
            else -> {
                val starred = model.people.count { it.starred }
                val groups = model.groups.size
                PeopleList(
                    people = model.people,
                    state = listState,
                    modifier = body,
                    above = {
                        if (starred > 0) {
                            NameRow(
                                AnnotatedString(stringResource(R.string.tab_favourites)),
                                note = pluralStringResource(R.plurals.group_size, starred, starred),
                                onPress = onFavourites,
                            )
                        }
                        NameRow(
                            AnnotatedString(stringResource(R.string.tab_groups)),
                            note = if (groups > 0) pluralStringResource(R.plurals.groups_count, groups, groups) else null,
                            onPress = onGroups,
                        )
                    },
                    onOpen = onOpen,
                )
            }
        }
    }
}

/** The round + in the corner, as on the phone's own contacts list. */
@Composable
internal fun NewButton(onNew: () -> Unit) {
    // A filled circle, as theirs is; black, since the panel has no colour to fill it with.
    FloatingActionButtonMMD(
        onClick = onNew,
        shape = androidx.compose.foundation.shape.CircleShape,
        containerColor = MaterialTheme.colorScheme.onSurface,
        contentColor = MaterialTheme.colorScheme.surface,
    ) {
        Icon(Icons.Add, contentDescription = stringResource(R.string.cd_new_contact), modifier = Modifier.size(30.dp))
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
    onOpen: (Person) -> Unit,
) {
    LazyColumnMMD(modifier, state = state) {
        if (above != null) item(key = "above") { Column { above() } }
        items(people, key = { it.id }) { p -> PersonRow(p, note(p)) { onOpen(p) } }
        // Room under the last row, so the round + never sits on top of a name.
        item(key = "foot") { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
internal fun PersonRow(person: Person, note: String? = null, onPress: () -> Unit) {
    val name = person.name.ifBlank { stringResource(R.string.no_name) }
    NameRow(boldSurname(name, person.family), note = note, onPress = onPress)
}

/** The people someone starred. */
@Composable
fun FavouritesScreen(model: BookModel, onBack: () -> Unit, onOpen: (Person) -> Unit) {
    val starred = model.people.filter { it.starred }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(
                title = { BarTitle(stringResource(R.string.tab_favourites)) },
                navigationIcon = { BarButton(Icons.BackLight, stringResource(R.string.cd_back), onBack) },
            )
        },
    ) { padding ->
        val body = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface)
        if (starred.isEmpty()) Quiet(stringResource(R.string.favourites_empty), body)
        else PeopleList(starred, modifier = body, onOpen = onOpen)
    }
}

/** Every group, and a row to make another. */
@Composable
fun GroupsScreen(model: BookModel, onBack: () -> Unit, onGroup: (Long) -> Unit, onNewGroup: () -> Unit) {
    val context = LocalContext.current
    // The account is named only when there is more than one, where it tells two "Family"s apart.
    val many = model.groups.map { it.account }.distinct().size > 1
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(
                title = { BarTitle(stringResource(R.string.tab_groups)) },
                navigationIcon = { BarButton(Icons.BackLight, stringResource(R.string.cd_back), onBack) },
            )
        },
    ) { padding ->
        LazyColumnMMD(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface)) {
            items(model.groups, key = { it.id }) { g ->
                val size = pluralStringResource(R.plurals.group_size, g.size, g.size)
                NameRow(
                    AnnotatedString(g.title),
                    note = if (many) "$size · ${Labels.account(context, g.account)}" else size,
                    onPress = { onGroup(g.id) },
                )
            }
            item { NameRow(AnnotatedString(stringResource(R.string.group_new)), onPress = onNewGroup) }
        }
    }
}

@Composable
internal fun Quiet(text: String, modifier: Modifier) {
    Box(modifier.padding(16.dp), contentAlignment = Alignment.TopStart) {
        TextMMD(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}
