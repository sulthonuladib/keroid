package com.sulthonuladib.keroid.term

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.sulthonuladib.keroid.R

/**
 * Desktop Kero's bundled JetBrains Mono Nerd Font, for terminal text only.
 * Blocking resource loading ensures the first grid measurement uses the actual
 * font, not a temporary fallback that would request different pane geometry.
 * The four real faces preserve SGR bold/italic without synthesising them.
 */
val TerminalFontFamily: FontFamily = FontFamily(
    Font(R.font.jetbrains_mono_nerd_regular, FontWeight.Normal, FontStyle.Normal, FontLoadingStrategy.Blocking),
    Font(R.font.jetbrains_mono_nerd_bold, FontWeight.Bold, FontStyle.Normal, FontLoadingStrategy.Blocking),
    Font(R.font.jetbrains_mono_nerd_italic, FontWeight.Normal, FontStyle.Italic, FontLoadingStrategy.Blocking),
    Font(R.font.jetbrains_mono_nerd_bold_italic, FontWeight.Bold, FontStyle.Italic, FontLoadingStrategy.Blocking),
)
