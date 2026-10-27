package com.sulthonuladib.keroid.term

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import android.content.ClipData
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import androidx.compose.runtime.snapshotFlow

/**
 * Draws a [TerminalSession]'s grid with Compose text.
 *
 * The grid belongs to the daemon, so glyphs are placed at exact column offsets
 * and each style run is clipped to its own cells: a fallback font with a wider
 * advance can then never push the rest of a line out of the grid. Rows are
 * rebuilt only when their cells change.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TerminalView(
    session: TerminalSession,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily = TerminalFontFamily,
    fontSize: TextUnit = 13.sp,
    lineHeight: TextUnit = (fontSize.value * 1.25f).sp,
    theme: TerminalTheme? = null,
    background: Color = theme?.let { Color(it.background) }
        ?: MaterialTheme.colorScheme.surfaceContainerLowest,
    fitToScreen: Boolean = false,
    pan: Offset = Offset.Zero,
    onPanClamped: (Offset) -> Unit = {},
    onTap: () -> Unit = {},
    onGrid: ((cols: Int, rows: Int, cellWidth: Int, cellHeight: Int) -> Unit)? = null,
) {
    val terminal = session.terminal
    val measurer = rememberTextMeasurer(cacheSize = 512)
    val style = remember(fontFamily, fontSize, lineHeight) {
        TextStyle(fontFamily = fontFamily, fontSize = fontSize, lineHeight = lineHeight)
    }
    val selectionColor = theme?.selection ?: DEFAULT_SELECTION

    // The theme belongs to the terminal, not to this composable: programs then
    // see Ghostty's own palette and the grid recolours on the next capture.
    LaunchedEffect(terminal, theme) {
        if (theme != null) {
            terminal.setTheme(theme)
            session.invalidate()
        }
    }
    val metrics = remember(measurer, style) { cellMetrics(measurer, style) }
    var renderer by remember(terminal) { mutableStateOf(CellRenderer(0, 0, selectionColor)) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(session) {
        snapshotFlow { session.frames }.conflate().collect {
            // Coalesce replay bursts; keep native locks and row building off main.
            // Publish a fresh snapshot, never mutate the grid being drawn.
            delay(16)
            val previous = renderer
            renderer = withContext(Dispatchers.Default) {
                var captured = previous
                terminal.withGrid { cols, rows ->
                    captured = CellRenderer(cols, rows, selectionColor)
                    captured.capture(terminal)
                }
                captured.rebuild(previous)
                captured
            }
        }
    }

    val viewport = terminalViewport(
        viewSize.width.toFloat(), viewSize.height.toFloat(),
        renderer.cols * metrics.width, renderer.rows * metrics.height, fitToScreen, pan,
    )
    LaunchedEffect(viewport.offset, viewSize, renderer) {
        if (viewSize.width > 0 && viewSize.height > 0 && renderer.cols > 0) onPanClamped(viewport.offset)
    }

    val density = LocalDensity.current
    val imeSettled = WindowInsets.ime.getBottom(density) == WindowInsets.imeAnimationTarget.getBottom(density)

    // Ask for the grid the phone fits. The screen only changes when the daemon
    // answers with a resize frame, so the program's output is never laid out at a
    // size the daemon did not use.
    LaunchedEffect(viewSize, metrics, imeSettled) {
        if (!imeSettled) return@LaunchedEffect
        if (viewSize.width <= 0 || viewSize.height <= 0) return@LaunchedEffect
        if (metrics.width <= 0f || metrics.height <= 0f) return@LaunchedEffect
        // Insets animate through many cell counts. Send only settled geometry;
        // the local mirror still resizes exclusively on welcome/'z'.
        delay(160)
        val cols = (viewSize.width / metrics.width).toInt().coerceAtLeast(1)
        val rows = (viewSize.height / metrics.height).toInt().coerceAtLeast(1)
        onGrid?.invoke(cols, rows, ceil(metrics.width).toInt(), ceil(metrics.height).toInt())
    }

    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val currentViewport by rememberUpdatedState(viewport)
    val currentRenderer by rememberUpdatedState(renderer)
    val currentPan by rememberUpdatedState(onPanClamped)
    val currentTap by rememberUpdatedState(onTap)
    Box(modifier.fillMaxSize().onSizeChanged { viewSize = it }.background(background)) {
    Canvas(Modifier.fillMaxSize()
        .semantics {
            contentDescription = "Terminal grid"
            scrollBy { _, y -> session.scroll((y / metrics.height).toInt()); true }
        }
        .pointerInput(session) { detectTapGestures(onTap = { currentTap() }) }
        .pointerInput(session, metrics) {
            fun selectAt(point: Offset, start: Boolean) {
                val vp = currentViewport
                val grid = currentRenderer
                if (grid.cols == 0 || grid.rows == 0) return
                val x = ((point.x - vp.offset.x) / (metrics.width * vp.scale)).toInt().coerceIn(0, grid.cols - 1)
                val y = ((point.y - vp.offset.y) / (metrics.height * vp.scale)).toInt().coerceIn(0, grid.rows - 1)
                session.select(x, y, start)
            }
            detectDragGesturesAfterLongPress(onDragStart = { selectAt(it, true) }) { change, _ ->
                change.consume()
                selectAt(change.position, false)
            }
        }
        .pointerInput(session, metrics, fitToScreen) {
            var remainder = 0f
            detectDragGestures(onDragStart = { remainder = 0f }) { change, delta ->
                change.consume()
                val vp = currentViewport
                val grid = currentRenderer
                // Pan oversized rows into view before entering history. Once in
                // history, both directions navigate it; horizontal drag always pans.
                val history = grid.cursor[3] > 0 || (delta.y > 0f && vp.offset.y >= 0f)
                if (history) {
                    remainder -= delta.y / (metrics.height * vp.scale)
                    val rows = remainder.toInt()
                    if (rows != 0) { session.clearSelection(); session.scroll(rows); remainder -= rows }
                }
                if (!fitToScreen) currentPan(Offset(vp.offset.x + delta.x,
                    if (history) vp.offset.y else vp.offset.y + delta.y))
            }
        }) {
        if (renderer.cols == 0 || renderer.rows == 0) return@Canvas
        val cellWidth = metrics.width
        val cellHeight = metrics.height
        if (cellWidth <= 0f || cellHeight <= 0f) return@Canvas
        // Readable cells by default; fitting/panning never changes the VT grid.
        clipRect {
            translate(viewport.offset.x, viewport.offset.y) {
                scale(scaleX = viewport.scale, scaleY = viewport.scale, pivot = Offset.Zero) {
                    val first = (-viewport.offset.y / (cellHeight * viewport.scale)).toInt().coerceAtLeast(0)
                    val last = ceil((viewSize.height - viewport.offset.y) / (cellHeight * viewport.scale)).toInt().coerceAtMost(renderer.rows)
                    drawGrid(renderer, measurer, style, cellWidth, cellHeight, first, last)
                }
            }
        }
    }
    if (renderer.hasSelection) Surface(Modifier.align(Alignment.TopStart).padding(8.dp),
        shape = MaterialTheme.shapes.extraLarge, shadowElevation = 4.dp) {
        Row {
            TextButton(onClick = {
                session.copySelection { text -> scope.launch {
                    if (text.isNotEmpty()) clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Terminal selection", text)))
                    session.clearSelection()
                } }
            }) { Text("Copy") }
            TextButton(onClick = { session.clearSelection() }) { Text("Cancel") }
        }
    }
    if (renderer.cursor[3] > 0) Surface(Modifier.align(Alignment.TopEnd).padding(8.dp),
        shape = MaterialTheme.shapes.extraLarge, shadowElevation = 4.dp) {
        TextButton(onClick = { session.clearSelection(); session.scrollToBottom() }) { Text("Jump to live") }
    }
    }
}

private fun DrawScope.drawGrid(
    renderer: CellRenderer,
    measurer: TextMeasurer,
    style: TextStyle,
    cellWidth: Float,
    cellHeight: Float,
    firstRow: Int,
    lastRow: Int,
) {
    for (row in firstRow until lastRow) {
        // Backgrounds: one rectangle while the colour holds.
        var column = 0
        while (column < renderer.cols) {
            val bg = renderer.backgroundAt(row, column)
            var end = column + 1
            while (end < renderer.cols && renderer.backgroundAt(row, end) == bg) end++
            drawRect(
                color = Color(bg),
                topLeft = Offset(column * cellWidth, row * cellHeight),
                size = Size((end - column) * cellWidth, cellHeight),
            )
            column = end
        }

        // Glyphs: a run per style, placed at its own column and clipped to it.
        for (run in renderer.runs(row)) {
            if (run.text.text.isBlank()) continue
            val left = run.start * cellWidth
            val width = run.length * cellWidth
            clipRect(left = left, top = row * cellHeight, right = left + width, bottom = (row + 1) * cellHeight) {
                drawText(
                    measurer.measure(run.text, style, softWrap = false, maxLines = 1),
                    topLeft = Offset(left, row * cellHeight),
                )
            }
        }
    }

    val cursorColumn = renderer.cursor[0]
    val cursorRow = renderer.cursor[1]
    if (renderer.cursor[2] != 0 && cursorRow in 0 until renderer.rows && cursorColumn in 0 until renderer.cols) {
        drawRect(
            color = Color(renderer.foregroundAt(cursorRow, cursorColumn)).copy(alpha = 0.6f),
            topLeft = Offset(cursorColumn * cellWidth, cursorRow * cellHeight),
            size = Size(cellWidth, cellHeight),
        )
    }
}

/** Cells of one row that share a style, drawn as one text run. */
private class StyleRun(val start: Int, val length: Int, val text: AnnotatedString)

