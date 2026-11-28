package com.mosman.thrum

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import kotlin.math.sin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.border
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
    // Saved, so turning the phone or changing the theme mid-way doesn't restart it (2026-11-28).
    var screen by rememberSaveable { mutableIntStateOf(0) }

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
            screen == 2 -> SoundToTouch(onNext = { screen = 3 }, onSkip = onDone)
            screen == 3 -> Sources(onNext = { screen = 4 }, onSkip = onDone)
            screen == 4 -> FeelCalls(onNext = { screen = 5 })
            screen == 5 -> FeelMusic(onNext = { screen = 6 }, onSkip = onDone)
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
 * pick B, "burst" (2026-11-28, scratch/opening-c.html):
 *
 * 1. The splash's five pills pulse to the sample rhythm.
 * 2. They burst into the welcome page's dotted wave: every dot of the wave
 *    starts inside one of the pills and flies to its place, sweeping left to
 *    right, while THRUM glides up to its spot beside the mark.
 * 3. The welcome page's words fade in under the wave.
 *
 * One layer draws the pills and the flying dots; when the last dot lands, the
 * welcome page's own wave takes over at exactly the same positions, on the same
 * ripple clock, so nothing jumps.
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
    var origin by remember { mutableStateOf(Offset.Zero) }
    var splashPills by remember { mutableStateOf<Rect?>(null) }
    var splashWord by remember { mutableStateOf<Rect?>(null) }
    var welcomeWord by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(Unit) {
        if (clock.value >= T_END) return@LaunchedEffect
        // The check has already run; the splash exists so the first thing the
        // app ever does is say what it is doing.
        if (moving) clock.animateTo(T_END, tween(T_END.toInt(), easing = LinearEasing)) else clock.snapTo(T_END)
        onArrived()
    }

    // The clock is read only inside drawing and layout lambdas, never while
    // composing: each frame then redraws, rather than rebuilding the welcome
    // page underneath (on the Pixel that rebuild made frames late).
    val done by remember { derivedStateOf { clock.value >= T_END } }
    val onWord: (Rect) -> Unit = remember { { welcomeWord = it.translate(-origin) } }
    var screen by remember { mutableStateOf(IntSize.Zero) }
    WarmWave(screen)
    Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow(); screen = it.size }) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (done) 1f else clamp01((clock.value - SPLASH - 1100f) / 300f) }
                // Not drawn at all until it starts to show: drawing it hidden cost every frame.
                .drawWithContent { if (done || clock.value >= SPLASH + 1100f) drawContent() },
        ) {
            Welcome(
                onSeeHow = onSeeHow,
                onSkip = onSkip,
                landed = done,
                rhythm = rhythm,
                rhythmStartedAt = startedAt,
                onWord = onWord,
            )
        }
        if (!done) {
            Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - clamp01((clock.value - SPLASH) / BURST * 3f) }) {
                Splash(onPills = { splashPills = it.translate(-origin) }, onWord = { splashWord = it.translate(-origin) })
            }
            BurstLayer(time = { clock.value }, pills = splashPills, rhythmMath = rhythmMath, waveFraction = WELCOME_WAVE)
            // THRUM glides from the splash's spot to beside the mark.
            val fromW = splashWord
            if (fromW != null) {
                val to = welcomeWord ?: fromW
                fun fly() = outCubic((clock.value - SPLASH - 500f) / 700f)
                Text(
                    stringResource(R.string.app_name).uppercase(),
                    style = ThrumType.splash,
                    color = ThrumAccentInk,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .offset {
                            val centre = lerp(fromW.center, to.center, fly())
                            IntOffset((centre.x - fromW.width / 2).roundToInt(), (centre.y - fromW.height / 2).roundToInt())
                        }
                        .graphicsLayer {
                            val scale = lerp(1f, to.height / fromW.height, fly())
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
        }
    }
}

/**
 * The pills, then their burst into the wave. [time] reads the opening's clock,
 * only while drawing; [waveFraction] is how tall the wave the dots fly into is,
 * as a share of the screen.
 */
