package com.sulthonuladib.keroid.term

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class TerminalTextEditTest {
    private fun edit(before: String, after: String, expected: String) =
        assertArrayEquals(expected.toByteArray(), terminalTextEdit(before, after))

    @Test fun composingPrefixesAreSentOnlyOnce() {
        edit(IME_SENTINEL, IME_SENTINEL + "h", "h")
        edit(IME_SENTINEL + "h", IME_SENTINEL + "he", "e")
        edit(IME_SENTINEL + "he", IME_SENTINEL + "hello", "llo")
        edit(IME_SENTINEL + "hello", IME_SENTINEL + "hello", "")
    }

    @Test fun composingReplacementAndDeletionEraseOnlyChangedSuffix() {
        edit("teh", "the", "\u007f\u007fhe")
        edit("hello", "hell", "\u007f")
        edit(IME_SENTINEL, "", "\u007f")
        edit("word", "word\n", "\r")
    }

    @Test fun unicodeEditsNeverSplitSurrogatePairsOrTruncateUtf8() {
        edit("a😀", "a😎", "\u007f😎")
        edit("😀", "", "\u007f")
        edit("", "你好é😀", "你好é😀")
        assertArrayEquals("\u0003你好😀".toByteArray(), TerminalKeys.typed("c你好😀", true))
    }

    @Test fun fastTypingBurstIsNeitherRepeatedNorDropped() {
        val burst = "the quick brown fox jumps over the lazy dog 0123456789 ".repeat(30)
        val output = java.io.ByteArrayOutputStream()
        var old = IME_SENTINEL
        for (ch in burst) {
            val next = old + ch
            output.write(terminalTextEdit(old, next))
            old = next
        }
        assertArrayEquals(burst.toByteArray(), output.toByteArray())
    }
}
