package com.wanderwildwood.enishi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.enishi.R
import com.wanderwildwood.enishi.data.Person
import com.wanderwildwood.enishi.data.search

/**
 * Finds a person by any part of a name, any run of digits from a number, or part of an
 * address. Results come as the reader types; there is nothing to press.
 */
@Composable
fun SearchScreen(
    model: BookModel,
    title: String,
    onBack: () -> Unit,
    only: (Person) -> Boolean = { true },
    onOpen: (Person) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val people = remember(model.people) { model.people.filter(only) }
    val found = remember(query, people, model.findable) { search(query, people, model.findable) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column {
                TopAppBarMMD(
                    title = { TextMMD(text = title) },
                    navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
                )
                TextFieldMMD(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { TextMMD(text = stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyMedium) },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).focusRequester(focus),
                )
                HorizontalDividerMMD()
            }
        },
    ) { padding ->
        val body = Modifier.fillMaxSize().padding(padding).imePadding().background(MaterialTheme.colorScheme.surface)
        when {
            query.isBlank() -> Unit
            found.isEmpty() -> Quiet(stringResource(R.string.search_none), body)
            else -> LazyColumnMMD(body) {
                items(found, key = { it.person.id }) { f -> PersonRow(f.person, f.by) { onOpen(f.person) } }
            }
        }
    }
}