@Composable
private fun BurstLayer(time: () -> Float, pills: Rect?, rhythmMath: BarMath, waveFraction: Float) {
    val palette = LocalThrumPalette.current
    val density = LocalDensity.current
    val (accent, dark) = dotWaveColour()
    val flying = remember { DotBatches(FLY_LEVELS * 2, DotWave.COUNT) }
    // Worked out once per screen size: where each dot lands, and the landed wave as one picture.
    val cache = remember { BurstCache() }
    // Each dot's start inside its pill, and its delay: the same every launch.
    val seeds = remember {
        Triple(
            FloatArray(DotWave.COUNT) { seeded(it) },
            FloatArray(DotWave.COUNT) { seeded(it + 9) },
            FloatArray(DotWave.COUNT) { n -> columnOf(n) * SWEEP + seeded(n + 3) * SCATTER },
        )
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = time()
        val from = pills ?: return@Canvas
        val px = with(density) { 1.dp.toPx() }
        val ink = palette.ink
        val shapes = pillRects(from.center, 1f, rhythmMath, min(t, SPLASH).toLong(), px)
        if (t < SPLASH) {
            shapes.forEachIndexed { k, r ->
                drawRoundRect(if (k == 2) accent else ink, r.topLeft, r.size, CornerRadius(min(r.width, r.height) / 2))
            }
            return@Canvas
        }
        val w = size.width
        // Whole pixels, as the welcome page's own wave is laid out, so they share one picture.
        val waveH = (size.height * waveFraction).roundToInt().toFloat()
        cache.prepare(w, waveH, accent)
        val target = cache.target
        val burst = (t - SPLASH) / BURST
        // The sky comes in as the dots leave the pills.
        if (dark) drawWaveSky(clamp01(burst * 1.5f), height = waveH)

        // Columns where every dot has landed show as the finished picture, uncovered
        // left to right with the sweep. Each column's last dot lands at its delay's
        // upper end plus the flight time.
        val landedTo = (burst - SCATTER - FLIGHT) / SWEEP
        if (landedTo > 0f) {
            val edge = if (landedTo >= 1f) w else {
                // The leftmost screen x any not-yet-landed column can reach.
                if (landedTo < 0.5f) w / 2f + (landedTo - 0.5f) * w * 1.25f else w / 2f + (landedTo - 0.5f) * w
            }
            clipRect(right = edge, bottom = waveH) { drawImage(cache.picture!!) }
        }
        if (landedTo >= 1f) return@Canvas

        // Everything else is still flying (or waiting at its spot for its column).
        val (sx, sy, delay) = seeds
        val levels = FLY_LEVELS
        flying.clear()
        for (n in 0 until DotWave.COUNT) {
            if (columnOf(n) <= landedTo) continue
            if (DotWave.brightness[n] <= 0.01f) continue
            val q = outCubic((burst - delay[n]) / FLIGHT)
            val pill = n % 5
            val r = shapes[pill]
            val level = (q * levels).toInt().coerceIn(0, levels - 1)
            flying.add(
                (if (pill == 2) levels else 0) + level,
                lerp(r.left + sx[n] * r.width, target[n * 2], q),
                lerp(r.top + sy[n] * r.height, target[n * 2 + 1], q),
            )
        }
        val dot = DotWave.radius(DotWave.ROWS / 2, w) * 2f
        drawIntoCanvas { canvas ->
            for (i in 0 until levels * 2) {
                val q = (i % levels + 0.5f) / levels
                val start = if (i >= levels) accent else ink
                val colour = androidx.compose.ui.graphics.lerp(start, accent, q).copy(alpha = lerp(1f, 0.7f, q))
                flying.draw(canvas.nativeCanvas, i, colour, lerp(dot * 1.5f, dot * 0.9f, q))
            }
        }
    }
}

/** Where the burst's dots land, and the landed wave as a picture, for one screen size and colour. */
private class BurstCache {
    val target = FloatArray(DotWave.COUNT * 2)
    var picture: ImageBitmap? = null
    private var w = -1f
    private var h = -1f
    private var colour = Color.Unspecified

