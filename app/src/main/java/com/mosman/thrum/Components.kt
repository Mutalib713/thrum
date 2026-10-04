package com.mosman.thrum

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

// --- Colours. -------------------------------------------------------------
//
// Read from the theme, so every screen follows the phone's light or dark
// setting (PROFILE §4 item 13). See [ThrumPalette] for what each one is for,
// and why there are two yellows.

val ThrumField: Color @Composable @ReadOnlyComposable get() = LocalThrumPalette.current.field
val ThrumSurface: Color @Composable @ReadOnlyComposable get() = LocalThrumPalette.current.surface
val ThrumSurface2: Color @Composable @ReadOnlyComposable get() = LocalThrumPalette.current.surface2
val ThrumInk: Color @Composable @ReadOnlyComposable get() = LocalThrumPalette.current.ink
val ThrumInkSoft: Color @Composable @ReadOnlyComposable get() = LocalThrumPalette.current.inkSoft
val ThrumInk2: Color @Composable @ReadOnlyComposable get() = LocalThrumPalette.current.ink2
val ThrumRule: Color @Composable @ReadOnlyComposable get() = LocalThrumPalette.current.rule
val ThrumLine: Color @Composable @ReadOnlyComposable get() = LocalThrumPalette.current.line
val ThrumAccent: Color @Composable @ReadOnlyComposable get() = LocalThrumPalette.current.accent
val ThrumOnAccent: Color @Composable @ReadOnlyComposable get() = LocalThrumPalette.current.onAccent
val ThrumAccentInk: Color @Composable @ReadOnlyComposable get() = LocalThrumPalette.current.accentInk
val ThrumWarn: Color @Composable @ReadOnlyComposable get() = LocalThrumPalette.current.warn
val ThrumSupport: Color @Composable @ReadOnlyComposable get() = LocalThrumPalette.current.support
val ThrumTabBar: Color @Composable @ReadOnlyComposable get() = LocalThrumPalette.current.tabBar

// --- Screen frames. -------------------------------------------------------

/**
 * The scrolling page every screen is built on.
 *
 * [overTabs] is for a tab's own content: the tab bar below already keeps
 * clear of the phone's navigation bar, so the page pads only the top and
 * sides, and leaves room at the bottom for the mini player when one shows.
 * A full screen over the tabs (the player, Tune, Export) pads every side.
 */
@Composable
fun ThrumPage(
    overTabs: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val insets = if (overTabs) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    } else {
        WindowInsets.safeDrawing
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ThrumField)
            .windowInsetsPadding(insets)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.S5, vertical = 18.dp)
            .padding(bottom = if (overTabs && Player.now != null) MINI_PLAYER_ROOM else 0.dp),
        content = content,
    )
}

/** The top of a screen that opens over the tabs: back, and its name. */
@Composable
fun ThrumTopBar(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Space.S2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S1),
    ) {
        IconButtonBox(icon = "back", label = stringResource(R.string.player_close), onClick = onBack)
        Text(
            title,
            style = ThrumType.title,
            color = ThrumInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        action?.invoke()
    }
}

/** A 48 dp touch target around one icon, labelled for screen readers. */
@Composable
fun IconButtonBox(
    icon: String,
    label: String,
    onClick: () -> Unit,
    tint: Color = ThrumInk,
    enabled: Boolean = true,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier = Modifier
            .size(Touch.min)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        ThrumIcon(name = icon, tint = if (enabled) tint else ThrumRule, size = iconSize)
    }
}

/** Small capitals over a section: "FOR CALLS". */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = ThrumInk2) {
    Text(
        text.uppercase(),
        style = ThrumType.overline,
        color = color,
        modifier = modifier.semantics { heading() },
    )
}

// --- Surfaces. ------------------------------------------------------------

