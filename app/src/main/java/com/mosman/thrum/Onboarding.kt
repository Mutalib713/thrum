package com.mosman.thrum

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.toSize
import kotlin.math.abs
import kotlin.math.min
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * First launch: seven screens, calls before music, the phone check before
 * everything. Task 18, drawn in `docs/design/screens` screens 1–7.
 *
 * - **Screen 1 is the capability check** (Sacred Rule 2). An incapable phone
 *   never sees screen 2: it sees the honest dead end, with no button on it.
 * - **Screen 3 lets the phone be felt before any permission** — the flat
 *   buzz on one side, the demo rhythm on the other, both exactly what they
 *   say they are. Until the built-in collection ships (Task 27), the Thrum
 *   side is the rhythm the app has demoed since Task 1, and is not dressed up
 *   as a Thrum Original "with sound": the first UI drawn here did that, and
 *   played the demo with no sound at all.
 * - **Call access is asked, never demanded.** "Maybe later" goes on, and Home
 *   keeps saying calls cannot work until it is granted.
 *
 * Nothing here is sample data: the first UI drawn on these screens named
 * Mutalib's own call song as the reader's, and drew a player with a play
 * button that did nothing.
 */
@Composable
fun OnboardingFlow(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val capability = remember { Haptics.capability(ctx) }
    var screen by remember { mutableIntStateOf(0) }

    // Back walks back through the screens, but never into the splash, which
    // would only bounce forward again a second later.
    BackHandler(enabled = screen in 2..6) { screen -= 1 }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ThrumField),
    ) {
        when {
            !capability.usable -> CapabilityDeadEnd(capability)
            // The splash and the welcome page are one piece since 2026-11-28, so
            // the pills and the wordmark can fly from one into the other.
            screen <= 1 -> SplashToWelcome(
                settled = screen == 1,
                onArrived = { screen = 1 },
                onSeeHow = { screen = 2 },
                onSkip = onDone,
            )
            screen == 2 -> SoundToTouch(onNext = { screen = 3 })
            screen == 3 -> Sources(onNext = { screen = 4 })
            screen == 4 -> FeelCalls(onNext = { screen = 5 })
            screen == 5 -> FeelMusic(onNext = { screen = 6 })
            else -> YouAreSet(onDone = onDone)
        }
    }
}

/** A first-launch screen: scrolls on a small phone, its button at the bottom. */
@Composable
private fun Step(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.S5, vertical = 28.dp),
        content = content,
    )
}

@Composable
private fun Statement(text: String, top: Int = 28) {
    Text(
        text,
        style = ThrumType.statement,
        color = ThrumInk,
        modifier = Modifier
            .padding(top = top.dp)
            .semantics { heading() },
    )
}

@Composable
private fun Lead(text: String) {
    Text(text, style = ThrumType.lead, color = ThrumInkSoft, modifier = Modifier.padding(top = Space.S3))
}

/**
 * The opening: the splash, then the move into the welcome page. Mutalib's
 * hollow-ring version, approved 2026-11-28 (scratch/transition-combo.html):
 *
 * 1. The splash's five pills pulse to the sample rhythm.
 * 2. They slide together into one hollow yellow ring (only the edge is yellow;
 *    inside is the normal screen).
 * 3. The ring grows into a yellow frame, a window with the welcome page inside
 *    it, until the frame reaches the screen's edges.
 * 4. The frame shrinks back to a small ring where the welcome page's mark sits,
 *    and splits into the five pills there, while THRUM flies from the splash's
 *    spot to beside them (pick A, kept).
 *
 * One layer draws the pills, the ring and the frame through every step, so
 * nothing changes hands mid-flight. On landing, the welcome page's own pills
 * and wordmark take over in the same spot, on the same rhythm clock.
 *
 * With Android's "Remove animations" on, the welcome page simply appears.
 * [settled] is the way back from the next screen: the welcome page, no splash.
 */
