package com.wanderwildwood.enishi.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.enishi.R
import com.wanderwildwood.enishi.data.Account
import com.wanderwildwood.enishi.data.Person
import kotlinx.coroutines.launch

/**
 * The people in a group, and the two things a group is for: writing to all of them at once by
 * text or by email. A long press on a person takes them out of it, asking first.
 */
@Composable
fun GroupScreen(
    model: BookModel,
    groupId: Long,
    onBack: () -> Unit,
    onOpen: (Person) -> Unit,
    onAddPeople: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val group = model.groups.firstOrNull { it.id == groupId }
    var members by remember(groupId) { mutableStateOf<Set<Long>>(emptySet()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf(false) }
    LaunchedEffect(groupId, model.people, model.groups) {
        members = model.io { it.members(groupId) }.getOrDefault(emptySet())
    }
    // A group just made is not in the list until the next read lands; one that was there and
    // is gone was deleted, here or elsewhere, so it is back to where it was listed.
    var seen by remember(groupId) { mutableStateOf(false) }
    LaunchedEffect(group != null) { if (group != null) seen = true }
    if (group == null) {
        LaunchedEffect(model.groups) { if (seen) onBack() }
        return
    }
    val people = model.people.filter { it.id in members }

    fun open(intent: Intent) {
        if (!start(context, intent)) notice = context.getString(R.string.nothing_opens)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(
                title = { BarTitle(group.title) },
                navigationIcon = { BarButton(Icons.BackLight, stringResource(R.string.cd_back), onBack) },
            )
        },
        bottomBar = { notice?.let { NoticeStrip(it) { notice = null } } },
    ) { padding ->
        LazyColumnMMD(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface)) {
            if (people.isNotEmpty()) {
                item {
                    PlainRow(stringResource(R.string.group_text_all), onPress = {
                        scope.launch {
                            val numbers = model.io { it.numbersFor(members) }.getOrDefault(emptyList())
                            if (numbers.isEmpty()) notice = context.getString(R.string.group_no_numbers)
                            // Semicolons, which is what Android's own messaging reads as a list.
                            else open(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + numbers.joinToString(";") { Uri.encode(it) })))
                        }
                    })
                }
                item {
                    PlainRow(stringResource(R.string.group_email_all), onPress = {
                        scope.launch {
                            val emails = model.io { it.emailsFor(members) }.getOrDefault(emptyList())
                            if (emails.isEmpty()) notice = context.getString(R.string.group_no_emails)
                            else open(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_EMAIL, emails.toTypedArray()))
                        }
                    })
                }
                item { Heading(pluralStringResource(R.plurals.group_size, people.size, people.size)) }
            }
            items(people, key = { it.id }) { p ->
                val (armed, press) = rememberArmed(p.id) {
                    scope.launch { model.io { it.removeFromGroup(groupId, p.id) } }
                }
                PlainRow(
                    title = if (armed) stringResource(R.string.group_remove_armed) else p.name.ifBlank { stringResource(R.string.no_name) },
                    bold = armed,
                    onPress = { if (armed) press() else onOpen(p) },
                    onLongPress = press,
                )
            }
            item {
                if (people.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDividerMMD()
                }
                PlainRow(stringResource(R.string.group_add_people), onPress = onAddPeople)
            }
            item { PlainRow(stringResource(R.string.group_rename), onPress = { renaming = true }) }
            item {
                val (armed, press) = rememberArmed(groupId) {
                    scope.launch {
                        model.io { it.deleteGroup(groupId) }
                        onBack()
                    }
                }
                PlainRow(stringResource(if (armed) R.string.group_delete_armed else R.string.group_delete), bold = armed, onPress = press)
            }
        }
    }

    if (renaming) {
        NameDialog(stringResource(R.string.group_rename), group.title, onDismiss = { renaming = false }) { title ->
            renaming = false
            scope.launch { model.io { it.renameGroup(groupId, title) } }
        }
    }
}

/** One box for a name, and the press that keeps it. Used for a new group and a renamed one. */
@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    EInkDialog(onDismiss) {
        TextMMD(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        BareField(name, { name = it }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        OutlinedButtonMMD(
            onClick = { if (name.isNotBlank()) onDone(name.trim()) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = stringResource(R.string.save), style = MaterialTheme.typography.bodySmall) }
    }
}

/** A new group, made in the account new contacts go to. */
@Composable
fun NewGroupDialog(model: BookModel, onDismiss: () -> Unit, onMade: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    NameDialog(stringResource(R.string.group_new), "", onDismiss) { title ->
        scope.launch {
            val account: Account = model.newContactAccount()
            val id = model.io { it.createGroup(title, account) }.getOrNull()
            onDismiss()
            if (id != null) {
                model.refresh()
                onMade(id)
            }
        }
    }
}

/**
 * Choosing people to put in a group: a box beside each, and one press to add them all. Only
 * people kept in the group's own account are listed, since nobody else can join it.
 */
@Composable
fun AddPeopleScreen(model: BookModel, groupId: Long, onDone: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val group = model.groups.firstOrNull { it.id == groupId }
    var members by remember { mutableStateOf<Set<Long>?>(null) }
    var chosen by remember { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(groupId) { members = model.io { it.members(groupId) }.getOrDefault(emptySet()) }
    val people = model.people.filter { members != null && it.id !in members!! }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(
                title = { BarTitle(group?.title.orEmpty()) },
                navigationIcon = { BarButton(Icons.CloseLight, stringResource(R.string.cd_close)) { onDone(null) } },
            )
        },
        bottomBar = {
            if (chosen.isNotEmpty() && group != null) {
                androidx.compose.foundation.layout.Column {
                    HorizontalDividerMMD()
                    FootButton(
                        pluralStringResource(R.plurals.group_add_n, chosen.size, chosen.size),
                        Modifier.fillMaxWidth().padding(10.dp),
                    ) {
                        scope.launch {
                            val refused = model.io { it.addToGroup(group, chosen) }.getOrDefault(chosen.size)
                            onDone(if (refused > 0) context.resources.getQuantityString(R.plurals.group_refused, refused, refused, Labels.account(context, group.account)) else null)
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumnMMD(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface)) {
            items(people, key = { it.id }) { p ->
                val on = p.id in chosen
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { chosen = if (on) chosen - p.id else chosen + p.id }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    com.mudita.mmd.components.checkbox.CheckboxMMD(checked = on, onCheckedChange = null)
                    Spacer(Modifier.width(14.dp))
                    TextMMD(
                        text = p.name.ifBlank { stringResource(R.string.no_name) },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (on) FontWeight.Bold else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HorizontalDividerMMD(thickness = 0.5.dp)
            }
        }
    }
}
