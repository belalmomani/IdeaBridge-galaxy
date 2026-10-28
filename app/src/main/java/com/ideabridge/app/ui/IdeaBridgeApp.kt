package com.ideabridge.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ideabridge.app.IdeasViewModel
import com.ideabridge.app.IdeaRules
import com.ideabridge.app.R
import com.ideabridge.app.SaveResult
import kotlinx.coroutines.launch

private enum class Tab(val route: String, val labelRes: Int, val icon: ImageVector, val implemented: Boolean) {
    Home("home", R.string.nav_galaxies, Icons.Filled.Home, true),
    Notes("notes", R.string.nav_notes, Icons.Filled.Create, true),
    Links("links", R.string.nav_links, Icons.Filled.Share, false),
    Settings("settings", R.string.nav_settings, Icons.Filled.Settings, true),
}

/** أدوات تنقل بسيطة؛ المسارات نصوص مثل "galaxy/5" و"note/5/0" وتُحفظ عبر تدوير الشاشة. */
private class Nav(
    val push: (String) -> Unit,
    val pop: () -> Unit,
    val top: (String) -> Unit,
    val say: (String) -> Unit,
)

@Composable
fun IdeaBridgeApp(vm: IdeasViewModel = viewModel()) {
    // فرض اتجاه RTL لكل عناصر الواجهة
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        var stack by rememberSaveable { mutableStateOf(arrayListOf("home")) }
        val route = stack.last()

        val nav = remember {
            Nav(
                push = { r -> stack = ArrayList(stack + r) },
                pop = { if (stack.size > 1) stack = ArrayList(stack.dropLast(1)) },
                top = { r -> stack = arrayListOf(r) },
                say = { msg ->
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar(msg)
                    }
                },
            )
        }
        BackHandler(enabled = stack.size > 1) { nav.pop() }

        val msgSoon = stringResource(R.string.msg_coming_soon)
        val isTopLevel = route == "home" || route == "notes" || route == "settings"

        Scaffold(
            modifier = Modifier.fillMaxSize().imePadding(),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (isTopLevel) {
                    NavigationBar {
                        Tab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = route == item.route,
                                onClick = { if (item.implemented) nav.top(item.route) else nav.say(msgSoon) },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = if (item.implemented) Modifier else Modifier.alpha(0.5f),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                val parts = route.split('/')
                val a = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                val b = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                when (parts[0]) {
                    "home" -> GalaxiesRoute(vm, nav)
                    "notes" -> NotesRoute(vm, nav)
                    "settings" -> SettingsRoute(vm, nav)
                    "galaxy" -> GalaxyRoute(a, vm, nav)
                    "note" -> NoteRoute(galaxyId = a, noteId = b, vm = vm, nav = nav)
                    "relate" -> RelateRoute(a, vm, nav)
                }
            }
        }
    }
}

/** يعرض رسالة النجاح أو الخطأ المناسبة، ويستدعي onSuccess فقط عند نجاح حقيقي. */
private fun <T> handleResult(
    result: SaveResult<T>,
    context: android.content.Context,
    nav: Nav,
    successMessage: Int,
    onSuccess: () -> Unit = {},
) {
    when (result) {
        is SaveResult.Success -> { nav.say(context.getString(successMessage)); onSuccess() }
        is SaveResult.Failure -> nav.say(context.getString(result.error.messageRes()))
    }
}

@Composable
private fun GalaxiesRoute(vm: IdeasViewModel, nav: Nav) {
    val context = LocalContext.current
    val galaxies by vm.galaxies.collectAsStateWithLifecycle()
    val results by vm.ideas.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }

    GalaxiesScreen(
        galaxies = galaxies,
        query = query,
        onQueryChange = vm::setQuery,
        results = results,
        onOpenGalaxy = { nav.push("galaxy/$it") },
        onOpenNote = { nav.push("note/0/$it") },
        onNewGalaxy = { showCreate = true },
        onNewNote = { nav.push("note/0/0") },
    )
    if (showCreate) {
        GalaxyEditDialog(
            title = stringResource(R.string.galaxy_create_title),
            confirmLabel = stringResource(R.string.create),
            initialName = "",
            initialColor = (galaxies?.size ?: 0) % GalaxyPalette.size,
            onConfirm = { name, color ->
                // لا تُغلق النافذة إلا بعد نجاح الإنشاء حتى لا يضيع الاسم المكتوب
                vm.createGalaxy(name, color) { r ->
                    handleResult(r, context, nav, R.string.msg_galaxy_created) { showCreate = false }
                }
            },
            onDismiss = { showCreate = false },
        )
    }
}

@Composable
private fun NotesRoute(vm: IdeasViewModel, nav: Nav) {
    val notes by vm.ideas.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    IdeasScreen(
        ideas = notes,
        query = query,
        onQueryChange = vm::setQuery,
        onOpen = { nav.push("note/0/$it") },
        onAdd = { nav.push("note/0/0") },
    )
}

