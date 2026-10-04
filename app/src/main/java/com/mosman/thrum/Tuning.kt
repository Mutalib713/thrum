package com.mosman.thrum

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Screen 15: Tune the feel.
 */
object Tuning {

    data class Preset(val name: String, val subtitle: String, val bodyMs: Int, val pattern: String)

    val CRISP = Preset("Crisp", "Short taps with gaps", ScoreBuilder.BODY_MIN_MS, "pulse")
    val FULL = Preset("Full", "Longer beats", 240, "heart")
    val STRONG = Preset("Strong", "Closest to a buzz", ScoreBuilder.BODY_MS, "buzz")

    val ALL = listOf(CRISP, FULL, STRONG)

    fun matching(bodyMs: Int): Preset? = ALL.firstOrNull { it.bodyMs == bodyMs }

    val RESET_PUNCH = ScoreBuilder.MIN_FELT
    val RESET_DISTANCE = 0
    val RESET_BODY = ScoreBuilder.BODY_MS
}

@Composable
fun TuneScreen(
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }

    var selectedPreset by remember {
        val initial = Tuning.matching(store.body) ?: Tuning.CRISP
        mutableIntStateOf(Tuning.ALL.indexOf(initial).coerceAtLeast(0))
    }
    var intensity by remember { mutableFloatStateOf(store.punch.toFloat()) }
    var focus by remember { mutableFloatStateOf(store.distance.toFloat()) }
    var duration by remember { mutableFloatStateOf(store.body.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ThrumField)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        // Topbar: back button + title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onClose() },
                contentAlignment = Alignment.CenterStart,
            ) {
                ThrumIcon(name = "back", tint = ThrumInk, size = 22.dp)
            }
            Text(
                text = "Tune the feel",
                color = ThrumInk,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        // 3 Presets Card List
        ThrumCard(
            modifier = Modifier.padding(top = 8.dp),
            padding = PaddingValues(0.dp),
        ) {
            Tuning.ALL.forEachIndexed { index, preset ->
                if (index > 0) Divider(color = ThrumRule, thickness = 1.dp)
                val isSelected = selectedPreset == index
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedPreset = index
                            duration = preset.bodyMs.toFloat()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ThrumRadio(selected = isSelected)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(preset.name, color = ThrumInk, fontSize = 15.5.sp, fontWeight = FontWeight.Medium)
                        Text(preset.subtitle, color = ThrumInk2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 1.dp))
                    }
                    Box(Modifier.width(64.dp)) {
                        PulseRibbon(pattern = preset.pattern, height = 26.dp, barWidth = 2.dp, barGap = 1.dp)
                    }
                }
            }
        }

        Text(
            text = "FINE-TUNE",
            color = ThrumInk2,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(top = 22.dp, bottom = 8.dp, start = 2.dp),
        )

        // Sliders Card
        ThrumCard(
            padding = PaddingValues(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ThrumSliderRow(
                    title = "Intensity",
                    valueLabel = if (intensity >= 200f) "Max" else "${intensity.toInt()}",
                    value = intensity,
                    valueRange = 120f..210f,
                    onValueChange = { intensity = it },
                    help = "How hard each beat lands.",
                )

                ThrumSliderRow(
                    title = "Focus",
                    valueLabel = if (focus <= 20f) "Whole kit" else "Mostly the beat",
                    value = focus,
                    valueRange = 0f..100f,
                    onValueChange = { focus = it },
                    help = "Left lets the snare and hats through. Right keeps only the beat.",
                )

                ThrumSliderRow(
                    title = "Duration",
                    valueLabel = if (duration <= 150f) "Short · ${duration.toInt()} ms" else "Longer · ${duration.toInt()} ms",
                    value = duration,
                    valueRange = 100f..400f,
                    onValueChange = { duration = it },
                    help = "Longer beats push harder. Shorter ones stay crisp.",
                )
            }
        }

        Spacer(Modifier.height(36.dp))

        // Bottom actions: Reset to balanced & Done
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThrumTextButton(
                text = "Reset to balanced",
                onClick = {
                    intensity = Tuning.RESET_PUNCH.toFloat()
                    focus = Tuning.RESET_DISTANCE.toFloat()
                    duration = Tuning.RESET_BODY.toFloat()
                    selectedPreset = 2
                },
                color = ThrumInk2,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = "Done",
                onClick = {
                    val s = store.armedScore
                    if (s != null) {
                        store.arm(s, store.sourceUri, intensity.toInt(), focus.toInt(), duration.toInt())
                    }
                    onClose()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
