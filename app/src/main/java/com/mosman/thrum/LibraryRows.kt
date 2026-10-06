package com.mosman.thrum

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The rows My Haptics and Music share since 2026-10-06, as Mutalib picked
 * them from `scratch/rows-menu-variants.png`:
 *
 * - **B, play on the rhythm.** The small rhythm picture is the play button;
 *   the name opens the song's page; ⋮ holds the rest. The name gets the
 *   room two buttons used to take — squeezed names were the "vertical
 *   letters" bug.
 * - **E, a small menu at the ⋮**, opening where it was tapped.
 * - A **long-press** starts picking several; the top of the list turns into
 *   a bar with Share, Export and (in My Haptics) Delete side by side.
 */

/** One line in a row's ⋮ menu. */
data class RowMenuItem(
    val icon: String,
    val title: String,
    val help: String? = null,
    val warn: Boolean = false,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryRow(
    name: String,
    subtitle: String,
    score: Score?,
    kindIcon: String,
    first: Boolean,
    last: Boolean,
    canPlay: Boolean,
    playing: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    menu: List<RowMenuItem>,
    isCalls: Boolean = false,
    note: String? = null,
    noteWarn: Boolean = false,
) {
    val corner = Radius.large
    val shape = when {
        first && last -> RoundedCornerShape(corner)
        first -> RoundedCornerShape(topStart = corner, topEnd = corner)
        last -> RoundedCornerShape(bottomStart = corner, bottomEnd = corner)
        else -> RectangleShape
    }
    val background = when {
        selected -> ThrumAccent.copy(alpha = 0.22f)
        playing -> ThrumAccent.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ThrumSurface)
            .background(background)
            .combinedClickable(
                role = Role.Button,
                onClick = if (selecting) onSelect else onOpen,
                onLongClick = onSelect,
            ),
    ) {
        if (!first) HorizontalDivider(color = ThrumRule, thickness = 1.dp)
        Row(
            modifier = Modifier.padding(start = 14.dp, end = Space.S1, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S3),
        ) {
            if (selecting) {
                SelectMark(selected = selected, onToggle = onSelect)
            } else {
                RhythmTile(
                    score = score,
                    kindIcon = kindIcon,
                    canPlay = canPlay,
                    playing = playing,
                    label = stringResource(if (playing) R.string.row_pause else R.string.row_play, name),
                    onPlay = onPlay,
                    onLongPress = onSelect,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = ThrumType.row, color = ThrumInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val calls = if (isCalls) " · ● " + stringResource(R.string.haptics_calls_badge) else ""
                Text(
                    subtitle + calls,
                    style = ThrumType.meta,
                    color = if (isCalls) ThrumAccentInk else ThrumInk2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                note?.let { Text(it, style = ThrumType.meta, color = if (noteWarn) ThrumWarn else ThrumInk2) }
            }
            if (!selecting && menu.isNotEmpty()) {
                RowMenu(items = menu, label = stringResource(R.string.row_more, name))
            } else if (!selecting) {
                Box(Modifier.size(Touch.min))
            }
        }
    }
}

/**
 * The rhythm picture as the play button (sketch B). The song's own rhythm
 * shows faintly behind a round play mark; while it plays, the picture
 * brightens and the mark turns to pause. A song not made yet shows what
 * kind it is instead, and still plays: the player makes its haptic first.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RhythmTile(
    score: Score?,
    kindIcon: String,
    canPlay: Boolean,
    playing: Boolean,
    label: String,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(TILE)
            .clip(shape)
            .background(if (playing) ThrumAccent.copy(alpha = 0.30f) else ThrumSurface2)
            .then(
                if (canPlay) {
                    Modifier
                        .combinedClickable(role = Role.Button, onClick = onPlay, onLongClick = onLongPress)
                        .semantics { contentDescription = label }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (score != null) {
            // Drawn from the amplitudes, not the score, so a screen reader
            // hears the button's name rather than the picture's.
            PulseRibbon(
                amplitudes = score.amplitudes,
                limit = TILE_STEPS,
                height = TILE,
                barWidth = 2.dp,
                barGap = 1.dp,
                modifier = Modifier.alpha(if (playing) 0.9f else 0.5f),
            )
        } else {
            ThrumIcon(name = kindIcon, tint = ThrumInk2, size = 22.dp, modifier = Modifier.alpha(0.6f))
        }
        if (canPlay) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (playing) ThrumAccent else ThrumSurface.copy(alpha = 0.94f)),
                contentAlignment = Alignment.Center,
            ) {
                ThrumIcon(name = if (playing) "pause" else "play", tint = if (playing) ThrumOnAccent else ThrumInk, size = 14.dp)
            }
        }
    }
}

/** While picking several: a round mark in the picture's place. */
@Composable
private fun SelectMark(selected: Boolean, onToggle: () -> Unit) {
    Box(modifier = Modifier.size(TILE), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (selected) ThrumAccentInk else Color.Transparent)
                .border(2.dp, if (selected) ThrumAccentInk else ThrumInk2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) ThrumIcon(name = "check", tint = ThrumSurface, size = 16.dp)
        }
    }
}