@Composable
private fun SplashToWelcome(settled: Boolean, onArrived: () -> Unit, onSeeHow: () -> Unit, onSkip: () -> Unit) {
    val moving = motionAllowed()
    val clock = remember { Animatable(if (settled) T_END else 0f) }
    val rhythm = remember { Demo.rhythm() }
    val rhythmMath = remember { BarMath(rhythm.amplitudes, rhythm.stepMs) }
    val startedAt = remember { SystemClock.uptimeMillis() }
    val palette = LocalThrumPalette.current
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var screen by remember { mutableStateOf(Size.Zero) }
    var splashPills by remember { mutableStateOf<Rect?>(null) }
    var splashWord by remember { mutableStateOf<Rect?>(null) }
    var welcomePills by remember { mutableStateOf<Rect?>(null) }
    var welcomeWord by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(Unit) {
        if (clock.value >= T_END) return@LaunchedEffect
        // The check has already run; the splash exists so the first thing the
        // app ever does is say what it is doing.
        if (moving) clock.animateTo(T_END, tween(T_END.toInt(), easing = LinearEasing)) else clock.snapTo(T_END)
        onArrived()
    }

    val t = clock.value
    val done = t >= T_END
    val merge = inOut((t - T_MERGE) / MERGE)
    val grow = inOut((t - T_GROW) / GROW)
    val back = inOut((t - T_BACK) / BACK)

    // Where everything is this frame, in the opening's own coordinates.
    val px = with(density) { 1.dp.toPx() }
    val fromP = splashPills
    val toP = welcomePills ?: fromP
    var pieces: List<Piece> = emptyList()
    var window: Rect? = null
    var windowRadius = 0f
    if (!done && fromP != null && toP != null && screen != Size.Zero) {
        val circle = Rect(fromP.center, RING_D * px / 2)
        val full = Rect(Offset.Zero, screen)
        val markCircle = Rect(toP.center, MARK_RING_D * px / 2)
        val accent = palette.accent
        when {
            t < T_GROW -> {
                // 1-2: the pills pulse, then slide together into one ring.
                val pills = pillRects(fromP.center, 1f, rhythmMath, min(t, T_MERGE).toLong(), px)
                val hollow = clamp01((merge - 0.55f) / 0.45f)
                pieces = pills.mapIndexed { k, r ->
                    val at = lerpRect(r, circle, merge)
                    val colour = if (merge > 0.45f || k == 2) accent else palette.ink
                    Piece(at, min(at.width, at.height) / 2, colour, hollow, RING_EDGE * px)
                }
            }
            t < T_BACK -> {
                // 3: the ring grows into a frame that reaches the screen's edges.
                val w = lerp(circle.width, screen.width, outCubic(grow * 1.35f))
                val h = lerp(circle.height, screen.height, grow)
                val centre = Offset(screen.width / 2, lerp(circle.center.y, screen.height / 2, grow))
                val r = Rect(centre.x - w / 2, centre.y - h / 2, centre.x + w / 2, centre.y + h / 2)
                val radius = lerp(circle.width / 2, SCREEN_RADIUS * px, outCubic(grow * 1.2f))
                pieces = listOf(Piece(r, radius, accent, 1f, lerp(RING_EDGE, FRAME_EDGE, grow) * px))
                window = r
                windowRadius = radius
            }
            else -> {
                // 4: the frame shrinks to a ring at the mark, then splits into the pills.
                val toCircle = clamp01(back / 0.65f)
                val split = clamp01((back - 0.65f) / 0.35f)
                val edge = lerp(FRAME_EDGE, MARK_EDGE, outCubic(toCircle)) * px
                if (split <= 0f) {
                    val r = lerpRect(full, markCircle, outCubic(toCircle))
                    pieces = listOf(Piece(r, lerp(SCREEN_RADIUS * px, markCircle.width / 2, outCubic(toCircle)), accent, 1f, edge))
                } else {
                    // The same clock the welcome page's mark runs on, so the handover doesn't jump.
                    val marks = pillRects(toP.center, MARK_SCALE, rhythmMath, SystemClock.uptimeMillis() - startedAt, px)
                    pieces = marks.mapIndexed { k, mp ->
                        val r = lerpRect(markCircle, mp, inOut(split))
                        val colour = if (split > 0.5f && k != 2) palette.ink else accent
                        Piece(r, min(r.width, r.height) / 2, colour, 1f - split, edge)
                    }
                }
            }
        }
    }
    // The welcome page: hidden until the frame opens; inside the frame while it grows
    // (plain dark while the ring is small, so no cut-off words show through), then everywhere.
    val welcomeAlpha = when {
        done -> 1f
        t < T_GROW -> 0f
        t < T_BACK -> outCubic((grow - 0.25f) / 0.35f)
        else -> 1f
    }
    val splashAlpha = when {
        t < T_GROW -> 1f - merge * 0.4f
        t < T_HOLD -> 0.6f * (1f - grow)
        else -> 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow(); screen = it.size.toSize() },
    ) {
        val clip = window
        val clipRadius = windowRadius
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = welcomeAlpha }
                .drawWithContent {
                    if (clip == null) {
                        drawContent()
                    } else {
                        val path = Path().apply { addRoundRect(RoundRect(clip, CornerRadius(clipRadius))) }
                        clipPath(path) { this@drawWithContent.drawContent() }
                    }
                },
        ) {
            Welcome(
                onSeeHow = onSeeHow,
                onSkip = onSkip,
                lockupShown = done,
                rhythm = rhythm,
                rhythmStartedAt = startedAt,
                onPills = { welcomePills = it.translate(-origin) },
                onWord = { welcomeWord = it.translate(-origin) },
            )
        }
        if (!done) {
            Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = splashAlpha }) {
                Splash(onPills = { splashPills = it.translate(-origin) }, onWord = { splashWord = it.translate(-origin) })
            }
            // The moving layer: the pills, the ring and the frame.
            Canvas(Modifier.fillMaxSize()) { pieces.forEach { drawPiece(it) } }
            // THRUM: fades under the growing frame, then flies to the lockup as the frame pulls back.
            val fromW = splashWord
            if (fromW != null) {
                val to = welcomeWord ?: fromW
                val fly = if (t >= T_BACK) outCubic((back - 0.15f) / 0.85f) else 0f
                val wordAlpha = when {
                    t < T_GROW -> 1f
                    t < T_BACK -> 1f - grow
                    else -> clamp01(back * 3f)
                }
                val centre = lerp(fromW.center, to.center, fly)
                val scale = lerp(1f, to.height / fromW.height, fly)
                Text(
                    stringResource(R.string.app_name).uppercase(),
                    style = ThrumType.splash,
                    color = ThrumAccentInk,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .offset { IntOffset((centre.x - fromW.width / 2).roundToInt(), (centre.y - fromW.height / 2).roundToInt()) }
                        .graphicsLayer { scaleX = scale; scaleY = scale; alpha = wordAlpha },
                )
            }
        }
    }
}

