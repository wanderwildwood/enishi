package com.wanderwildwood.enishi.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.enishi.R
import com.wanderwildwood.enishi.data.Draft
import com.wanderwildwood.enishi.data.Person
import com.wanderwildwood.enishi.data.VCard
import com.wanderwildwood.enishi.data.alreadyHere
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Reading {
    data object Busy : Reading
    /** The file could not be opened at all. */
    data object Unopenable : Reading
    /** It opened, and held no card. */
    data object Unreadable : Reading
    data class Read(val cards: List<Draft>) : Reading
}

/**
 * A .vcf: one contact card sent in a message, or a whole address book from another phone.
 *
 * One card is shown as the person it is, and added through the editor, so it can be looked
 * over first. Many are listed and added together; anyone already here is left alone rather
 * than added twice, and the reader is told how many that was.
 */
@Composable
fun CardsScreen(
    model: BookModel,
    uri: Uri,
    onBack: () -> Unit,
    onAddOne: (Draft) -> Unit,
    onOpen: (Person) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reading by remember(uri) { mutableStateOf<Reading>(Reading.Busy) }
    var progress by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }
    // What this screen added itself, which is not "already here" in the sense the reader meant.
    var addedHere by remember { mutableStateOf<Set<Int>>(emptySet()) }

    LaunchedEffect(uri) {
        reading = withContext(Dispatchers.IO) {
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes == null) Reading.Unopenable
            else runCatching { VCard.read(bytes) }.getOrDefault(emptyList()).let { if (it.isEmpty()) Reading.Unreadable else Reading.Read(it) }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.cards_title)) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
            )
        },
    ) { padding ->
        val body = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface)
        when (val r = reading) {
            Reading.Busy -> Unit
            Reading.Unopenable -> Quiet(stringResource(R.string.cards_unopenable), body)
            Reading.Unreadable -> Quiet(stringResource(R.string.cards_unreadable), body)
            is Reading.Read -> {
                val cards = r.cards
                // Waits for the book, so "already here" is never said of an empty one.
                if (!model.loaded) return@Scaffold
                val here = remember(cards, model.people, model.findable) {
                    cards.map { alreadyHere(it, model.people, model.findable) }
                }
                if (cards.size == 1) {
                    OneCard(cards[0], here[0], body, onAdd = { onAddOne(cards[0]) }, onOpen = onOpen)
                    return@Scaffold
                }
                val fresh = cards.filterIndexed { i, _ -> here[i] == null }
                LazyColumnMMD(body) {
                    item {
                        Column(Modifier.padding(16.dp)) {
                            TextMMD(
                                text = done ?: progress ?: pluralStringResource(R.plurals.cards_count, cards.size, cards.size) +
                                    if (fresh.size < cards.size) " " + pluralStringResource(R.plurals.cards_already, cards.size - fresh.size, cards.size - fresh.size) else "",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (done == null && progress == null && fresh.isNotEmpty()) {
                                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))
                                FootButton(pluralStringResource(R.plurals.cards_add_all, fresh.size, fresh.size), Modifier.fillMaxWidth()) {
                                    scope.launch {
                                        val account = model.newContactAccount()
                                        var added = 0
                                        for ((n, card) in fresh.withIndex()) {
                                            progress = context.getString(R.string.cards_adding, n + 1, fresh.size)
                                            if (model.io { it.create(card, account) }.getOrNull() != null) {
                                                added++
                                                addedHere = addedHere + cards.indexOf(card)
                                            }
                                        }
                                        progress = null
                                        done = context.resources.getQuantityString(R.plurals.cards_added, added, added) +
                                            if (added < fresh.size) " " + context.getString(R.string.cards_some_failed, fresh.size - added) else ""
                                    }
                                }
                            }
                        }
                        HorizontalDividerMMD()
                    }
                    itemsIndexed(cards) { i, card ->
                        PlainRow(
                            title = card.spokenName.ifBlank { card.phones.firstOrNull()?.value ?: card.emails.firstOrNull()?.value ?: stringResource(R.string.no_name) },
                            note = when {
                                i in addedHere -> stringResource(R.string.cards_added_now)
                                here[i] != null -> stringResource(R.string.cards_here)
                                else -> null
                            },
                            onPress = here[i]?.let { p -> { onOpen(p) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OneCard(card: Draft, here: Person?, modifier: Modifier, onAdd: () -> Unit, onOpen: (Person) -> Unit) {
    val context = LocalContext.current
    LazyColumnMMD(modifier) {
        item {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) {
                TextMMD(text = card.spokenName.ifBlank { stringResource(R.string.no_name) }, style = MaterialTheme.typography.headlineSmall)
                listOf(card.jobTitle, card.company).filter { it.isNotBlank() }.joinToString(", ").takeIf { it.isNotEmpty() }?.let {
                    TextMMD(text = it, style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDividerMMD()
        }
        card.phones.forEach { item { PlainRow(it.value, Labels.phone(context, it.type, it.label), onPress = null) } }
        card.emails.forEach { item { PlainRow(it.value, stringResource(R.string.kind_email, Labels.email(context, it.type, it.label)), onPress = null) } }
        card.addresses.forEach { item { PlainRow(it.value, stringResource(R.string.kind_address, Labels.address(context, it.type, it.label)), titleLines = 4, onPress = null) } }
        card.events.forEach { item { PlainRow(Labels.day(it.value), Labels.event(context, it.type, it.label), onPress = null) } }
        item {
            Column(Modifier.padding(16.dp)) {
                if (here != null) {
                    TextMMD(text = stringResource(R.string.cards_one_here, here.name), style = MaterialTheme.typography.bodySmall)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 10.dp))
                    FootButton(stringResource(R.string.cards_open), Modifier.fillMaxWidth()) { onOpen(here) }
                } else {
                    FootButton(stringResource(R.string.cards_add_one), Modifier.fillMaxWidth(), onClick = onAdd)
                }
            }
        }
    }
}
