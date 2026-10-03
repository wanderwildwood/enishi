package com.wanderwildwood.enishi.ui

import android.content.ContentUris
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.enishi.PickKind
import com.wanderwildwood.enishi.R
import com.wanderwildwood.enishi.data.Field
import com.wanderwildwood.enishi.data.Person
import kotlinx.coroutines.launch

/**
 * Another app asking for a person, a number, an email address or a postal address — Messaging
 * attaching a contact card, Email filling in a recipient. Only people who have the thing asked
 * for are listed; one who has several is asked which.
 *
 * What goes back is the standard address for it, which the asking app reads with its own
 * contacts permission, exactly as it would from any other contacts app.
 */
@Composable
fun PickScreen(
    model: BookModel,
    kind: PickKind,
    /** Shown above everyone, for "add this to someone": a new contact instead. */
    newRow: String? = null,
    onNew: () -> Unit = {},
    onSearch: () -> Unit,
    /** Searching instead of listing: the same pick, found by typing. */
    searching: Boolean = false,
    onCancel: () -> Unit,
    onPerson: ((Person) -> Unit)? = null,
    onPicked: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var choosing by remember { mutableStateOf<Pair<Person, List<Field>>?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    val people = remember(model.people, model.findable, kind) { model.people.filter { has(model, it, kind) } }

    fun picked(person: Person) {
        onPerson?.let { return it(person) }
        if (kind == PickKind.CONTACT) return onPicked(model.book.lookupUri(person.id, person.lookup))
        scope.launch {
            val card = model.io { it.card(person.id, model.lastNameFirst) }.getOrNull() ?: return@launch
            val rows = when (kind) {
                PickKind.PHONE -> card.draft.phones
                PickKind.EMAIL -> card.draft.emails
                else -> card.draft.addresses
            }
            when (rows.size) {
                0 -> notice = context.getString(R.string.pick_has_none, person.name)
                1 -> onPicked(rowUri(kind, rows[0].dataId!!))
                else -> choosing = person to rows
            }
        }
    }

    if (searching) {
        SearchScreen(model, stringResource(titleOf(kind, newRow != null)), onCancel, only = { has(model, it, kind) }, onOpen = ::picked)
    } else Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(titleOf(kind, newRow != null))) },
                navigationIcon = { BarButton(Icons.Close, stringResource(R.string.cd_close), onCancel) },
                actions = { BarButton(Icons.Search, stringResource(R.string.cd_search), onSearch) },
            )
        },
        bottomBar = { notice?.let { NoticeStrip(it) { notice = null } } },
    ) { padding ->
        val body = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface)
        if (!model.loaded) return@Scaffold
        if (people.isEmpty() && newRow == null) {
            Quiet(stringResource(R.string.pick_none), body)
            return@Scaffold
        }
        Lettered(
            people = people,
            modifier = body,
            above = newRow?.let { label -> { PlainRow(label, bold = true, onPress = onNew) } },
            onOpen = ::picked,
        )
    }

    choosing?.let { (person, rows) ->
        EInkDialog(onDismiss = { choosing = null }) {
            TextMMD(text = person.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            rows.forEach { f ->
                PlainRow(
                    title = f.value,
                    titleLines = 3,
                    note = when (kind) {
                        PickKind.PHONE -> Labels.phone(context, f.type, f.label)
                        PickKind.EMAIL -> Labels.email(context, f.type, f.label)
                        else -> Labels.address(context, f.type, f.label)
                    },
                    onPress = {
                        choosing = null
                        onPicked(rowUri(kind, f.dataId!!))
                    },
                )
            }
        }
    }
}

/** Whether this person has what was asked for. Addresses are not indexed, so all are listed. */
internal fun has(model: BookModel, p: Person, kind: PickKind): Boolean = when (kind) {
    PickKind.PHONE -> model.findable[p.id]?.numbers?.isNotEmpty() == true
    PickKind.EMAIL -> model.findable[p.id]?.emails?.isNotEmpty() == true
    else -> true
}

private fun titleOf(kind: PickKind, adding: Boolean): Int = when {
    adding -> R.string.pick_add_to
    kind == PickKind.PHONE -> R.string.pick_number
    kind == PickKind.EMAIL -> R.string.pick_email
    kind == PickKind.ADDRESS -> R.string.pick_address
    else -> R.string.pick_contact
}

/** content://com.android.contacts/data/phones/12 and its kin: one row, as the asker expects. */
internal fun rowUri(kind: PickKind, dataId: Long): Uri = ContentUris.withAppendedId(
    when (kind) {
        PickKind.PHONE -> Phone.CONTENT_URI
        PickKind.EMAIL -> Email.CONTENT_URI
        else -> StructuredPostal.CONTENT_URI
    },
    dataId,
)
