package com.sulthonuladib.keroid.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sulthonuladib.keroid.data.ThemeMode
import com.sulthonuladib.keroid.term.KeroWash
import com.sulthonuladib.keroid.term.TerminalAppearance
import com.sulthonuladib.keroid.term.TerminalFontChoice
import com.sulthonuladib.keroid.term.TerminalTheme
import com.sulthonuladib.keroid.term.terminalFontFamily
import com.sulthonuladib.keroid.ui.components.DetailBar
import com.sulthonuladib.keroid.ui.components.GroupDivider
import com.sulthonuladib.keroid.ui.components.GroupedCard
import com.sulthonuladib.keroid.ui.components.SectionLabel
import com.sulthonuladib.keroid.ui.components.SegmentedGroup
import com.sulthonuladib.keroid.ui.components.SettingsRow
import com.sulthonuladib.keroid.ui.theme.KeroidMono
import com.sulthonuladib.keroid.ui.theme.KeroidTheme
import com.sulthonuladib.keroid.ui.theme.keroid
import kotlin.math.roundToInt

/**
 * Appearance is one pair, shown as two terminal windows: choosing a light or
 * dark theme changes the interface and the terminal together. There is no
 * separate "app colour" setting.
 */
@Composable
fun AppearanceScreen(
    appearance: TerminalAppearance,
    dark: Boolean,
    mode: ThemeMode,
    onMode: (ThemeMode) -> Unit,
    onChange: (TerminalAppearance) -> Unit,
    onPickTheme: (dark: Boolean) -> Unit,
    onBack: () -> Unit,
    onTerminalType: () -> Unit,
) {
    val itemColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxSize()) {
        DetailBar(
            "Appearance",
            onBack,
            subtitle = "${appearance.darkTheme} · ${appearance.lightTheme.substringAfterLast(' ')}",
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.keroid.window,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Theme pair", style = MaterialTheme.typography.titleSmall)
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                when (mode) {
                                    ThemeMode.System -> "following system"
                                    ThemeMode.Light -> "light"
                                    ThemeMode.Dark -> "dark"
                                },
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                fontFamily = KeroidMono,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(
                            onClick = {
                                onChange(appearance.copy(lightTheme = appearance.darkTheme, darkTheme = appearance.lightTheme))
                            },
                            shape = CircleShape,
                            color = MaterialTheme.keroid.raised,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.SwapVert, "Swap light and dark themes", Modifier.size(18.dp))
                            }
                        }
                    }
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ThemeMini(
                            name = appearance.lightTheme,
                            theme = TerminalAppearance.resolveTheme(appearance.lightTheme, false),
                            label = "${appearance.lightTheme.substringAfterLast(' ')} · light",
                            modifier = Modifier.weight(1f),
                        )
                        ThemeMini(
                            name = appearance.darkTheme,
                            theme = TerminalAppearance.resolveTheme(appearance.darkTheme, true),
                            label = "${appearance.darkTheme.substringAfterLast(' ')} · dark",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Text(
                "One palette for both surfaces. Switching fades in 320 ms.",
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = itemColor,
            )
            SegmentedGroup(
                options = listOf("System", "Light", "Dark"),
                selected = mode.ordinal,
                onSelect = { onMode(ThemeMode.entries[it]) },
                modifier = Modifier.padding(top = 10.dp),
            )
            SectionLabel("Theme pair")
            GroupedCard {
                ThemeRow("Light theme", appearance.lightTheme, dark = false) { onPickTheme(false) }
                GroupDivider()
                ThemeRow("Dark theme", appearance.darkTheme, dark = true) { onPickTheme(true) }
            }
            SectionLabel("One source, two surfaces")
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.keroid.window,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EngineChip("Terminal", Icons.Outlined.Terminal)
                        Text("·", color = itemColor.copy(alpha = 0.5f))
                        Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        Text("Interface", style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        "Both resolve from the same slot, so the terminal and the chrome cannot drift apart.",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = itemColor,
                    )
                }
            }
            SettingsRow(
                icon = Icons.Outlined.Terminal,
                title = "Terminal type",
                subtitle = "${appearance.font.label} · ${appearance.fontSize.roundToInt()} sp",
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = onTerminalType,
                modifier = Modifier.padding(top = 10.dp)
                    .background(MaterialTheme.keroid.window, RoundedCornerShape(24.dp)),
            )
            TextButton(
                onClick = {
                    onChange(
                        appearance.copy(
                            lightTheme = TerminalAppearance.DEFAULT_LIGHT,
                            darkTheme = TerminalAppearance.DEFAULT_DARK,
                        ),
                    )
                },
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 18.dp),
            ) {
                Text("Reset to Catppuccin")
            }
        }
    }
}