/**
 * The opening on every launch after the first (Mutalib, 2026-11-28: "open
 * every time"). The same first half as [SplashToWelcome]: the pills pulse,
 * merge into the hollow ring, and the ring grows into a frame with the app
 * inside it. There is no welcome page to land on, so instead of shrinking
 * back, the frame reaches the screen's edges and fades, leaving Home.
 *
 * [content] is always composed in the same place, so the app underneath
 * keeps its state when the opening ends. Remove animations skips it.
 */
@Composable
fun OpeningInto(active: Boolean, onDone: () -> Unit, content: @Composable () -> Unit) {
    val moving = motionAllowed()
    val clock = remember { Animatable(0f) }
    val rhythm = remember { Demo.rhythm() }
    val rhythmMath = remember { BarMath(rhythm.amplitudes, rhythm.stepMs) }
    val palette = LocalThrumPalette.current
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var screen by remember { mutableStateOf(Size.Zero) }
    var splashPills by remember { mutableStateOf<Rect?>(null) }
    var splashWord by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        if (moving) clock.animateTo(T_LAUNCH_END, tween(T_LAUNCH_END.toInt(), easing = LinearEasing)) else clock.snapTo(T_LAUNCH_END)
        onDone()
    }

    val t = clock.value
    val done = !active || t >= T_LAUNCH_END
    val merge = inOut((t - T_MERGE) / MERGE)
    val grow = inOut((t - T_GROW) / GROW)
    val fade = outCubic((t - T_HOLD) / LAUNCH_FADE)

    val px = with(density) { 1.dp.toPx() }
    val fromP = splashPills
    var pieces: List<Piece> = emptyList()
    var window: Rect? = null
    var windowRadius = 0f
    if (!done && fromP != null && screen != Size.Zero) {
        val circle = Rect(fromP.center, RING_D * px / 2)
        val accent = palette.accent
        if (t < T_GROW) {
            val pills = pillRects(fromP.center, 1f, rhythmMath, min(t, T_MERGE).toLong(), px)
            val hollow = clamp01((merge - 0.55f) / 0.45f)
            pieces = pills.mapIndexed { k, r ->
                val at = lerpRect(r, circle, merge)
                val colour = if (merge > 0.45f || k == 2) accent else palette.ink
                Piece(at, min(at.width, at.height) / 2, colour, hollow, RING_EDGE * px)
            }
        } else {
            val w = lerp(circle.width, screen.width, outCubic(grow * 1.35f))
            val h = lerp(circle.height, screen.height, grow)
            val centre = Offset(screen.width / 2, lerp(circle.center.y, screen.height / 2, grow))
            val r = Rect(centre.x - w / 2, centre.y - h / 2, centre.x + w / 2, centre.y + h / 2)
            val radius = lerp(circle.width / 2, SCREEN_RADIUS * px, outCubic(grow * 1.2f))
            pieces = listOf(Piece(r, radius, accent.copy(alpha = 1f - fade), 1f, lerp(RING_EDGE, FRAME_EDGE, grow) * px))
            if (t < T_HOLD) {
                window = r
                windowRadius = radius
            }
        }
    }
    val contentAlpha = when {
        done -> 1f
        t < T_GROW -> 0f
        t < T_HOLD -> outCubic((grow - 0.25f) / 0.35f)
        else -> 1f
    }
    val splashAlpha = when {
        t < T_GROW -> 1f - merge * 0.4f
        else -> 0.6f * (1f - grow)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow(); screen = it.size.toSize() },
    ) {
        val clip = window
        val clipRadius = windowRadius
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha }
                .drawWithContent {
                    if (clip == null) {
                        drawContent()
                    } else {
                        val path = Path().apply { addRoundRect(RoundRect(clip, CornerRadius(clipRadius))) }
                        clipPath(path) { this@drawWithContent.drawContent() }
                    }
                },
        ) {
            content()
        }
        if (!done) {
            Box(modifier = Modifier.fillMaxSize().background(ThrumField).graphicsLayer { alpha = splashAlpha }) {
                // No "Checking your phone's motor": that check runs on the first launch only.
                Splash(
                    onPills = { splashPills = it.translate(-origin) },
                    onWord = { splashWord = it.translate(-origin) },
                    checking = false,
                )
            }
            Canvas(Modifier.fillMaxSize()) { pieces.forEach { drawPiece(it) } }
            val word = splashWord
            if (word != null) {
                val wordAlpha = if (t < T_GROW) 1f else 1f - grow
                Text(
                    stringResource(R.string.app_name).uppercase(),
                    style = ThrumType.splash,
                    color = ThrumAccentInk,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .offset { IntOffset(word.left.roundToInt(), word.top.roundToInt()) }
                        .graphicsLayer { alpha = wordAlpha },
                )
            }
        }
    }
}


