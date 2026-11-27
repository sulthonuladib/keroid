package com.sulthonuladib.keroid.term

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.sulthonuladib.keroid.ui.components.VerticalHairline

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
    Surface(modifier.widthIn(max = 560.dp).then(if (!expanded || keyboardVisible) Modifier.height(56.dp) else Modifier),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 4.dp) {
        if (!expanded) {
            Row(Modifier.fillMaxSize().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                // The pinned set from the design: modifiers, editing keys and
                // arrows. The rest live behind Keys, so the bar never crowds a
                // phone-width terminal.
                DockKey("ctrl", "Control", selected = ctrl, minWidth = 40.dp) { onCtrl(!ctrl) }
                DockKey("esc", "Escape", minWidth = 40.dp) { press(TerminalKey.Escape) }
                DockKey("tab", "Tab", minWidth = 40.dp) { press(TerminalKey.Tab) }
                VerticalHairline(Modifier.padding(horizontal = 2.dp))
                DockKey("←", "Arrow left", minWidth = 40.dp) { press(TerminalKey.Left) }
                DockKey("→", "Arrow right", minWidth = 40.dp) { press(TerminalKey.Right) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = toggleKeyboard, modifier = Modifier.size(40.dp)) {
                    Icon(if (keyboardVisible) Icons.Outlined.KeyboardHide else Icons.Outlined.Keyboard,
                        if (keyboardVisible) "Hide keyboard" else "Show keyboard",
                        modifier = Modifier.size(20.dp))
                }
                Surface(
                    onClick = onToggle,
                    modifier = Modifier.height(40.dp).semantics { contentDescription = "Show extra keys" },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.KeyboardCommandKey, null, modifier = Modifier.size(16.dp))
                        Text("Keys", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        } else if (keyboardVisible) {
            // With the IME up, a tall panel would either cover the live prompt or
            // force the cursor to jump. Keep the same 56 dp dock height and let
            // the full grouped panel wait until the keyboard is dismissed.
            Row(Modifier.fillMaxSize().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                DockKey("Esc", "Escape", minWidth = 40.dp) { press(TerminalKey.Escape) }
                DockKey("Ctrl", "Control", selected = ctrl, minWidth = 40.dp) { onCtrl(!ctrl) }
                DockKey("Tab", "Tab", minWidth = 40.dp) { press(TerminalKey.Tab) }
                VerticalHairline(Modifier.padding(horizontal = 2.dp))
                DockKey("←", "Arrow left", minWidth = 40.dp) { press(TerminalKey.Left) }
                DockKey("→", "Arrow right", minWidth = 40.dp) { press(TerminalKey.Right) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = toggleKeyboard, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.KeyboardHide, "Hide keyboard", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.ExpandMore, "Hide extra keys", modifier = Modifier.size(20.dp))
                }
            }
        } else {
            val keys = listOf(
                DockKeySpec("←", "Arrow left") { press(TerminalKey.Left) },
                DockKeySpec("↓", "Arrow down") { press(TerminalKey.Down) },
                DockKeySpec("↑", "Arrow up") { press(TerminalKey.Up) },
                DockKeySpec("→", "Arrow right") { press(TerminalKey.Right) },
                DockKeySpec("Home", "Home") { press(TerminalKey.Home) },
                DockKeySpec("End", "End") { press(TerminalKey.End) },
                DockKeySpec("PgUp", "Page up") { press(TerminalKey.PageUp) },
                DockKeySpec("PgDn", "Page down") { press(TerminalKey.PageDown) },
                DockKeySpec("/", "Slash") { onBytes("/".toByteArray()) },
                DockKeySpec("|", "Pipe") { onBytes("|".toByteArray()) },
                DockKeySpec("~", "Tilde") { onBytes("~".toByteArray()) },
                DockKeySpec("-", "Dash") { onBytes("-".toByteArray()) },
                DockKeySpec("=", "Equals") { onBytes("=".toByteArray()) },
                DockKeySpec("[", "Left bracket") { onBytes("[".toByteArray()) },
                DockKeySpec("]", "Right bracket") { onBytes("]".toByteArray()) },
                DockKeySpec("\\", "Backslash") { onBytes("\\".toByteArray()) },
                DockKeySpec("⌫", "Backspace") { press(TerminalKey.Backspace) },
                DockKeySpec("Del", "Delete") { press(TerminalKey.Delete) },
                DockKeySpec("C-c", "Control C") { control('c') },
                DockKeySpec("C-r", "Control R") { control('r') },
                DockKeySpec("C-w", "Control W") { control('w') },
                DockKeySpec("C-u", "Control U") { control('u') },
                DockKeySpec("C-k", "Control K") { control('k') },
                DockKeySpec("C-z", "Control Z") { control('z') },
            )
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Keys", style = MaterialTheme.typography.titleMedium)
                    if (ctrl) Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            "ctrl locked",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = toggleKeyboard, modifier = Modifier.size(36.dp)) {
                        Icon(if (keyboardVisible) Icons.Outlined.KeyboardHide else Icons.Outlined.Keyboard,
                            if (keyboardVisible) "Hide keyboard" else "Show keyboard",
                            modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.ExpandMore, "Hide extra keys", modifier = Modifier.size(20.dp))
                    }
                }
                KeySectionLabel("Modifiers")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DockKey("ctrl", "Control", selected = ctrl, modifier = Modifier.weight(1f)) { onCtrl(!ctrl) }
                    DockKey("alt", "Alt", selected = alt, modifier = Modifier.weight(1f)) { alt = !alt }
                    DockKey("shift", "Shift", selected = shift, modifier = Modifier.weight(1f)) { shift = !shift }
                    DockKey("esc", "Escape", modifier = Modifier.weight(1f)) { press(TerminalKey.Escape) }
                    DockKey("tab", "Tab", modifier = Modifier.weight(1f)) { press(TerminalKey.Tab) }
                }
                KeySectionLabel("Navigation · symbols · editing")
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(keys.size, key = { keys[it].description }, contentType = { "terminal-key" }) { index ->
                        val key = keys[index]
                        DockKey(key.label, key.description, selected = key.selected,
                            modifier = Modifier.fillMaxWidth(), minWidth = 0.dp, onClick = key.onClick)
                    }
                }
                KeySectionLabel("Input")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DockKey("space", "Space", modifier = Modifier.weight(2f), minWidth = 0.dp) {
                        onBytes(" ".toByteArray()); onCtrl(false); shift = false; alt = false
                    }
                    DockKey("enter", "Enter", modifier = Modifier.weight(1f), minWidth = 0.dp) { press(TerminalKey.Enter) }
                    DockKey("del", "Delete", modifier = Modifier.weight(1f), minWidth = 0.dp) { press(TerminalKey.Delete) }
                }
                Text(
                    "Long-press a modifier to lock it. Sticky keys stay lit.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A quiet mono heading inside the expanded key panel. */
@Composable
private fun KeySectionLabel(text: String) {
    Text(
        text.uppercase(),
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
                    selected: Boolean = false, minWidth: Dp = 48.dp, onClick: () -> Unit) {
    Surface(modifier = modifier.heightIn(min = 48.dp).widthIn(min = minWidth),
        onClick = onClick, shape = MaterialTheme.shapes.small,
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