    fun prepare(width: Float, height: Float, wave: Color) {
        if (width == w && height == h && wave == colour && picture != null) return
        DotWave.positions(width, height, target)
        picture = WavePictures.get(width, height, wave)
        w = width
        h = height
        colour = wave
    }
}

/** Paints the wave's picture in the background while the splash shows, so the burst never waits for it. */
@Composable
private fun WarmWave(screen: IntSize) {
    val (colour, _) = dotWaveColour()
    LaunchedEffect(screen, colour) {
        if (screen == IntSize.Zero) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            WavePictures.get(screen.width.toFloat(), (screen.height * WELCOME_WAVE).roundToInt().toFloat(), colour)
        }
    }
}

/** A dot's column, 0 at the left to 1 at the right. */
private fun columnOf(n: Int) = (n % DotWave.COLS) / (DotWave.COLS - 1f)

/** The burst's timing, as shares of [BURST]: the left-to-right sweep, each dot's random extra delay, and its flight. */
private const val SWEEP = 0.35f
private const val SCATTER = 0.15f
private const val FLIGHT = 0.55f

/** How many steps of "how far along" the flying dots are drawn in. */
private const val FLY_LEVELS = 10

/** A repeatable "random" number from 0 to 1, so every launch bursts the same way. */
private fun seeded(n: Int): Float {
    val x = sin(n * 127.1f) * 43758.547f
    return x - kotlin.math.floor(x)
}

/**
 * The opening on every launch after the first (Mutalib, 2026-11-28: "open
 * every time"), in the same burst: the pills pulse and burst into the wave.
 * There is no welcome page to land on, so the wave then fades, leaving Home.
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
    var origin by remember { mutableStateOf(Offset.Zero) }
    var splashPills by remember { mutableStateOf<Rect?>(null) }
    var splashWord by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        if (moving) clock.animateTo(T_LAUNCH_END, tween(T_LAUNCH_END.toInt(), easing = LinearEasing)) else clock.snapTo(T_LAUNCH_END)
        onDone()
    }

    // Read only inside drawing lambdas, so the app underneath isn't rebuilt every frame.
    val finished by remember { derivedStateOf { clock.value >= T_LAUNCH_END } }
    val done = !active || finished
    fun fade() = clamp01((clock.value - SPLASH - BURST) / LAUNCH_FADE)
    var screen by remember { mutableStateOf(IntSize.Zero) }
    if (!done) WarmWave(screen)

    Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow(); screen = it.size }) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (done) 1f else outCubic(fade()) }
                // Home is ready underneath but not drawn until it starts to show.
                .drawWithContent { if (done || clock.value >= SPLASH + BURST) drawContent() },
        ) { content() }
        if (!done) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - fade() }
                    .background(ThrumField),
            ) {
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - clamp01((clock.value - SPLASH) / BURST * 3f) }) {
                    // No "Checking your phone's motor": that check runs on the first launch only.
                    Splash(
                        onPills = { splashPills = it.translate(-origin) },
                        onWord = { splashWord = it.translate(-origin) },
                        checking = false,
                    )
                }
                BurstLayer(time = { clock.value }, pills = splashPills, rhythmMath = rhythmMath, waveFraction = WELCOME_WAVE)
                val word = splashWord
                if (word != null) {
                    Text(
                        stringResource(R.string.app_name).uppercase(),
                        style = ThrumType.splash,
                        color = ThrumAccentInk,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .offset { IntOffset(word.left.roundToInt(), word.top.roundToInt()) }
                            .graphicsLayer { alpha = 1f - clamp01(((clock.value - SPLASH) / BURST - 0.4f) / 0.5f) },
                    )
                }
            }
        }
    }
}

/**
 * The five pills around [centre] at [scale], each at its rhythm height: the same
 * numbers [Bars] draws Pills with in the splash's [PILLS_W] x [PILLS_H] box.
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

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)

/** Ease out, cubic: fast at the start, settling gently. */
private fun outCubic(x: Float): Float {
    val f = 1 - clamp01(x)
    return 1 - f * f * f
}

