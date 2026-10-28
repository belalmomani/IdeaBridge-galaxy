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

/** الشاشة الرئيسية: مجرّاتك على خلفية فضاء، مع بحث عام في كل الملاحظات. */
@Composable
fun GalaxiesScreen(
    galaxies: List<GalaxyRow>?,
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<IdeaEntity>?,
    onOpenGalaxy: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
    onNewGalaxy: () -> Unit,
    onNewNote: () -> Unit,
) {
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
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear_search))
                        }
                    }
                } else null,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (query.isNotBlank()) {
                    SearchResults(results, onOpenNote)
                } else {
                    GalaxyList(galaxies, onOpenGalaxy, onNewGalaxy)
                }
                ExtendedFloatingActionButton(
                    onClick = onNewNote,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.new_note)) },
                )
            }
        }
    }
}

@Composable
private fun SearchResults(results: List<IdeaEntity>?, onOpenNote: (Long) -> Unit) {
    when {
        results == null -> Text(stringResource(R.string.loading), Modifier.padding(16.dp))
        results.isEmpty() -> Text(stringResource(R.string.search_no_results), Modifier.padding(16.dp))
        else -> LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(results, key = { it.id }) { note -> IdeaCard(note) { onOpenNote(note.id) } }
        }
    }
}

@Composable
private fun GalaxyList(galaxies: List<GalaxyRow>?, onOpen: (Long) -> Unit, onNewGalaxy: () -> Unit) {
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
        item {
            OutlinedButton(onClick = onNewGalaxy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.new_galaxy))
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

/** نافذة إنشاء مجرة أو إعادة تسميتها مع اختيار اللون. */
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
        confirmButton = {
            TextButton(onClick = { onConfirm(name, color) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
