@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.library.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.library.data.Book

@Composable
fun EditBookScreen(
    mode: EditMode,
    original: Book?,
    extraColumns: List<String>,
    defaultLocation: String,
    onBack: () -> Unit,
    onSave: (Book) -> Unit,
) {
    val start = remember(mode, original) {
        if (mode == EditMode.ADD || original == null) Book(location = defaultLocation) else original
    }
    var title by rememberSaveable { mutableStateOf(start.title) }
    var author by rememberSaveable { mutableStateOf(start.author) }
    var year by rememberSaveable { mutableStateOf(start.year) }
    var location by rememberSaveable { mutableStateOf(start.location) }
    var comments by rememberSaveable { mutableStateOf(start.comments) }
    val extras = remember {
        mutableStateMapOf<String, String>().apply { extraColumns.forEach { put(it, start.extra[it].orEmpty()) } }
    }
    var triedSave by remember { mutableStateOf(false) }
    val locationOptions = remember(start) {
        val base = listOf("44", "71")
        val current = start.location.trim()
        if (current.isNotEmpty() && current !in base) base + current else base
    }

    val save = {
        triedSave = true
        if (title.isNotBlank()) {
            onSave(
                start.copy(
                    id = if (mode == EditMode.MODIFY) start.id else "",
                    title = title.trim(),
                    author = author.trim(),
                    year = year.trim(),
                    location = location.trim(),
                    comments = comments.trim(),
                    extra = extras.toMap(),
                )
            )
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (mode) {
                            EditMode.ADD -> "Add a book"
                            EditMode.MODIFY -> "Modify book"
                            EditMode.DUPLICATE -> "Duplicate book"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = { TextButton(onClick = { save() }) { Text("Save") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (mode == EditMode.DUPLICATE) {
                Text(
                    "A copy of “${original?.title.orEmpty()}”. Change what's different (for example the location) and save.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val titleError = triedSave && title.isBlank()
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title *") },
                isError = titleError,
                supportingText = if (titleError) {
                    { Text("A title is needed") }
                } else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text("Author") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = year,
                onValueChange = { year = it },
                label = { Text("Year") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Location", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                locationOptions.forEachIndexed { i, option ->
                    SegmentedButton(
                        selected = location.trim() == option,
                        onClick = { location = option },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = locationOptions.size),
                    ) { Text(option) }
                }
            }

            OutlinedTextField(
                value = comments,
                onValueChange = { comments = it },
                label = { Text("Comments") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            extraColumns.forEach { col ->
                OutlinedTextField(
                    value = extras[col].orEmpty(),
                    onValueChange = { extras[col] = it },
                    label = { Text(col) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(4.dp))
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (mode == EditMode.MODIFY) "Save changes" else "Save book")
            }
        }
    }
}
