package com.wanderwildwood.enishi.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.enishi.R
import com.wanderwildwood.enishi.data.Call
import com.wanderwildwood.enishi.data.callsWith
import com.wanderwildwood.enishi.data.howLong
import com.wanderwildwood.enishi.data.numberKey
import com.wanderwildwood.enishi.data.whenSaid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The calls with one person, newest first, as the phone's own contacts app shows them behind
 * its Call history button: their name over the list, an arrow for the way each call went, when
 * it was in bold, and how long it lasted under that. A press calls them back.
 *
 * The call log is asked for the first time this page would show it, from a row that says so.
 */
@Composable
fun CallsScreen(model: BookModel, contactId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val (card, missing) = rememberCard(model, contactId)
    var notice by remember { mutableStateOf<String?>(null) }
    val dial = rememberDialer { notice = context.getString(R.string.nothing_opens) }

    fun allowed() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(allowed()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    val numbers = card?.let { distinctNumbers(it.draft.phones).map { p -> p.value } } ?: emptyList()
    var calls by remember(numbers) { mutableStateOf<List<Call>?>(null) }
    LaunchedEffect(numbers, granted) {
        if (granted && numbers.isNotEmpty()) calls = withContext(Dispatchers.IO) {
            runCatching { callsWith(context.contentResolver, numbers) }.getOrDefault(emptyList())
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(
                title = { BarTitle(card?.name?.ifBlank { stringResource(R.string.no_name) } ?: "") },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
            )
        },
        bottomBar = { notice?.let { NoticeStrip(it) { notice = null } } },
    ) { padding ->
        val body = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface)
        if (card == null) {
            if (missing) Quiet(stringResource(R.string.contact_gone), body)
            return@Scaffold
        }
        if (!granted) {
            Column(body) {
                NameRow(AnnotatedString(stringResource(R.string.calls_show)), note = stringResource(R.string.calls_show_note)) {
                    ask.launch(Manifest.permission.READ_CALL_LOG)
                }
            }
            return@Scaffold
        }
        val list = calls ?: return@Scaffold
        if (list.isEmpty()) {
            Quiet(stringResource(R.string.calls_none), body)
            return@Scaffold
        }
        val now = System.currentTimeMillis()
        val hours24 = android.text.format.DateFormat.is24HourFormat(context)
        val several = numbers.map(::numberKey).distinct().size > 1
        val today = stringResource(R.string.calls_today)
        val yesterday = stringResource(R.string.calls_yesterday)
        LazyColumnMMD(body) {
            items(list.size) { i ->
                val call = list[i]
                val kind = stringResource(
                    when (call.kind) {
                        Call.Kind.IN -> R.string.calls_in
                        Call.Kind.OUT -> R.string.calls_out
                        Call.Kind.MISSED -> R.string.calls_missed
                        Call.Kind.DECLINED -> R.string.calls_declined
                    },
                )
                // "2 min 0 sec • Outgoing", and which number only when they have more than one.
                val note = listOfNotNull(howLong(call.seconds), kind, call.number.takeIf { several }).joinToString(" • ")
                CallRow(
                    icon = when (call.kind) {
                        Call.Kind.IN -> Icons.CallIn
                        Call.Kind.OUT -> Icons.CallOut
                        else -> Icons.CallMissed
                    },
                    title = whenSaid(call.at, now, today, yesterday, hours24),
                    note = note,
                ) { dial(call.number) }
            }
        }
    }
}

/**
 * One call, as the phone's own history lists it: the arrow at the left, the words 61dp in,
 * the dotted rule from the words to the edge of the page.
 */
@Composable
private fun CallRow(icon: ImageVector, title: String, note: String, onPress: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPress)
            .padding(start = 17.5.dp, end = 16.dp, top = 12.dp, bottom = 16.5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(15.5.dp))
        Column(Modifier.weight(1f)) {
            TextMMD(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = rowText, lineHeight = 26.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            TextMMD(
                text = note,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    DottedRule(start = 61.dp, end = 0.dp)
}