/** One shape of the opening: a pill, the ring or the frame. [hollow] 0 is filled, 1 only the edge. */
private class Piece(val rect: Rect, val radius: Float, val colour: Color, val hollow: Float, val edge: Float)

private fun DrawScope.drawPiece(p: Piece) {
    val fill = 1f - p.hollow
    if (fill > 0f) drawRoundRect(p.colour.copy(alpha = p.colour.alpha * fill), p.rect.topLeft, p.rect.size, CornerRadius(p.radius))
    val stroke = p.edge * p.hollow
    if (stroke > 0.3f) {
        // The edge sits inside the shape, as the sketch's border does.
        val inset = stroke / 2
        drawRoundRect(
            p.colour,
            Offset(p.rect.left + inset, p.rect.top + inset),
            Size(p.rect.width - stroke, p.rect.height - stroke),
            CornerRadius((p.radius - inset).coerceAtLeast(0f)),
            style = Stroke(stroke),
        )
    }
}

/**
 * The five pills around [centre] at [scale], each at its rhythm height: the same
 * numbers [Bars] draws Pills with in the splash's [PILLS_W] x [PILLS_H] box, so the
 * opening's pills and the welcome page's mark are the same shape.
 */
private fun pillRects(centre: Offset, scale: Float, math: BarMath, ms: Long, px: Float): List<Rect> {
    val scales = floatArrayOf(0.5f, 0.78f, 1f, 0.78f, 0.5f)
    val boxW = PILLS_W.value * px
    val boxH = PILLS_H.value * px
    val fit = min(1f, boxW * 0.92f / ((26f * 5 + 16f * 4) * px))
    val bw = 26f * px * fit
    val gap = 16f * px * fit
    val maxH = boxH * 0.8f
    val total = bw * 5 + gap * 4
    return scales.mapIndexed { k, sc ->
        val v = math.env(ms - abs(k - 2) * 45L)
        val h = (bw + v * sc * (maxH - bw)) * scale
        val w = bw * scale
        val x = centre.x + (-total / 2 + k * (bw + gap) + bw / 2) * scale
        Rect(x - w / 2, centre.y - h / 2, x + w / 2, centre.y + h / 2)
    }
}

