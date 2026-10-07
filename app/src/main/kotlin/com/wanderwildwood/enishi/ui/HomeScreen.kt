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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.FloatingActionButtonMMD
import com.mudita.mmd.components.checkbox.CheckboxMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.enishi.R
import com.wanderwildwood.enishi.data.Person

/**
 * Where the app opens, drawn as the phone's own contacts app is: a bold title, everyone in one
 * plain list with the surname in bold, and a round + for someone new. Nothing above everyone:
 * favourites are the Phone app's to list, as on the phone's own apps. A cog and an `i`, top
 * right, as in every app here.
 *
 * A long press on anyone starts choosing several: a box beside every row, the count in the bar,
 * and Merge, Share and Delete along the foot. Back, or the cross, stops.
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
    onImport: () -> Unit,
    chosen: Set<Long>,
    onChoose: (Person) -> Unit,
    onChosenDone: () -> Unit,
    onMerge: () -> Unit,
    onShareChosen: () -> Unit,
    onDeleteChosen: () -> Unit,
    notice: String?,
    onNoticeSeen: () -> Unit,
) {
    val choosing = chosen.isNotEmpty()
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
                        BarButton(Icons.Settings, stringResource(R.string.cd_settings), onSettings)
                        BarButton(Icons.Info, stringResource(R.string.cd_about), onAbout)
                    },
                )
            }
        },
        bottomBar = {
            Column {
                notice?.let { NoticeStrip(it, onNoticeSeen) }
                if (choosing) ChosenBar(chosen.size, onMerge, onShareChosen, onDeleteChosen)
            }
        },
        floatingActionButton = { if (!choosing) NewButton(onNew) },
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
                PeopleList(
                    people = model.people,
                    state = listState,
                    modifier = body,
                    chosen = if (choosing) chosen else null,
                    onLongPress = onChoose,
                    onOpen = { if (choosing) onChoose(it) else onOpen(it) },
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
        Icon(Icons.Add, contentDescription = stringResource(R.string.cd_new_contact), modifier = Modifier.size(34.dp))
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
    onOpen: (Person) -> Unit,
) {
    LazyColumnMMD(modifier, state = state) {
        if (above != null) item(key = "above") { Column { above() } }
        items(people, key = { it.id }) { p ->
            PersonRow(p, note(p), chosen = chosen?.let { p.id in it }, onLongPress = onLongPress?.let { { it(p) } }) { onOpen(p) }
        }
        // Room under the last row, so the round + never sits on top of a name.
        item(key = "foot") { Spacer(Modifier.height(80.dp)) }
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
        boldSurname(name, person.family),
        note = note,
        leading = chosen?.let { on -> { CheckboxMMD(checked = on, onCheckedChange = null) } },
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
