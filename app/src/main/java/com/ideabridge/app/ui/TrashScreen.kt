package com.ideabridge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ideabridge.app.R
import com.ideabridge.app.data.TrashKind
import com.ideabridge.app.data.TrashRow

/** سلة المحذوفات: ما حذفته (ملاحظة أو مجرة بكل ملاحظاتها) محفوظ 30 يومًا ويمكن استعادته. */
@Composable
fun TrashScreen(
    entries: List<TrashRow>?,
    onRestore: (Long) -> Unit,
    onDeleteForever: (Long) -> Unit,
    onEmptyTrash: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmEmpty by rememberSaveable { mutableStateOf(false) }
    var toDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    SubScreen(
        title = stringResource(R.string.trash_title),
        onBack = onBack,
        actions = {
            if (!entries.isNullOrEmpty()) {
                TextButton(onClick = { confirmEmpty = true }) {
                    Text(stringResource(R.string.trash_empty_action), color = MaterialTheme.colorScheme.error)
                }
            }
        },
    ) {
        Text(
            stringResource(R.string.trash_retention),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        when {
            entries == null -> Text(stringResource(R.string.loading), Modifier.padding(16.dp))
            entries.isEmpty() -> Box(Modifier.padding(16.dp)) {
                EmptyCard(stringResource(R.string.trash_empty_title), stringResource(R.string.trash_empty_body))
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(entries, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(item.title.ifBlank { stringResource(R.string.trash_untitled) }, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (item.kind == TrashKind.GALAXY) {
                                    stringResource(R.string.trash_kind_galaxy, item.noteCount)
                                } else {
                                    stringResource(R.string.trash_kind_note)
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                stringResource(R.string.trash_deleted_at, formatDate(item.deletedAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onRestore(item.id) }) { Text(stringResource(R.string.trash_restore)) }
                                OutlinedButton(onClick = { toDelete = item.id }) {
                                    Text(stringResource(R.string.trash_delete_forever), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmEmpty) {
        ConfirmDialog(
            title = stringResource(R.string.trash_empty_confirm_title),
            message = stringResource(R.string.trash_empty_confirm_message),
            confirmLabel = stringResource(R.string.trash_empty_action),
            onConfirm = { confirmEmpty = false; onEmptyTrash() },
            onDismiss = { confirmEmpty = false },
        )
    }
    val pending = entries?.firstOrNull { it.id == toDelete }
    if (pending != null) {
        ConfirmDialog(
            title = stringResource(R.string.trash_item_delete_title),
            message = stringResource(R.string.trash_item_delete_message, pending.title),
            confirmLabel = stringResource(R.string.trash_delete_forever),
            onConfirm = { toDelete = null; onDeleteForever(pending.id) },
            onDismiss = { toDelete = null },
        )
    }
}
