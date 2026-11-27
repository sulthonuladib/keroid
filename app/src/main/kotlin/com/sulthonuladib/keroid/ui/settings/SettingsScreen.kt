package com.sulthonuladib.keroid.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sulthonuladib.keroid.data.ThemeMode
import com.sulthonuladib.keroid.term.TerminalAppearance
import com.sulthonuladib.keroid.ui.components.DetailBar
import com.sulthonuladib.keroid.ui.components.GroupDivider
import com.sulthonuladib.keroid.ui.components.GroupedCard
import com.sulthonuladib.keroid.ui.components.LargeHeader
import com.sulthonuladib.keroid.ui.components.SectionLabel
import com.sulthonuladib.keroid.ui.components.SettingsRow
import com.sulthonuladib.keroid.ui.theme.KeroidMono
import com.sulthonuladib.keroid.ui.theme.KeroidTheme
import com.sulthonuladib.keroid.ui.theme.keroid
import kotlin.math.roundToInt

private fun ThemeMode.label() = when (this) {
    ThemeMode.System -> "Follow system"
    ThemeMode.Light -> "Light"
    ThemeMode.Dark -> "Dark"
}

/**
 * Settings is a root tab, not a pushed screen: a 96 dp header and two grouped
 * lists. Everything that changes the look lives behind Appearance so the tab
 * stays short.
 */
@Composable
fun SettingsScreen(
    theme: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onTerminalTest: (() -> Unit)? = null,
    onTerminalAppearance: (() -> Unit)? = null,
    appearance: TerminalAppearance = TerminalAppearance(),
    showBack: Boolean = false,
    onTerminalType: (() -> Unit)? = null,
) {
    var about by remember { mutableStateOf(false) }
    var licences by remember { mutableStateOf(false) }
    var hostKeys by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "dev"
    }
    val pair = "${appearance.darkTheme} · ${appearance.lightTheme.substringAfterLast(' ')}"
    Column(Modifier.fillMaxSize()) {
        if (showBack) {
            DetailBar("Settings", onBack, subtitle = "Keroid $version · $pair")
        } else {
            LargeHeader(title = "Settings", titleTag = "settings-title", subtitle = {
                Text("Keroid $version · $pair", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            })
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 112.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "appearance-label") { SectionLabel("Appearance") }
            item(key = "appearance-card") {
                GroupedCard {
                    SettingsRow(
                        icon = Icons.Outlined.Palette,
                        title = "Theme",
                        subtitle = pair,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = onTerminalAppearance?.let { open ->
                            { onTheme(theme); open() }
                        },
                    )
                    GroupDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Terminal,
                        title = "Terminal type",
                        subtitle = "${appearance.font.label} · ${appearance.fontSize.roundToInt()} sp",
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = onTerminalType ?: onTerminalAppearance,
                    )
                    GroupDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Shield,
                        title = "Host keys",
                        subtitle = "Pinned on first connect · review trusted machines",
                        onClick = { hostKeys = true },
                    )
                    GroupDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Notifications,
                        title = "Notifications",
                        subtitle = "Needs you and finished runs",
                        trailing = {
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.keroid.raised) {
                                Text(
                                    "SOON",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    fontFamily = KeroidMono,
                                    fontSize = 8.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
            item(key = "about-label") { SectionLabel("About") }
            item(key = "about-card") {
                GroupedCard {
                    SettingsRow(
                        icon = Icons.Outlined.Info,
                        title = "About Keroid",
                        subtitle = "$version · Android client for Kero",
                        onClick = { about = true },
                    )
                    GroupDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Description,
                        title = "Licences",
                        subtitle = "Open source · fonts, Ghostty themes, libraries",
                        onClick = { licences = true },
                    )
                    if (onTerminalTest != null) {
                        GroupDivider()
                        SettingsRow(
                            icon = Icons.Outlined.Code,
                            title = "Developer",
                            subtitle = "Terminal renderer test",
                            onClick = onTerminalTest,
                        )
                    }
                }
            }
            item(key = "note") {
                Text(
                    "Changes save as you go. Nothing leaves the device except the connection itself.",
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 18.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (about) AlertDialog(
        onDismissRequest = { about = false },
        title = { Text("Keroid") },
        text = { Text("The Android client for Kero. Remote sessions, agents and PTYs stay on your machine; the phone is a window into them.\n\nVersion $version") },
        confirmButton = { TextButton(onClick = { about = false }) { Text("Close") } },
    )
    if (licences) AlertDialog(
        onDismissRequest = { licences = false },
        title = { Text("Licences") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Keroid is open source. Third-party notices are in the repository.")
                Text("Google Sans Flex — SIL Open Font License 1.1", style = MaterialTheme.typography.bodySmall)
                Text("JetBrains Mono Nerd Font — SIL Open Font License 1.1", style = MaterialTheme.typography.bodySmall)
                Text("Ghostty themes — MIT, and each theme keeps its author's copyright", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { licences = false }) { Text("Close") } },
    )
    if (hostKeys) AlertDialog(
        onDismissRequest = { hostKeys = false },
        title = { Text("Host keys") },
        text = {
            Text("A machine's host key is pinned the first time you connect. If it changes, Keroid blocks the connection and asks you to verify the fingerprint through another channel. There is no one-tap bypass.")
        },
        confirmButton = { TextButton(onClick = { hostKeys = false }) { Text("Close") } },
    )
}

@Preview(name = "Settings · dark", widthDp = 390, heightDp = 844, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Settings · light", widthDp = 390, heightDp = 844)
@Composable
private fun SettingsPreview() {
    KeroidTheme(dynamicColor = false) {
        SettingsScreen(theme = ThemeMode.System, onTheme = {}, onBack = {})
    }
}
