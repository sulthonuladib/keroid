package com.sulthonuladib.keroid.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sulthonuladib.keroid.term.TerminalAppearance
import com.sulthonuladib.keroid.term.TerminalFontChoice
import com.sulthonuladib.keroid.term.TerminalTheme
import com.sulthonuladib.keroid.term.terminalFontFamily
import com.sulthonuladib.keroid.ui.theme.KeroidTheme
import kotlin.math.roundToInt

/**
 * Terminal-only appearance: two Ghostty themes (one per app appearance, as
 * desktop Kero has) and the terminal font. Never changes the app's Material
 * theme, and the preview draws locally so it cannot reach a host.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalAppearanceScreen(
    appearance: TerminalAppearance,
    dark: Boolean,
    onChange: (TerminalAppearance) -> Unit,
    onPickTheme: (dark: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Terminal appearance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TerminalPreview(appearance, appearance.theme(dark))
                SectionLabel("Theme")
                PickerRow("Dark theme", appearance.darkTheme, "Choose a dark terminal theme") {
                    onPickTheme(true)
                }
                PickerRow("Light theme", appearance.lightTheme, "Choose a light terminal theme") {
                    onPickTheme(false)
                }
                Text("The terminal uses the app's light or dark appearance to pick one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                SectionLabel("Font")
                TerminalFontChoice.entries.forEach { choice ->
                    Row(Modifier.fillMaxWidth().clickable {
                        onChange(appearance.copy(font = choice))
                    }.padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected = choice == appearance.font, onClick = null)
                        Column(Modifier.weight(1f)) {
                            Text(choice.label, style = MaterialTheme.typography.bodyLarge)
                            Text(if (choice == TerminalFontChoice.Bundled)
                                "Bundled Nerd Font, with real bold and italic faces."
                            else "The device's own monospace font.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Stepper("Font size", "${appearance.fontSize.roundToInt()} sp",
                    appearance.fontSize, TerminalAppearance.MIN_FONT_SIZE, TerminalAppearance.MAX_FONT_SIZE,
                    step = 1f) { onChange(appearance.copy(fontSize = it)) }
                Stepper("Line height", String.format(java.util.Locale.US, "%.2f×", appearance.lineHeight),
                    appearance.lineHeight, TerminalAppearance.MIN_LINE_HEIGHT, TerminalAppearance.MAX_LINE_HEIGHT,
                    step = 0.05f) { onChange(appearance.copy(lineHeight = it)) }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun PickerRow(title: String, value: String, description: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 14.dp)
        .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A term-preview of the theme and font: colours, bold, italic and a wide glyph. */
@Composable
fun TerminalPreview(appearance: TerminalAppearance, theme: TerminalTheme) {
    val palette = remember(theme) { theme.palette }
    val family = terminalFontFamily(appearance.font)
    val sample = remember(palette) {
        buildAnnotatedString {
            for (index in 0 until 8) {
                withStyle(SpanStyle(color = Color(palette[index]))) { append("███") }
                if (index != 7) append(' ')
            }
            append('\n')
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("bold") }
            append("  ")
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("italic") }
            append("  ")
            withStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)) {
                append("underline")
            }
            append("\n手 wide · 你好 · é → ✓\n")
            append("user@keroid:~$ ")
            withStyle(SpanStyle(color = Color(palette[2]))) { append("go test -race ./...") }
        }
    }
    Surface(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(12.dp),
        color = Color(theme.background),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Text(sample, modifier = Modifier.padding(12.dp).semantics {
            contentDescription = "Terminal theme preview"
        }, color = Color(theme.foreground), fontFamily = family,
            fontSize = appearance.fontSize.sp, lineHeight = appearance.lineHeightSp.sp)
    }
}

/** Whole-point steps keep stored values exact, so a slider cannot drift. */
@Composable
private fun Stepper(
    title: String,
    value: String,
    current: Float,
    min: Float,
    max: Float,
    step: Float,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onChange((current - step).coerceIn(min, max)) },
                enabled = current > min + step / 2f,
                modifier = Modifier.semantics { contentDescription = "Decrease $title" }) { Text("−") }
            // SliderState's range is fixed at construction, as the value is here.
            val state = remember(min, max) { SliderState(current, 0, min..max) }
            LaunchedEffect(current) { if (state.value != current) state.value = current }
            Slider(state = state, onValueChange = { onChange(it.coerceIn(min, max)) },
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            OutlinedButton(onClick = { onChange((current + step).coerceIn(min, max)) },
                enabled = current < max - step / 2f,
                modifier = Modifier.semantics { contentDescription = "Increase $title" }) { Text("+") }
        }
    }
}

/**
 * Search-first theme list, as the home screen is. Each row previews its own
 * colours, so choosing a theme needs no connection and no extra tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalThemePickerScreen(
    dark: Boolean,
    selected: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val names = remember { TerminalAppearance.themeNames }
    val matches = remember(query) {
        if (query.isBlank()) names
        else names.filter { it.contains(query, ignoreCase = true) }
    }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(if (dark) "Dark terminal theme" else "Light terminal theme") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Search ${names.size} themes") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (matches.isEmpty()) {
                    Text("No theme matches “$query”.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp))
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(matches) { name ->
                        ThemeRow(name = name,
                            theme = TerminalAppearance.resolveTheme(name, dark),
                            selected = name == selected,
                            onClick = {
                                onSelect(name)
                                onBack()
                            })
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeRow(name: String, theme: TerminalTheme, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick)
        .semantics { contentDescription = "$name theme" }
        .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // The theme's own background, a foreground sample and four palette hues.
        Row(Modifier.width(64.dp).height(32.dp).clip(RoundedCornerShape(6.dp))
            .background(Color(theme.background))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(theme.foreground, theme.colors[1], theme.colors[2], theme.colors[4]).forEach {
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(it)))
            }
        }
        Text(name, style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Outlined.Check, contentDescription = "Selected",
            tint = MaterialTheme.colorScheme.primary)
    }
}

@Preview(name = "Terminal appearance · dark", widthDp = 412, heightDp = 915,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TerminalAppearancePreview() {
    KeroidTheme(dynamicColor = false) {
        TerminalAppearanceScreen(TerminalAppearance(), dark = true, onChange = {}, onPickTheme = {}, onBack = {})
    }
}
