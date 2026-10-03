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
                style = MaterialTheme.typography.bodyMedium,
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
    HorizontalDividerMMD(thickness = 0.5.dp)
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
) {
    TextFieldMMD(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
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
