package com.ideabridge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ideabridge.app.IdeaError
import com.ideabridge.app.R
import com.ideabridge.app.data.IdeaEntity
import com.ideabridge.app.data.RelationType
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun IdeaError.messageRes(): Int = when (this) {
    IdeaError.EmptyTitle -> R.string.err_empty_title
    IdeaError.TitleTooLong -> R.string.err_title_too_long
    IdeaError.DescriptionTooLong -> R.string.err_description_too_long
    IdeaError.SameIdea -> R.string.err_same_idea
    IdeaError.IdeaNotFound -> R.string.err_idea_not_found
    IdeaError.RelationNotFound -> R.string.err_relation_not_found
    IdeaError.DuplicateRelation -> R.string.err_duplicate_relation
    IdeaError.EmptyGalaxyName -> R.string.err_empty_galaxy_name
    IdeaError.GalaxyNameTooLong -> R.string.err_galaxy_name_too_long
    IdeaError.GalaxyNotFound -> R.string.err_galaxy_not_found
    IdeaError.Unknown -> R.string.err_unknown
}

fun RelationType.labelRes(): Int = when (this) {
    RelationType.SIMILARITY -> R.string.type_similarity
    RelationType.CAUSE -> R.string.type_cause
    RelationType.EFFECT -> R.string.type_effect
    RelationType.DEPENDENCY -> R.string.type_dependency
    RelationType.MERGE -> R.string.type_merge
}

fun RelationType.sentenceRes(): Int = when (this) {
    RelationType.SIMILARITY -> R.string.sentence_similarity
    RelationType.CAUSE -> R.string.sentence_cause
    RelationType.EFFECT -> R.string.sentence_effect
    RelationType.DEPENDENCY -> R.string.sentence_dependency
    RelationType.MERGE -> R.string.sentence_merge
}

fun formatDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale("ar")).format(Date(millis))

@Composable
fun SubScreen(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1)
            actions()
        }
        content()
    }
}

@Composable
fun SectionTitle(title: String) = Text(title, style = MaterialTheme.typography.titleMedium)

@Composable
fun EmptyCard(title: String, body: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun IdeaCard(idea: IdeaEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(idea.title, style = MaterialTheme.typography.titleMedium)
            idea.description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
