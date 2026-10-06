package com.mosman.thrum

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Thrum's visual system. Colour world: **sulphur-concrete** — wet board-marked
 * concrete in a stairwell with a sulphur-yellow safety line painted across it.
 *
 * Chosen because the language of *machinery that moves* is exactly this product:
 * a motor throwing mass around inside a phone. Safety yellow is what people
 * paint on things that vibrate.
 *
 * Every pair below was computed by the palette engine rather than eyeballed. The
 * one that matters most: **the yellow is 1.53:1 on the light field and can never
 * be text there.** In light mode it is a fill with dark ink on top (9.68:1), and
 * [LightAccentInk] is the darkened equivalent used for words.
 */

// --- Dark: the palette's own roles. ---------------------------------------
private val Field = Color(0xFF1C1C1A)
private val Field2 = Color(0xFF242422)
private val SurfaceRaised = Color(0xFF2B2B28)
private val Ink = Color(0xFFEFEFEA) // 14.80:1 on field
private val Ink2 = Color(0xFFA8A8A1) // 7.14:1 on field
private val Rule = Color(0xFF3A3A36)
private val Accent = Color(0xFFD8C513) // 9.68:1 on field
private val Warn = Color(0xFFE0691C) // 5.04:1 on field
private val Support = Color(0xFF59A1D4) // 6.07:1 on field

// Three more from the final screens' own tokens (`docs/design/screens/
// thrum-screens.html`), which the screens were drawn with: the softer ink of
// a lead paragraph, the outline of a secondary button, and the tab bar, a
// step darker than the page.
private val InkSoft = Color(0xFFD6D6CF)
private val Line = Color(0xFF4A4A44)
private val TabBar = Color(0xFF1A1A18)

// --- Light: the same world in daylight, designed rather than inverted. -----
private val LightField = Color(0xFFEFEFEA)
private val LightField2 = Color(0xFFE4E4DE)
private val LightSurface = Color(0xFFF7F7F2)
private val LightInk = Color(0xFF1C1C1A) // 14.80:1
private val LightInk2 = Color(0xFF55554F) // 6.51:1
private val LightRule = Color(0xFFD2D2CA)
private val LightAccentInk = Color(0xFF6B6109) // 5.45:1 — the raw yellow fails here
private val LightWarn = Color(0xFFA8552C) // 4.55:1
private val LightSupport = Color(0xFF2C5F87)

/**
 * The palette as Thrum's screens use it, in whichever light the phone is in.
 *
 * Every screen reads its colours from here — through the `Thrum*` names in
 * `Components.kt` — so following the phone's light or dark setting (PROFILE
 * §4 item 13) is one decision in one place. The UI first drew on this was
 * dark only, with raw colours in every file; on a phone in light mode the
 * status bar's dark icons sat on a dark page.
 *
 * **Two yellows, on purpose.** [accent] is the sulphur yellow as a *fill* —
 * a button, a switch, a progress bar — with [onAccent] on top, and it is the
 * same in both modes. [accentInk] is the yellow as *words, icons and ribbon
 * bars* on the page: the raw yellow in the dark, and the darkened one in the
 * light, because the raw yellow is 1.53:1 on the light field and can never
 * be read there.
 *
 * No value here is new: dark comes from the palette and the final screens,
 * light from the light roles above, which the palette engine computed.
 */
@Immutable
data class ThrumPalette(
    val dark: Boolean,
    val field: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val inkSoft: Color,
    val ink2: Color,
    val rule: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val accentInk: Color,
    val warn: Color,
    /**
     * Words on a warning tint. The orange itself in the dark; in the light,
     * the orange on its own pale tint is 3.7:1, under the 4.5 small text
     * needs, so the words go ink and the icon beside them carries the colour.
     */
    val warnText: Color,
    val support: Color,
    val tabBar: Color,
)

private val DarkPalette = ThrumPalette(
    dark = true,
    field = Field,
    surface = Field2,
    surface2 = SurfaceRaised,
    ink = Ink,
    inkSoft = InkSoft,
    ink2 = Ink2,
    rule = Rule,
    line = Line,
    accent = Accent,
    onAccent = Field,
    accentInk = Accent,
    warn = Warn,
    warnText = Warn,
    support = Support,
    tabBar = TabBar,
)

private val LightPalette = ThrumPalette(
    dark = false,
    field = LightField,
    surface = LightSurface,
    surface2 = LightField2,
    ink = LightInk,
    inkSoft = LightInk,
    ink2 = LightInk2,
    rule = LightRule,
    line = LightRule,
    accent = Accent,
    onAccent = LightInk,
    accentInk = LightAccentInk,
    warn = LightWarn,
    warnText = LightInk,
    support = LightSupport,
    tabBar = LightField2,
)

