package com.ideabridge.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ideabridge.app.IdeaRules
import com.ideabridge.app.R
import com.ideabridge.app.data.GalaxyRow
import com.ideabridge.app.data.IdeaEntity
import com.ideabridge.app.engine.ArabicText
import com.ideabridge.app.engine.LinkSuggestion

/** الشاشة الرئيسية: خريطة المجرات (متصلة نصيًا) أو قائمتها، مع بحث يشمل أسماء المجرات والملاحظات. */
@Composable
fun GalaxiesScreen(
    galaxies: List<GalaxyRow>?,
    galaxyLinks: List<LinkSuggestion>,
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<IdeaEntity>?,
    onOpenGalaxy: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
    onNewGalaxy: () -> Unit,
    onNewNote: () -> Unit,
) {
    var mapMode by rememberSaveable { mutableStateOf(true) }
    SpaceBackground {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint_global)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear_search))
                        }
                    }
                } else null,
            )
            if (query.isBlank() && !galaxies.isNullOrEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mapMode, onClick = { mapMode = true }, label = { Text(stringResource(R.string.mode_map)) })
                    FilterChip(selected = !mapMode, onClick = { mapMode = false }, label = { Text(stringResource(R.string.mode_list)) })
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    query.isNotBlank() -> SearchResults(
                        galaxies = galaxies.orEmpty().filter { matchesName(it.name, query) },
                        notes = results,
                        onOpenGalaxy = onOpenGalaxy,
                        onOpenNote = onOpenNote,
                    )
                    galaxies != null && galaxies.isNotEmpty() && mapMode -> Column(Modifier.fillMaxSize()) {
                        GalaxyMapGraph(galaxies, galaxyLinks, onOpenGalaxy, Modifier.weight(1f).fillMaxWidth())
                        GalaxyLinksPanel(galaxyLinks, galaxies)
                    }
                    else -> GalaxyList(galaxies, onOpenGalaxy)
                }
                ExtendedFloatingActionButton(
                    onClick = onNewNote,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.new_note)) },
                )
                OutlinedButton(
                    onClick = onNewGalaxy,
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                ) { Text(stringResource(R.string.new_galaxy)) }
            }
        }
    }
}

/** مطابقة الاسم مع تجاهل التشكيل والتطويل وأشكال الألف. */
private fun matchesName(name: String, query: String): Boolean =
    ArabicText.normalizeChars(name).contains(ArabicText.normalizeChars(query.trim()))

@Composable
private fun SearchResults(
    galaxies: List<GalaxyRow>,
    notes: List<IdeaEntity>?,
    onOpenGalaxy: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    when {
        notes == null -> Text(stringResource(R.string.loading), Modifier.padding(16.dp))
        galaxies.isEmpty() && notes.isEmpty() -> Text(stringResource(R.string.search_no_results), Modifier.padding(16.dp))
        else -> LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (galaxies.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.search_galaxies_title)) }
                items(galaxies, key = { "g${it.id}" }) { g -> GalaxyCard(g) { onOpenGalaxy(g.id) } }
            }
            if (notes.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.search_notes_title)) }
                items(notes, key = { "n${it.id}" }) { note -> IdeaCard(note) { onOpenNote(note.id) } }
            }
        }
    }
}

@Composable
private fun GalaxyList(galaxies: List<GalaxyRow>?, onOpen: (Long) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            galaxies == null -> item { Text(stringResource(R.string.loading)) }
            galaxies.isEmpty() -> item {
                EmptyCard(stringResource(R.string.galaxies_empty_title), stringResource(R.string.galaxies_empty_body))
            }
            else -> items(galaxies, key = { it.id }) { galaxy -> GalaxyCard(galaxy) { onOpen(galaxy.id) } }
        }
    }
}

/** لماذا ترتبط هذه المجرات؟ الكلمات المشتركة بين أسمائها وملاحظاتها، وليست فهمًا للمعنى. */
@Composable
private fun GalaxyLinksPanel(links: List<LinkSuggestion>, galaxies: List<GalaxyRow>) {
    if (links.isEmpty()) return
    val names = remember(galaxies) { galaxies.associate { it.id to it.name } }
    Column(Modifier.fillMaxWidth().heightIn(max = 170.dp).padding(start = 16.dp, end = 16.dp, bottom = 88.dp)) {
        Text(
            stringResource(R.string.galaxy_links_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(links, key = { it.key }) { l ->
                Text(
                    stringResource(
                        R.string.galaxy_link_reason,
                        names[l.firstId].orEmpty(), names[l.secondId].orEmpty(),
                        l.sharedTerms.joinToString("، ") { "«$it»" }, l.score,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun GalaxyCard(galaxy: GalaxyRow, onClick: () -> Unit) {
    val color = galaxyColor(galaxy.colorIndex)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.07f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GalaxyOrb(color, 56.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(galaxy.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.galaxy_note_count, galaxy.noteCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** نافذة إنشاء مجرة (تصنيف) أو إعادة تسميتها مع اختيار اللون. */
@Composable
fun GalaxyEditDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    initialColor: Int,
    onConfirm: (name: String, colorIndex: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var color by rememberSaveable { mutableStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(IdeaRules.MAX_GALAXY_NAME) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.galaxy_name_label)) },
                    supportingText = { Text(stringResource(R.string.galaxy_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GalaxyPalette.forEachIndexed { index, c ->
                        GalaxyOrb(
                            color = c,
                            size = if (index == color) 40.dp else 30.dp,
                            modifier = Modifier.clickable { color = index },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, color) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
