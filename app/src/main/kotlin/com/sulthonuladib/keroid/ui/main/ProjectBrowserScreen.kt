package com.sulthonuladib.keroid.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

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
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(title = { Column {
                Text("Open project")
                Text(machineName, style = MaterialTheme.typography.labelMedium)
            } }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).padding(horizontal = 16.dp)) {
            if (!connection.connected) {
                Text("Reconnect to this machine to browse its folders.")
                return@Column
            }
            OutlinedTextField(value = path, onValueChange = { path = it }, singleLine = true,
                label = { Text("Remote folder") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { browse(path) }),
                trailingIcon = { TextButton(onClick = { browse(path) }, enabled = !action.busy) { Text("Go") } })
            OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("Filter folders") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { browse(directory?.parent.orEmpty()) },
                    enabled = !directory?.parent.isNullOrEmpty() && !browser.loading && !action.busy) {
                    Icon(Icons.Outlined.ArrowUpward, null)
                    Text("Parent folder")
                }
                FilterChip(selected = hidden, onClick = {
                    hidden = !hidden
                    onBrowse(directory?.dir ?: path, hidden)
                }, enabled = !action.busy, label = { Text("Hidden") })
            }
            if (browser.loading || action.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            browser.error?.let { ErrorCard(it) }
            action.error?.let { ErrorCard(it) }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                val folders = directory?.entries.orEmpty().filter { it.dir && it.name.contains(query, ignoreCase = true) }
                if (folders.isEmpty() && !browser.loading) item { Text("No matching folders.", Modifier.padding(16.dp)) }
                items(folders, key = { it.name }) { folder ->
                    ListItem(
                        supportingContent = if (folder.repo) ({ Text("Git repository") }) else null,
                        leadingContent = { Icon(if (folder.repo) Icons.Outlined.Source else Icons.Outlined.Folder, null) },
                        modifier = Modifier.clickable(enabled = !browser.loading && !action.busy) {
                            directory?.let { browse(it.child(folder.name)) }
                        }) { Text(folder.name) }
                }
            }
            Button(onClick = { directory?.let { onOpen(it.dir) } },
                enabled = directory != null && !browser.loading && browser.error == null && !action.busy,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(if (action.busy) "Opening…" else "Open folder and start session")
            }
        }
    }
}
