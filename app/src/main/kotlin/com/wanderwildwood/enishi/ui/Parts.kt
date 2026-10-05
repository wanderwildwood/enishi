package com.wanderwildwood.enishi.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import kotlinx.coroutines.delay

@Composable
internal fun BarButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** A word in the top bar that does something — Save, Edit — bold when it is ready to. */
@Composable
internal fun BarWord(text: String, ready: Boolean = true, onClick: () -> Unit) {
    TextMMD(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (ready) FontWeight.Bold else null,
        modifier = Modifier.clickable(enabled = ready, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

/**
 * A row that asks before it acts: the first press arms it and changes what it says, a second
 * press does it, and it disarms itself after four seconds so nothing is left live for whoever
 * picks the phone up next. Returns whether it is armed, and the press to hand the row.
 */
@Composable
internal fun rememberArmed(key: Any?, onConfirmed: () -> Unit): Pair<Boolean, () -> Unit> {
    var armed by remember(key) { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(4000)
            armed = false
        }
    }
    return armed to {
        if (armed) {
            armed = false
            onConfirmed()
        } else {
            armed = true
        }
    }
}

@Composable
internal fun Heading(text: String, onClick: (() -> Unit)? = null) {
    TextMMD(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
    )
    HorizontalDividerMMD()
}

/** A row: what it is, and a line under it only when that line says something. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlainRow(
    title: String,
    note: String? = null,
    bold: Boolean = false,
    titleLines: Int = 1,
    onLongPress: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onPress: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onPress != null || onLongPress != null) it.combinedClickable(onClick = onPress ?: {}, onLongClick = onLongPress) else it }
            .padding(start = 16.dp, end = if (trailing != null) 8.dp else 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            TextMMD(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (bold) FontWeight.Bold else null,
                maxLines = titleLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (!note.isNullOrEmpty()) {
                TextMMD(text = note, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
    DottedRule()
}

@Composable
internal fun FootButton(label: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButtonMMD(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        contentPadding = PaddingValues(horizontal = 6.dp),
    ) {
        TextMMD(text = label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A short thing to say, along the foot, gone after a few seconds. */
@Composable
internal fun NoticeStrip(text: String, onSeen: () -> Unit) {
    LaunchedEffect(text) {
        delay(5000)
        onSeen()
    }
    HorizontalDividerMMD()
    TextMMD(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSeen).padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/** A label over a text box, the way MMD draws a field: white, one rule beneath. */
@Composable
internal fun LabelledField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    words: Boolean = true,
    singleLine: Boolean = true,
    hint: String? = null,
) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        if (label.isNotEmpty()) TextMMD(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        BareField(value, onChange, Modifier.fillMaxWidth(), keyboard, words, singleLine, hint)
    }
}

@Composable
internal fun BareField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    words: Boolean = true,
    singleLine: Boolean = true,
    hint: String? = null,
    /** A box just added by the reader takes the cursor, so the next thing typed lands in it. */
    focusNow: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusNow) { if (focusNow) runCatching { focus.requestFocus() } }
    TextFieldMMD(
        value = value,
        onValueChange = onChange,
        modifier = modifier.focusRequester(focus),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        placeholder = hint?.let { { TextMMD(text = it, style = MaterialTheme.typography.labelSmall) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            capitalization = if (words && keyboard == KeyboardType.Text) KeyboardCapitalization.Words else KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
    )
}

// ---------------------------------------------------------------- the phone's own look
//
// Contacts is drawn to sit beside the Kompakt's own contacts app: the same bold title, the
// same plain list with the surname in bold, the same dotted rule between rows. Measured off
// that app on the phone, not guessed: a rule of 3px dashes and 2px gaps, inset to the text,
// rows 85px apart, names at 20sp.

/** The dotted rule between rows, inset to where the text starts and ends. */
@Composable
internal fun DottedRule(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    androidx.compose.foundation.Canvas(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp),
    ) {
        drawLine(
            color = ink,
            start = androidx.compose.ui.geometry.Offset(0f, size.height / 2),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2),
            strokeWidth = 1.dp.toPx().coerceAtMost(1.5f),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                floatArrayOf(2.3.dp.toPx(), 1.5.dp.toPx()),
            ),
        )
    }
}

/** A top bar's title, in bold as the phone's own apps set it. */
@Composable
internal fun BarTitle(text: String) {
    // Black, not Bold: the phone's own titles measure a 5px stem at this size, Bold a 4px one.
    TextMMD(text = text, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** A name with the surname in bold: "Ada **Whitlock**", or "**Whitlock**, Ada" turned round. */
internal fun boldSurname(name: String, family: String): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        val at = if (family.isNotBlank()) name.lastIndexOf(family) else -1
        if (at < 0) {
            append(name)
            return@buildAnnotatedString
        }
        append(name.substring(0, at))
        withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) { append(family) }
        append(name.substring(at + family.length))
    }

/** One row of the list, as the phone's own contacts app draws it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NameRow(
    name: androidx.compose.ui.text.AnnotatedString,
    note: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onPress: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 65.dp)
            .combinedClickable(onClick = onPress, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        leading?.let {
            it()
            androidx.compose.foundation.layout.Spacer(Modifier.size(14.dp))
        }
        Column(Modifier.weight(1f)) {
            TextMMD(text = name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!note.isNullOrEmpty()) {
                TextMMD(text = note, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    DottedRule()
}

/** Where a long press on a value sends it, on a page that lets values be copied. */
internal val LocalCopy = androidx.compose.runtime.staticCompositionLocalOf<((String) -> Unit)?> { null }

/** A field as the phone's own app lists one: a bold label, the value under it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LabelValue(label: String, value: String, lines: Int = 6, onPress: (() -> Unit)? = null) {
    val copy = LocalCopy.current
    Column(
        Modifier
            .fillMaxWidth()
            .let {
                if (onPress != null || copy != null) {
                    it.combinedClickable(onClick = onPress ?: {}, onLongClick = copy?.let { c -> { c(value) } })
                } else it
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        TextMMD(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        TextMMD(text = value, style = MaterialTheme.typography.bodyLarge, maxLines = lines, overflow = TextOverflow.Ellipsis)
    }
    DottedRule()
}

/**
 * The top bar, with the heavy rule under it that the phone's own contacts app draws: 4px there,
 * where the library's bar draws one.
 */
@Composable
internal fun Bar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    Column {
        com.mudita.mmd.components.top_app_bar.TopAppBarMMD(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            showDivider = false,
        )
        HorizontalDividerMMD(thickness = 3.dp, color = MaterialTheme.colorScheme.onSurface)
    }
}