/**
 * A copy of the VT grid. [capture] is the only place the native terminal is read;
 * rows rebuild their runs only when their cells change, and backgrounds are
 * resolved through the same snapshot.
 */
private class CellRenderer(val cols: Int, val rows: Int, private val selectionColor: Int) {
    private val cells = IntArray(cols * rows * GhosttyTerminal.STRIDE)
    val cursor = IntArray(4)
    var hasSelection: Boolean = false
        private set
    private val rowRuns = arrayOfNulls<List<StyleRun>>(rows)

    fun capture(terminal: GhosttyTerminal) { terminal.render(cells, cursor) }

    fun rebuild(previous: CellRenderer) {
        hasSelection = (0 until cols * rows).any { flags(it) and GhosttyTerminal.FLAG_SELECTED != 0 }
        for (row in 0 until rows) {
            val same = cols == previous.cols && row < previous.rows &&
                (row * cols * GhosttyTerminal.STRIDE until (row + 1) * cols * GhosttyTerminal.STRIDE)
                    .all { cells[it] == previous.cells[it] }
            rowRuns[row] = if (same) previous.rowRuns[row] else buildRuns(row)
        }
    }

    fun backgroundAt(row: Int, column: Int): Int {
        val cell = row * cols + column
        if (flags(cell) and GhosttyTerminal.FLAG_SELECTED != 0) return selectionColor
        val inverse = flags(cell) and GhosttyTerminal.FLAG_INVERSE != 0
        return color(cell, if (inverse) FG_CHANNEL else BG_CHANNEL)
    }