/** The opening's timeline in ms, from the approved sketch. */
private const val SPLASH = 1200f
private const val BURST = 1300f
private const val T_END = SPLASH + 1400f

/** On later launches the landed wave fades into Home. */
private const val LAUNCH_FADE = 350f
private const val T_LAUNCH_END = SPLASH + BURST + LAUNCH_FADE

/** How tall the welcome page's wave is, as a share of the screen: the sketch's 430 of 650. */
private const val WELCOME_WAVE = 0.66f

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

/**
 * One onboarding screen in direction C (Mutalib, 2026-11-28): the dotted wave
 * across the top, the progress dots in the middle above it, the words from
 * [textTop] (a share of the screen's height) down, and the two choices pinned
 * at the bottom: the way out on the left, the main button on the right with ›.
 * [art] sits over the wave (the cards some screens show there).
 */
@Composable
private fun WaveStep(
    step: Int?,
    textTop: Float,
    left: String?,
    onLeft: () -> Unit,
    right: String,
    onRight: () -> Unit,
    waveFraction: Float = OTHER_WAVE,
    waveAlpha: Float = 1f,
    art: @Composable BoxScope.(screenHeight: Dp) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val h = maxHeight
        DotWaveBackground(modifier = Modifier.fillMaxWidth().height(h * waveFraction), alpha = waveAlpha)
        art(h)
        if (step != null) {
            ThrumDots(
                count = 5,
                activeIndex = step,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(top = Space.S3),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(start = Space.S5, end = Space.S5, top = h * textTop, bottom = FOOT_ROOM),
            content = content,
        )
        StepFoot(
            left = left,
            onLeft = onLeft,
            right = right,
            onRight = onRight,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Bottom of every onboarding screen: the way out on the left, the main button on the right with ›. */
@Composable
private fun StepFoot(left: String?, onLeft: () -> Unit, right: String, onRight: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, ThrumField, ThrumField)))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(start = Space.S3, end = Space.S4, top = Space.S5, bottom = Space.S4),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (left != null) {
            ThrumTextButton(text = left, onClick = onLeft, color = ThrumInk2)
        } else {
            Spacer(Modifier.width(1.dp))
        }
        Spacer(Modifier.width(Space.S2))
        PrimaryButton(text = right, onClick = onRight, trailingIcon = "chev")
    }
}

/** Room under the words for the pinned foot, so the last line never hides behind it. */
private val FOOT_ROOM = 104.dp

/** How tall the wave is on the screens after the welcome page: a little shorter, leaving room for more words. */
private const val OTHER_WAVE = 0.52f

@Composable
private fun Welcome(
    onSeeHow: () -> Unit,
    onSkip: () -> Unit,
    landed: Boolean,
    rhythm: Score,
    rhythmStartedAt: Long,
    onWord: (Rect) -> Unit,
) {
    WaveStep(
        step = 0,
        textTop = 0.50f,
        left = stringResource(R.string.onboarding_skip),
        onLeft = onSkip,
        right = stringResource(R.string.onboarding_see_how),
        onRight = onSeeHow,
        waveFraction = WELCOME_WAVE,
        // The opening draws the wave while its dots fly in, then hands it over.
        waveAlpha = if (landed) 1f else 0f,
    ) {
        // The lockup: the pills mark beside THRUM, just above the title.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(PILLS_W * MARK_SCALE, PILLS_H * MARK_SCALE), contentAlignment = Alignment.Center) {
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
                    // Hidden while the opening's THRUM glides here, so there is never two.
                    .graphicsLayer { alpha = if (landed) 1f else 0f },
            )
        }
        Text(
            // Two lines, as the screens draw it.
            stringResource(R.string.onboarding_tagline).replace(". ", ".\n"),
            style = ThrumType.hero,
            color = ThrumInk,
            modifier = Modifier
                .padding(top = Space.S2)
                .semantics { heading() },
        )
        Text(stringResource(R.string.onboarding_welcome_body), style = ThrumType.lead, color = ThrumInkSoft, modifier = Modifier.padding(top = Space.S3))
        Row(
            modifier = Modifier.padding(top = Space.S4),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Space.S2),
        ) {
            ThrumIcon(name = "check", tint = ThrumAccentInk, size = 18.dp)
            Text(stringResource(R.string.onboarding_welcome_capable), style = ThrumType.meta, color = ThrumInk)
        }
    }
}