private fun lerpRect(a: Rect, b: Rect, k: Float) =
    Rect(lerp(a.left, b.left, k), lerp(a.top, b.top, k), lerp(a.right, b.right, k), lerp(a.bottom, b.bottom, k))

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)

/** Ease in and out, cubic: the sketch's `ease`. */
private fun inOut(x: Float): Float {
    val k = clamp01(x)
    if (k < 0.5f) return 4 * k * k * k
    val f = -2 * k + 2
    return 1 - f * f * f / 2
}

/** Ease out, cubic: the sketch's `easeOut`. */
private fun outCubic(x: Float): Float {
    val f = 1 - clamp01(x)
    return 1 - f * f * f
}

/** The opening's timeline in ms, from the approved sketch. */
private const val SPLASH = 1200f
private const val MERGE = 500f
private const val GROW = 600f
private const val HOLD = 120f
private const val BACK = 750f
private const val T_MERGE = SPLASH
private const val T_GROW = T_MERGE + MERGE
private const val T_HOLD = T_GROW + GROW
private const val T_BACK = T_HOLD + HOLD
private const val T_END = T_BACK + BACK

/** On later launches the frame fades at the screen's edges instead of shrinking back. */
private const val LAUNCH_FADE = 250f
private const val T_LAUNCH_END = T_HOLD + LAUNCH_FADE

/** The ring's size and edges, in dp, from the sketch. */
private const val RING_D = 64f
private const val MARK_RING_D = 34f
private const val RING_EDGE = 7f
private const val FRAME_EDGE = 9f
private const val MARK_EDGE = 3.5f
private const val SCREEN_RADIUS = 30f

/** The pills' box, on the splash and (scaled to [MARK_SCALE]) on the welcome page. */
private val PILLS_W = 200.dp
private val PILLS_H = 140.dp
private const val MARK_SCALE = 0.3f

/**
 * The splash. It keeps room for the pills and the wordmark and reports where,
 * but doesn't draw them: the moving layer in [SplashToWelcome] does, from the
 * first frame, so they never change hands mid-flight.
 */
@Composable
private fun Splash(onPills: (Rect) -> Unit, onWord: (Rect) -> Unit, checking: Boolean = true) {
    val accent = ThrumAccent
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(colors = listOf(accent.copy(alpha = 0.22f), Color.Transparent), radius = 800f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Space.S5, vertical = 28.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            // Room for Pills, Thrum's own style (Mutalib, 2026-11-28: B on the
            // splash and as the default), swelling to the built-in sample
            // rhythm: there is no song of the user's yet on first launch.
            Box(Modifier.size(PILLS_W, PILLS_H).onGloballyPositioned { onPills(it.boundsInWindow()) })
            // Room for the wordmark: drawn by the moving layer, kept here for
            // its place and for screen readers.
            Text(
                stringResource(R.string.app_name).uppercase(),
                style = ThrumType.splash,
                color = ThrumAccentInk,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .padding(top = 28.dp)
                    .onGloballyPositioned { onWord(it.boundsInWindow()) }
                    .graphicsLayer { alpha = 0f }
                    .semantics { heading() },
            )
            Text(stringResource(R.string.onboarding_tagline), style = ThrumType.lead, color = ThrumInk, modifier = Modifier.padding(top = Space.S2))
            Spacer(Modifier.weight(1f))
            if (checking) Text(stringResource(R.string.onboarding_checking), style = ThrumType.meta, color = ThrumInk2)
        }
    }
}