    fun foregroundAt(row: Int, column: Int): Int = color(row * cols + column, FG_CHANNEL)

    fun runs(row: Int): List<StyleRun> {
        rowRuns[row]?.let { return it }
        val built = buildRuns(row)
        rowRuns[row] = built
        return built
    }

    private fun buildRuns(row: Int): List<StyleRun> {
        val runs = mutableListOf<StyleRun>()
        var column = 0
        while (column < cols) {
            val cell = row * cols + column
            val width = cells[cell * GhosttyTerminal.STRIDE + 4]
            if (width == 0) { column++; continue } // wide glyph's spacer tail
            val start = column
            val style = styleKey(cell)
            column = (column + width).coerceAtMost(cols)
            // Wide glyphs get their own exact-column run; never insert a second
            // space for a tail or let fallback glyph advances shift later cells.
            if (width == 1) while (column < cols &&
                cells[(row * cols + column) * GhosttyTerminal.STRIDE + 4] == 1 &&
                styleKey(row * cols + column) == style) column++
            runs += StyleRun(start, column - start, buildText(row, start, column - start))
        }
        return runs
    }

    private fun buildText(row: Int, start: Int, length: Int): AnnotatedString = buildAnnotatedString {
        val first = row * cols + start
        pushStyle(spanStyle(first, flags(first)))
        for (column in start until start + length) {
            val cell = row * cols + column
            if (cells[cell * GhosttyTerminal.STRIDE + 4] == 0) continue
            if (flags(cell) and GhosttyTerminal.FLAG_INVISIBLE != 0) append(' ')
            else appendCell(codepoint(cell))
        }
        pop()
    }