@Composable
private fun GalaxyRoute(id: Long, vm: IdeasViewModel, nav: Nav) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val state by remember(id) { vm.galaxy(id) }.collectAsStateWithLifecycle(initialValue = null)
    val allNotes by remember(id) { vm.notesIn(id, "") }.collectAsStateWithLifecycle(initialValue = emptyList())
    val listNotes by remember(id, query) { vm.notesIn(id, query) }.collectAsStateWithLifecycle(initialValue = null)
    val relations by remember(id) { vm.galaxyRelations(id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val suggestions by remember(id) { vm.linkSuggestions(id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    GalaxyScreen(
        state = state,
        allNotes = allNotes,
        listNotes = listNotes,
        relations = relations,
        suggestions = suggestions,
        query = query,
        onQueryChange = { query = it },
        onBack = nav.pop,
        onOpenNote = { nav.push("note/$id/$it") },
        onNewNote = { nav.push("note/$id/0") },
        onRename = { name, color ->
            vm.updateGalaxy(id, name, color) { r -> handleResult(r, context, nav, R.string.msg_galaxy_renamed) }
        },
        onDelete = {
            vm.deleteGalaxy(id) { r -> handleResult(r, context, nav, R.string.msg_galaxy_deleted) { nav.pop() } }
        },
        onAcceptSuggestion = { s ->
            vm.acceptSuggestion(s) { r -> handleResult(r, context, nav, R.string.msg_relation_added) }
        },
        onRejectSuggestion = vm::rejectSuggestion,
    )
}

@Composable
private fun SettingsRoute(vm: IdeasViewModel, nav: Nav) {
    val context = LocalContext.current
    SettingsScreen(onExport = { uri ->
        vm.exportBackup(uri) { ok ->
            nav.say(context.getString(if (ok) R.string.backup_done else R.string.backup_failed))
        }
    })
}

@Composable
private fun NoteRoute(galaxyId: Long, noteId: Long, vm: IdeasViewModel, nav: Nav) {
    if (noteId == 0L) {
        EditorHost(galaxyId, 0L, "", "", vm, nav)
        return
    }
    val state by remember(noteId) { vm.idea(noteId) }.collectAsStateWithLifecycle(initialValue = null)
    val note = state?.value
    when {
        state == null -> SubScreen(stringResource(R.string.note_screen_title), nav.pop) {
            Text(stringResource(R.string.loading), Modifier.padding(16.dp))
        }
        note == null -> SubScreen(stringResource(R.string.note_screen_title), nav.pop) {
            Text(stringResource(R.string.note_not_found), Modifier.padding(16.dp))
        }
        else -> {
            val body = note.description.orEmpty()
            // العنوان المشتق من أول سطر لا يُكرَّر في حقل العنوان
            val title = if (IdeaRules.effectiveTitle("", body) == note.title) "" else note.title
            // القيم الابتدائية تُقرأ مرة واحدة فقط، فلا يُعاد ضبط المحرر مع كل تحديث للقاعدة أثناء الكتابة
            EditorHost(galaxyId, noteId, title, body, vm, nav)
        }
    }
}

@Composable
private fun EditorHost(galaxyId: Long, noteId: Long, title: String, body: String, vm: IdeasViewModel, nav: Nav) {
    val context = LocalContext.current
    NoteEditorScreen(
        initialNoteId = noteId,
        initialTitle = title,
        initialBody = body,
        relationsOf = vm::relations,
        save = { id, t, b -> vm.saveNote(id, galaxyId, t, b) },
        onBack = nav.pop,
        onDeleteNote = { id ->
            vm.deleteIdea(id) { r -> handleResult(r, context, nav, R.string.msg_idea_deleted) { nav.pop() } }
        },
        onLink = { id -> nav.push("relate/$id") },
        onOpenNote = { nav.push("note/0/$it") },
        onDeleteRelation = { relationId ->
            vm.deleteRelation(relationId) { r -> handleResult(r, context, nav, R.string.msg_relation_deleted) }
        },
        onError = { nav.say(context.getString(it.messageRes())) },
    )
}

@Composable
private fun RelateRoute(firstId: Long, vm: IdeasViewModel, nav: Nav) {
    val context = LocalContext.current
    val ideas by vm.allIdeas.collectAsStateWithLifecycle()
    RelationScreen(
        initialFirstId = firstId,
        ideas = ideas,
        onSubmit = { first, second, type, done ->
            vm.addRelation(first, second, type) { r ->
                handleResult(r, context, nav, R.string.msg_relation_added) { nav.pop() }
                done()
            }
        },
        onBack = nav.pop,
    )
}
