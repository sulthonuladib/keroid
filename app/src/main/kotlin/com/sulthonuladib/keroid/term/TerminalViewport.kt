package com.sulthonuladib.keroid.term

import androidx.compose.ui.geometry.Offset

/** Renderer-only transform. These dimensions must never be sent to the VT. */
internal data class TerminalViewport(val scale: Float, val offset: Offset)

internal fun terminalViewport(
    width: Float,
    height: Float,
    gridWidth: Float,
    gridHeight: Float,
    fit: Boolean,
    pan: Offset,
): TerminalViewport {
    val scale = if (fit && gridWidth > 0 && gridHeight > 0) {
        minOf(1f, width / gridWidth, height / gridHeight)
    } else 1f
    return TerminalViewport(scale, if (fit) Offset.Zero else Offset(
        pan.x.coerceIn(minOf(0f, width - gridWidth), 0f),
        pan.y.coerceIn(minOf(0f, height - gridHeight), 0f),
    ))
}
