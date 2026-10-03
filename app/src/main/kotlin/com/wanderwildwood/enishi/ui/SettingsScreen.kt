package com.wanderwildwood.enishi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.enishi.R
import com.wanderwildwood.enishi.data.Account

/** How names are ordered and shown, where new people go, and a whole book in or out. */
@Composable
fun SettingsScreen(
    model: BookModel,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    notice: String?,
    onNoticeSeen: () -> Unit,
) {
    val context = LocalContext.current
    var saveTo by remember { mutableStateOf<Account?>(null) }
    LaunchedEffect(model.saveTo, model.accounts) { saveTo = model.newContactAccount() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(
                title = { BarTitle(stringResource(R.string.settings_title)) },
                navigationIcon = { BarButton(Icons.BackLight, stringResource(R.string.cd_back), onBack) },
            )
        },
        bottomBar = { notice?.let { NoticeStrip(it, onNoticeSeen) } },
    ) { padding ->
        LazyColumnMMD(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface)) {
            item {
                PlainRow(
                    title = stringResource(R.string.settings_sort),
                    note = stringResource(if (model.sortByLast) R.string.settings_last_name else R.string.settings_first_name),
                    onPress = { model.chooseSortByLast(!model.sortByLast) },
                )
            }
            item {
                PlainRow(
                    title = stringResource(R.string.settings_show),
                    note = stringResource(if (model.lastNameFirst) R.string.settings_last_first else R.string.settings_first_first),
                    onPress = { model.chooseLastNameFirst(!model.lastNameFirst) },
                )
            }
            if (model.accounts.size > 1) {
                item {
                    PlainRow(
                        title = stringResource(R.string.settings_save_to),
                        note = saveTo?.let { Labels.account(context, it) },
                        onPress = {
                            val all = model.accounts
                            val now = saveTo ?: all.first()
                            model.chooseSaveTo(all[(all.indexOf(now) + 1) % all.size])
                        },
                    )
                }
            }
            item { Heading(stringResource(R.string.settings_files)) }
            item { PlainRow(stringResource(R.string.settings_import), onPress = onImport) }
            item {
                PlainRow(
                    title = stringResource(R.string.settings_export),
                    note = pluralStringResource(R.plurals.settings_export_note, model.people.size, model.people.size),
                    onPress = onExport,
                )
            }
        }
    }
}
