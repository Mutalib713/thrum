package com.mosman.thrum

import android.os.Build
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

// --- Light: the same world in daylight, designed rather than inverted. -----
private val LightField = Color(0xFFEFEFEA)
private val LightField2 = Color(0xFFE4E4DE)
private val LightSurface = Color(0xFFF7F7F2)
private val LightInk = Color(0xFF1C1C1A) // 14.80:1
private val LightInk2 = Color(0xFF55554F) // 6.51:1
private val LightRule = Color(0xFFD2D2CA)
private val LightAccentInk = Color(0xFF6B6109) // 5.45:1 — the raw yellow fails here
private val LightWarn = Color(0xFFA8552C) // 4.55:1

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
    secondary = Color(0xFF2C5F87),
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
        wallpaperColours && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)

        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = AppType,
        shapes = AppShapes,
        content = content,
    )
}
