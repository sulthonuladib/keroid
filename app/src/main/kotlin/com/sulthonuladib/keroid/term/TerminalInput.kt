package com.sulthonuladib.keroid.term

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Legacy bytes only for callers without a native terminal (including input-only tests). */
object TerminalKeys {
    val escape = byteArrayOf(0x1B)
    val tab = byteArrayOf(0x09)
    val enter = byteArrayOf(0x0D)
    val backspace = byteArrayOf(0x7F)
    val arrowUp = csi("A")
    val arrowDown = csi("B")
    val arrowRight = csi("C")
    val arrowLeft = csi("D")
    val home = csi("H")
    val end = csi("F")
    val pageUp = csi("5~")
    val pageDown = csi("6~")
    val delete = csi("3~")
    private fun csi(rest: String) = "\u001b[$rest".toByteArray()
    fun control(letter: Char): ByteArray {
        val code = letter.uppercaseChar().code
        if (code !in 'A'.code..'Z'.code) return letter.toString().toByteArray()
        return byteArrayOf((code and 0x1F).toByte())
    }
    fun typed(text: String, ctrl: Boolean): ByteArray {
        if (!ctrl) return text.toByteArray()
        return buildString {
            for (letter in text) append(if (letter.uppercaseChar() in 'A'..'Z')
                (letter.uppercaseChar().code and 0x1F).toChar() else letter)
        }.toByteArray(Charsets.UTF_8)
    }
}

/** Invisible editor, with a bounded composing context rather than reset-per-keystroke input. */
@Composable
fun TerminalInputLayer(
    ctrl: Boolean,
    onBytes: (ByteArray) -> Unit,
    onCtrlUsed: () -> Unit,
    modifier: Modifier = Modifier,
    resetContext: Int = 0,
    showKeyboard: Int = 0,
    onKey: ((TerminalKeyStroke) -> Unit)? = null,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var typed by remember { mutableStateOf(TextFieldValue(IME_SENTINEL, TextRange(1))) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(resetContext) { typed = TextFieldValue(IME_SENTINEL, TextRange(1)) }
    LaunchedEffect(showKeyboard) {
        if (showKeyboard > 0) { focus.requestFocus(); keyboard?.show() }
    }
    // A full-size clickable sibling blocks the canvas's history/selection gestures.
    Box(modifier) {
        BasicTextField(
            value = typed,
            onValueChange = { edit ->
                val bytes = terminalTextEdit(typed.text, edit.text)
                if (bytes.isNotEmpty()) {
                    if (onKey == null) onBytes(TerminalKeys.typed(bytes.toString(Charsets.UTF_8), ctrl))
                    else deliverImeEdit(bytes, ctrl, onBytes, onKey)
                    if (ctrl) onCtrlUsed()
                }
                typed = when {
                    edit.text.isEmpty() -> TextFieldValue(IME_SENTINEL, TextRange(1))
                    // Bound only committed context; never invalidate composition.
                    edit.composition == null && edit.selection.collapsed &&
                        edit.selection.end == edit.text.length && edit.text.length > 256 -> {
                        var start = edit.text.length - 128
                        if (edit.text[start].isLowSurrogate()) start++
                        val tail = edit.text.substring(start)
                        TextFieldValue(tail, TextRange(tail.length))
                    }
                    else -> edit
                }
            },
            singleLine = false,
            textStyle = TextStyle(color = androidx.compose.ui.graphics.Color.Transparent, fontSize = 1.sp),
            cursorBrush = SolidColor(androidx.compose.ui.graphics.Color.Transparent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, autoCorrectEnabled = false),
            modifier = Modifier.size(1.dp).testTag("terminal-ime").focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    if (onKey != null) {
                        val stroke = TerminalKeyStroke.fromAndroid(event.nativeKeyEvent, ctrl)
                            ?: return@onPreviewKeyEvent false
                        if (event.type == KeyEventType.KeyDown) {
                            typed = TextFieldValue(IME_SENTINEL, TextRange(1))
                            if (ctrl) onCtrlUsed()
                        }
                        onKey(stroke)
                        return@onPreviewKeyEvent true
                    }
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (event.isCtrlPressed) {
                        val letter = event.nativeKeyEvent.unicodeChar.toChar()
                        if (letter in 'A'..'Z' || letter in 'a'..'z') {
                            onBytes(TerminalKeys.control(letter)); return@onPreviewKeyEvent true
                        }
                    }
                    val bytes = when (event.key) {
                        Key.Enter, Key.NumPadEnter -> TerminalKeys.enter
                        Key.Escape -> TerminalKeys.escape
                        Key.Tab -> TerminalKeys.tab
                        Key.Backspace -> TerminalKeys.backspace
                        Key.Delete -> TerminalKeys.delete
                        Key.DirectionUp -> TerminalKeys.arrowUp
                        Key.DirectionDown -> TerminalKeys.arrowDown
                        Key.DirectionLeft -> TerminalKeys.arrowLeft
                        Key.DirectionRight -> TerminalKeys.arrowRight
                        Key.MoveHome -> TerminalKeys.home
                        Key.MoveEnd -> TerminalKeys.end
                        Key.PageUp -> TerminalKeys.pageUp
                        Key.PageDown -> TerminalKeys.pageDown
                        else -> null
                    }
                    if (bytes != null) {
                        typed = TextFieldValue(IME_SENTINEL, TextRange(1))
                        onBytes(bytes); true
                    } else false
                },
        )
    }
}

