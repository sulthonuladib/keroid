package com.sulthonuladib.keroid.term

import androidx.compose.ui.text.font.FontFamily

/**
 * The terminal's own look: which Ghostty theme each appearance uses, and the
 * terminal font. It never affects the app's Material appearance — the terminal
 * follows the app's light/dark mode only to choose between the two themes, as
 * desktop Kero picks its light or dark terminal theme for the window.
 *
 * Defaults match the Keroid design system: Catppuccin Latte for light and
 * Mocha for dark. The same pair also colours the app's own interface, so one
 * choice cannot leave the terminal and the chrome in different palettes.
 */
data class TerminalAppearance(
    /** Theme name for a light app appearance. */
    val lightTheme: String = DEFAULT_LIGHT,
    /** Theme name for a dark app appearance. */
    val darkTheme: String = DEFAULT_DARK,
    val font: TerminalFontChoice = TerminalFontChoice.Bundled,
    /** Terminal font size in sp. */
    val fontSize: Float = 13f,
    /** Line height as a multiple of the font size, as desktop Kero's setting. */
    val lineHeight: Float = 1.3f,
) {
    /** The theme this appearance actually shows, resolved and never null. */
    fun theme(dark: Boolean): TerminalTheme =
        resolveTheme(if (dark) darkTheme else lightTheme, dark)

    val lineHeightSp: Float get() = fontSize * lineHeight

    companion object {
        /** Kero's own terminal palette, still selectable from the catalogue. */
        const val KEROID = "Keroid"

        /** The design default: one pair drives the app surface and the terminal. */
        const val DEFAULT_LIGHT = "Catppuccin Latte"
        const val DEFAULT_DARK = "Catppuccin Mocha"

        const val MIN_FONT_SIZE = 10f
        const val MAX_FONT_SIZE = 22f
        const val MIN_LINE_HEIGHT = 1.0f
        const val MAX_LINE_HEIGHT = 1.8f

        /** Everything a picker offers: Keroid, the ten Kero washes, then Ghostty. */
        val themeNames: List<String> =
            listOf(KEROID) + KeroWash.all.map { it.name } + TerminalThemes.all.map { it.name }

        /**
         * A stored name to a theme. An unknown name (a removed Ghostty theme, a
         * hand-edited preference) falls back to Keroid's palette rather than
         * failing or showing stale colours.
         */
        fun resolveTheme(name: String, dark: Boolean): TerminalTheme = when {
            name == KEROID -> keroidTheme(dark)
            else -> KeroWash.byName(name)?.theme(dark)
                ?: TerminalThemes.byName(name)
                ?: keroidTheme(dark)
        }
    }
}

/** Where the terminal's glyphs come from. Only fonts the APK can actually draw. */
enum class TerminalFontChoice(val label: String) {
    Bundled("JetBrains Mono"),
    Monospace("System monospace"),
}

/** The font family for a choice; the bundled faces keep bold and italic real. */
fun terminalFontFamily(choice: TerminalFontChoice): FontFamily = when (choice) {
    TerminalFontChoice.Bundled -> TerminalFontFamily
    TerminalFontChoice.Monospace -> FontFamily.Monospace
}

/**
 * Kero's own terminal colours, copied from the desktop's `kero` theme so both
 * clients' terminals read the same. Values are ARGB: 16 program colours, then
 * background, foreground, cursor, cursor text, selection, selection text.
 */
fun keroidTheme(dark: Boolean): TerminalTheme = if (dark) KEROID_DARK else KEROID_LIGHT

private val KEROID_LIGHT = TerminalTheme("Keroid", intArrayOf(
    0xFF1D1D1F.toInt(), 0xFFD3302F.toInt(), 0xFF22863A.toInt(), 0xFFB08800.toInt(),
    0xFF2F5BD8.toInt(), 0xFFA033C4.toInt(), 0xFF0F8199.toInt(), 0xFF8E8E93.toInt(),
    0xFF5F5F66.toInt(), 0xFFEF4444.toInt(), 0xFF2FA84F.toInt(), 0xFFD4A017.toInt(),
    0xFF3B82F6.toInt(), 0xFFC04EE0.toInt(), 0xFF14A3BF.toInt(), 0xFFB4B4BB.toInt(),
    0xFFFFFFFF.toInt(), 0xFF1D1D1F.toInt(), 0xFF1D1D1F.toInt(), 0xFFFFFFFF.toInt(),
    0xFFCFDCFB.toInt(), 0xFF1D1D1F.toInt(),
))

private val KEROID_DARK = TerminalTheme("Keroid", intArrayOf(
    0xFF3A3A40.toInt(), 0xFFFF6B64.toInt(), 0xFF4FD17A.toInt(), 0xFFF2C14E.toInt(),
    0xFF6B9BFF.toInt(), 0xFFD07CF0.toInt(), 0xFF4CC9E0.toInt(), 0xFFC9C9CF.toInt(),
    0xFF6C6C78.toInt(), 0xFFFF8A84.toInt(), 0xFF74E397.toInt(), 0xFFFFD479.toInt(),
    0xFF8FB3FF.toInt(), 0xFFE09CF7.toInt(), 0xFF7FDCEC.toInt(), 0xFFF5F5F7.toInt(),
    0xFF232328.toInt(), 0xFFE6E6EA.toInt(), 0xFFE6E6EA.toInt(), 0xFF161618.toInt(),
    0xFF2F4170.toInt(), 0xFFFFFFFF.toInt(),
))