@Composable
private fun EngineChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier.height(32.dp).background(MaterialTheme.keroid.raised, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ThemeRow(title: String, name: String, dark: Boolean, onClick: () -> Unit) {
    val theme = TerminalAppearance.resolveTheme(name, dark)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable(onClick = onClick)
            .semantics { contentDescription = "$title $name" }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ThemeSwatch(theme)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ThemeSwatch(theme: TerminalTheme) {
    Row(
        Modifier.size(48.dp, 36.dp).clip(RoundedCornerShape(12.dp))
            .background(Color(theme.background))
            .border(1.dp, MaterialTheme.keroid.hairline, RoundedCornerShape(12.dp)),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(theme.palette[1], theme.palette[2], theme.palette[3], theme.palette[4]).forEach {
            Box(Modifier.size(5.dp).clip(CircleShape).background(Color(it)))
        }
    }
}

/** The two terminal windows in the pair hero, drawn locally. */
@Composable
private fun ThemeMini(name: String, theme: TerminalTheme, label: String, modifier: Modifier = Modifier) {
    val dark = theme.isDark
    Surface(
        modifier.height(96.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(theme.background),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(theme.foreground).copy(alpha = 0.10f)),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(Color(theme.palette[2])))
                Text(
                    "codex",
                    fontFamily = KeroidMono,
                    fontSize = 7.5.sp,
                    color = Color(theme.foreground).copy(alpha = 0.75f),
                )
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color(theme.palette[2]))) { append("› ") }
                    append("./gradlew test\n")
                    withStyle(SpanStyle(color = Color(theme.palette[4]))) { append("ok ") }
                    append("24 passed\n")
                    append("~/keroid")
                },
                fontFamily = KeroidMono,
                fontSize = 8.2.sp,
                lineHeight = 13.sp,
                color = Color(theme.foreground).copy(alpha = if (dark) 0.9f else 1f),
                maxLines = 4,
            )
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, fontFamily = KeroidMono, fontSize = 9.sp,
                    color = Color(theme.foreground).copy(alpha = 0.7f))
            }
        }
    }
}

/**
 * Terminal type: the live preview, the two faces the APK actually carries, and
 * size/line-height steppers. No theme rows here — those live in Appearance.
 */
