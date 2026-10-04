package com.wanderwildwood.enishi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.radio_button.RadioButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.enishi.R
import com.wanderwildwood.enishi.data.Card
import kotlinx.coroutines.launch

/**
 * Several people made into one, by hand. The reader chooses whose name stays; everything the
 * others have that this one lacks is added to them, and the others go. Each person is listed
 * with what they hold, so the fuller one is easy to see.
 */
@Composable
fun MergeScreen(model: BookModel, ids: List<Long>, onBack: () -> Unit, onMerged: (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cards by remember(ids) { mutableStateOf<List<Card>>(emptyList()) }
    var keep by remember(ids) { mutableStateOf<Long?>(null) }
    var working by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ids) {
        cards = model.io { b -> ids.mapNotNull { b.card(it, model.lastNameFirst) } }.getOrDefault(emptyList())
        // The one with most in it, unless only a messenger keeps them: they cannot be written to.
        keep = cards.filter { !it.readOnly }.maxByOrNull { held(it) }?.id
    }
    val kept = cards.firstOrNull { it.id == keep }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(
                title = { BarTitle(stringResource(R.string.merge_title)) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
            )
        },
        bottomBar = {
            Column {
                notice?.let { NoticeStrip(it) { notice = null } }
                if (kept != null) {
                    HorizontalDividerMMD()
                    FootButton(
                        stringResource(R.string.merge_into, kept.name.ifBlank { stringResource(R.string.no_name) }),
                        Modifier.fillMaxWidth().padding(10.dp),
                        enabled = !working && cards.size >= 2,
                    ) {
                        working = true
                        scope.launch {
                            val merged = model.io { it.merge(kept.id, cards.map { c -> c.id }) }
                            working = false
                            merged.onSuccess(onMerged).onFailure { notice = context.getString(R.string.merge_failed) }
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumnMMD(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface)) {
            item {
                TextMMD(
                    text = stringResource(R.string.merge_body),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(16.dp),
                )
                HorizontalDividerMMD()
            }
            items(cards, key = { it.id }) { c ->
                val on = c.id == keep
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !c.readOnly) { keep = c.id }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    RadioButtonMMD(selected = on, onClick = null, enabled = !c.readOnly)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        TextMMD(
                            text = c.name.ifBlank { stringResource(R.string.no_name) },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextMMD(
                            text = if (c.readOnly) stringResource(R.string.merge_messenger) else describe(c),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                DottedRule()
            }
        }
    }
}

/** How much a person holds, to put the fullest first in line to be kept. */
private fun held(c: Card): Int = c.draft.run {
    phones.size + emails.size + addresses.size + events.size + websites.size +
        listOf(company, note, nickname).count { it.isNotBlank() }
}

/** "2 numbers · 1 email · DAVx5": what they have, and where they are kept. */
@Composable
private fun describe(c: Card): String {
    val context = LocalContext.current
    val d = c.draft
    val parts = mutableListOf<String>()
    if (d.phones.isNotEmpty()) parts += pluralStringResource(R.plurals.merge_numbers, d.phones.size, d.phones.size)
    if (d.emails.isNotEmpty()) parts += pluralStringResource(R.plurals.merge_emails, d.emails.size, d.emails.size)
    if (d.addresses.isNotEmpty()) parts += pluralStringResource(R.plurals.merge_addresses, d.addresses.size, d.addresses.size)
    if (d.company.isNotBlank()) parts += d.company
    c.parts.map { Labels.account(context, it.account) }.distinct().forEach { parts += it }
    return parts.joinToString(" · ")
}