@Composable
private fun Welcome(
    onSeeHow: () -> Unit,
    onSkip: () -> Unit,
    lockupShown: Boolean,
    rhythm: Score,
    rhythmStartedAt: Long,
    onPills: (Rect) -> Unit,
    onWord: (Rect) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Space.S5, vertical = 28.dp),
    ) {
        ThrumDots(count = 5, activeIndex = 0)
        // The pills mark beside the wordmark, where the splash's pills land.
        // Hidden while they fly, so there is never two of them.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(PILLS_W * MARK_SCALE, PILLS_H * MARK_SCALE)
                    .onGloballyPositioned { onPills(it.boundsInWindow()) }
                    .graphicsLayer { alpha = if (lockupShown) 1f else 0f },
                contentAlignment = Alignment.Center,
            ) {
                // The same rhythm clock as the splash, so the pulses carry on.
                val at = remember { SystemClock.uptimeMillis() - rhythmStartedAt }
                Bars(
                    style = BarStyle.PILLS,
                    score = rhythm,
                    positionMs = at,
                    playing = true,
                    modifier = Modifier
                        .requiredSize(PILLS_W, PILLS_H)
                        .graphicsLayer { scaleX = MARK_SCALE; scaleY = MARK_SCALE },
                )
            }
            Spacer(Modifier.width(Space.S2))
            Text(
                stringResource(R.string.app_name).uppercase(),
                style = ThrumType.wordmark,
                color = ThrumAccentInk,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .onGloballyPositioned { onWord(it.boundsInWindow()) }
                    .graphicsLayer { alpha = if (lockupShown) 1f else 0f },
            )
        }
        Text(
            // Two lines, as the screens draw it.
            stringResource(R.string.onboarding_tagline).replace(". ", ".\n"),
            style = ThrumType.hero,
            color = ThrumInk,
            modifier = Modifier
                .padding(top = 14.dp)
                .semantics { heading() },
        )
        Text(stringResource(R.string.onboarding_welcome_body), style = ThrumType.lead, color = ThrumInkSoft, modifier = Modifier.padding(top = Space.S4))
        Row(
            modifier = Modifier.padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S2),
        ) {
            ThrumIcon(name = "check", tint = ThrumAccentInk, size = 18.dp)
            Text(stringResource(R.string.onboarding_welcome_capable), style = ThrumType.meta, color = ThrumInk)
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(text = stringResource(R.string.onboarding_see_how), onClick = onSeeHow, modifier = Modifier.fillMaxWidth())
        ThrumTextButton(
            text = stringResource(R.string.onboarding_skip),
            onClick = onSkip,
            color = ThrumInk2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.S2),
        )
    }
}

