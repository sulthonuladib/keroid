package com.sulthonuladib.keroid.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sulthonuladib.keroid.R
import com.sulthonuladib.keroid.term.TerminalAppearance
import com.sulthonuladib.keroid.term.TerminalTheme

/**
 * Keroid's design system. The terminal theme pair is the single source of
 * truth: the app surface, chrome and terminal all derive from the same
 * Ghostty/Kero palette, so switching the pair cannot leave one surface behind.
 *
 * Defaults are Catppuccin Latte (light) and Mocha (dark), as the Doop design
 * specifies. Dynamic colour is deliberately absent.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KeroidTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = true,
    lightTheme: String = TerminalAppearance.DEFAULT_LIGHT,
    darkThemeName: String = TerminalAppearance.DEFAULT_DARK,
    content: @Composable () -> Unit,
) {
    val terminal = TerminalAppearance.resolveTheme(
        if (darkTheme) darkThemeName else lightTheme,
        darkTheme,
    )
    val scheme = rememberKeroidScheme(terminal, darkTheme)
    val colors = rememberKeroidColors(terminal, darkTheme)
    CompositionLocalProvider(
        LocalKeroidColors provides colors,
        // Text outside a Surface otherwise falls back to platform black, which
        // disappears on a dark terminal-derived card.
        androidx.compose.material3.LocalContentColor provides scheme.onSurface,
    ) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            typography = KeroidTypography,
            shapes = KeroidShapes,
            content = content,
        )
    }
}

@Composable
private fun rememberKeroidScheme(theme: TerminalTheme, dark: Boolean): ColorScheme =
    androidx.compose.runtime.remember(theme, dark) { keroidScheme(theme, dark) }

@Composable
private fun rememberKeroidColors(theme: TerminalTheme, dark: Boolean): KeroidColors =
    androidx.compose.runtime.remember(theme, dark) { keroidColors(theme, dark) }

/** Semantic roles the Material scheme cannot name. */
@Immutable
data class KeroidColors(
    val isDark: Boolean,
    val window: Color,
    val sunken: Color,
    val raised: Color,
    val high: Color,
    val quiet: Color,
    val hairline: Color,
    val attention: Color,
    val onAttention: Color,
    val success: Color,
    val danger: Color,
    val info: Color,
    val agentCodex: Color,
    val agentClaude: Color,
    val agentCodexContainer: Color,
    val agentClaudeContainer: Color,
    val progressTrack: Color,
)

val LocalKeroidColors = staticCompositionLocalOf { keroidColors(lightFallback, dark = false) }

/** `MaterialTheme.keroid`-style access for the extra roles. */
val androidx.compose.material3.MaterialTheme.keroid: KeroidColors
    @Composable @ReadOnlyComposable get() = LocalKeroidColors.current

private val lightFallback = TerminalAppearance.resolveTheme(TerminalAppearance.DEFAULT_LIGHT, false)

private fun keroidScheme(theme: TerminalTheme, dark: Boolean): ColorScheme {
    val window = Color(theme.background)
    val fg = Color(theme.foreground)
    val surface = window.shade(if (dark) 0.20f else 0.045f)
    val sunken = window.shade(if (dark) 0.30f else 0.075f)
    val raised = window.tint(if (dark) 0.055f else 0.028f)
    val high = window.tint(if (dark) 0.095f else 0.050f)
    val primary = Color(theme.palette[4])
    val primaryContainer = window.blend(primary, if (dark) 0.20f else 0.14f)
    val secondary = Color(theme.palette[6])
    val tertiary = Color(theme.palette[5])
    val error = Color(theme.palette[1])
    val muted = fg.blend(window, 0.20f)
    val quiet = fg.blend(window, 0.32f)
    val outline = fg.blend(window, 0.52f)
    val onPrimary = inkOn(primary)
    val onPrimaryContainer = inkOn(primaryContainer)
    val onSecondary = inkOn(secondary)
    val secondaryContainer = window.blend(secondary, if (dark) 0.18f else 0.16f)
    val tertiaryContainer = window.blend(tertiary, if (dark) 0.18f else 0.16f)
    val errorContainer = window.blend(error, if (dark) 0.20f else 0.18f)
    return ColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = primary.blend(window, 0.55f),
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = inkOn(secondaryContainer),
        tertiary = tertiary,
        onTertiary = inkOn(tertiary),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = inkOn(tertiaryContainer),
        background = surface,
        onBackground = fg,
        surface = surface,
        onSurface = fg,
        surfaceVariant = sunken,
        onSurfaceVariant = muted,
        surfaceTint = primary,
        inverseSurface = fg,
        inverseOnSurface = surface,
        error = error,
        onError = inkOn(error),
        errorContainer = errorContainer,
        onErrorContainer = inkOn(errorContainer),
        outline = outline,
        outlineVariant = outline.blend(surface, 0.42f),
        scrim = Color.Black,
        surfaceBright = high,
        surfaceDim = sunken,
        surfaceContainer = window,
        surfaceContainerHigh = high,
        surfaceContainerHighest = raised,
        surfaceContainerLow = window,
        surfaceContainerLowest = sunken,
    )
}

