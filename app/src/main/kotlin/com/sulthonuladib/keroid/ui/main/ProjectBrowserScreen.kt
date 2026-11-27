package com.sulthonuladib.keroid.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sulthonuladib.keroid.ui.components.DetailBar
import com.sulthonuladib.keroid.ui.components.GroupDivider
import com.sulthonuladib.keroid.ui.components.GroupedCard
import com.sulthonuladib.keroid.ui.theme.KeroidMono
import com.sulthonuladib.keroid.ui.theme.keroid

/** Remote folder picker, like desktop's fs.list → project.create → create flow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectBrowserScreen(
    machineId: String,
    machineName: String,
    connection: MachineUiState,
    browser: ProjectBrowserState,
    action: SessionActionState,
    onBack: () -> Unit,
    onBrowse: (String, Boolean) -> Unit,
    onOpen: (String) -> Unit,
) {
    var path by rememberSaveable(machineId) { mutableStateOf("~") }
    var query by rememberSaveable(machineId) { mutableStateOf("") }
    var hidden by rememberSaveable(machineId) { mutableStateOf(false) }
    val directory = browser.takeIf { it.machineId == machineId }?.directory
    LaunchedEffect(machineId, connection.revision) { onBrowse(path, hidden) }
    LaunchedEffect(directory?.dir) {
        directory?.let { path = it.dir; query = "" }
    }
    val browse: (String) -> Unit = { onBrowse(it, hidden) }
    Column(Modifier.fillMaxSize()) {
        DetailBar("Open project", onBack, subtitle = machineName)
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (!connection.connected) {
                Text("Reconnect to this machine to browse its folders.")
                return@Column
            }
            OutlinedTextField(
                value = path, onValueChange = { path = it }, singleLine = true,
                label = { Text("Remote folder") }, modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { browse(path) }),
                trailingIcon = { TextButton(onClick = { browse(path) }, enabled = !action.busy) { Text("Go") } },
            )
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("Filter folders") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = MaterialTheme.shapes.medium,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { browse(directory?.parent.orEmpty()) },
                    enabled = !directory?.parent.isNullOrEmpty() && !browser.loading && !action.busy,
                ) {
                    Icon(Icons.Outlined.ArrowUpward, null, Modifier.size(18.dp))
                    Text("Parent folder", Modifier.padding(start = 6.dp))
                }
                Surface(
                    onClick = {
                        hidden = !hidden
                        onBrowse(directory?.dir ?: path, hidden)
                    },
                    enabled = !action.busy,
                    shape = RoundedCornerShape(10.dp),
                    color = if (hidden) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.keroid.raised,
                    contentColor = if (hidden) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text("Hidden", Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLarge)
                }
            }
            if (browser.loading || action.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            browser.error?.let { ErrorCard(it) }
            action.error?.let { ErrorCard(it) }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp, top = 8.dp)) {
                val folders = directory?.entries.orEmpty().filter { it.dir && it.name.contains(query, ignoreCase = true) }
                if (folders.isEmpty() && !browser.loading) item {
                    Text("No matching folders.", Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (folders.isNotEmpty()) item(key = "folder-card") {
                    GroupedCard {
                        folders.forEachIndexed { index, folder ->
                            if (index > 0) GroupDivider()
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                    .clickable(enabled = !browser.loading && !action.busy) {
                                        directory?.let { browse(it.child(folder.name)) }
                                    }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    if (folder.repo) Icons.Outlined.Source else Icons.Outlined.Folder,
                                    null,
                                    Modifier.size(22.dp),
                                    tint = if (folder.repo) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(folder.name, style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (folder.repo) Text("Git repository",
                                        fontFamily = KeroidMono,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary)
                                    else Text("Folder", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Button(
                onClick = { directory?.let { onOpen(it.dir) } },
                enabled = directory != null && !browser.loading && browser.error == null && !action.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(bottom = 8.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(if (action.busy) "Opening…" else "Open folder and start session")
            }
        }
    }
}