/** Screen 2. Which of the two patterns is playing: 0 none, 1 Android's, 2 Thrum's. */
@Composable
private fun SoundToTouch(onNext: () -> Unit, onSkip: () -> Unit) {
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

    WaveStep(
        step = 1,
        textTop = 0.40f,
        left = stringResource(R.string.onboarding_skip),
        onLeft = onSkip,
        right = stringResource(R.string.onboarding_next),
        onRight = onNext,
        art = { screenH ->
            // The sound becomes beats: the wave on the left, the pills it turns into on the right.
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = screenH * 0.13f, end = Space.S5),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.onboarding_diagram_beats), style = ThrumType.meta, color = ThrumInk2)
                Bars(
                    style = BarStyle.PILLS,
                    score = rhythm,
                    positionMs = rememberLoopMs(rhythm),
                    playing = true,
                    modifier = Modifier.padding(top = Space.S2).size(120.dp, 90.dp),
                )
            }
            Text(
                stringResource(R.string.onboarding_diagram_sound),
                style = ThrumType.meta,
                color = ThrumInk2,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = screenH * 0.13f, start = Space.S5),
            )
        },
    ) {
        Statement(stringResource(R.string.onboarding_touch_title), top = 0)
        Lead(stringResource(R.string.onboarding_touch_body))
        Text(
            stringResource(R.string.onboarding_feel_title),
            style = ThrumType.row,
            color = ThrumInkSoft,
            modifier = Modifier.padding(top = Space.S4),
        )
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
        Text(
            stringResource(R.string.onboarding_feel_note),
            style = ThrumType.meta,
            color = ThrumInk2,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun FeelRow(label: String, score: Score, playing: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.S2)
            .clip(RoundedCornerShape(Radius.large))
            .background(ThrumSurface)
            .padding(horizontal = Space.S3, vertical = Space.S2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S3),
    ) {
        CirclePlayButton(playing = playing, onClick = onToggle)
        Text(label, style = ThrumType.meta, color = ThrumInk, modifier = Modifier.width(96.dp))
        // Moves only while this row plays; otherwise still at its strongest beat.
        SongBars(score = score, playing = playing, positionMs = rememberLoopMs(score), modifier = Modifier.weight(1f).height(32.dp))
    }
}

@Composable
private fun Sources(onNext: () -> Unit, onSkip: () -> Unit) {
    WaveStep(
        step = 2,
        textTop = 0.38f,
        left = stringResource(R.string.onboarding_skip),
        onLeft = onSkip,
        right = stringResource(R.string.onboarding_next),
        onRight = onNext,
    ) {
        Statement(stringResource(R.string.onboarding_sources_title), top = 0)
        Lead(stringResource(R.string.onboarding_sources_body))
        Column(modifier = Modifier.padding(top = Space.S3)) {
            SourceRow("phone", stringResource(R.string.onboarding_calls_row), stringResource(R.string.onboarding_calls_row_help))
            HorizontalDivider(color = ThrumRule, thickness = 1.dp)
            SourceRow("music", stringResource(R.string.onboarding_music_row), stringResource(R.string.onboarding_music_row_help))
            HorizontalDivider(color = ThrumRule, thickness = 1.dp)
            SourceRow("video", stringResource(R.string.onboarding_videos_row), stringResource(R.string.onboarding_videos_row_help))
        }
    }
}