@Composable
fun ThrumCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = ThrumSurface,
    borderColor: Color = ThrumRule,
    cornerRadius: Dp = Radius.large,
    padding: PaddingValues = PaddingValues(Space.S4),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            // After the clip, so the press ripple keeps the card's corners.
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun IconCircle(
    icon: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    cornerRadius: Dp = 12.dp,
    iconSize: Dp = 22.dp,
    tint: Color = ThrumAccentInk,
    background: Color = ThrumSurface2,
    borderColor: Color = ThrumRule,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        ThrumIcon(name = icon, tint = tint, size = iconSize)
    }
}

// --- Buttons. -------------------------------------------------------------

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    small: Boolean = false,
    enabled: Boolean = true,
) {
    val h = if (small) 44.dp else 52.dp
    // Disabled reads as disabled without going unreadable: dark ink on the
    // rule colour was 1.6:1, which is a button nobody can read.
    val ink = if (enabled) ThrumOnAccent else ThrumInk2
    Row(
        modifier = modifier
            .heightIn(min = h)
            .clip(CircleShape)
            .background(if (enabled) ThrumAccent else ThrumRule)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) Space.S4 else 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            ThrumIcon(name = icon, tint = ink, size = if (small) 14.dp else 18.dp)
            Spacer(Modifier.width(Space.S2))
        }
        Text(
            text,
            style = if (small) ThrumType.buttonSmall else ThrumType.button,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    small: Boolean = false,
    enabled: Boolean = true,
) {
    val h = if (small) 44.dp else 52.dp
    val ink = if (enabled) ThrumInk else ThrumInk2
    Row(
        modifier = modifier
            .heightIn(min = h)
            .clip(CircleShape)
            .border(1.dp, ThrumLine, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) Space.S4 else 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            ThrumIcon(name = icon, tint = ink, size = if (small) 14.dp else 18.dp)
            Spacer(Modifier.width(Space.S2))
        }
        Text(
            text,
            style = if (small) ThrumType.buttonSmall else ThrumType.button,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ThrumTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    color: Color = ThrumAccentInk,
) {
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.S3, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            ThrumIcon(name = icon, tint = color, size = 16.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = ThrumType.button, color = color)
    }
}

@Composable
fun CirclePlayButton(
    playing: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 14.dp,
) {
    val label = stringResource(if (playing) R.string.player_pause else R.string.player_play)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, ThrumLine, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        ThrumIcon(name = if (playing) "pause" else "play", tint = ThrumInk, size = iconSize)
    }
}

@Composable
fun BigPlayButton(
    playing: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
    enabled: Boolean = true,
) {
    val label = stringResource(if (playing) R.string.player_pause else R.string.player_play)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) ThrumAccent else ThrumRule)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        ThrumIcon(name = if (playing) "pause" else "play", tint = if (enabled) ThrumOnAccent else ThrumInk2, size = 30.dp)
    }
}

// --- Chips, choices, switches. -------------------------------------------

enum class ChipKind { ACCENT, WARN, GREY }

@Composable
fun ThrumChip(
    text: String,
    modifier: Modifier = Modifier,
    kind: ChipKind = ChipKind.ACCENT,
    hasDot: Boolean = false,
    icon: String? = null,
) {
    val palette = LocalThrumPalette.current
    val bg = when (kind) {
        ChipKind.ACCENT -> palette.accent.copy(alpha = 0.14f)
        ChipKind.WARN -> palette.warn.copy(alpha = 0.16f)
        ChipKind.GREY -> palette.surface2
    }
    val textColor = when (kind) {
        ChipKind.ACCENT -> palette.accentInk
        ChipKind.WARN -> palette.warnText
        ChipKind.GREY -> palette.ink2
    }
    val markColor = when (kind) {
        ChipKind.ACCENT -> palette.accentInk
        ChipKind.WARN -> palette.warn
        ChipKind.GREY -> palette.ink2
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (hasDot) {
            Box(
                Modifier
                    .size(Space.S2)
                    .clip(CircleShape)
                    .background(markColor),
            )
        }
        if (icon != null) ThrumIcon(name = icon, tint = markColor, size = 14.dp)
        Text(text, style = ThrumType.chip, color = textColor, maxLines = 1)
    }
}

/**
 * Two to four choices in a pill. Each choice is announced as selected or not,
 * because colour is never the only signal.
 */
