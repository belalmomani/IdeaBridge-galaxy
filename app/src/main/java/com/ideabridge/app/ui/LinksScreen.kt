package com.ideabridge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ideabridge.app.R
import com.ideabridge.app.data.LinkRow

/** كل الروابط المحفوظة، بما فيها الروابط بين ملاحظتين من مجرتين مختلفتين (لا تُرسم داخل أي مجرة). */
@Composable
fun LinksScreen(
    links: List<LinkRow>?,
    onOpenNote: (Long) -> Unit,
    onDeleteLink: (Long) -> Unit,
) {
    var toDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    SpaceBackground {
        Column(Modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.links_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp),
            )
            when {
                links == null -> Text(stringResource(R.string.loading), Modifier.padding(16.dp))
                links.isEmpty() -> Box(Modifier.padding(16.dp)) {
                    EmptyCard(stringResource(R.string.links_empty_title), stringResource(R.string.links_empty_body))
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(links, key = { it.id }) { row ->
                        Card(
                            onClick = { onOpenNote(row.firstIdeaId) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        ) {
                            Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        stringResource(row.type.sentenceRes(), row.firstTitle, row.secondTitle),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    val crossGalaxy = row.firstGalaxy != row.secondGalaxy
                                    Text(
                                        if (crossGalaxy) {
                                            stringResource(R.string.link_cross_galaxy, row.firstGalaxy, row.secondGalaxy)
                                        } else {
                                            row.firstGalaxy
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                IconButton(onClick = { toDelete = row.id }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_relation_desc))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val pending = links?.firstOrNull { it.id == toDelete }
    if (pending != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_relation_title),
            message = stringResource(pending.type.sentenceRes(), pending.firstTitle, pending.secondTitle),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { toDelete = null; onDeleteLink(pending.id) },
            onDismiss = { toDelete = null },
        )
    }
}
