package com.example.library.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.library.Config
import com.example.library.data.ApiException
import com.example.library.data.Book
import com.example.library.data.ExcelReader
import com.example.library.data.LibrarySnapshot
import com.example.library.data.LocalStore
import com.example.library.data.SheetApi
import com.example.library.data.SheetTable
import com.example.library.data.SortOption
import com.example.library.data.UnsupportedFileException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class EditMode { ADD, MODIFY, DUPLICATE }

sealed interface Screen {
    data object Library : Screen
    data class Edit(val mode: EditMode, val original: Book?) : Screen
}

data class PendingImport(val fileName: String, val table: SheetTable)

data class UiState(
    val userName: String? = null,
    val books: List<Book> = emptyList(),
    val extraColumns: List<String> = emptyList(),
    val sheetUrl: String? = null,
    val loadedOnce: Boolean = false,
    val isRefreshing: Boolean = false,
    val isImporting: Boolean = false,
    val offline: Boolean = false,
    val search: String = "",
    val sort: SortOption = SortOption.RECENT,
    val locationFilter: String? = null,
    val addedByFilter: String? = null,

    val openedForSort: Map<String, Long> = emptyMap(),
    val screen: Screen = Screen.Library,
    val pendingImport: PendingImport? = null,
    val defaultLocation: String = "44",
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LocalStore(app)
    private val api = SheetApi(Config.SHEET_URL, Config.SHEET_TOKEN)
    private val apiLock = Mutex()
    private var knownVersion: String = "0"
    private var lastOpened: MutableMap<String, Long> = store.loadLastOpened().toMutableMap()

    private val _state = MutableStateFlow(
        UiState(
            userName = store.userName,
            openedForSort = lastOpened.toMap(),
            defaultLocation = store.lastLocation,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        store.loadCache()?.let { snap ->
            knownVersion = snap.version
            _state.update {
                it.copy(books = snap.books, extraColumns = snap.extraColumns, sheetUrl = snap.sheetUrl, loadedOnce = true)
            }
        }
    }

    fun signIn(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        store.userName = clean
        _state.update { it.copy(userName = clean) }
    }

    fun signOut() {
        store.userName = null
        _state.update { it.copy(userName = null, screen = Screen.Library) }
    }

    fun onAppVisible() {
        _state.update { it.copy(openedForSort = lastOpened.toMap()) }
    }

    suspend fun syncWhileVisible() {
        if (!Config.isConfigured) return
        refreshNow(userInitiated = false)
        var lastFull = System.currentTimeMillis()
        while (true) {
            delay(Config.POLL_INTERVAL_MS)
            try {
                val v = apiLock.withLock { api.version() }
                val stale = System.currentTimeMillis() - lastFull > Config.FULL_REFRESH_MS
                if (v != knownVersion || stale || _state.value.offline) {
                    refreshNow(userInitiated = false)
                    lastFull = System.currentTimeMillis()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(offline = true) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshNow(userInitiated = true) }
    }

    private suspend fun refreshNow(userInitiated: Boolean) {
        if (!Config.isConfigured) {
            if (userInitiated) _messages.send(NOT_CONFIGURED)
            return
        }
        if (userInitiated) _state.update { it.copy(isRefreshing = true) }
        try {
            val snap = apiLock.withLock { api.list() }
            applySnapshot(snap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(offline = true) }
            if (userInitiated) _messages.send(messageOf(e))
        } finally {
            _state.update { it.copy(isRefreshing = false, loadedOnce = true) }
        }
    }

    private fun applySnapshot(snap: LibrarySnapshot) {
        knownVersion = snap.version
        _state.update { s ->

            val pending = s.books.filter { it.isLocal }
            s.copy(
                books = snap.books + pending,
                extraColumns = snap.extraColumns,
                sheetUrl = snap.sheetUrl,
                offline = false,
            )
        }
        viewModelScope.launch(Dispatchers.IO) { store.saveCache(snap) }
    }

    fun setSearch(q: String) = _state.update { it.copy(search = q) }
    fun setLocationFilter(loc: String?) = _state.update { it.copy(locationFilter = loc) }
    fun setAddedByFilter(name: String?) = _state.update { it.copy(addedByFilter = name) }
    fun setSort(sort: SortOption) = _state.update { it.copy(sort = sort, openedForSort = lastOpened.toMap()) }

    fun markOpened(book: Book) {
        if (book.isLocal) return
        lastOpened[book.id] = System.currentTimeMillis()
        store.saveLastOpened(lastOpened)
    }

    fun startAdd() = _state.update { it.copy(screen = Screen.Edit(EditMode.ADD, null)) }
    fun startModify(book: Book) { markOpened(book); _state.update { it.copy(screen = Screen.Edit(EditMode.MODIFY, book)) } }
    fun startDuplicate(book: Book) { markOpened(book); _state.update { it.copy(screen = Screen.Edit(EditMode.DUPLICATE, book)) } }
    fun closeEditor() = _state.update { it.copy(screen = Screen.Library) }

    fun save(mode: EditMode, draft: Book) {
        val user = _state.value.userName ?: return
        if (!Config.isConfigured) {
            viewModelScope.launch { _messages.send(NOT_CONFIGURED) }
            return
        }
        store.lastLocation = draft.location.ifBlank { store.lastLocation }
        _state.update { it.copy(screen = Screen.Library, defaultLocation = store.lastLocation) }
        if (mode == EditMode.MODIFY) update(draft, user) else add(draft, user)
    }

    private fun add(draft: Book, user: String) {
        val now = System.currentTimeMillis()
        val temp = draft.copy(
            id = Book.LOCAL_ID_PREFIX + UUID.randomUUID(),
            added = now, modified = now, addedBy = user, modifiedBy = user,
        )
        _state.update { it.copy(books = it.books + temp) }
        viewModelScope.launch {
            try {
                val id = apiLock.withLock { api.add(draft, user) }
                _state.update { s -> s.copy(books = s.books.map { if (it.id == temp.id) temp.copy(id = id) else it }) }
                _messages.send("Added “${draft.title.trim()}”")
                refreshNow(userInitiated = false)
            } catch (e: Exception) {
                _state.update { s -> s.copy(books = s.books.filterNot { it.id == temp.id }) }
                _messages.send("Couldn't add the book. ${messageOf(e)}")
            }
        }
    }

    private fun update(draft: Book, user: String) {
        val before = _state.value.books.firstOrNull { it.id == draft.id } ?: run {
            viewModelScope.launch { _messages.send("This book was just deleted by someone else.") }
            return
        }
        val edited = draft.copy(modified = System.currentTimeMillis(), modifiedBy = user)
        _state.update { s -> s.copy(books = s.books.map { if (it.id == draft.id) edited else it }) }
        viewModelScope.launch {
            try {
                apiLock.withLock { api.update(draft, user) }
                _messages.send("Saved changes")
                refreshNow(userInitiated = false)
            } catch (e: Exception) {
                _state.update { s -> s.copy(books = s.books.map { if (it.id == draft.id) before else it }) }
                _messages.send("Couldn't save the changes. ${messageOf(e)}")
            }
        }
    }

    fun delete(book: Book) {
        val user = _state.value.userName ?: return
        if (book.isLocal) return
        _state.update { s -> s.copy(books = s.books.filterNot { it.id == book.id }) }
        viewModelScope.launch {
            try {
                apiLock.withLock { api.delete(book.id, user) }
                _messages.send("Deleted “${book.title}”")
                refreshNow(userInitiated = false)
            } catch (e: Exception) {
                _state.update { s -> s.copy(books = s.books + book) }
                _messages.send("Couldn't delete the book. ${messageOf(e)}")
            }
        }
    }

    fun pickedImportFile(uri: Uri) {
        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                val (name, table) = withContext(Dispatchers.IO) {
                    val name = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "file"
                    val table = app.contentResolver.openInputStream(uri)?.use { ExcelReader.read(it, name) }
                        ?: throw UnsupportedFileException("Couldn't open the file.")
                    name to table
                }
                val rows = table.rows.count { r -> r.any { it.isNotBlank() } }
                if (table.headers.all { it.isBlank() } || rows == 0) {
                    _messages.send("No books found in $name. The first row should hold the column names.")
                } else {
                    _state.update { it.copy(pendingImport = PendingImport(name, table)) }
                }
            } catch (e: UnsupportedFileException) {
                _messages.send(e.message ?: "Unsupported file")
            } catch (e: Exception) {
                _messages.send("Couldn't read the file: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun cancelImport() = _state.update { it.copy(pendingImport = null) }

    fun confirmImport() {
        val pending = _state.value.pendingImport ?: return
        val user = _state.value.userName ?: return
        if (!Config.isConfigured) {
            viewModelScope.launch { _messages.send(NOT_CONFIGURED) }
            return
        }
        _state.update { it.copy(pendingImport = null, isImporting = true) }
        viewModelScope.launch {
            try {
                val r = apiLock.withLock { api.import(pending.table, user) }
                val parts = buildList {
                    add("${r.added} new book${if (r.added == 1) "" else "s"} added")
                    if (r.updated > 0) add("${r.updated} updated")
                    if (r.skipped > 0) add("${r.skipped} skipped (already there or no title)")
                }
                _messages.send(parts.joinToString(", "))
                refreshNow(userInitiated = false)
            } catch (e: Exception) {
                _messages.send("Import failed. ${messageOf(e)}")
            } finally {
                _state.update { it.copy(isImporting = false) }
            }
        }
    }

    private fun messageOf(e: Exception): String = when (e) {
        is ApiException -> e.message ?: "Something went wrong"
        else -> e.message ?: "Something went wrong"
    }

    companion object {
        const val NOT_CONFIGURED = "The app isn't connected to the Google Sheet yet (see SETUP.md)."
    }
}
