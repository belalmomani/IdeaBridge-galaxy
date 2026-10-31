package com.ideabridge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ideabridge.app.Loaded
import com.ideabridge.app.R
import com.ideabridge.app.data.GalaxyEntity
import com.ideabridge.app.data.IdeaEntity
import com.ideabridge.app.data.RelationEntity
import com.ideabridge.app.engine.LinkStrength
import com.ideabridge.app.engine.IdeaDoc
import com.ideabridge.app.engine.LinkSuggestion
import com.ideabridge.app.engine.MergeResult
import com.ideabridge.app.engine.NoteMerger
import com.ideabridge.app.engine.SuggestionType

/** داخل المجرة: عرض الكواكب (الافتراضي) أو القائمة، مع اقتراحات الروابط وإجراءات المجرة. */
@Composable
fun GalaxyScreen(
    state: Loaded<GalaxyEntity>?,
    allNotes: List<IdeaEntity>,
    listNotes: List<IdeaEntity>?,
    relations: List<RelationEntity>,
    suggestions: List<LinkSuggestion>,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
    onRename: (name: String, colorIndex: Int) -> Unit,
    onDelete: () -> Unit,
    onAcceptSuggestion: (LinkSuggestion) -> Unit,
    onRejectSuggestion: (String) -> Unit,
    onSaveMerged: (title: String, body: String, done: (Boolean) -> Unit) -> Unit,
) {
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var planetsMode by rememberSaveable { mutableStateOf(true) }
    var showSuggestions by rememberSaveable { mutableStateOf(true) }
    var mergeFor by remember { mutableStateOf<LinkSuggestion?>(null) }
    val galaxy = state?.value

    SpaceBackground {
        SubScreen(
            title = galaxy?.name ?: stringResource(R.string.loading),
            onBack = onBack,
            actions = {
                if (galaxy != null) {
                    IconButton(onClick = { showRename = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.rename))
                    }
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            },
        ) {
            when {
                state == null -> Unit
                galaxy == null -> Text(stringResource(R.string.galaxy_not_found), Modifier.padding(16.dp))
                else -> {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = planetsMode, onClick = { planetsMode = true },
                            label = { Text(stringResource(R.string.mode_planets)) },
                        )
                        FilterChip(
                            selected = !planetsMode, onClick = { planetsMode = false },
                            label = { Text(stringResource(R.string.mode_list)) },
                        )
                        if (planetsMode) {
                            FilterChip(
                                selected = showSuggestions, onClick = { showSuggestions = !showSuggestions },
                                label = { Text(stringResource(R.string.suggestions_chip, suggestions.size)) },
                            )
                        }
                    }
                    if (planetsMode) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            GalaxyGraph(
                                notes = allNotes,
                                relations = relations,
                                suggestions = suggestions,
                                showSuggestions = showSuggestions,
                                color = galaxyColor(galaxy.colorIndex),
                                onOpenNote = onOpenNote,
                                modifier = Modifier.fillMaxSize(),
                            )
                            ExtendedFloatingActionButton(
                                onClick = onNewNote,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                text = { Text(stringResource(R.string.new_note)) },
                            )
                        }
                        if (showSuggestions && suggestions.isNotEmpty()) {
                            SuggestionPanel(suggestions, allNotes, onAcceptSuggestion, onRejectSuggestion) { mergeFor = it }
                        }
                    } else {
                        IdeasScreen(
                            ideas = listNotes,
                            query = query,
                            onQueryChange = onQueryChange,
                            onOpen = onOpenNote,
                            onAdd = onNewNote,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            emptyTitle = stringResource(R.string.galaxy_empty_title),
                            emptyBody = stringResource(R.string.galaxy_empty_body),
                        )
                    }
                }
            }
        }
    }

    if (showRename && galaxy != null) {
        GalaxyEditDialog(
            title = stringResource(R.string.galaxy_rename_title),
            confirmLabel = stringResource(R.string.save),
            initialName = galaxy.name,
            initialColor = galaxy.colorIndex,
            onConfirm = { name, color -> showRename = false; onRename(name, color) },
            onDismiss = { showRename = false },
        )
    }
    if (showDelete && galaxy != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_galaxy_title),
            message = stringResource(R.string.delete_galaxy_message, galaxy.name),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { showDelete = false; onDelete() },
            onDismiss = { showDelete = false },
        )
    }

    val merging = mergeFor
    if (merging != null) {
        val result = remember(merging, allNotes) {
            val byId = allNotes.associateBy { it.id }
            val a = byId[merging.firstId]
            val b = byId[merging.secondId]
            if (a == null || b == null) {
                MergeResult.NeedMore("إحدى الملاحظتين لم تعد موجودة.")
            } else {
                NoteMerger.propose(
                    IdeaDoc(a.id, a.title, a.description), IdeaDoc(b.id, b.title, b.description),
                    merging.type, merging.sharedTerms, merging.cues,
                )
            }
        }
        MergeDialog(result = result, onSave = onSaveMerged, onDismiss = { mergeFor = null })
    }
}

@Composable
private fun SuggestionPanel(
    suggestions: List<LinkSuggestion>,
    notes: List<IdeaEntity>,
    onAccept: (LinkSuggestion) -> Unit,
    onReject: (String) -> Unit,
    onMerge: (LinkSuggestion) -> Unit,
) {
    val titles = remember(notes) { notes.associate { it.id to it.title } }
    Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).padding(horizontal = 16.dp)) {
        Text(
            stringResource(R.string.suggestions_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(suggestions, key = { it.key }) { s ->
                val strength = stringResource(
                    when (s.strength) {
                        LinkStrength.STRONG -> R.string.strength_strong
                        LinkStrength.MEDIUM -> R.string.strength_medium
                        LinkStrength.WEAK -> R.string.strength_weak
                    }
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(R.string.suggestion_pair, titles[s.firstId].orEmpty(), titles[s.secondId].orEmpty()),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            stringResource(
                                R.string.suggestion_reason,
                                s.sharedTerms.joinToString("، ") { "«$it»" }, s.score, strength,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val cuesText = s.cues.distinct().joinToString("، ") { "«$it»" }
                        when (s.type) {
                            SuggestionType.PROBLEM_SOLUTION ->
                                Text(stringResource(R.string.suggestion_type_problem, cuesText), style = MaterialTheme.typography.labelMedium)
                            SuggestionType.CAUSE_EFFECT ->
                                Text(stringResource(R.string.suggestion_type_cause, cuesText), style = MaterialTheme.typography.labelMedium)
                            SuggestionType.SIMILARITY -> Unit
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onAccept(s) }) { Text(stringResource(R.string.suggestion_accept)) }
                            OutlinedButton(onClick = { onReject(s.key) }) { Text(stringResource(R.string.suggestion_reject)) }
                            TextButton(onClick = { onMerge(s) }) { Text(stringResource(R.string.merge_button)) }
                        }
                    }
                }
            }
        }
    }
}
