package com.mosman.thrum

import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The three presets of screen 15 — and why they are three numbers and not
 * nine.
 *
 * The 2026-09-21 measurement (PLAN.md, Task 8 follow-up part two) walked the
 * Duration dial across the real armed track and measured sustained drive at
 * **0.334** at 100 ms, **0.417** at 240 ms and **0.540** at 400 ms, against
 * the stock buzz's 0.500. Those three points are the presets: **Crisp** is
 * more silence than the buzz, **Full** is nearly parity, **Strong** is past
 * it — the design's own words agree ("Strong: closest to a buzz"). No
 * measurement ever distinguished a Punch or a Distance per preset, so the
 * presets do not touch them: the fine-tune dials keep whatever the user has,
 * and a preset changes exactly the axis that was measured.
 *
 * The final values are still Mutalib's hand to confirm (Task 15's second
 * half) — but they start from measurement, not from a guess.
 */
object Tuning {

    /**
     * [pattern] is an illustration of the preset's shape, drawn only when
     * there is no song to draw it on (the default feel, with no call song).
     */
    data class Preset(val nameRes: Int, val helpRes: Int, val bodyMs: Int, val pattern: String)

    /** "Short taps with gaps." Still 54 % on the measured track — more than the buzz. */
    val CRISP = Preset(R.string.tune_preset_crisp, R.string.tune_preset_crisp_help, ScoreBuilder.BODY_MIN_MS, "pulse")

    /** "Longer beats." 0.417 sustained — nearly parity with the buzz. */
    val FULL = Preset(R.string.tune_preset_full, R.string.tune_preset_full_help, 240, "heart")

    /** "Closest to a buzz." 0.540 sustained, longest felt run 1,460 ms. */
    val STRONG = Preset(R.string.tune_preset_strong, R.string.tune_preset_strong_help, ScoreBuilder.BODY_MS, "buzz")

    val ALL = listOf(CRISP, FULL, STRONG)

    /** The preset a Duration dial is currently sitting on, or null off-ladder. */
    fun matching(bodyMs: Int): Preset? = ALL.firstOrNull { it.bodyMs == bodyMs }

    /**
     * "Reset to balanced": the app's own defaults — the felt floor for
     * Intensity, the whole kit for Focus, and the only Duration that reached
     * parity with the buzz on the measured track.
     */
    val RESET_PUNCH = ScoreBuilder.MIN_FELT
    val RESET_DISTANCE = 0
    val RESET_BODY = ScoreBuilder.BODY_MS
}

/** What the Tune screen is tuning. */
sealed interface TuneTarget {
    /**
     * The song for calls — from Settings' "Default feel". Its dials are the
     * stored ones, which every new haptic starts from; with no call song
     * they are only that default.
     */
    data object Calls : TuneTarget

    /** One song's haptic — from its page in the player (PROFILE §4 item 6). */
    data class Song(val track: Track, val haptic: Haptic) : TuneTarget
}

/**
 * Screen 15: Tune the feel.
 *
 * **Changes apply straight away**, which is what the screen says. The song is
 * read once when the screen opens (about eight seconds); after that every
 * dial move rebuilds the rhythm from the kept analysis in milliseconds, and
 * the result is saved when a dial is let go. The first version of this
 * screen saved the new numbers beside the *old* rhythm and rebuilt nothing,
 * so the motor went on playing what it played before — the same split
 * between a score and its dials that was measured and fixed on 2026-09-21.
 */
