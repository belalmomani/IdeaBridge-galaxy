package com.ideabridge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ideabridge.app.Loaded
import com.ideabridge.app.R
import com.ideabridge.app.data.GalaxyEntity
import com.ideabridge.app.data.IdeaEntity

/** داخل المجرة: ملاحظاتها مع بحث خاص بها، وإجراءات إعادة التسمية والحذف. */
@Composable
fun GalaxyScreen(
    state: Loaded<GalaxyEntity>?,
    notes: List<IdeaEntity>?,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
    onRename: (name: String, colorIndex: Int) -> Unit,
    onDelete: () -> Unit,
) {
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
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
                else -> IdeasScreen(
                    ideas = notes,
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
}
