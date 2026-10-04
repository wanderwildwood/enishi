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
import com.wanderwildwood.enishi.data.Backups

/** How names are ordered and shown, where new people go, a whole book in or out, and backups. */
@Composable
fun SettingsScreen(
    model: BookModel,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    notice: String?,
    onNoticeSeen: () -> Unit,
    backups: Backups,
    /** Bumped whenever a backup setting changes or one is written, so the rows read again. */
    backupsSeen: Int,
    onBackupEvery: (Int) -> Unit,
    onBackupFolder: () -> Unit,
    onBackupNow: () -> Unit,
) {
    val context = LocalContext.current
    var saveTo by remember { mutableStateOf<Account?>(null) }
    LaunchedEffect(model.saveTo, model.accounts) { saveTo = model.newContactAccount() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(
                title = { BarTitle(stringResource(R.string.settings_title)) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
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
            item { Heading(stringResource(R.string.settings_backups)) }
            item {
                val every = remember(backupsSeen) { backups.every }
                PlainRow(
                    title = stringResource(R.string.backup_every),
                    note = stringResource(
                        when (every) {
                            0 -> R.string.backup_off
                            1 -> R.string.backup_daily
                            else -> R.string.backup_weekly
                        },
                    ),
                    // Off, every day, every week, and round again — one press each, as "Sort by" goes.
                    onPress = { onBackupEvery(if (every == 0) 1 else if (every == 1) 7 else 0) },
                )
            }
            item {
                val name = remember(backupsSeen) { backups.folderName() }
                PlainRow(
                    title = stringResource(R.string.backup_folder),
                    note = name ?: stringResource(R.string.backup_folder_none),
                    onPress = onBackupFolder,
                )
            }
            item {
                val note = remember(backupsSeen) {
                    backups.problem?.let { context.getString(R.string.backup_problem, it) }
                        ?: backups.last.takeIf { it > 0 }?.let {
                            val day = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(it))
                            context.resources.getQuantityString(R.plurals.backup_note, Backups.KEEP, day, Backups.KEEP)
                        }
                        ?: context.getString(R.string.backup_never)
                }
                PlainRow(title = stringResource(R.string.backup_now), note = note, onPress = onBackupNow)
            }
        }
    }
}
