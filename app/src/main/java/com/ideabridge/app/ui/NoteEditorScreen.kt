package com.ideabridge.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ideabridge.app.GalaxyMove
import com.ideabridge.app.IdeaError
import com.ideabridge.app.IdeaRules
import com.ideabridge.app.R
import com.ideabridge.app.SaveResult
import com.ideabridge.app.engine.ListEditing
import com.ideabridge.app.data.RelationRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SaveStatus { Idle, Saving, Saved, Failed }

/**
 * محرر ملاحظة مثل ملاحظات الهاتف: عنوان ونص حر، ويحفظ تلقائيًا بعد توقف الكتابة.
 * لا تُحفظ ملاحظة فارغة تمامًا. عند فشل الحفظ تبقى الكتابة في الشاشة وتظهر حالة الفشل.
 */
@Composable
fun NoteEditorScreen(
    initialNoteId: Long,
    initialTitle: String,
    initialBody: String,
    relationsOf: (Long) -> Flow<List<RelationRow>>,
    save: suspend (noteId: Long, title: String, body: String) -> SaveResult<Long>,
    onBack: () -> Unit,
    onDeleteNote: (Long) -> Unit,
    onLink: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
    onDeleteRelation: (Long) -> Unit,
    onError: (IdeaError) -> Unit,
    suggestGalaxy: suspend (noteId: Long) -> GalaxyMove?,
    onMove: (noteId: Long, galaxyId: Long) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var bodyField by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initialBody)) }
    val body = bodyField.text
    var noteId by rememberSaveable { mutableStateOf(initialNoteId) }
    var status by remember { mutableStateOf(SaveStatus.Idle) }
    var saving by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var relationToDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    var move by remember { mutableStateOf<GalaxyMove?>(null) }
    val dismissedGalaxies = remember { mutableSetOf<Long>() }
    val scope = rememberCoroutineScope()

    // يحفظ آخر نص؛ وإن وصل تعديل أثناء الحفظ يعيد المحاولة بعد انتهائه (لا نسخ مكررة).
    suspend fun flush() {
        if (saving) { dirty = true; return }
        saving = true
        status = SaveStatus.Saving
        try {
            do {
                dirty = false
                val currentBody = bodyField.text // يُقرأ من الحالة مباشرة حتى لا تُحفظ نسخة قديمة عند إعادة المحاولة
                val effective = IdeaRules.effectiveTitle(title, currentBody)
                if (effective.isEmpty()) { status = SaveStatus.Idle; break }
                val result = withContext(NonCancellable) { save(noteId, effective, currentBody) }
                when (result) {
                    is SaveResult.Success -> { noteId = result.value; status = SaveStatus.Saved }
                    is SaveResult.Failure -> { status = SaveStatus.Failed; onError(result.error); break }
                }
            } while (dirty)
            // اقتراح مجرة أنسب: يظهر بعد الحفظ فقط، ولا يُنقل شيء دون موافقة المستخدم
            if (status == SaveStatus.Saved && noteId != 0L) {
                move = try {
                    suggestGalaxy(noteId)?.takeIf { it.galaxyId !in dismissedGalaxies }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            saving = false
        }
    }

    val firstRun = remember { booleanArrayOf(true) }
    LaunchedEffect(title, body) {
        if (firstRun[0]) { firstRun[0] = false; return@LaunchedEffect }
        delay(700)
        flush()
    }

    fun leave() { scope.launch { flush(); onBack() } }
    BackHandler { leave() }

    val relations by remember(noteId) { relationsOf(noteId) }.collectAsStateWithLifecycle(initialValue = emptyList())

    SubScreen(
        title = stringResource(R.string.note_screen_title),
        onBack = { leave() },
        actions = {
            Text(
                text = when (status) {
                    SaveStatus.Idle -> ""
                    SaveStatus.Saving -> stringResource(R.string.status_saving)
                    SaveStatus.Saved -> stringResource(R.string.status_saved)
                    SaveStatus.Failed -> stringResource(R.string.status_failed)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (status == SaveStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (noteId != 0L) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                }
            }
        },
    ) {
        val plain = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        )
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val suggestion = move
            if (suggestion != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.move_banner, suggestion.galaxyName, suggestion.words.joinToString("، ") { "«$it»" }),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { move = null; onMove(noteId, suggestion.galaxyId) }) {
                                Text(stringResource(R.string.move_action, suggestion.galaxyName))
                            }
                            TextButton(onClick = { dismissedGalaxies.add(suggestion.galaxyId); move = null }) {
                                Text(stringResource(R.string.move_ignore))
                            }
                        }
                    }
                }
            }
            TextField(
                value = title,
                onValueChange = { title = it.take(IdeaRules.MAX_LENGTH) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.titleLarge,
                placeholder = { Text(stringResource(R.string.note_title_hint), style = MaterialTheme.typography.titleLarge) },
                singleLine = true,
                colors = plain,
            )
            TextField(
                value = bodyField,
                onValueChange = { typed ->
                    val limited = if (typed.text.length > IdeaRules.MAX_DESCRIPTION) {
                        typed.copy(text = typed.text.take(IdeaRules.MAX_DESCRIPTION))
                    } else {
                        typed
                    }
                    // Enter داخل قائمة يبدأ عنصرًا جديدًا تلقائيًا
                    val continued = if (limited.selection.collapsed) {
                        ListEditing.continueList(bodyField.text, limited.text, limited.selection.end)
                    } else {
                        null
                    }
                    bodyField = if (continued != null) {
                        TextFieldValue(continued.text, TextRange(continued.selStart, continued.selEnd))
                    } else {
                        limited
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                placeholder = { Text(stringResource(R.string.note_body_hint)) },
                colors = plain,
            )
            if (noteId != 0L) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(stringResource(R.string.relations_title))
                    if (relations.isEmpty()) {
                        EmptyCard(stringResource(R.string.relations_title), stringResource(R.string.relations_empty))
                    } else {
                        relations.forEach { row ->
                            val other = if (row.firstIdeaId == noteId) row.secondIdeaId else row.firstIdeaId
                            Card(
                                onClick = { onOpenNote(other) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            ) {
                                Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(stringResource(row.type.sentenceRes(), row.firstTitle, row.secondTitle), style = MaterialTheme.typography.bodyLarge)
                                        Text(stringResource(row.type.labelRes()), style = MaterialTheme.typography.labelMedium)
                                    }
                                    IconButton(onClick = { relationToDelete = row.id }) {
                                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_relation_desc))
                                    }
                                }
                            }
                        }
                    }
                    Button(onClick = { onLink(noteId) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.link_to_note))
                    }
                }
            }
        }
        // شريط أدوات القوائم فوق لوحة المفاتيح مباشرة
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            fun applyEdit(edit: ListEditing.Edit) {
                val text = edit.text.take(IdeaRules.MAX_DESCRIPTION)
                bodyField = TextFieldValue(
                    text, TextRange(minOf(edit.selStart, text.length), minOf(edit.selEnd, text.length)),
                )
            }
            AssistChip(
                onClick = { applyEdit(ListEditing.toggleChecklist(bodyField.text, bodyField.selection.start, bodyField.selection.end)) },
                label = { Text(stringResource(R.string.fmt_checklist)) },
            )
            AssistChip(
                onClick = { applyEdit(ListEditing.toggleDone(bodyField.text, bodyField.selection.end)) },
                label = { Text(stringResource(R.string.fmt_done)) },
            )
            AssistChip(
                onClick = { applyEdit(ListEditing.toggleBullet(bodyField.text, bodyField.selection.start, bodyField.selection.end)) },
                label = { Text(stringResource(R.string.fmt_bullet)) },
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_note_title),
            message = stringResource(R.string.delete_note_message),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { confirmDelete = false; onDeleteNote(noteId) },
            onDismiss = { confirmDelete = false },
        )
    }
    val pending = relations.firstOrNull { it.id == relationToDelete }
    if (pending != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_relation_title),
            message = stringResource(pending.type.sentenceRes(), pending.firstTitle, pending.secondTitle),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { relationToDelete = null; onDeleteRelation(pending.id) },
            onDismiss = { relationToDelete = null },
        )
    }
}
