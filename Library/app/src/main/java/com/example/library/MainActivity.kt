package com.example.library

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.library.data.BookQuery
import com.example.library.ui.EditBookScreen
import com.example.library.ui.LibraryScreen
import com.example.library.ui.LibraryTheme
import com.example.library.ui.LibraryViewModel
import com.example.library.ui.Screen
import com.example.library.ui.SignInScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.onAppVisible()
                vm.syncWhileVisible()
            }
        }

        setContent {
            LibraryTheme { App(vm) }
        }
    }
}

private val IMPORT_TYPES = arrayOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel",
    "text/csv",
    "text/comma-separated-values",
    "application/octet-stream",
)

@Composable
private fun App(vm: LibraryViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            snackbar.showSnackbar(msg, duration = if (msg.length > 60) SnackbarDuration.Long else SnackbarDuration.Short)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pickedImportFile(uri)
    }

    if (state.userName == null) {
        SignInScreen(knownNames = BookQuery.familyNames(state.books), onSignIn = { vm.signIn(it) })
    } else {
        when (val screen = state.screen) {
            Screen.Library -> LibraryScreen(
                state = state,
                vm = vm,
                snackbar = snackbar,
                onImportClick = { picker.launch(IMPORT_TYPES) },
            )
            is Screen.Edit -> EditBookScreen(
                mode = screen.mode,
                original = screen.original,
                extraColumns = state.extraColumns,
                defaultLocation = state.defaultLocation,
                onBack = { vm.closeEditor() },
                onSave = { vm.save(screen.mode, it) },
            )
        }
    }
}
