@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.library.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.library.Config
import com.example.library.data.Book
import com.example.library.data.BookQuery
import com.example.library.data.SortOption
import java.text.DateFormat
import java.util.Date

@Composable
fun LibraryScreen(
    state: UiState,
    vm: LibraryViewModel,
    snackbar: SnackbarHostState,
    onImportClick: () -> Unit,
) {
    val visible = remember(state.books, state.search, state.locationFilter, state.addedByFilter, state.sort, state.openedForSort) {
        BookQuery.apply(state.books, state.search, state.locationFilter, state.addedByFilter, state.sort, state.openedForSort)
    }
    val locations = remember(state.books) {
        (listOf("44", "71") + state.books.map { it.location.trim() }.filter { it.isNotEmpty() }).distinct()
    }
    val names = remember(state.books) { BookQuery.familyNames(state.books) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<Book?>(null) }
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current

    LaunchedEffect(state.sort, state.locationFilter, state.addedByFilter, state.search, state.openedForSort) {
        listState.scrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Welcome, ${state.userName ?: ""}",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val count = "${state.books.count { !it.isLocal }} books"
                        Text(
                            if (state.offline) "$count · offline" else count,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (state.isImporting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    if (state.offline) {
                        Icon(Icons.Filled.CloudOff, contentDescription = "Offline", tint = MaterialTheme.colorScheme.error)
                    }
                    OverflowMenu(state, vm, onImportClick)
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.startAdd() }) {
                Icon(Icons.Filled.Add, contentDescription = "Add a book")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!Config.isConfigured) NotConfiguredBanner()

            OutlinedTextField(
                value = state.search,
                onValueChange = { vm.setSearch(it) },
                placeholder = { Text("Search title, author, notes…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.search.isNotEmpty()) {
                        IconButton(onClick = { vm.setSearch("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )

            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MenuChip(
                    label = "Sort: ${state.sort.label.substringBefore(" (")}",
                    icon = Icons.AutoMirrored.Filled.Sort,
                    active = state.sort != SortOption.RECENT,
                    options = SortOption.entries.map { it.label },
                    selectedIndex = state.sort.ordinal,
                    onSelect = { vm.setSort(SortOption.entries[it]) },
                )
                MenuChip(
                    label = "Location: ${state.locationFilter ?: "All"}",
                    icon = Icons.Filled.Place,
                    active = state.locationFilter != null,
                    options = listOf("All") + locations,
                    selectedIndex = state.locationFilter?.let { locations.indexOf(it) + 1 } ?: 0,
                    onSelect = { vm.setLocationFilter(if (it == 0) null else locations[it - 1]) },
                )
                if (names.isNotEmpty()) {
                    MenuChip(
                        label = "Added by: ${state.addedByFilter ?: "Anyone"}",
                        icon = Icons.Filled.Person,
                        active = state.addedByFilter != null,
                        options = listOf("Anyone") + names,
                        selectedIndex = state.addedByFilter?.let { names.indexOf(it) + 1 } ?: 0,
                        onSelect = { vm.setAddedByFilter(if (it == 0) null else names[it - 1]) },
                    )
                }
            }

            val filtering = state.search.isNotBlank() || state.locationFilter != null || state.addedByFilter != null
            if (filtering) {
                Text(
                    "${visible.size} of ${state.books.size} books",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { vm.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (!state.loadedOnce && Config.isConfigured) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (visible.isEmpty()) {
                            item {
                                EmptyState(
                                    if (filtering) "No books match." else "No books yet.\nTap + to add one, or import an Excel file from the ⋮ menu."
                                )
                            }
                        }
                        items(visible, key = { it.id }) { book ->
                            BookRow(book) {
                                focus.clearFocus()
                                vm.markOpened(book)
                                selectedId = book.id
                            }
                        }
                    }
                }
            }
        }
    }

    val selected = selectedId?.let { id -> state.books.firstOrNull { it.id == id } }
    if (selected != null) {
        BookDetailsSheet(
            book = selected,
            extraColumns = state.extraColumns,
            onDismiss = { selectedId = null },
            onDuplicate = { selectedId = null; vm.startDuplicate(selected) },
            onModify = { selectedId = null; vm.startModify(selected) },
            onDelete = { confirmDelete = selected },
        )
    }

    confirmDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text("Delete “${book.title}”?") },
            text = { Text("It will be removed from the Google Sheet and from everyone's phone.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = null; selectedId = null; vm.delete(book) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }

    state.pendingImport?.let { p ->
        val count = p.table.rows.count { r -> r.any { it.isNotBlank() } }
        AlertDialog(
            onDismissRequest = { vm.cancelImport() },
            icon = { Icon(Icons.Filled.UploadFile, contentDescription = null) },
            title = { Text("Import $count rows?") },
            text = {
                Text(
                    "From ${p.fileName}\nColumns: ${p.table.headers.filter { it.isNotBlank() }.joinToString(", ")}\n\n" +
                        "Books already in the library (same title, author and location) are skipped. " +
                        "Columns the Sheet doesn't have yet are added to it."
                )
            },
            confirmButton = { TextButton(onClick = { vm.confirmImport() }) { Text("Import") } },
            dismissButton = { TextButton(onClick = { vm.cancelImport() }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun OverflowMenu(state: UiState, vm: LibraryViewModel, onImportClick: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Menu") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Import books from Excel") },
                leadingIcon = { Icon(Icons.Filled.UploadFile, contentDescription = null) },
                onClick = { open = false; onImportClick() },
            )
            val url = state.sheetUrl
            if (url != null) {
                DropdownMenuItem(
                    text = { Text("Open the Google Sheet") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                    onClick = { open = false; runCatching { uriHandler.openUri(url) } },
                )
            }
            DropdownMenuItem(
                text = { Text("Refresh") },
                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                onClick = { open = false; vm.refresh() },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Not ${state.userName}? Change name") },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                onClick = { open = false; vm.signOut() },
            )
        }
    }
}

@Composable
private fun MenuChip(
    label: String,
    icon: ImageVector,
    active: Boolean,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = active,
            onClick = { open = true },
            label = { Text(label) },
            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { open = false; onSelect(i) },
                    trailingIcon = if (i == selectedIndex) {
                        { Icon(Icons.Filled.Check, contentDescription = "Selected") }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun BookRow(book: Book, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    book.title.ifBlank { "(no title)" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOf(book.author, book.year).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (book.comments.isNotBlank()) {
                    Text(
                        book.comments,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            if (book.isLocal) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else if (book.location.isNotBlank()) {
                LocationBadge(book.location)
            }
        }
    }
}

@Composable
fun LocationBadge(location: String) {
    val c = MaterialTheme.colorScheme
    val (bg, fg) = when (location.trim()) {
        "44" -> c.primaryContainer to c.onPrimaryContainer
        "71" -> c.tertiaryContainer to c.onTertiaryContainer
        else -> c.secondaryContainer to c.onSecondaryContainer
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(12.dp)) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Home, contentDescription = "Location", modifier = Modifier.size(14.dp))
            Text(location.trim(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BookDetailsSheet(
    book: Book,
    extraColumns: List<String>,
    onDismiss: () -> Unit,
    onDuplicate: () -> Unit,
    onModify: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(book.title.ifBlank { "(no title)" }, style = MaterialTheme.typography.headlineSmall)
                    if (book.author.isNotBlank()) {
                        Text("by ${book.author}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (book.location.isNotBlank()) {
                    Spacer(Modifier.width(12.dp))
                    LocationBadge(book.location)
                }
            }
            Spacer(Modifier.height(16.dp))
            DetailRow("Year", book.year)
            DetailRow("Location", book.location)
            DetailRow("Comments", book.comments)
            extraColumns.forEach { col ->
                val v = book.extra[col].orEmpty()
                if (v.isNotBlank()) DetailRow(col, v)
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(
                "Added ${formatDate(book.added)}" + if (book.addedBy.isNotBlank()) " by ${book.addedBy}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (book.modified > 0 && book.modified != book.added) {
                Text(
                    "Last modified ${formatDate(book.modified)}" + if (book.modifiedBy.isNotBlank()) " by ${book.modifiedBy}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))

            if (book.isLocal) {
                Text("Saving to the Google Sheet…", style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDuplicate, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Duplicate")
                    }
                    Button(onClick = onModify, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Modify")
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NotConfiguredBanner() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            "Not connected to the Google Sheet yet. Paste the Web App link into Config.kt and rebuild (see SETUP.md).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

private fun formatDate(ms: Long): String =
    if (ms <= 0) "—" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))
