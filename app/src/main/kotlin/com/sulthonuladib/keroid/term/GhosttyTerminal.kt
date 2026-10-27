package com.sulthonuladib.keroid.term

import java.io.Closeable

/**
 * A libghostty-vt terminal: it owns the emulation, not the network or the pixels.
 * Feed it the bytes from a Kero attach stream and read its grid back with
 * [render]; see [TerminalView] for the renderer.
 *
 * The native libraries come from scripts/build-ghostty.sh. When they are absent
 * (a build without that step) [available] is false and the UI keeps its text
 * snapshot instead.
 */
class GhosttyTerminal(cols: Int, rows: Int) : Closeable {
    private var handle: Long = if (available) nativeNew(cols, rows) else 0L

    // The native terminal is not thread-safe: the writer thread and the UI
    // renderer both touch it, so every native call holds this lock.
    private val lock = Any()

    var cols: Int = cols
        private set
    var rows: Int = rows
        private set

    /** False when the native terminal could not be created; [render] then reports 0. */
    val usable: Boolean get() = handle != 0L

    fun write(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        synchronized(lock) {
            if (handle != 0L) nativeWrite(handle, bytes)
        }
    }

    fun write(text: String) = write(text.toByteArray(Charsets.UTF_8))

    fun resize(cols: Int, rows: Int, cellWidthPx: Int = 0, cellHeightPx: Int = 0) {
        if (cols <= 0 || rows <= 0) return
        synchronized(lock) {
            if (handle == 0L) return
            nativeResize(handle, cols, rows, cellWidthPx, cellHeightPx)
            this.cols = cols
            this.rows = rows
        }
    }

    /**
     * Fills [cells] with [cols] × [rows] entries of [STRIDE] ints (codepoint,
     * foreground, background, flags, width) and [cursor] with
     * (column, row, visible, unused). Returns how many cells were written.
     */
    fun render(cells: IntArray, cursor: IntArray): Int = synchronized(lock) {
        if (handle != 0L) nativeRender(handle, cells, cursor) else 0
    }

    internal fun scroll(delta: Int, bottom: Boolean = false) = synchronized(lock) {
        if (handle != 0L) nativeScroll(handle, delta, bottom)
    }

    internal fun select(x: Int, y: Int, start: Boolean) = synchronized(lock) {
        if (handle != 0L) nativeSelect(handle, x, y, start)
    }

    internal fun encode(key: TerminalKeyStroke): ByteArray = synchronized(lock) {
        if (handle != 0L) nativeKey(handle, key.code, key.mods, key.text.toByteArray(Charsets.UTF_8), key.unshifted, key.action)
            ?: ByteArray(0) else ByteArray(0)
    }

    internal fun selectedText(): String = synchronized(lock) {
        if (handle != 0L) nativeCopy(handle)?.toString(Charsets.UTF_8).orEmpty() else ""
    }

    /**
     * Applies a Ghostty theme's chrome colours and its 16 program colours over
     * the built-in 256-colour palette. Programs may still set their own colours
     * (OSC 4/10/11/12) afterwards, exactly as on the desktop.
     */
    internal fun setTheme(theme: TerminalTheme): Boolean = synchronized(lock) {
        handle != 0L && nativeSetTheme(handle, theme.foreground, theme.background, theme.cursor, theme.palette)
    }

    /** Keep dimension selection and the following render in one native lock. */
    internal fun withGrid(block: (cols: Int, rows: Int) -> Unit) = synchronized(lock) {
        block(cols, rows)
    }

    override fun close() {
        synchronized(lock) {
            val closing = handle
            handle = 0L
            if (closing != 0L) nativeFree(closing)
        }
    }

    companion object {
        /** Ints per cell in the array [render] fills. */
        const val STRIDE = 5

        const val FLAG_BOLD = 1
        const val FLAG_ITALIC = 2
        const val FLAG_UNDERLINE = 4
        const val FLAG_STRIKETHROUGH = 8
        const val FLAG_FAINT = 16
        const val FLAG_BLINK = 32
        const val FLAG_INVERSE = 64
        const val FLAG_INVISIBLE = 128
        const val FLAG_SELECTED = 256

        private val loadFailure: Throwable? = try {
            System.loadLibrary("keroid_term")
            null
        } catch (t: Throwable) {
            // A build without scripts/build-ghostty.sh has no native libraries.
            android.util.Log.w("KeroidTerm", "libghostty-vt unavailable: ${t.message}", t)
            t
        }

        val available: Boolean get() = loadFailure == null
        val unavailableReason: String? get() = loadFailure?.message

        @JvmStatic private external fun nativeNew(cols: Int, rows: Int): Long
        @JvmStatic private external fun nativeFree(handle: Long)
        @JvmStatic private external fun nativeWrite(handle: Long, bytes: ByteArray): Boolean
        @JvmStatic private external fun nativeResize(
            handle: Long,
            cols: Int,
            rows: Int,
            cellWidthPx: Int,
            cellHeightPx: Int,
        )
        @JvmStatic private external fun nativeRender(handle: Long, cells: IntArray, cursor: IntArray): Int
        @JvmStatic private external fun nativeScroll(handle: Long, delta: Int, bottom: Boolean)
        @JvmStatic private external fun nativeSelect(handle: Long, x: Int, y: Int, start: Boolean)
        @JvmStatic private external fun nativeCopy(handle: Long): ByteArray?
        @JvmStatic private external fun nativeSetTheme(handle: Long, foreground: Int, background: Int,
                                                      cursor: Int, palette: IntArray): Boolean
        @JvmStatic private external fun nativeKey(handle: Long, code: Int, mods: Int, text: ByteArray,
                                                 unshifted: Int, action: Int): ByteArray?
    }
}
