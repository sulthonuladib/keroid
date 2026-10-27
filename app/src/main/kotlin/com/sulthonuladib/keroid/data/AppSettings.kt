package com.sulthonuladib.keroid.data

import android.content.Context
import androidx.core.content.edit
import com.sulthonuladib.keroid.term.TerminalAppearance
import com.sulthonuladib.keroid.term.TerminalFontChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Singleton

/**
 * How the app's own UI follows light and dark, plus terminal-only appearance.
 * An interface so tests can supply a fake without Android storage.
 */
enum class ThemeMode { System, Light, Dark }

interface Preferences {
    val theme: StateFlow<ThemeMode>
    fun setTheme(mode: ThemeMode)

    /** Terminal-only appearance: Ghostty themes and the terminal font. */
    val terminalAppearance: StateFlow<TerminalAppearance>
    fun setTerminalAppearance(appearance: TerminalAppearance)
}

/**
 * Small, non-secret preferences. Kept out of the encrypted library on purpose:
 * losing this must never affect saved machines or keys.
 */
@Singleton
class AppSettings(private val context: Context) : Preferences {
    private val prefs = context.getSharedPreferences("settings.v1", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(
        prefs.getString(KEY_THEME, null)
            ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
            ?: ThemeMode.System,
    )
    override val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    override fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        prefs.edit { putString(KEY_THEME, mode.name) }
    }

    private val _terminalAppearance = MutableStateFlow(readTerminalAppearance())
    override val terminalAppearance: StateFlow<TerminalAppearance> =
        _terminalAppearance.asStateFlow()

    override fun setTerminalAppearance(appearance: TerminalAppearance) {
        _terminalAppearance.value = appearance
        prefs.edit {
            putString(KEY_TERMINAL_LIGHT_THEME, appearance.lightTheme)
            putString(KEY_TERMINAL_DARK_THEME, appearance.darkTheme)
            putString(KEY_TERMINAL_FONT, appearance.font.name)
            putFloat(KEY_TERMINAL_FONT_SIZE, appearance.fontSize)
            putFloat(KEY_TERMINAL_LINE_HEIGHT, appearance.lineHeight)
        }
    }

    /** Stored values are re-validated: an old or hand-edited value must not break the grid. */
    private fun readTerminalAppearance() = TerminalAppearance(
        lightTheme = prefs.getString(KEY_TERMINAL_LIGHT_THEME, null) ?: TerminalAppearance.KEROID,
        darkTheme = prefs.getString(KEY_TERMINAL_DARK_THEME, null) ?: TerminalAppearance.KEROID,
        font = prefs.getString(KEY_TERMINAL_FONT, null)?.let { stored ->
            TerminalFontChoice.entries.firstOrNull { it.name == stored }
        } ?: TerminalFontChoice.Bundled,
        fontSize = prefs.getFloat(KEY_TERMINAL_FONT_SIZE, 13f)
            .takeIf { it.isFinite() }
            ?.coerceIn(TerminalAppearance.MIN_FONT_SIZE, TerminalAppearance.MAX_FONT_SIZE) ?: 13f,
        lineHeight = prefs.getFloat(KEY_TERMINAL_LINE_HEIGHT, 1.3f)
            .takeIf { it.isFinite() }
            ?.coerceIn(TerminalAppearance.MIN_LINE_HEIGHT, TerminalAppearance.MAX_LINE_HEIGHT) ?: 1.3f,
    )

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_TERMINAL_LIGHT_THEME = "terminal.lightTheme"
        const val KEY_TERMINAL_DARK_THEME = "terminal.darkTheme"
        const val KEY_TERMINAL_FONT = "terminal.font"
        const val KEY_TERMINAL_FONT_SIZE = "terminal.fontSize"
        const val KEY_TERMINAL_LINE_HEIGHT = "terminal.lineHeight"
    }
}