@Composable
fun TuneScreen(target: TuneTarget, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onClose)

    val armed = remember { store.armedScore != null }
    val (sourceUri, sourceName) = remember(target) {
        when (target) {
            is TuneTarget.Song -> target.track.sourceUri.takeUnless { ThrumFile.isImported(it) } to target.track.name
            TuneTarget.Calls -> store.sourceUri to (store.armedScore?.sourceName.orEmpty())
        }
    }
    var punch by remember(target) {
        mutableIntStateOf(if (target is TuneTarget.Song) target.haptic.punch else store.punch)
    }
    var distance by remember(target) {
        mutableIntStateOf(if (target is TuneTarget.Song) target.haptic.distance else store.distance)
    }
    var body by remember(target) {
        mutableIntStateOf(if (target is TuneTarget.Song) target.haptic.bodyMs else store.body)
    }

    var analysis by remember(target) { mutableStateOf<HapticMaker.Analysis?>(null) }
    LaunchedEffect(target) {
        if (sourceUri != null) analysis = HapticMaker.analyse(ctx, Uri.parse(sourceUri), sourceName)
    }
    val ready = analysis as? HapticMaker.Analysis.Ok
    val reading = sourceUri != null && analysis == null

    // What the dials make now: a call's first 45 seconds — for a song, its
    // opening, which is the part a call would play and enough to feel.
    val preview = ready?.let {
        ScoreBuilder.callScore(it.levels, it.stepMs, sourceName, punch, distance, body)
    }

    val problem: String? = when {
        target is TuneTarget.Song && ThrumFile.isImported(target.track.sourceUri) ->
            stringResource(R.string.tune_imported)
        target == TuneTarget.Calls && armed && sourceUri == null ->
            stringResource(R.string.tune_problem, stringResource(R.string.tune_no_source))
        analysis is HapticMaker.Analysis.Failed ->
            stringResource(R.string.tune_problem, (analysis as HapticMaker.Analysis.Failed).message)
        else -> null
    }
    // With nothing armed and nothing to read, the dials set the default feel.
    val defaultsOnly = target == TuneTarget.Calls && !armed

    /** Write what the dials say, to whatever this screen is tuning. */
    fun commit() {
        when (target) {
            TuneTarget.Calls -> {
                if (!armed) {
                    store.setDefaultTuning(punch, distance, body)
                    return
                }
                val built = ready ?: return
                val call = ScoreBuilder.callScore(built.levels, built.stepMs, sourceName, punch, distance, body)
                if (call.isSilent()) return
                store.arm(call, sourceUri, punch, distance, body)
                // The same song in the library takes the same feel, so its
                // page and the call never disagree.
                val uri = sourceUri ?: return
                scope.launch {
                    val inLibrary = withContext(Dispatchers.IO) { LibraryDb.get(ctx).dao().hapticFor(uri) } != null
                    if (inLibrary) {
                        val whole = ScoreBuilder.wholeScore(built.levels, built.stepMs, sourceName, punch, distance, body)
                        Player.retuned(ctx, HapticMaker.save(ctx, uri, whole, punch, distance, body))
                    }
                }
            }

            is TuneTarget.Song -> {
                val built = ready ?: return
                scope.launch {
                    val whole = ScoreBuilder.wholeScore(built.levels, built.stepMs, sourceName, punch, distance, body)
                    if (whole.isSilent()) return@launch
                    val saved = HapticMaker.save(ctx, target.track.sourceUri, whole, punch, distance, body)
                    Player.retuned(ctx, saved)
                    if (store.sourceUri == target.track.sourceUri) {
                        store.arm(saved.callWindow(), saved.trackUri, punch, distance, body)
                    }
                }
            }
        }
    }

    // "Feel it": the rhythm the dials make, on the motor, with a playhead.
    var progress by remember { mutableFloatStateOf(-1f) }
    var feelJob by remember { mutableStateOf<Job?>(null) }
    fun stopFeel() {
        feelJob?.cancel()
        feelJob = null
        progress = -1f
        Haptics.stop(ctx)
    }
    DisposableEffect(Unit) { onDispose { stopFeel() } }

    ThrumPage {
        ThrumTopBar(title = stringResource(R.string.tune_title), onBack = onClose)

        if (sourceName.isNotEmpty()) {
            Text(
                sourceName,
                style = ThrumType.meta,
                color = ThrumInk2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = Space.S3),
            )
        }

        // The rhythm itself, read once and redrawn on every dial move.
        when {
            reading -> Row(
                modifier = Modifier.padding(vertical = Space.S3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.S3),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp, color = ThrumAccentInk)
                Column {
                    Text(stringResource(R.string.loading_title, sourceName), style = ThrumType.row, color = ThrumInk)
                    Text(stringResource(R.string.loading_body), style = ThrumType.meta, color = ThrumInk2)
                }
            }

            preview != null -> ThrumCard(padding = PaddingValues(Space.S4)) {
                PulseRibbon(score = preview, progress = progress, height = 88.dp, barWidth = 2.dp, barGap = 1.dp)
                val still = preview.amplitudes.count { it == 0 } * 100 / preview.amplitudes.size.coerceAtLeast(1)
                Text(
                    stringResource(R.string.tech_row, preview.amplitudes.size, preview.stepMs, still),
                    style = ThrumType.meta,
                    color = ThrumInk2,
                    modifier = Modifier.padding(top = Space.S2),
                )
            }
        }

        // The three presets: Duration only, the measured axis.
        ThrumCard(modifier = Modifier.padding(top = Space.S3), padding = PaddingValues(0.dp)) {
            Tuning.ALL.forEachIndexed { index, preset ->
                if (index > 0) HorizontalDivider(color = ThrumRule, thickness = 1.dp)
                val selected = body == preset.bodyMs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected, role = Role.RadioButton, enabled = problem == null) {
                            body = preset.bodyMs
                            commit()
                        }
                        .padding(horizontal = Space.S4, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ThrumRadio(selected = selected)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(preset.nameRes), style = ThrumType.row, color = ThrumInk)
                        Text(stringResource(preset.helpRes), style = ThrumType.meta, color = ThrumInk2)
                    }
                    Box(Modifier.width(64.dp)) {
                        // This song with this preset, when there is a song;
                        // the preset's shape, when there is not.
                        val shown = ready?.let {
                            ScoreBuilder.callScore(it.levels, it.stepMs, sourceName, punch, distance, preset.bodyMs)
                        }
                        if (shown != null) {
                            PulseRibbon(score = shown, limit = PRESET_RIBBON_STEPS, height = 26.dp, barWidth = 2.dp, barGap = 1.dp)
                        } else {
                            PulseRibbon(pattern = preset.pattern, height = 26.dp, barWidth = 2.dp, barGap = 1.dp)
                        }
                    }
                }
            }
        }

        Overline(stringResource(R.string.tune_fine), modifier = Modifier.padding(top = 22.dp, bottom = Space.S2, start = 2.dp))

        ThrumCard(padding = PaddingValues(Space.S4)) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.S4)) {
                // Never to 255: see ScoreBuilder.MIN_HEADROOM. A floor at the
                // ceiling leaves no room for loud and quiet beats.
                ThrumSliderRow(
                    title = stringResource(R.string.tune_intensity, punch),
                    value = punch.toFloat(),
                    valueRange = 120f..(Score.MAX_AMPLITUDE - ScoreBuilder.MIN_HEADROOM).toFloat(),
                    onValueChange = { punch = it.toInt() },
                    onValueChangeFinished = { commit() },
                    help = stringResource(R.string.tune_intensity_help),
                    enabled = problem == null,
                )
                ThrumSliderRow(
                    title = stringResource(R.string.tune_duration, body),
                    value = body.toFloat(),
                    valueRange = ScoreBuilder.BODY_MIN_MS.toFloat()..ScoreBuilder.BODY_MAX_MS.toFloat(),
                    onValueChange = { body = it.toInt() },
                    onValueChangeFinished = { commit() },
                    help = stringResource(R.string.tune_duration_help),
                    enabled = problem == null,
                )
                ThrumSliderRow(
                    title = stringResource(R.string.tune_focus, distance),
                    value = distance.toFloat(),
                    valueRange = 0f..100f,
                    onValueChange = { distance = it.toInt() },
                    onValueChangeFinished = { commit() },
                    help = stringResource(R.string.tune_focus_help),
                    enabled = problem == null,
                )
            }
        }

        problem?.let { message ->
            Text(message, style = ThrumType.body, color = ThrumWarn, modifier = Modifier.padding(top = Space.S3))
        }
        Text(
            stringResource(if (defaultsOnly) R.string.tune_defaults_note else R.string.tune_note),
            style = ThrumType.meta,
            color = ThrumInk2,
            modifier = Modifier.padding(top = Space.S3, start = Space.S1, end = Space.S1),
        )

        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.S2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThrumTextButton(
                text = stringResource(R.string.tune_reset),
                onClick = {
                    // One rebuild, not three: a reset that saved three times
                    // would be three chances to flicker.
                    punch = Tuning.RESET_PUNCH
                    distance = Tuning.RESET_DISTANCE
                    body = Tuning.RESET_BODY
                    commit()
                },
                color = ThrumInk2,
                modifier = Modifier.weight(1f),
            )
            if (preview != null) {
                SecondaryButton(
                    text = stringResource(
                        when {
                            progress >= 0f -> R.string.ready_stop
                            target == TuneTarget.Calls -> R.string.ready_feel
                            else -> R.string.tune_feel
                        },
                    ),
                    icon = if (progress >= 0f) null else "play",
                    small = true,
                    onClick = {
                        if (progress >= 0f) {
                            stopFeel()
                        } else {
                            // The player owns the motor too; it pauses rather
                            // than fight a preview for it.
                            if (Player.now?.playing == true) Player.togglePause(ctx)
                            stopFeel()
                            if (Haptics.play(ctx, preview) == null) {
                                feelJob = scope.launch {
                                    val started = SystemClock.elapsedRealtime()
                                    while (true) {
                                        val f = (SystemClock.elapsedRealtime() - started).toFloat() / preview.durationMs
                                        if (f >= 1f) break
                                        progress = f
                                        delay(Motion.FRAME)
                                    }
                                    progress = -1f
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(Space.S3))
        PrimaryButton(text = stringResource(R.string.tune_done), onClick = onClose, modifier = Modifier.fillMaxWidth())
    }
}

/** About eight seconds at 20 ms: enough of the song to see each preset's shape. */
private const val PRESET_RIBBON_STEPS = 400
