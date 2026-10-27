package com.sulthonuladib.keroid.term

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Resolves the packaged faces on Android, with no network/downloadable fonts. */
@RunWith(AndroidJUnit4::class)
class TerminalFontTest {
    private fun face(weight: FontWeight, style: FontStyle): Typeface {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return createFontFamilyResolver(context).resolve(TerminalFontFamily, weight, style).value as Typeface
    }

    @Test
    fun bundledFacesResolveWithTheirRealStylesAndNerdFontSymbols() {
        for ((weight, style) in listOf(
            FontWeight.Normal to FontStyle.Normal,
            FontWeight.Bold to FontStyle.Normal,
            FontWeight.Normal to FontStyle.Italic,
            FontWeight.Bold to FontStyle.Italic,
        )) {
            val typeface = face(weight, style)
            assertNotEquals("must use the bundled font, not system monospace", Typeface.MONOSPACE, typeface)
            assertEquals(weight == FontWeight.Bold, typeface.isBold)
            assertEquals(style == FontStyle.Italic, typeface.isItalic)
            val paint = Paint().apply { this.typeface = typeface; textSize = 32f }
            assertTrue("Nerd Font folder icon is missing", paint.hasGlyph("\uf07b"))
            assertTrue("Nerd Font branch icon is missing", paint.hasGlyph("\ue0a0"))
        }
    }

    @Test
    fun asciiUsesOneCellAdvanceAcrossAllFaces() {
        for (weight in listOf(FontWeight.Normal, FontWeight.Bold)) {
            for (style in listOf(FontStyle.Normal, FontStyle.Italic)) {
                val paint = Paint().apply { typeface = face(weight, style); textSize = 32f }
                val cell = paint.measureText("M")
                for (character in "iW0 ._@") {
                    assertEquals("unequal cell width for '$character' in $weight/$style", cell, paint.measureText(character.toString()), 0.01f)
                }
            }
        }
    }
}
