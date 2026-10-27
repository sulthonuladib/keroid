package com.sulthonuladib.keroid.term

/**
 * Translate an IME edit, not its entire composing buffer, to terminal input.
 * The terminal cursor stays at the end of this small local context. Replacing
 * a composing suffix erases that suffix before writing the replacement. Count
 * Unicode codepoints, not UTF-16 units, when generating Backspace.
 */
internal fun terminalTextEdit(before: String, after: String): ByteArray {
    var prefix = 0
    while (prefix < before.length && prefix < after.length && before[prefix] == after[prefix]) prefix++
    if (prefix > 0 && prefix < before.length && before[prefix].isLowSurrogate()) prefix--
    val erased = before.codePointCount(prefix, before.length)
    return ByteArray(erased) { 0x7F } + after.substring(prefix).replace("\n", "\r").toByteArray(Charsets.UTF_8)
}

/** Gives an IME something to delete even when its local context is empty. */
internal const val IME_SENTINEL = "\u200B"