/** Screen 3. Which of the two patterns is playing: 0 none, 1 Android's, 2 Thrum's. */
@Composable
private fun SoundToTouch(onNext: () -> Unit) {
    val ctx = LocalContext.current
    val buzz = remember { Demo.systemBuzz() }
    val rhythm = remember { Demo.rhythm() }
    var playing by remember { mutableIntStateOf(0) }

    // A button shows "pause" only while its pattern is really playing:
    // reset when the pattern ends, and the motor stopped when the screen goes.
    LaunchedEffect(playing) {
        val length = when (playing) {
            1 -> buzz.durationMs
            2 -> rhythm.durationMs
            else -> return@LaunchedEffect
        }
        delay(length)
        playing = 0
    }
    DisposableEffect(Unit) { onDispose { Haptics.stop(ctx) } }

    fun toggle(which: Int, score: Score) {
        if (playing == which) {
            Haptics.stop(ctx)
            playing = 0
        } else {
            playing = if (Haptics.play(ctx, score) == null) which else 0
        }
    }

    Step {
        ThrumDots(count = 5, activeIndex = 1)
        Statement(stringResource(R.string.onboarding_touch_title))
        Lead(stringResource(R.string.onboarding_touch_body))

        // How it works: a sound, the beats in it, the motor.
        ThrumCard(modifier = Modifier.padding(top = 20.dp), padding = PaddingValues(Space.S4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.S2),
            ) {
                // The new bars (2026-11-28): the sound drawn as a wave (Mirror), the
                // beats Thrum finds in it as Pills, both looping the sample rhythm.
                val loop = rememberLoopMs(rhythm)
                Column(modifier = Modifier.weight(1f)) {
                    Bars(style = BarStyle.MIRROR, score = rhythm, positionMs = loop, playing = true, compact = true, modifier = Modifier.fillMaxWidth().height(44.dp))
                    Text(stringResource(R.string.onboarding_diagram_sound), style = ThrumType.meta, color = ThrumInk2, modifier = Modifier.padding(top = 6.dp))
                }
                ThrumIcon(name = "chev", tint = ThrumInk2, size = 16.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Bars(style = BarStyle.PILLS, score = rhythm, positionMs = loop, playing = true, compact = true, modifier = Modifier.fillMaxWidth().height(44.dp))
                    Text(stringResource(R.string.onboarding_diagram_beats), style = ThrumType.meta, color = ThrumInk2, modifier = Modifier.padding(top = 6.dp))
                }
                ThrumIcon(name = "chev", tint = ThrumInk2, size = 16.dp)
                Column(modifier = Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ThrumIcon(name = "vib", tint = ThrumAccentInk, size = 34.dp)
                    Text(stringResource(R.string.onboarding_diagram_phone), style = ThrumType.meta, color = ThrumInk2, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        ThrumCard(modifier = Modifier.padding(top = Space.S3), padding = PaddingValues(Space.S4)) {
            Overline(stringResource(R.string.onboarding_feel_title))
            FeelRow(
                label = stringResource(R.string.onboarding_feel_android),
                score = buzz,
                playing = playing == 1,
                onToggle = { toggle(1, buzz) },
            )
            FeelRow(
                label = stringResource(R.string.onboarding_feel_thrum),
                score = rhythm,
                playing = playing == 2,
                onToggle = { toggle(2, rhythm) },
            )
        }
        Text(
            stringResource(R.string.onboarding_feel_note),
            style = ThrumType.meta,
            color = ThrumInk2,
            modifier = Modifier.padding(top = 10.dp),
        )

        Spacer(Modifier.height(Space.S6))
        PrimaryButton(text = stringResource(R.string.onboarding_next), onClick = onNext, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun FeelRow(label: String, score: Score, playing: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.S3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S3),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = ThrumType.meta, color = ThrumInk2)
            // Moves only while this row plays; otherwise still at its strongest beat.
            SongBars(score = score, playing = playing, positionMs = rememberLoopMs(score), modifier = Modifier.fillMaxWidth().height(36.dp))
        }
        CirclePlayButton(playing = playing, onClick = onToggle)
    }
}

@Composable
private fun Sources(onNext: () -> Unit) {
    Step {
        ThrumDots(count = 5, activeIndex = 2)
        Statement(stringResource(R.string.onboarding_sources_title))
        Lead(stringResource(R.string.onboarding_sources_body))
        ThrumCard(modifier = Modifier.padding(top = 22.dp), padding = PaddingValues(0.dp)) {
            SourceRow("phone", stringResource(R.string.onboarding_calls_row), stringResource(R.string.onboarding_calls_row_help))
            HorizontalDivider(color = ThrumRule, thickness = 1.dp)
            SourceRow("music", stringResource(R.string.onboarding_music_row), stringResource(R.string.onboarding_music_row_help))
            HorizontalDivider(color = ThrumRule, thickness = 1.dp)
            SourceRow("video", stringResource(R.string.onboarding_videos_row), stringResource(R.string.onboarding_videos_row_help))
        }
        Spacer(Modifier.height(Space.S6))
        PrimaryButton(text = stringResource(R.string.onboarding_next), onClick = onNext, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SourceRow(icon: String, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.S4, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconCircle(icon = icon, size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = ThrumType.row, color = ThrumInk)
            Text(subtitle, style = ThrumType.meta, color = ThrumInk2, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun FeelCalls(onNext: () -> Unit) {
    val ctx = LocalContext.current
    // Polled: the user leaves for system settings, grants, and comes back to
    // a screen that already knows — and says Next instead of asking again.
    val permitted by rememberPolled(hasCallAccess(ctx)) { hasCallAccess(it) }
    val mayBeLocked = remember { callAccessMayBeLocked(ctx) }
    var asked by remember { mutableStateOf(Store(ctx).callAccessAsked) }
    Step {
        ThrumDots(count = 5, activeIndex = 3)
        ThrumCard(modifier = Modifier.padding(top = Space.S5), padding = PaddingValues(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Overline(stringResource(R.string.onboarding_calls_example_head))
                ThrumIcon(name = "phone", tint = ThrumAccentInk, size = 18.dp)
            }
            // An illustration of a call's rhythm in the new bars, looping the sample.
            val callRhythm = remember { Demo.rhythm() }
            Bars(
                style = rememberBarStyle(),
                score = callRhythm,
                positionMs = rememberLoopMs(callRhythm),
                playing = true,
                modifier = Modifier.padding(top = Space.S3).fillMaxWidth().height(96.dp),
            )
            Text(
                stringResource(R.string.onboarding_calls_example_line),
                style = ThrumType.meta,
                color = ThrumInk2,
                modifier = Modifier.padding(top = Space.S2),
            )
        }
        Statement(stringResource(R.string.onboarding_calls_title), top = 24)
        Lead(stringResource(R.string.onboarding_calls_body))
        Text(
            stringResource(R.string.onboarding_calls_permission),
            style = ThrumType.meta,
            color = ThrumInk2,
            modifier = Modifier.padding(top = Space.S3),
        )
        Spacer(Modifier.height(Space.S6))
        if (permitted) {
            PrimaryButton(text = stringResource(R.string.onboarding_next), onClick = onNext, modifier = Modifier.fillMaxWidth())
        } else {
            PrimaryButton(
                text = stringResource(R.string.onboarding_calls_action),
                onClick = {
                    asked = true
                    openCallAccess(ctx)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (asked && mayBeLocked) {
                CallAccessLockedHelp(modifier = Modifier.padding(top = Space.S5))
            }
            ThrumTextButton(
                text = stringResource(R.string.onboarding_calls_later),
                onClick = onNext,
                color = ThrumInk2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.S2),
            )
        }
    }
}

@Composable
private fun FeelMusic(onNext: () -> Unit) {
    Step {
        ThrumDots(count = 5, activeIndex = 4)
        // An illustration of a rhythm, not a pretend player: there is no
        // song here to play, so there is no play button.
        ThrumCard(modifier = Modifier.padding(top = Space.S5), padding = PaddingValues(Space.S4)) {
            val musicRhythm = remember { Demo.rhythm() }
            Bars(
                style = rememberBarStyle(),
                score = musicRhythm,
                positionMs = rememberLoopMs(musicRhythm),
                playing = true,
                modifier = Modifier.fillMaxWidth().height(110.dp),
            )
        }
        Statement(stringResource(R.string.onboarding_music_title), top = 24)
        Lead(stringResource(R.string.onboarding_music_body))
        Text(
            stringResource(R.string.onboarding_music_privacy),
            style = ThrumType.meta,
            color = ThrumInk2,
            modifier = Modifier.padding(top = Space.S3),
        )
        Spacer(Modifier.height(Space.S6))
        PrimaryButton(text = stringResource(R.string.onboarding_next), onClick = onNext, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun YouAreSet(onDone: () -> Unit) {
    Step {
        // Empty, and it says so: this is where the first haptic will be.
        ThrumCard(
            modifier = Modifier.padding(top = 28.dp),
            backgroundColor = ThrumSurface,
            padding = PaddingValues(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.onboarding_done_hint),
                    style = ThrumType.body,
                    color = ThrumInk2,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Statement(stringResource(R.string.onboarding_done_title), top = 24)
        Lead(stringResource(R.string.onboarding_done_body))
        Spacer(Modifier.height(Space.S6))
        // Both finish first launch. The pick itself happens on Home, whose
        // empty calls card exists for exactly this.
        PrimaryButton(text = stringResource(R.string.onboarding_done_action), onClick = onDone, modifier = Modifier.fillMaxWidth())
        ThrumTextButton(
            text = stringResource(R.string.onboarding_done_look),
            onClick = onDone,
            color = ThrumInk2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.S2),
        )
    }
}

/**
 * The dead end. No way forward is drawn, because there is none: a motor that
 * cannot vary strength turns every rhythm back into the flat buzz the phone
 * already makes. The first UI drew the design board's note about this — "No
 * button here, on purpose" — inside a button-shaped outline.
 */
@Composable
private fun CapabilityDeadEnd(capability: Haptics.Capability) {
    Step {
        Text(stringResource(R.string.app_name).uppercase(), style = ThrumType.wordmark, color = ThrumAccentInk)
        Text(
            stringResource(R.string.blocked_title),
            style = ThrumType.statement,
            color = ThrumWarn,
            modifier = Modifier
                .padding(top = 28.dp)
                .semantics { heading() },
        )
        Lead(stringResource(R.string.blocked_body))
        Lead(stringResource(R.string.blocked_safe))
        Text(
            stringResource(
                R.string.blocked_detail,
                stringResource(if (capability.hasVibrator) R.string.phonecheck_yes else R.string.phonecheck_none),
                stringResource(if (capability.amplitudeControl) R.string.phonecheck_yes else R.string.phonecheck_no),
            ),
            style = ThrumType.meta,
            color = ThrumInk2,
            modifier = Modifier.padding(top = Space.S3),
        )
    }
}