@Composable
private fun SourceRow(icon: String, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.S3),
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
    val callRhythm = remember { Demo.rhythm() }
    WaveStep(
        step = 3,
        textTop = 0.44f,
        // "Maybe later" is the way out here; once access is on there's nothing to put off.
        left = if (permitted) null else stringResource(R.string.onboarding_calls_later),
        onLeft = onNext,
        right = stringResource(if (permitted) R.string.onboarding_next else R.string.onboarding_calls_action),
        onRight = {
            if (permitted) {
                onNext()
            } else {
                asked = true
                openCallAccess(ctx)
            }
        },
        art = { screenH ->
            // An incoming call, over the wave: the song's beat on the motor.
            ThrumCard(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = screenH * 0.24f, start = Space.S6, end = Space.S6),
                padding = PaddingValues(horizontal = Space.S4, vertical = Space.S3),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S3)) {
                    IconCircle(icon = "phone", size = 36.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.onboarding_calls_example_head), style = ThrumType.row, color = ThrumInk)
                        Text(stringResource(R.string.onboarding_calls_example_line), style = ThrumType.meta, color = ThrumInk2)
                    }
                    Bars(
                        style = rememberBarStyle(),
                        score = callRhythm,
                        positionMs = rememberLoopMs(callRhythm),
                        playing = true,
                        compact = true,
                        modifier = Modifier.size(52.dp, 40.dp),
                    )
                }
            }
        },
    ) {
        Statement(stringResource(R.string.onboarding_calls_title), top = 0)
        Lead(stringResource(R.string.onboarding_calls_body))
        if (!permitted) {
            // The reassurance sits right above the button it is about (2026-11-28).
            Text(
                stringResource(R.string.onboarding_calls_permission),
                style = ThrumType.meta,
                color = ThrumInk2,
                modifier = Modifier.padding(top = Space.S4),
            )
            if (asked && mayBeLocked) {
                CallAccessLockedHelp(modifier = Modifier.padding(top = Space.S4))
            }
        }
    }
}

@Composable
private fun FeelMusic(onNext: () -> Unit, onSkip: () -> Unit) {
    val musicRhythm = remember { Demo.rhythm() }
    WaveStep(
        step = 4,
        textTop = 0.46f,
        left = stringResource(R.string.onboarding_skip),
        onLeft = onSkip,
        right = stringResource(R.string.onboarding_next),
        onRight = onNext,
        art = { screenH ->
            // An illustration of a rhythm, not a pretend player: there is no
            // song here to play, so there is no play button.
            ThrumCard(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = screenH * 0.26f, start = Space.S6, end = Space.S6),
                padding = PaddingValues(horizontal = Space.S4, vertical = Space.S3),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S3)) {
                    IconCircle(icon = "music", size = 36.dp)
                    Bars(
                        style = rememberBarStyle(),
                        score = musicRhythm,
                        positionMs = rememberLoopMs(musicRhythm),
                        playing = true,
                        compact = true,
                        modifier = Modifier.weight(1f).height(40.dp),
                    )
                }
            }
        },
    ) {
        Statement(stringResource(R.string.onboarding_music_title), top = 0)
        Lead(stringResource(R.string.onboarding_music_body))
        Text(
            stringResource(R.string.onboarding_music_privacy),
            style = ThrumType.meta,
            color = ThrumInk2,
            modifier = Modifier.padding(top = Space.S3),
        )
    }
}

@Composable
private fun YouAreSet(onDone: () -> Unit) {
    WaveStep(
        step = null,
        textTop = 0.50f,
        // Both finish first launch. The pick itself happens on Home, whose
        // empty calls card exists for exactly this.
        left = stringResource(R.string.onboarding_done_look),
        onLeft = onDone,
        right = stringResource(R.string.onboarding_done_action),
        onRight = onDone,
        art = { screenH ->
            // Empty, and it says so: this is where the first haptic will be.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = screenH * 0.12f, start = Space.S6, end = Space.S6)
                    .fillMaxWidth()
                    .heightIn(min = 100.dp)
                    .border(1.5.dp, ThrumRule, RoundedCornerShape(Radius.large)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.onboarding_done_hint),
                    style = ThrumType.body,
                    color = ThrumInk2,
                    textAlign = TextAlign.Center,
                )
            }
        },
    ) {
        Statement(stringResource(R.string.onboarding_done_title), top = 0)
        Lead(stringResource(R.string.onboarding_done_body))
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
