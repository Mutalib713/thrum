package com.mosman.thrum

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ThrumField = Color(0xFF1C1C1A)
val ThrumSurface = Color(0xFF242422)
val ThrumSurface2 = Color(0xFF2B2B28)
val ThrumInk = Color(0xFFEFEFEA)
val ThrumInk2 = Color(0xFFA8A8A1)
val ThrumRule = Color(0xFF3A3A36)
val ThrumLine = Color(0xFF4A4A44)
val ThrumAccent = Color(0xFFD8C513)
val ThrumWarn = Color(0xFFE0691C)
val ThrumSupport = Color(0xFF59A1D4)
val ThrumOnlineBlue = Color(0xFF8CC4F0)

@Composable
fun ThrumCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = ThrumSurface,
    borderColor: Color = ThrumRule,
    cornerRadius: Dp = 16.dp,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
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
    tint: Color = ThrumAccent,
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
    val textSp = if (small) 14.sp else 15.sp
    Row(
        modifier = modifier
            .heightIn(min = h)
            .clip(CircleShape)
            .background(if (enabled) ThrumAccent else ThrumRule)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (small) 16.dp else 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            ThrumIcon(name = icon, tint = ThrumField, size = if (small) 14.dp else 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = ThrumField,
            fontSize = textSp,
            fontWeight = FontWeight.SemiBold,
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
) {
    val h = if (small) 44.dp else 52.dp
    val textSp = if (small) 14.sp else 15.sp
    Row(
        modifier = modifier
            .heightIn(min = h)
            .clip(CircleShape)
            .border(1.dp, ThrumLine, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (small) 16.dp else 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            ThrumIcon(name = icon, tint = ThrumInk, size = if (small) 14.dp else 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = ThrumInk,
            fontSize = textSp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun ThrumTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    color: Color = ThrumAccent,
) {
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            ThrumIcon(name = icon, tint = color, size = 16.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            color = color,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
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
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, ThrumLine, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ThrumIcon(
            name = if (playing) "pause" else "play",
            tint = ThrumInk,
            size = iconSize,
        )
    }
}

@Composable
fun BigPlayButton(
    playing: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(ThrumAccent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ThrumIcon(
            name = if (playing) "pause" else "play",
            tint = ThrumField,
            size = 30.dp,
        )
    }
}

enum class ChipKind { ACCENT, WARN, GREY, ONLINE }

@Composable
fun ThrumChip(
    text: String,
    modifier: Modifier = Modifier,
    kind: ChipKind = ChipKind.ACCENT,
    hasDot: Boolean = false,
    icon: String? = null,
) {
    val bg = when (kind) {
        ChipKind.ACCENT -> ThrumAccent.copy(alpha = 0.14f)
        ChipKind.WARN -> ThrumWarn.copy(alpha = 0.16f)
        ChipKind.GREY -> ThrumSurface2
        ChipKind.ONLINE -> ThrumSupport.copy(alpha = 0.16f)
    }
    val textColor = when (kind) {
        ChipKind.ACCENT -> ThrumAccent
        ChipKind.WARN -> ThrumWarn
        ChipKind.GREY -> ThrumInk2
        ChipKind.ONLINE -> ThrumOnlineBlue
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
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(textColor),
            )
        }
        if (icon != null) {
            ThrumIcon(name = icon, tint = textColor, size = 14.dp)
        }
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun ThrumSegmentedControl(
    options: List<String> = emptyList(),
    items: List<String> = emptyList(),
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val choices = if (options.isNotEmpty()) options else items
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(ThrumSurface)
            .border(1.dp, ThrumRule, RoundedCornerShape(24.dp))
            .padding(4.dp),
    ) {
        choices.forEachIndexed { index, option ->
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
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) ThrumInk else ThrumInk2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Format milliseconds as "3:58" */
fun clockOf(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

const val POLL_MS = 800L

fun MyHaptics.Row.asTrack(): Track = Track(
    sourceUri = uri,
    name = name,
    artist = "",
    durationMs = 0L,
    kind = kind,
    readable = readable,
)


@Composable
fun ThrumDots(
    count: Int = 5,
    activeIndex: Int = 0,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = 8.dp),
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

@Composable
fun ThrumRadio(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .border(2.dp, if (selected) ThrumAccent else ThrumLine, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(ThrumAccent),
            )
        }
    }
}

@Composable
fun ThrumSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) ThrumAccent else ThrumRule)
            .clickable { onCheckedChange(!checked) }
            .padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(ThrumField),
        )
    }
}

@Composable
fun ThrumSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    help: String,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = ThrumInk, fontSize = 16.sp)
            Text(valueLabel, color = ThrumInk2, fontSize = 14.sp)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = ThrumAccent,
                activeTrackColor = ThrumAccent,
                inactiveTrackColor = ThrumRule,
            ),
            modifier = Modifier.padding(vertical = 2.dp),
        )
        Text(help, color = ThrumInk2, fontSize = 12.5.sp, lineHeight = 17.sp)
    }
}

@Composable
fun OriginalsShelf(
    onSelect: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        Pair("Afro Groove", "afro"),
        Pair("Heartbeat", "heart"),
        Pair("Pulse", "pulse"),
        Pair("Energy", "energy"),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { (name, pattern) ->
            ThrumCard(
                modifier = Modifier
                    .width(136.dp)
                    .clickable { onSelect?.invoke(name) },
                padding = PaddingValues(12.dp),
            ) {
                PulseRibbon(
                    pattern = pattern,
                    limit = 200,
                    height = 40.dp,
                    barWidth = 2.dp,
                    barGap = 1.dp,
                )
                Text(
                    name,
                    color = ThrumInk,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    "Thrum Original",
                    color = ThrumInk2,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
fun ThrumBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .clickable(enabled = false) {} // don't dismiss when tapping sheet
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(ThrumSurface)
                .border(1.dp, ThrumLine, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ThrumInk2.copy(alpha = 0.6f))
                    .align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(18.dp))
            content()
        }
    }
}