private fun keroidColors(theme: TerminalTheme, dark: Boolean): KeroidColors {
    val window = Color(theme.background)
    val fg = Color(theme.foreground)
    val attention = Color(0xFFF9E2AF)
    val success = Color(theme.palette[2])
    val danger = Color(theme.palette[1])
    val info = Color(theme.palette[6])
    val codex = Color(theme.palette[5])
    val claude = Color(theme.palette[6])
    return KeroidColors(
        isDark = dark,
        window = window,
        sunken = window.shade(if (dark) 0.30f else 0.075f),
        raised = window.tint(if (dark) 0.055f else 0.028f),
        high = window.tint(if (dark) 0.095f else 0.050f),
        quiet = fg.blend(window, 0.32f),
        hairline = if (dark) Color.White.copy(alpha = 0.07f) else Color(0xFF12142A).copy(alpha = 0.075f),
        attention = attention,
        onAttention = attention.blend(Color.Black, 0.82f),
        success = success,
        danger = danger,
        info = info,
        agentCodex = codex,
        agentClaude = claude,
        agentCodexContainer = window.blend(codex, if (dark) 0.18f else 0.16f),
        agentClaudeContainer = window.blend(claude, if (dark) 0.18f else 0.16f),
        progressTrack = fg.copy(alpha = 0.12f),
    )
}

private fun Color.blend(other: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = red * (1f - f) + other.red * f,
        green = green * (1f - f) + other.green * f,
        blue = blue * (1f - f) + other.blue * f,
        alpha = alpha * (1f - f) + other.alpha * f,
    )
}

private fun Color.shade(amount: Float) = blend(Color.Black, amount)
private fun Color.tint(amount: Float) = blend(Color.White, amount)

/** Dark ink on light fills, warm white on dark fills, chosen by measured contrast. */
private fun inkOn(background: Color): Color {
    val dark = Color(0xFF11111B)
    val light = Color(0xFFFFFFFF)
    val d = contrast(background, dark)
    val l = contrast(background, light)
    return if (d >= l) dark else light
}

private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

// ---------------------------------------------------------------------------
// Type

/** Google Sans Flex, bundled under the SIL Open Font License. */
val KeroidSans = FontFamily(
    Font(R.font.google_sans_flex_regular, FontWeight.Normal),
    Font(R.font.google_sans_flex_medium, FontWeight.Medium),
    Font(R.font.google_sans_flex_semibold, FontWeight.SemiBold),
    Font(R.font.google_sans_flex_bold, FontWeight.Bold),
)

/** JetBrains Mono is also the terminal face; UI mono labels share it. */
val KeroidMono = FontFamily.Monospace

private fun sans(
    size: Int,
    line: Int,
    weight: FontWeight,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = KeroidSans,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
)

val KeroidTypography = Typography(
    displayLarge = sans(40, 48, FontWeight.SemiBold, -0.6),
    displayMedium = sans(36, 44, FontWeight.SemiBold, -0.5),
    displaySmall = sans(32, 40, FontWeight.SemiBold, -0.44),
    headlineLarge = sans(32, 40, FontWeight.SemiBold, -0.44),
    headlineMedium = sans(28, 36, FontWeight.SemiBold, -0.4),
    headlineSmall = sans(22, 28, FontWeight.SemiBold, -0.3),
    titleLarge = sans(20, 26, FontWeight.SemiBold, -0.24),
    titleMedium = sans(16, 22, FontWeight.SemiBold, -0.16),
    titleSmall = sans(14, 20, FontWeight.SemiBold, -0.1),
    bodyLarge = sans(15, 21, FontWeight.Normal),
    bodyMedium = sans(13, 19, FontWeight.Normal),
    bodySmall = sans(11, 16, FontWeight.Normal),
    labelLarge = sans(13, 18, FontWeight.SemiBold),
    labelMedium = sans(11, 14, FontWeight.SemiBold),
    labelSmall = sans(10, 13, FontWeight.SemiBold),
)

// ---------------------------------------------------------------------------
// Shape

val KeroidShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