/** Preserve edit order when text, deletion and Enter arrive in one IME batch. */
private fun deliverImeEdit(bytes: ByteArray, ctrl: Boolean, text: (ByteArray) -> Unit,
                           key: (TerminalKeyStroke) -> Unit) {
    val plain = StringBuilder()
    fun flush() { if (plain.isNotEmpty()) { text(plain.toString().toByteArray()); plain.setLength(0) } }
    val points = bytes.toString(Charsets.UTF_8).codePoints().toArray()
    for (cp in points) {
        val special = when (cp) {
            127 -> AndroidKeyEvent.KEYCODE_DEL
            13 -> AndroidKeyEvent.KEYCODE_ENTER
            9 -> AndroidKeyEvent.KEYCODE_TAB
            else -> null
        }
        if (special != null || ctrl) {
            flush()
            val code = special ?: if (cp in 'a'.code..'z'.code) AndroidKeyEvent.KEYCODE_A + cp - 'a'.code
                else if (cp in 'A'.code..'Z'.code) AndroidKeyEvent.KEYCODE_A + cp - 'A'.code else 0
            val stroke = TerminalKeyStroke(code, if (ctrl && special == null) TerminalKeyStroke.CTRL else 0,
                if (special == null) String(Character.toChars(cp)) else "", cp)
            key(stroke)
            key(stroke.copy(action = 0))
        } else plain.appendCodePoint(cp)
    }
    flush()
}

