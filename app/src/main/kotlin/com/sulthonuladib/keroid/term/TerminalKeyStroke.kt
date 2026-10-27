package com.sulthonuladib.keroid.term

import android.view.KeyEvent

/** Android physical key plus logical, unmodified text. JNI maps names, not enum ordinals. */
data class TerminalKeyStroke(
    val code: Int,
    val mods: Int = 0,
    val text: String = "",
    val unshifted: Int = 0,
    val action: Int = 1,
) {
    companion object {
        const val SHIFT = 1
        const val CTRL = 2
        const val ALT = 4
        const val SUPER = 8

        fun fromAndroid(event: KeyEvent, stickyCtrl: Boolean): TerminalKeyStroke? {
            val code = event.keyCode
            val supported = code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ||
                code in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z ||
                code in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 ||
                code in setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_ESCAPE,
                    KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL,
                    KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END,
                    KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_INSERT,
                    KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_PERIOD,
                    KeyEvent.KEYCODE_GRAVE, KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_EQUALS,
                    KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEvent.KEYCODE_BACKSLASH,
                    KeyEvent.KEYCODE_SEMICOLON, KeyEvent.KEYCODE_APOSTROPHE, KeyEvent.KEYCODE_SLASH)
            if (!supported) return null
            // Keep Shift for logical text; do not hand transformed C0 bytes to
            // Ghostty. Dead-key composition belongs to Android's IME instead.
            val unicode = event.getUnicodeChar(event.metaState and
                (KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK or KeyEvent.META_META_MASK).inv())
            if (unicode < 0) return null
            val mods = (if (event.isShiftPressed) SHIFT else 0) or
                (if (event.isCtrlPressed || stickyCtrl) CTRL else 0) or
                (if (event.isAltPressed) ALT else 0) or (if (event.isMetaPressed) SUPER else 0)
            return TerminalKeyStroke(code, mods,
                if (unicode >= 32 && unicode != 127) String(Character.toChars(unicode)) else "",
                event.getUnicodeChar(0),
                if (event.action == KeyEvent.ACTION_UP) 0 else if (event.repeatCount > 0) 2 else 1)
        }
    }
}

/** Names used by the on-screen dock; every key goes through the same encoder. */
enum class TerminalKey(val code: Int, val fallback: ByteArray) {
    Escape(KeyEvent.KEYCODE_ESCAPE, TerminalKeys.escape),
    Tab(KeyEvent.KEYCODE_TAB, TerminalKeys.tab),
    Enter(KeyEvent.KEYCODE_ENTER, TerminalKeys.enter),
    Backspace(KeyEvent.KEYCODE_DEL, TerminalKeys.backspace),
    Delete(KeyEvent.KEYCODE_FORWARD_DEL, TerminalKeys.delete),
    Up(KeyEvent.KEYCODE_DPAD_UP, TerminalKeys.arrowUp),
    Down(KeyEvent.KEYCODE_DPAD_DOWN, TerminalKeys.arrowDown),
    Left(KeyEvent.KEYCODE_DPAD_LEFT, TerminalKeys.arrowLeft),
    Right(KeyEvent.KEYCODE_DPAD_RIGHT, TerminalKeys.arrowRight),
    Home(KeyEvent.KEYCODE_MOVE_HOME, TerminalKeys.home),
    End(KeyEvent.KEYCODE_MOVE_END, TerminalKeys.end),
    PageUp(KeyEvent.KEYCODE_PAGE_UP, TerminalKeys.pageUp),
    PageDown(KeyEvent.KEYCODE_PAGE_DOWN, TerminalKeys.pageDown),
}