val LocalThrumPalette = staticCompositionLocalOf { DarkPalette }

private val DarkScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Field,
    primaryContainer = Accent,
    onPrimaryContainer = Field,
    secondary = Support,
    onSecondary = Field,
    background = Field,
    onBackground = Ink,
    surface = Field2,
    onSurface = Ink,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = Ink2,
    outline = Rule,
    outlineVariant = Rule,
    error = Warn,
    onError = Field,
    errorContainer = SurfaceRaised,
    onErrorContainer = Warn,
)

private val LightScheme = lightColorScheme(
    primary = LightAccentInk,
    onPrimary = LightField,
    primaryContainer = Accent,
    onPrimaryContainer = LightInk,
    secondary = LightSupport,
    onSecondary = LightField,
    background = LightField,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightField2,
    onSurfaceVariant = LightInk2,
    outline = LightRule,
    outlineVariant = LightRule,
    error = LightWarn,
    onError = LightField,
    errorContainer = LightField2,
    onErrorContainer = LightWarn,
)

/**
 * Space Grotesk carries identity, IBM Plex Sans carries reading.
 *
 * Both bundled: the app has no `INTERNET` permission, so downloadable fonts are
 * not available — and shipping the system face as the only face is how an
 * Android app announces that nobody chose anything.
 */
@OptIn(ExperimentalTextApi::class)
private val Display = FontFamily(
    Font(
        R.font.space_grotesk,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.space_grotesk,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

@OptIn(ExperimentalTextApi::class)
private val Body = FontFamily(
    Font(
        R.font.plex_sans_regular,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.plex_sans_regular,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.plex_sans_regular,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
)

/** ~1.25 ratio: 12 · 14 · 16 · 20 · 25 · 31 · 39. Body never below 16sp. */
private val AppType = Typography(
    displayLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 39.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 31.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Medium,
        fontSize = 25.sp, lineHeight = 32.sp, letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Medium,
        fontSize = 20.sp, lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
    ),
)

/**
 * The text styles the screens use, by role.
 *
 * The sizes are the final screens' own (`thrum-screens.html`), so the app
 * still looks like what was agreed. What this adds is the faces: the UI first
 * drawn on these screens set a size on every line and nothing else, so every
 * heading came out in the reading face and Space Grotesk was never seen.
 * Headings here are Space Grotesk; everything read is IBM Plex Sans.
 */
object ThrumType {
    /** The splash's wordmark. */
    val splash = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 46.sp, letterSpacing = 4.sp)

    /** "Hear it. Feel it." on the welcome screen. */
    val hero = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 39.sp, lineHeight = 44.sp)

    /** A screen's statement: "Music", "Turn sound into touch". */
    val statement = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 31.sp, lineHeight = 36.sp)

    /** The small wordmark at the top of Home and first launch. */
    val wordmark = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 25.sp, letterSpacing = 3.sp)

    /** A song's name on its page, a sheet's question. */
    val heading = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 25.sp, lineHeight = 30.sp)

    /** A card's headline: "No song for calls", the call song's name. */
    val title = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp)

    /** The paragraph under a statement. Never under 16 sp. */
    val lead = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)

    /** A list row's name, a setting's name. */
    val row = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 15.5.sp, lineHeight = 21.sp)

    val body = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)

    /** The second line of a row, a note under a control. */
    val meta = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 18.sp)

    /** Small capitals over a section: "FOR CALLS". */
    val overline = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.4.sp)

    val button = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    val buttonSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    val chip = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    val tab = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 11.5.sp)

    /** The big number in the tap test. */
    val figure = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 25.sp)
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(Radius.small),
    medium = RoundedCornerShape(Radius.medium),
    large = RoundedCornerShape(Radius.large),
)

/**
 * @param wallpaperColours Material You. **Off by default, and that is deliberate.**
 *   Handing this app's identity to whatever wallpaper is set reproduces the
 *   baseline-purple look on a purple wallpaper, and would make the armed and
 *   blocked states wallpaper-derived — the two things a user most has to read
 *   correctly must not change colour because someone changed their background.
 *   Offered as a switch, never assumed. `compose-m3.md`: dynamic colour always
 *   behind a brand fallback, semantic colours fixed.
 */
@Composable
fun ThrumTheme(
    dark: Boolean = isSystemInDarkTheme(),
    wallpaperColours: Boolean = false,
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val scheme = when {
        // minSdk is 31 (Android 12), so wallpaper colours always exist.
        wallpaperColours ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)

        dark -> DarkScheme
        else -> LightScheme
    }
    CompositionLocalProvider(LocalThrumPalette provides if (dark) DarkPalette else LightPalette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppType,
            shapes = AppShapes,
            content = content,
        )
    }
}