@Composable
fun TerminalTypeScreen(
    appearance: TerminalAppearance,
    dark: Boolean,
    onChange: (TerminalAppearance) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        DetailBar(
            "Terminal type",
            onBack,
            subtitle = "${appearance.font.label} · ${appearance.fontSize.roundToInt()} sp",
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            TerminalPreview(appearance, appearance.theme(dark))
            SectionLabel("Font")
            GroupedCard {
                TerminalFontChoice.entries.forEachIndexed { index, choice ->
                    if (index > 0) GroupDivider()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { onChange(appearance.copy(font = choice)) }
                            .semantics { contentDescription = "${choice.label} font" }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(choice.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (choice == TerminalFontChoice.Bundled) "Bundled Nerd Font, real bold and italic"
                                else "Android's built-in monospace face",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (choice == appearance.font) Icon(
                            Icons.Outlined.Check,
                            "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                        )
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
    Surface(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(18.dp),
        color = Color(theme.background),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.keroid.hairline)) {
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text(value, fontFamily = KeroidMono, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onChange((current - step).coerceIn(min, max)) },
                enabled = current > min + step / 2f,
                modifier = Modifier.semantics { contentDescription = "Decrease $title" }) { Text("−") }
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
 * Search-first theme list with real palette swatches. Kero's own theme and the
 * Ghostty catalogue share one list because both are terminal themes.
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
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val names = remember { TerminalAppearance.themeNames }
    val matches = remember(query, filter) {
        names.filter { name ->
            val group = when {
                name == TerminalAppearance.KEROID || KeroWash.byName(name) != null -> 1
                else -> 2
            }
            val groupOk = filter == 0 || filter == group
            groupOk && (query.isBlank() || name.contains(query.trim(), ignoreCase = true))
        }
    }
    Column(Modifier.fillMaxSize()) {
        DetailBar(
            if (dark) "Dark theme" else "Light theme",
            onBack,
            subtitle = "Used when the app is ${if (dark) "dark" else "light"}",
        )
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
            shape = RoundedCornerShape(18.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.keroid.sunken,
                unfocusedContainerColor = MaterialTheme.keroid.sunken,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        SegmentedGroup(
            options = listOf("All", "Kero", "Ghostty"),
            counts = listOf(names.size, KeroWash.all.size + 1, names.size - KeroWash.all.size - 1),
            selected = filter,
            onSelect = { filter = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (matches.isEmpty()) {
            Text("No theme matches “$query”.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            val kero = matches.filter { it == TerminalAppearance.KEROID || KeroWash.byName(it) != null }
            val ghostty = matches.filterNot { it in kero }
            if (kero.isNotEmpty()) {
                item(key = "kero-label") {
                    SectionLabel("Kero washes · ${kero.size}", Modifier.padding(horizontal = 16.dp))
                }
                items(kero, key = { "k:$it" }) { name ->
                    ThemeRow(name, TerminalAppearance.resolveTheme(name, dark), name == selected,
                        dark = dark) {
                        onSelect(name)
                        onBack()
                    }
                }
            }
            if (ghostty.isNotEmpty()) {
                item(key = "ghostty-label") {
                    SectionLabel("Ghostty · ${ghostty.size}", Modifier.padding(horizontal = 16.dp))
                }
                items(ghostty, key = { "g:$it" }) { name ->
                    ThemeRow(name, TerminalAppearance.resolveTheme(name, dark), name == selected,
                        dark = dark) {
                        onSelect(name)
                        onBack()
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeRow(
    name: String,
    theme: TerminalTheme,
    selected: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val wash = KeroWash.byName(name)
    val swatch = if (wash != null) {
        Brush.linearGradient(wash.gradient(dark).map { Color(it) })
    } else {
        Brush.linearGradient(listOf(Color(theme.background), Color(theme.background)))
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick)
        .semantics { contentDescription = "$name theme" }
        .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.width(64.dp).height(32.dp).clip(RoundedCornerShape(10.dp))
            .background(swatch)
            .border(1.dp, MaterialTheme.keroid.hairline, RoundedCornerShape(10.dp))
            .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(theme.foreground, theme.palette[1], theme.palette[2], theme.palette[4]).forEach {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Color(it)))
            }
        }
        Text(name, style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (theme.isDark) "dark" else "light", fontFamily = KeroidMono, fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (selected) Icon(Icons.Outlined.Check, contentDescription = "Selected",
            tint = MaterialTheme.colorScheme.primary)
    }
}

@Preview(name = "Appearance · dark", widthDp = 390, heightDp = 844,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppearancePreview() {
    KeroidTheme(dynamicColor = false) {
        AppearanceScreen(
            appearance = TerminalAppearance(), dark = true, mode = ThemeMode.System,
            onMode = {}, onChange = {}, onPickTheme = {}, onBack = {}, onTerminalType = {},
        )
    }
}

@Preview(name = "Theme picker · dark", widthDp = 390, heightDp = 844,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PickerPreview() {
    KeroidTheme(dynamicColor = false) {
        TerminalThemePickerScreen(dark = true, selected = TerminalAppearance.DEFAULT_DARK, onSelect = {}, onBack = {})
    }
}
