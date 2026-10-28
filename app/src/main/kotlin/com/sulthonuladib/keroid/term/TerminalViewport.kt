package com.sulthonuladib.keroid.term

import androidx.compose.ui.geometry.Offset

/**
 * Renderer-only transform. These dimensions must never be sent to the VT.
 *
 * [autoOffset] is the automatic bottom alignment that keeps the live row (and
 * therefore the cursor) above the floating dock. It is not part of the user's
 * pan, so a drag never applies the clearance twice.
 */
internal data class TerminalViewport(
    val scale: Float,
    val offset: Offset,
    val autoOffset: Float = 0f,
) {
    fun panForOffset(value: Offset) = Offset(value.x, value.y - autoOffset)
}

internal fun terminalViewport(
    width: Float,
    height: Float,
    gridWidth: Float,
    gridHeight: Float,
    fit: Boolean,
    pan: Offset,
    bottomOcclusion: Float = 0f,
): TerminalViewport {
    val visibleHeight = (height - bottomOcclusion.coerceAtLeast(0f)).coerceAtLeast(1f)
    val scale = if (fit && gridWidth > 0 && gridHeight > 0) {
        minOf(1f, width / gridWidth, visibleHeight / gridHeight)
    } else 1f
    if (fit) return TerminalViewport(scale, Offset.Zero)
    // A grid taller than the unobscured area follows its live bottom row, so the
    // program's cursor never starts hidden under the keys. The user's pan rides
    // on top of that alignment and can still reach every row.
    val auto = minOf(0f, visibleHeight - gridHeight)
    return TerminalViewport(scale, Offset(
        pan.x.coerceIn(minOf(0f, width - gridWidth), 0f),
        (pan.y + auto).coerceIn(auto, 0f),
    ), auto)
}

/**
 * Rows that fit above the floating dock while one clear row separates the live
 * row from the keys. Reserving a fixed row means folding the dock open by part
 * of a row cannot make the shared grid renegotiate its height.
 */
internal fun terminalRows(height: Float, cellHeight: Float, bottomOcclusion: Float): Int {
    if (cellHeight <= 0f) return 1
    val clearance = bottomOcclusion.coerceAtLeast(0f)
    val reserved = if (clearance > 0f) clearance + cellHeight else 0f
    return ((height - reserved) / cellHeight).toInt().coerceAtLeast(1)
}