/**
 * One floating row: Esc / Ctrl / Tab stay pinned while navigation and editing
 * keys scroll independently. TerminalDockLayout reserves renderer-only clearance;
 * expanding never changes the shared grid's measured geometry.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TerminalKeyBar(
    expanded: Boolean,
    onToggle: () -> Unit,
    ctrl: Boolean,
    onCtrl: (Boolean) -> Unit,
    onBytes: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    onKey: ((TerminalKeyStroke) -> Unit)? = null,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    // Floating IMEs can be visible with a zero bottom inset.
    val keyboardVisible = WindowInsets.isImeVisible
    var shift by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    val press: (TerminalKey) -> Unit = { key ->
        if (onKey == null) onBytes(key.fallback) else {
            val mods = (if (ctrl) TerminalKeyStroke.CTRL else 0) or
                (if (shift) TerminalKeyStroke.SHIFT else 0) or (if (alt) TerminalKeyStroke.ALT else 0)
            val stroke = TerminalKeyStroke(key.code, mods)
            onKey(stroke); onKey(stroke.copy(action = 0))
        }
        onCtrl(false); shift = false; alt = false
    }
    val control: (Char) -> Unit = { letter ->
        if (onKey == null) onBytes(TerminalKeys.control(letter)) else {
            val stroke = TerminalKeyStroke(AndroidKeyEvent.KEYCODE_A + (letter.lowercaseChar() - 'a'),
                TerminalKeyStroke.CTRL, letter.lowercaseChar().toString(), letter.lowercaseChar().code)
            onKey(stroke); onKey(stroke.copy(action = 0))
        }
        onCtrl(false); shift = false; alt = false
    }
    val toggleKeyboard = { if (keyboardVisible) keyboard?.hide() else keyboard?.show(); Unit }
    Surface(modifier.widthIn(max = 560.dp), shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 4.dp) {
        if (!expanded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onToggle, modifier = Modifier.heightIn(min = 48.dp)
                    .semantics { contentDescription = "Show extra keys" }) {
                    Icon(Icons.Outlined.KeyboardCommandKey, contentDescription = null)
                    Text(if (ctrl) "Ctrl" else "Keys", Modifier.padding(start = 4.dp))
                }
                IconButton(onClick = toggleKeyboard) {
                    Icon(if (keyboardVisible) Icons.Outlined.KeyboardHide else Icons.Outlined.Keyboard,
                        if (keyboardVisible) "Hide keyboard" else "Show keyboard")
                }
            }
        } else {
            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DockKey("Esc", "Escape", onClick = { press(TerminalKey.Escape) })
                CircleDockKey("Ctrl", "Control", ctrl) { onCtrl(!ctrl) }
                DockKey("Tab", "Tab", onClick = { press(TerminalKey.Tab) })
                // Only this group scrolls; essential keys and controls stay put.
                val scroll = rememberLazyListState()
                val snap = rememberSnapFlingBehavior(lazyListState = scroll)
                val keys = listOf(
                    DockKeySpec("←", "Arrow left") { press(TerminalKey.Left) },
                    DockKeySpec("↓", "Arrow down") { press(TerminalKey.Down) },
                    DockKeySpec("↑", "Arrow up") { press(TerminalKey.Up) },
                    DockKeySpec("→", "Arrow right") { press(TerminalKey.Right) },
                    DockKeySpec("↵", "Enter") { press(TerminalKey.Enter) },
                    DockKeySpec("Shift", "Shift", selected = shift) { shift = !shift },
                    DockKeySpec("Alt", "Alt", selected = alt) { alt = !alt },
                    DockKeySpec("Home", "Home") { press(TerminalKey.Home) },
                    DockKeySpec("End", "End") { press(TerminalKey.End) },
                    DockKeySpec("PgUp", "Page up") { press(TerminalKey.PageUp) },
                    DockKeySpec("PgDn", "Page down") { press(TerminalKey.PageDown) },
                    DockKeySpec("Del", "Delete") { press(TerminalKey.Delete) },
                    DockKeySpec("⌫", "Backspace") { press(TerminalKey.Backspace) },
                    DockKeySpec("C-c", "Control C") { control('c') },
                    DockKeySpec("C-r", "Control R") { control('r') },
                    DockKeySpec("C-w", "Control W") { control('w') },
                    DockKeySpec("C-u", "Control U") { control('u') },
                    DockKeySpec("C-k", "Control K") { control('k') },
                )
                Box(Modifier.weight(1f)) {
                    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Box {
                            LazyRow(state = scroll, flingBehavior = snap,
                                contentPadding = PaddingValues(horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                items(keys.size, key = { keys[it].description }, contentType = { "terminal-key" }) { index ->
                                    val key = keys[index]
                                    DockKey(key.label, key.description, selected = key.selected, onClick = key.onClick)
                                }
                            }
                            // Edge fade shows the group continues sideways without clipping a glyph.
                            if (scroll.canScrollForward) Box(Modifier.matchParentSize()) {
                                Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(20.dp)
                                    .background(Brush.horizontalGradient(listOf(Color.Transparent,
                                        MaterialTheme.colorScheme.surfaceContainerHighest))))
                            }
                        }
                    }
                }
                IconButton(onClick = toggleKeyboard) {
                    Icon(if (keyboardVisible) Icons.Outlined.KeyboardHide else Icons.Outlined.Keyboard,
                        if (keyboardVisible) "Hide keyboard" else "Show keyboard")
                }
                IconButton(onClick = onToggle) { Icon(Icons.Outlined.ExpandMore, "Hide extra keys") }
            }
        }
    }
}

/** One key of the scrollable group: label, accessibility name and tap action. */
private class DockKeySpec(val label: String, val description: String,
                          val selected: Boolean = false, val onClick: () -> Unit)

/** Standalone circular toggle, like the Rex command circle beside the key group. */
@Composable
private fun CircleDockKey(label: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, modifier = Modifier.size(48.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest) {
        Box(Modifier.semantics { contentDescription = description; this.selected = selected },
            contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

/** Key inside the grouped pill: transparent until selected, so the group reads as one. */
@Composable
private fun DockKey(label: String, description: String, modifier: Modifier = Modifier,
                    selected: Boolean = false, onClick: () -> Unit) {
    Surface(modifier = modifier.heightIn(min = 48.dp).widthIn(min = 48.dp),
        onClick = onClick, shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent) {
        Box(Modifier.padding(horizontal = 6.dp).semantics { contentDescription = description; this.selected = selected },
            contentAlignment = Alignment.Center) {
            val icon = when (label) {
                "↑" -> Icons.Outlined.KeyboardArrowUp
                "↓" -> Icons.Outlined.KeyboardArrowDown
                "←" -> Icons.AutoMirrored.Outlined.KeyboardArrowLeft
                "→" -> Icons.AutoMirrored.Outlined.KeyboardArrowRight
                "↵" -> Icons.AutoMirrored.Outlined.KeyboardReturn
                "⌫" -> Icons.AutoMirrored.Outlined.Backspace
                else -> null
            }
            if (icon != null) CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Icon(icon, null) // Terminal arrow meanings must not reverse in RTL locales.
            } else Text(label, style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}