    /** Style identity for run grouping: colour and flags after inverse, plus fg. */
    private fun styleKey(cell: Int): Long {
        val flags = flags(cell)
        val inverse = flags and GhosttyTerminal.FLAG_INVERSE != 0
        return (color(cell, if (inverse) BG_CHANNEL else FG_CHANNEL).toLong() shl 32) or
            (flags and GhosttyTerminal.FLAG_INVERSE.inv()).toLong()
    }

    private fun spanStyle(cell: Int, flags: Int): SpanStyle {
        val inverse = flags and GhosttyTerminal.FLAG_INVERSE != 0
        val fg = color(cell, if (inverse) BG_CHANNEL else FG_CHANNEL)
        val decoration = when {
            flags and GhosttyTerminal.FLAG_UNDERLINE != 0 -> TextDecoration.Underline
            flags and GhosttyTerminal.FLAG_STRIKETHROUGH != 0 -> TextDecoration.LineThrough
            else -> TextDecoration.None
        }
        return SpanStyle(
            color = Color(fg),
            fontWeight = if (flags and GhosttyTerminal.FLAG_BOLD != 0) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (flags and GhosttyTerminal.FLAG_ITALIC != 0) FontStyle.Italic else FontStyle.Normal,
            textDecoration = decoration,
        )
    }

    private fun codepoint(cell: Int) = cells[cell * GhosttyTerminal.STRIDE]
    private fun flags(cell: Int) = cells[cell * GhosttyTerminal.STRIDE + 3]

    private fun color(cell: Int, channel: Int) = cells[cell * GhosttyTerminal.STRIDE + channel]

    private companion object {
        const val FG_CHANNEL = 1
        const val BG_CHANNEL = 2
    }
}

/** Selection wash when no terminal theme is set (the text snapshot's look). */
private val DEFAULT_SELECTION = 0xFF385575.toInt()

private fun AnnotatedString.Builder.appendCell(codepoint: Int) {
    if (codepoint <= 0 || codepoint == ' '.code) {
        append(' ')
    } else if (codepoint < 0x10000) {
        append(codepoint.toChar())
    } else {
        val adjusted = codepoint - 0x10000
        append(Char(0xD800 + (adjusted shr 10)))
        append(Char(0xDC00 + (adjusted and 0x3FF)))
    }
}

private class CellMetrics(val width: Float, val height: Float)

/** Cell size from a monospace sample, rounded up so glyphs never overlap. */
private fun cellMetrics(measurer: TextMeasurer, style: TextStyle): CellMetrics {
    val sample = measurer.measure("M".repeat(10), style, softWrap = false, maxLines = 1)
    val line = measurer.measure("M", style, softWrap = false, maxLines = 1)
    return CellMetrics(
        width = ceil(sample.size.width / 10f).coerceAtLeast(1f),
        height = line.size.height.coerceAtLeast(1).toFloat(),
    )
}
