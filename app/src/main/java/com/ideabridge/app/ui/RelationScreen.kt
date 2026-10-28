package com.ideabridge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ideabridge.app.R
import com.ideabridge.app.data.IdeaEntity
import com.ideabridge.app.data.RelationType

/** إنشاء علاقة يدوية. الاتجاه: الفكرة الأولى ← النوع ← الفكرة الثانية. 0 = لم تُختر فكرة. */
@Composable
fun RelationScreen(
    initialFirstId: Long,
    ideas: List<IdeaEntity>?,
    onSubmit: (first: Long, second: Long, type: RelationType, done: () -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    var first by rememberSaveable { mutableStateOf(initialFirstId) }
    var second by rememberSaveable { mutableStateOf(0L) }
    var type by rememberSaveable { mutableStateOf(RelationType.SIMILARITY) }
    var saving by remember { mutableStateOf(false) }

    SubScreen(stringResource(R.string.relate_title), onBack) {
        when {
            ideas == null -> Text(stringResource(R.string.loading), Modifier.padding(16.dp))
            ideas.size < 2 -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EmptyCard(stringResource(R.string.relate_title), stringResource(R.string.relate_need_two))
                OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
            else -> {
                val same = first != 0L && first == second
                val firstIdea = ideas.firstOrNull { it.id == first }
                val secondIdea = ideas.firstOrNull { it.id == second }
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IdeaPicker(stringResource(R.string.relate_first), ideas, first) { first = it }
                    IdeaPicker(stringResource(R.string.relate_second), ideas, second) { second = it }
                    if (same) {
                        Text(
                            stringResource(R.string.err_same_idea),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    SectionTitle(stringResource(R.string.relate_type))
                    RelationType.entries.forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .selectable(selected = type == option, role = Role.RadioButton) { type = option },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = type == option, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(option.labelRes()))
                        }
                    }
                    if (firstIdea != null && secondIdea != null && !same) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.relate_preview), style = MaterialTheme.typography.labelMedium)
                                Text(
                                    stringResource(type.sentenceRes(), firstIdea.title, secondIdea.title),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            saving = true
                            onSubmit(first, second, type) { saving = false }
                        },
                        enabled = first != 0L && second != 0L && !same && !saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.relate_save)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdeaPicker(label: String, ideas: List<IdeaEntity>, selectedId: Long, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = ideas.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.title ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(stringResource(R.string.choose_idea)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ideas.forEach { idea ->
                DropdownMenuItem(
                    text = { Text(idea.title) },
                    onClick = { onSelect(idea.id); expanded = false },
                )
            }
        }
    }
}