/** The ⋮ and its small menu, opening where it was tapped (sketch E). */
@Composable
private fun RowMenu(items: List<RowMenuItem>, label: String) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButtonBox(icon = "more", label = label, onClick = { open = true }, tint = ThrumInk2)
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = ThrumSurface,
            shape = RoundedCornerShape(14.dp),
        ) {
            items.forEachIndexed { index, item ->
                if (item.warn && index > 0) HorizontalDivider(color = ThrumRule, thickness = 1.dp)
                val tint = if (item.warn) ThrumWarn else ThrumInk
                DropdownMenuItem(
                    text = {
                        Column(modifier = Modifier.padding(vertical = Space.S1)) {
                            Text(item.title, style = ThrumType.row, color = tint)
                            item.help?.let { Text(it, style = ThrumType.meta, color = ThrumInk2) }
                        }
                    },
                    leadingIcon = { ThrumIcon(name = item.icon, tint = tint, size = 20.dp) },
                    onClick = {
                        open = false
                        item.onClick()
                    },
                    colors = MenuDefaults.itemColors(),
                )
            }
        }
    }
}

/**
 * The top of the list while picking several (sketch G and H): close, how
 * many, and what can be done to all of them at once. Delete sits beside
 * Export, in its warning colour, only where there is a Delete.
 */
@Composable
fun SelectionBar(
    count: Int,
    total: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Column(modifier = Modifier.padding(top = Space.S1, bottom = Space.S2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButtonBox(icon = "x", label = stringResource(R.string.select_close), onClick = onClose)
            Text(
                pluralStringResource(R.plurals.select_count, count, count),
                style = ThrumType.heading,
                color = ThrumInk,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Space.S1)
                    .semantics { heading() },
            )
            IconButtonBox(icon = "share", label = stringResource(R.string.menu_share), onClick = onShare)
            IconButtonBox(icon = "export", label = stringResource(R.string.menu_export), onClick = onExport)
            if (onDelete != null) {
                IconButtonBox(icon = "trash", label = stringResource(R.string.menu_delete), onClick = onDelete, tint = ThrumWarn)
            }
        }
        if (count < total) {
            ThrumTextButton(
                text = stringResource(R.string.select_all),
                onClick = onSelectAll,
                modifier = Modifier.padding(start = Space.S1),
            )
        }
    }
}

/**
 * Rename, for something the user made. The old name starts selected, so
 * typing replaces it; with the cursor at the start, a first try on the
 * emulator glued the new name onto the old one.
 */
@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(current, selection = TextRange(0, current.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title), style = ThrumType.title, color = ThrumInk) },
        text = {
            val shape = RoundedCornerShape(12.dp)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = ThrumType.lead.copy(color = ThrumInk),
                cursorBrush = SolidColor(ThrumAccentInk),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(ThrumSurface2)
                    .border(1.dp, ThrumLine, shape)
                    .padding(horizontal = 14.dp, vertical = Space.S3)
                    .focusRequester(focus),
            )
        },
        containerColor = ThrumSurface,
        confirmButton = {
            ThrumTextButton(
                text = stringResource(R.string.rename_save),
                onClick = { onRename(text.text) },
            )
        },
        dismissButton = {
            ThrumTextButton(text = stringResource(R.string.haptics_delete_cancel), color = ThrumInk2, onClick = onDismiss)
        },
    )
}

/** Delete one or several, with a confirmation: a delete that can't be undone asks first. */
@Composable
fun DeleteDialog(count: Int, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.delete_title, count, count), style = ThrumType.title, color = ThrumInk) },
        text = { Text(stringResource(R.string.haptics_delete_body), style = ThrumType.body, color = ThrumInkSoft) },
        containerColor = ThrumSurface,
        confirmButton = {
            ThrumTextButton(text = stringResource(R.string.haptics_delete_confirm), color = ThrumWarn, onClick = onDelete)
        },
        dismissButton = {
            ThrumTextButton(text = stringResource(R.string.haptics_delete_cancel), color = ThrumInk2, onClick = onDismiss)
        },
    )
}

/**
 * Sketch I: before sending someone to Android's "Modify system settings"
 * page, say what it is for and what to do there. "Just the vibration" is
 * the way out with no permission at all.
 */
@Composable
fun RingtoneAskSheet(name: String, onOpenSettings: () -> Unit, onVibrationOnly: () -> Unit, onDismiss: () -> Unit) {
    ThrumBottomSheet(onDismiss = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ThrumIcon(name = "bell", tint = ThrumAccentInk, size = 40.dp)
        }
        Text(
            stringResource(R.string.ringtone_ask_title, name),
            style = ThrumType.heading,
            color = ThrumInk,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.S3)
                .semantics { heading() },
        )
        Text(
            stringResource(R.string.ringtone_ask_body),
            style = ThrumType.lead,
            color = ThrumInkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.S3),
        )
        Text(
            stringResource(R.string.ringtone_ask_how),
            style = ThrumType.meta,
            color = ThrumInk2,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.S3, bottom = Space.S4),
        )
        PrimaryButton(text = stringResource(R.string.ringtone_ask_open), onClick = onOpenSettings, modifier = Modifier.fillMaxWidth())
        ThrumTextButton(
            text = stringResource(R.string.ringtone_ask_vibration_only),
            onClick = onVibrationOnly,
            color = ThrumInk2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.S2)
                .height(Touch.min),
        )
    }
}

/** A short line at the bottom of the screen, for what happened after a menu choice. */
fun say(ctx: android.content.Context, text: String) {
    Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()
}

private val TILE = 54.dp

/** About two seconds of rhythm at 20 ms: enough to recognise a song by its shape. */
private const val TILE_STEPS = 100
