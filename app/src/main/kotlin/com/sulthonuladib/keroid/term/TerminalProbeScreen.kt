package com.sulthonuladib.keroid.term

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Offline fake attachment. No SSH, shell, saved machine or real pane is used. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalProbeScreen(
    onGeometry: (cols: Int, rows: Int) -> Unit = { _, _ -> },
    appearance: TerminalAppearance = TerminalAppearance(),
    dark: Boolean = true,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val session = remember { TerminalSession(80, 24, scope) }
    var keys by remember { mutableStateOf(false) }
    var ctrl by remember { mutableStateOf(false) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var resizes by remember { mutableIntStateOf(0) }
    var inputBytes by remember { mutableIntStateOf(0) }
    var inputContext by remember { mutableIntStateOf(0) }
    var showKeyboard by remember { mutableIntStateOf(0) }
    LaunchedEffect(session) { session.offer(DEMO.toByteArray()) }
    DisposableEffect(session) { onDispose { session.close() } }
    val input: (ByteArray) -> Unit = { bytes ->
        inputBytes += bytes.size
        session.scrollToBottom()
        // A deterministic fake PTY's echo, including erase and newline.
        val echo = buildString {
            for (ch in bytes.toString(Charsets.UTF_8)) when (ch) {
                '\u007f' -> append("\b \b")
                '\r', '\n' -> append("\r\n")
                else -> append(ch)
            }
        }
        session.offer(echo.toByteArray())
    }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(expandedHeight = 56.dp, title = {
                Column {
                    Text("Terminal test", style = MaterialTheme.typography.titleMedium)
                    Text("Offline · input $inputBytes B · sizes $resizes", style = MaterialTheme.typography.labelSmall)
                }
            }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    session.offer((1..200).joinToString("") { "history line %03d\r\n".format(it) }.toByteArray())
                }) { Text("200 lines") }
                TextButton(onClick = {
                    // Split a mirror-like restart into realistic wire-sized chunks.
                    val replay = "\u001b[?1049l\u001b[0m\u001b[H\u001b[2J\u001b[3J" +
                        (1..3000).joinToString("") { "replay line %04d\r\n".format(it) } + "Replay complete\r\n> "
                    scope.launch {
                        for (part in replay.toByteArray().asList().chunked(32768)) session.offer(part.toByteArray())
                    }
                }) { Text("Replay") }
                TextButton(onClick = { session.offer("\u001bc".toByteArray()) }) { Text("Clear") }
                TextButton(onClick = { session.scrollToBottom(); pan = Offset.Zero }) { Text("Live") }
            }
            TerminalDockLayout(Modifier.weight(1f).fillMaxWidth(), dock = {
                if (GhosttyTerminal.available) TerminalKeyBar(keys, { keys = !keys }, ctrl, { ctrl = it },
                    { session.input(it, input) }, Modifier.padding(8.dp),
                    onKey = { inputContext++; session.key(it, input) })
            }) { bottomOcclusion ->
                if (GhosttyTerminal.available) {
                    TerminalView(session, fitToScreen = false, pan = pan, bottomOcclusion = bottomOcclusion,
                        onPanClamped = { pan = it }, onTap = { showKeyboard++ },
                        fontFamily = terminalFontFamily(appearance.font),
                        fontSize = appearance.fontSize.sp,
                        lineHeight = appearance.lineHeightSp.sp,
                        theme = appearance.theme(dark),
                        onGrid = { cols, rows, _, _ ->
                            resizes++
                            onGeometry(cols, rows)
                            // Fake daemon confirmation. Only this adopts local geometry.
                            session.adoptSize(cols, rows)
                        })
                    TerminalInputLayer(ctrl, { session.input(it, input) }, { ctrl = false }, Modifier.fillMaxSize(),
                        resetContext = inputContext, showKeyboard = showKeyboard, onKey = { session.key(it, input) })
                } else Text(GhosttyTerminal.unavailableReason ?: "Native library missing", Modifier.padding(24.dp))
            }
        }
    }
}

private const val DEMO =
    "\u001b[1;36mkeroid\u001b[0m · offline terminal\r\n\r\n" +
        "\u001b[1mbold\u001b[0m  \u001b[3mitalic\u001b[0m  \u001b[4munderline\u001b[0m  \u001b[7mreverse\u001b[0m\r\n" +
        "\u001b[31mred\u001b[0m \u001b[32mgreen\u001b[0m \u001b[33myellow\u001b[0m \u001b[34mblue\u001b[0m " +
        "\u001b[35mmagenta\u001b[0m \u001b[36mcyan\u001b[0m\r\n" +
        "wide: 你好 éè → ✓\r\n\r\n" +
        "Tap to type. Drag down for history.\r\n" +
        "Long press and drag to select text.\r\n> "
