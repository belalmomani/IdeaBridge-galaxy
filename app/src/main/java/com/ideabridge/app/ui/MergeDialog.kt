package com.ideabridge.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ideabridge.app.R
import com.ideabridge.app.engine.MergeResult
import com.ideabridge.app.engine.NoteMerger

/** يعرض اقتراح دمج ملاحظتين. لا يُحفظ شيء إلا بضغط «حفظ كملاحظة جديدة»، ولا يُغلق الحوار إلا بعد نجاح الحفظ. */
@Composable
fun MergeDialog(
    result: MergeResult,
    onSave: (title: String, body: String, done: (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var index by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    val proposals = (result as? MergeResult.Ok)?.proposals.orEmpty()
    val current = proposals.getOrNull(index.coerceAtMost((proposals.size - 1).coerceAtLeast(0)))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.merge_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    result is MergeResult.NeedMore -> Text(result.reason)
                    current != null -> {
                        Text(current.title, style = MaterialTheme.typography.titleMedium)
                        Text(current.body, style = MaterialTheme.typography.bodyMedium)
                        current.basis.forEach { Text(it, style = MaterialTheme.typography.labelMedium) }
                        Text(NoteMerger.DISCLAIMER, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            if (current != null) {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        saving = true
                        onSave(current.title, current.body) { ok ->
                            saving = false
                            if (ok) onDismiss()
                        }
                    },
                ) { Text(stringResource(R.string.merge_save)) }
            }
        },
        dismissButton = {
            Row {
                if (proposals.size > 1) {
                    TextButton(onClick = { index = (index + 1) % proposals.size }) { Text(stringResource(R.string.merge_other)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
