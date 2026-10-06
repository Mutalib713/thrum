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
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * presets do not touch them: the two switches keep whatever the user has,
 * and a preset changes exactly the axis that was measured.
 *
 * Re-measured 2026-10-06 with today's analyser (with the light beats on):
 * 0.320, 0.392 and 0.502 — the same ladder, a little lower, with Strong
 * level with the buzz rather than past it.
 *
 * **Why the screen has no sliders any more** (Mutalib, 2026-10-06: "I
 * usually don't feel any changes"). The presets moved the motor's on-time
 * from 46 % to 67 %, which is felt. Intensity moved the average beat only
 * from 169 to 207 out of 255, and Focus moved nothing until its last step.
 * So the presets are the main choice, Focus became [extraTaps], Intensity
 * became [softer], and the Duration slider went because the presets are
 * its only three points anyone measured.
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

    /** "Short taps with space between." 0.320 sustained on AIZO — well under the buzz. */
    val CRISP = Preset(R.string.tune_preset_crisp, R.string.tune_preset_crisp_help, ScoreBuilder.BODY_MIN_MS, "pulse")

    /** "Longer taps." 0.392 sustained — most of the way to the buzz. */
    val FULL = Preset(R.string.tune_preset_full, R.string.tune_preset_full_help, 240, "heart")

    /** "Closest to the phone's own buzz." 0.502 sustained, longest felt run 1,540 ms. */
    val STRONG = Preset(R.string.tune_preset_strong, R.string.tune_preset_strong_help, ScoreBuilder.BODY_MS, "buzz")

    val ALL = listOf(CRISP, FULL, STRONG)

    /** The preset a Duration is currently sitting on, or null for an old fine-tuned one. */
    fun matching(bodyMs: Int): Preset? = ALL.firstOrNull { it.bodyMs == bodyMs }

    /**
     * The "Extra taps" switch, kept in the old Focus number so nothing saved
     * has to change shape.
     *
     * Measured 2026-10-06 on AIZO's call window, Focus moved the score by
     * under one point out of 255 anywhere from 0 to 99, and did something
     * only at 100, where it switched the light beats off. So it was always a
     * switch drawn as a slider; now it is drawn as what it is. Switching it
     * off takes the motor from 46 % to 27 % of the time on Crisp, and from
     * 67 % to 58 % on Strong.
     */
    const val EXTRA_TAPS_ON = 0
    const val EXTRA_TAPS_OFF = 100

    /** Whether a saved Focus number means Extra taps is on. Anything under 100 kept the light beats. */
    fun extraTaps(distance: Int): Boolean = distance < EXTRA_TAPS_OFF

    /**
     * The "Softer" switch, kept in the old Intensity number. Normal is the
     * felt floor every haptic has always started from; Softer is any number
     * under it, which [ScoreBuilder.wholeScore] reads as "turn every beat
     * down". See [ScoreBuilder.SOFTER_SCALE] for what it measured.
     */
    const val NORMAL_PUNCH = ScoreBuilder.MIN_FELT
    const val SOFTER_PUNCH = ScoreBuilder.SOFTER_FLOOR

    fun softer(punch: Int): Boolean = ScoreBuilder.isSofter(punch)

    /**
     * "Reset": the app's own defaults — Strong, with the light beats, at
     * normal strength. Strong is the only preset that reached the stock
     * buzz on the measured track.
     */
    const val RESET_PUNCH = NORMAL_PUNCH
    const val RESET_DISTANCE = EXTRA_TAPS_ON
    const val RESET_BODY = ScoreBuilder.BODY_MS
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
 * change rebuilds the rhythm from the kept analysis in milliseconds and saves
 * it. The first version of this screen saved the new numbers beside the *old*
 * rhythm and rebuilt nothing, so the motor went on playing what it played
 * before — the same split between a score and its dials that was measured
 * and fixed on 2026-09-21.
 *
 * **A choice made while the song is still being read is kept.** It waits in
 * `pending` and is saved the moment the reading finishes. Before, the save
 * returned early and dropped it, so the screen showed one setting while the
 * haptic kept the old one.
 *
 * **"Feel it" follows the controls.** A change while it plays restarts the
 * new rhythm from the same moment, so the hand feels the difference without
 * stopping and starting again. Mutalib, 2026-10-06: "I usually don't feel
 * any changes" — part of that was a preview that kept playing the old rhythm.
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

    // What the controls make now: a call's first 45 seconds — for a song, its
    // opening, which is the part a call would play and enough to feel.
    val preview = remember(ready, punch, distance, body) {
        ready?.let { ScoreBuilder.callScore(it.levels, it.stepMs, sourceName, punch, distance, body) }
    }
    // Each preset drawn on this song with the switches as they are.
    val presetShapes = remember(ready, punch, distance) {
        ready?.let { r ->
            Tuning.ALL.associateWith {
                ScoreBuilder.callScore(r.levels, r.stepMs, sourceName, punch, distance, it.bodyMs)
            }
        }
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
    // With nothing armed and nothing to read, the controls set the default feel.
    val defaultsOnly = target == TuneTarget.Calls && !armed

    var pending by remember { mutableStateOf(false) }
    // Saves one at a time and in order, so two quick taps can't land the
    // older one last.
    val saving = remember { Mutex() }

    /** Write what the controls say, to whatever this screen is tuning. */
    fun commit() {
        when (target) {
            TuneTarget.Calls -> {
                if (!armed) {
                    store.setDefaultTuning(punch, distance, body)
                    return
                }
                val built = ready ?: run {
                    pending = true
                    return
                }
                val call = ScoreBuilder.callScore(built.levels, built.stepMs, sourceName, punch, distance, body)
                if (call.isSilent()) return
                store.arm(call, sourceUri, punch, distance, body)
                // The same song in the library takes the same feel, so its
                // page and the call never disagree.
                val uri = sourceUri ?: return
                val p = punch
                val d = distance
                val b = body
                scope.launch {
                    saving.withLock {
                        val inLibrary = withContext(Dispatchers.IO) { LibraryDb.get(ctx).dao().hapticFor(uri) } != null
                        if (inLibrary) {
                            val whole = ScoreBuilder.wholeScore(built.levels, built.stepMs, sourceName, p, d, b)
                            Player.retuned(ctx, HapticMaker.save(ctx, uri, whole, p, d, b))
                        }
                    }
                }
            }

            is TuneTarget.Song -> {
                val built = ready ?: run {
                    pending = true
                    return
                }
                val p = punch
                val d = distance
                val b = body
                scope.launch {
                    saving.withLock {
                        val whole = ScoreBuilder.wholeScore(built.levels, built.stepMs, sourceName, p, d, b)
                        if (whole.isSilent()) return@withLock
                        val saved = HapticMaker.save(ctx, target.track.sourceUri, whole, p, d, b)
                        Player.retuned(ctx, saved)
                        if (store.sourceUri == target.track.sourceUri) {
                            store.arm(saved.callWindow(), saved.trackUri, p, d, b)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(ready) {
        if (ready != null && pending) {
            pending = false
            commit()
        }
    }

    // "Feel it": the rhythm the controls make, on the motor, with a playhead.
    var progress by remember { mutableFloatStateOf(-1f) }
    var feelJob by remember { mutableStateOf<Job?>(null) }
    var feelStartedAt by remember { mutableLongStateOf(0L) }
    fun stopFeel() {
        feelJob?.cancel()
        feelJob = null
        progress = -1f
        Haptics.stop(ctx)
    }

    /** Play [score] on the motor from [fromMs], with the playhead following it. */
    fun startFeel(score: Score, fromMs: Long) {
        stopFeel()
        if (fromMs >= score.durationMs) return
        if (Haptics.play(ctx, score.from(fromMs)) != null) return
        feelStartedAt = SystemClock.elapsedRealtime() - fromMs
        feelJob = scope.launch {
            while (true) {
                val f = (SystemClock.elapsedRealtime() - feelStartedAt).toFloat() / score.durationMs
                if (f >= 1f) break
                progress = f
                delay(Motion.FRAME)
            }
            progress = -1f
            feelJob = null
        }
    }
    DisposableEffect(Unit) { onDispose { stopFeel() } }

    // A change while "Feel it" plays: carry on from the same moment, in the
    // new rhythm.
    LaunchedEffect(preview) {
        val now = preview ?: return@LaunchedEffect
        if (feelJob != null) startFeel(now, SystemClock.elapsedRealtime() - feelStartedAt)
    }

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

        // The rhythm itself, read once and redrawn on every change.
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
            }
        }

        Text(
            stringResource(R.string.tune_intro),
            style = ThrumType.body,
            color = ThrumInk2,
            modifier = Modifier.padding(top = Space.S4, bottom = Space.S2, start = Space.S1, end = Space.S1),
        )

        // The three presets: the main choice, and the one axis that was
        // measured to change what a hand feels.
        ThrumCard(padding = PaddingValues(0.dp)) {
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
                        val shown = presetShapes?.get(preset)
                        if (shown != null) {
                            PulseRibbon(score = shown, limit = PRESET_RIBBON_STEPS, height = 26.dp, barWidth = 2.dp, barGap = 1.dp)
                        } else {
                            PulseRibbon(pattern = preset.pattern, height = 26.dp, barWidth = 2.dp, barGap = 1.dp)
                        }
                    }
                }
            }
        }
        // A Duration fine-tuned on the old slider sits between presets.
        if (Tuning.matching(body) == null) {
            Text(
                stringResource(R.string.tune_custom),
                style = ThrumType.meta,
                color = ThrumInk2,
                modifier = Modifier.padding(top = Space.S2, start = Space.S1, end = Space.S1),
            )
        }

        Overline(stringResource(R.string.tune_fine), modifier = Modifier.padding(top = 22.dp, bottom = Space.S2, start = 2.dp))

        ThrumCard(padding = PaddingValues(0.dp)) {
            ThrumSwitchRow(
                name = stringResource(R.string.tune_extra_taps),
                help = stringResource(R.string.tune_extra_taps_help),
                checked = Tuning.extraTaps(distance),
                onChange = { on ->
                    distance = if (on) Tuning.EXTRA_TAPS_ON else Tuning.EXTRA_TAPS_OFF
                    commit()
                },
                enabled = problem == null,
            )
            HorizontalDivider(color = ThrumRule, thickness = 1.dp)
            ThrumSwitchRow(
                name = stringResource(R.string.tune_softer),
                help = stringResource(R.string.tune_softer_help),
                checked = Tuning.softer(punch),
                onChange = { on ->
                    punch = if (on) Tuning.SOFTER_PUNCH else Tuning.NORMAL_PUNCH
                    commit()
                },
                enabled = problem == null,
            )
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
                val feeling = feelJob != null
                SecondaryButton(
                    text = stringResource(
                        when {
                            feeling -> R.string.ready_stop
                            target == TuneTarget.Calls -> R.string.ready_feel
                            else -> R.string.tune_feel
                        },
                    ),
                    icon = if (feeling) null else "play",
                    small = true,
                    onClick = {
                        if (feeling) {
                            stopFeel()
                        } else {
                            // The player owns the motor too; it pauses rather
                            // than fight a preview for it.
                            if (Player.now?.playing == true) Player.togglePause(ctx)
                            startFeel(preview, 0L)
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