@Composable
fun ThrumSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(ThrumSurface)
            .border(1.dp, ThrumRule, RoundedCornerShape(24.dp))
            .padding(Space.S1),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(ThrumSurface2)
                                .border(1.dp, ThrumLine, RoundedCornerShape(20.dp))
                        } else {
                            Modifier
                        },
                    )
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(vertical = 9.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = ThrumType.buttonSmall,
                    color = if (isSelected) ThrumInk else ThrumInk2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The first-launch progress dots. Decoration: the screen's heading says where you are. */
@Composable
fun ThrumDots(
    count: Int = 5,
    activeIndex: Int = 0,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = Space.S2),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until count) {
            val isActive = i == activeIndex
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(if (isActive) 20.dp else 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isActive) ThrumAccent else ThrumRule),
            )
        }
    }
}

/** Drawn only: the row around it is the control, and announces the choice. */
@Composable
fun ThrumRadio(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .border(2.dp, if (selected) ThrumAccentInk else ThrumLine, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(ThrumAccentInk),
            )
        }
    }
}

/**
 * A switch that says it is a switch, and whether it is on. The thumb stays
 * visible when off: the first one drew a dark thumb on a dark track.
 */
@Composable
fun ThrumSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(Space.S4))
            .background(if (checked) ThrumAccent else ThrumSurface2)
            .border(1.dp, if (checked) ThrumAccent else ThrumLine, RoundedCornerShape(Space.S4))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(Space.S1),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) ThrumOnAccent else ThrumInk2),
        )
    }
}

@Composable
fun ThrumSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    help: String,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(title, style = ThrumType.row, color = if (enabled) ThrumInk else ThrumInk2)
        Slider(
            value = value.coerceIn(valueRange),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = ThrumAccent,
                activeTrackColor = ThrumAccent,
                inactiveTrackColor = ThrumRule,
            ),
            modifier = Modifier.heightIn(min = Touch.min),
        )
        Text(help, style = ThrumType.meta, color = ThrumInk2)
    }
}

/**
 * A sheet over the screen. Tapping the dimmed area or pressing Back closes
 * it; tapping inside it does not. The first version switched its own "don't
 * close" guard off, so a tap on the sheet's empty space fell through to the
 * dimmed area and closed it.
 */
@Composable
fun ThrumBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .clip(shape)
                .background(ThrumSurface)
                .border(1.dp, ThrumLine, shape)
                // Swallows taps, so they never reach the dimmed area behind.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Space.S5, vertical = Space.S4),
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(Space.S1)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ThrumInk2.copy(alpha = 0.6f))
                    .align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(18.dp))
            content()
        }
    }
}

// --- Plain helpers. -------------------------------------------------------

/** Format milliseconds as "3:58". */
fun clockOf(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/**
 * How often the app re-reads things it cannot be told about — a permission
 * granted in system settings, the ringer switched with the volume keys. The
 * user leaves, changes something, comes back, and the screen already knows.
 */
const val POLL_MS = 800L

/** Room below a tab's content for the mini player when a song is playing. */
private val MINI_PLAYER_ROOM = 84.dp

/** The player can play a row even when its Track row is gone: the score carries the name. */
fun MyHaptics.Row.asTrack(): Track = Track(
    sourceUri = uri,
    name = name,
    durationMs = 0L,
    kind = kind,
    readable = readable,
)

/** Music access: `READ_MEDIA_AUDIO` from Android 13, the storage permission on 12. */
val musicPermission: String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

fun hasCallAccess(ctx: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

fun hasMusicAccess(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, musicPermission) == PackageManager.PERMISSION_GRANTED

/**
 * Something the app can only find out by asking again, asked every
 * [POLL_MS]. One copy of the loop the first build had in four files.
 */
@Composable
fun <T> rememberPolled(initial: T, read: (Context) -> T): State<T> {
    val ctx = LocalContext.current
    return produceState(initialValue = initial) {
        while (true) {
            value = read(ctx)
            delay(POLL_MS)
        }
    }
}
