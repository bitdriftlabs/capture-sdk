// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.bitdrift.gradletestapp.storage.StorageLabFiles
import io.bitdrift.gradletestapp.ui.designsystem.BdButtonSize
import io.bitdrift.gradletestapp.ui.designsystem.BdDropdownField
import io.bitdrift.gradletestapp.ui.designsystem.BdPrimaryButton
import io.bitdrift.gradletestapp.ui.designsystem.BdSecondaryButton
import io.bitdrift.gradletestapp.ui.designsystem.BdSectionCard
import io.bitdrift.gradletestapp.ui.designsystem.BdTextField
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

private enum class StorageDirectory { Caches, Files, NoBackup }

private enum class PreferenceType { String, Boolean, Int, Long, Float, StringSet }

private data class StorageSnapshot(
    val preferences: Map<String, Any?>,
    val files: List<StorageLabFiles.Entry>,
)

@Composable
fun StorageLabScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var fileName by rememberSaveable { mutableStateOf("storage_lab") }
    var key by rememberSaveable { mutableStateOf("demo.message") }
    var value by rememberSaveable { mutableStateOf("Hello from Android") }
    var type by rememberSaveable { mutableStateOf(PreferenceType.String) }
    var directory by rememberSaveable { mutableStateOf(StorageDirectory.Caches) }
    var path by rememberSaveable { mutableStateOf("sample.txt") }
    var size by rememberSaveable { mutableStateOf("4") }
    var snapshot by remember { mutableStateOf(StorageSnapshot(emptyMap(), emptyList())) }
    var message by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<StorageLabFiles.Entry?>(null) }
    var expanded by remember(directory) { mutableStateOf(setOf<String>()) }

    fun rootFor(selection: StorageDirectory) =
        File(
            when (selection) {
                StorageDirectory.Caches -> context.cacheDir
                StorageDirectory.Files -> context.filesDir
                StorageDirectory.NoBackup -> context.noBackupFilesDir
            },
            "StorageLab",
        )
    val root = rootFor(directory)

    fun preferences() = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)

    suspend fun reload() {
        val selectedName = fileName
        val selectedDirectory = directory
        val selectedRoot = rootFor(selectedDirectory)
        val data =
            withContext(Dispatchers.IO) {
                StorageSnapshot(
                    if (selectedName.isBlank()) emptyMap() else context.getSharedPreferences(selectedName, 0).all.toMap(),
                    StorageLabFiles(selectedRoot).entries(),
                )
            }
        if (selectedName == fileName && selectedDirectory == directory) snapshot = data
    }

    fun perform(
        success: String = "Refreshed.",
        action: () -> Unit = {},
    ) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { action() }
                reload()
                message = success
                failed = false
            } catch (error: Exception) {
                message = error.message ?: "Storage operation failed."
                failed = true
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(fileName, directory) {
        try {
            reload()
        } catch (error: Exception) {
            message = error.message ?: "Could not read storage."
            failed = true
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, fileName, directory) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) perform()
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BitdriftColors.Background)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BdSecondaryButton("Back", onBack, size = BdButtonSize.Compact)
            Text("Storage Lab", color = BitdriftColors.TextBright, modifier = Modifier.weight(1f).padding(12.dp))
            BdSecondaryButton("Refresh", { perform() }, enabled = !busy, size = BdButtonSize.Compact)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (message.isNotBlank()) {
                item {
                    Text(message, color = if (failed) BitdriftColors.Error else BitdriftColors.TextSecondary)
                }
            }
            item {
                BdSectionCard(title = "SharedPreferences", subtitle = "Remote settings commands use file_name, key and value.") {
                    BdTextField("Preferences file (file_name)", fileName, { fileName = it }, Modifier.fillMaxWidth(), enabled = !busy)
                    BdTextField("Key", key, { key = it }, Modifier.fillMaxWidth(), enabled = !busy)
                    BdDropdownField("Type", type, PreferenceType.entries, { type = it }, Modifier.fillMaxWidth())
                    BdTextField("Value (StringSet: JSON string array)", value, { value = it }, Modifier.fillMaxWidth(), enabled = !busy)
                    BdPrimaryButton("Set value", {
                        perform("Saved $key.") {
                            require(fileName.isNotBlank() && key.isNotEmpty()) { "Enter a preferences file and key." }
                            val editor = preferences().edit()
                            when (type) {
                                PreferenceType.String -> editor.putString(key, value)
                                PreferenceType.Boolean -> editor.putBoolean(key, value.toBooleanStrict())
                                PreferenceType.Int -> editor.putInt(key, value.toInt())
                                PreferenceType.Long -> editor.putLong(key, value.toLong())
                                PreferenceType.Float -> editor.putFloat(key, value.toFloat().also { require(it.isFinite()) })
                                PreferenceType.StringSet -> {
                                    val array = JSONArray(value)
                                    val strings =
                                        (0 until array.length())
                                            .map {
                                                require(array.get(it) is String) { "StringSet accepts only strings." }
                                                array.getString(it)
                                            }.toSet()
                                    editor.putStringSet(key, strings)
                                }
                            }
                            check(editor.commit()) { "Could not persist preferences." }
                        }
                    }, Modifier.fillMaxWidth(), enabled = !busy)
                    BdSecondaryButton("Add sample values", {
                        perform("Added demo.message, demo.enabled and demo.count.") {
                            require(fileName.isNotBlank()) { "Enter a preferences file." }
                            check(
                                preferences()
                                    .edit()
                                    .putString("demo.message", "Hello from Android")
                                    .putBoolean("demo.enabled", true)
                                    .putLong("demo.count", 42)
                                    .commit(),
                            )
                        }
                    }, Modifier.fillMaxWidth(), enabled = !busy)
                }
            }
            items(snapshot.preferences.entries.sortedBy { it.key }, key = { "pref:${it.key}" }) { entry ->
                BdSectionCard(title = entry.key) {
                    SelectionContainer {
                        Text("${entry.value?.javaClass?.simpleName}: ${entry.value}", color = BitdriftColors.TextPrimary)
                    }
                    BdSecondaryButton("Remove", {
                        perform("Removed ${entry.key}.") { check(preferences().edit().remove(entry.key).commit()) }
                    }, enabled = !busy)
                }
            }
            if (snapshot.preferences.isEmpty()) item { Text("No persisted preferences.", color = BitdriftColors.TextSecondary) }
            item {
                BdSectionCard(title = "Files", subtitle = "All lab files stay inside a StorageLab subdirectory.") {
                    BdDropdownField("OS directory", directory, StorageDirectory.entries, { directory = it }, Modifier.fillMaxWidth())
                    SelectionContainer { Text(root.path, color = BitdriftColors.TextSecondary) }
                    Text(
                        if (directory ==
                            StorageDirectory.Caches
                        ) {
                            "Remote path: StorageLab/sample.txt. Use recursive: true for directories."
                        } else {
                            "Built-in file commands only access Caches. This directory is for comparison."
                        },
                        color = BitdriftColors.TextSecondary,
                    )
                    BdTextField("Relative path (e.g. images/icons/logo.txt)", path, { path = it }, Modifier.fillMaxWidth(), enabled = !busy)
                    BdTextField("File size in KiB (1–10240)", size, { size = it }, Modifier.fillMaxWidth(), enabled = !busy)
                    BdPrimaryButton("Create file", {
                        perform("Created $path.") { StorageLabFiles(root).create(path, size.toInt(), false) }
                    }, Modifier.fillMaxWidth(), enabled = !busy)
                    BdSecondaryButton("Create directory", {
                        perform("Created $path.") { StorageLabFiles(root).create(path, 1, true) }
                    }, Modifier.fillMaxWidth(), enabled = !busy)
                }
            }
            item {
                BdSectionCard(
                    title = "StorageLab",
                    subtitle = "${snapshot.files.sumOf { it.bytes }} bytes · Refresh after remote commands.",
                ) {
                    if (snapshot.files.isEmpty()) {
                        Text(
                            "No lab files. The directory may be empty or deleted.",
                            color = BitdriftColors.TextSecondary,
                        )
                    }
                    StorageTree(
                        snapshot.files.groupBy { it.path.substringBeforeLast('/', "") },
                        "",
                        0,
                        expanded,
                        onToggle = { expanded = if (it in expanded) expanded - it else expanded + it },
                        onCopy = { clipboard.setText(AnnotatedString("StorageLab/${it.path}")) },
                        onDelete = { pendingDelete = it },
                        enabled = !busy,
                    )
                }
            }
        }
    }
    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${entry.path}?") },
            text = { Text(if (entry.directory) "This removes the directory and all its contents." else "This removes the lab file.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    perform("Deleted ${entry.path}.") { StorageLabFiles(root).delete(entry.path) }
                }) { Text("Delete", color = BitdriftColors.Error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StorageTree(
    children: Map<String, List<StorageLabFiles.Entry>>,
    parent: String,
    depth: Int,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    onCopy: (StorageLabFiles.Entry) -> Unit,
    onDelete: (StorageLabFiles.Entry) -> Unit,
    enabled: Boolean,
) {
    for (entry in children[parent].orEmpty().sortedWith(compareByDescending<StorageLabFiles.Entry> { it.directory }.thenBy { it.path })) {
        key(entry.path) {
            var menu by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().padding(start = (depth * 20).dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).clickable(enabled = entry.directory) { onToggle(entry.path) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (entry.directory) {
                            if (entry.path in
                                expanded
                            ) {
                                Icons.Default.ExpandMore
                            } else {
                                Icons.Default.ChevronRight
                            }
                        } else {
                            Icons.Default.Description
                        },
                        contentDescription = if (entry.directory) "Expand or collapse ${entry.path}" else null,
                        tint = BitdriftColors.Primary,
                        modifier = Modifier.size(20.dp),
                    )
                    if (entry.directory) {
                        Icon(
                            Icons.Default.Folder,
                            null,
                            tint = BitdriftColors.Primary,
                            modifier = Modifier.padding(start = 4.dp).size(20.dp),
                        )
                    }
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(entry.path.substringAfterLast('/'), color = BitdriftColors.TextBright)
                        if (!entry.directory) Text("${entry.bytes} bytes · ${entry.bytes / 1024} KiB", color = BitdriftColors.TextSecondary)
                    }
                }
                Box {
                    IconButton(onClick = {
                        menu = true
                    }, enabled = enabled) { Icon(Icons.Default.MoreVert, "Actions for ${entry.path}", tint = BitdriftColors.TextSecondary) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Copy command path") }, onClick = {
                            menu = false
                            onCopy(entry)
                        })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = {
                            menu = false
                            onDelete(entry)
                        })
                    }
                }
            }
            if (entry.directory && entry.path in expanded) {
                if (children[entry.path].isNullOrEmpty()) {
                    Text(
                        "Empty folder",
                        Modifier.padding(start = ((depth + 1) * 20).dp),
                        color = BitdriftColors.TextSecondary,
                    )
                }
                StorageTree(children, entry.path, depth + 1, expanded, onToggle, onCopy, onDelete, enabled)
            }
        }
    }
}
