package com.ideabridge.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ideabridge.app.R

@Composable
fun SettingsScreen(
    lockEnabled: Boolean,
    onToggleLock: (Boolean) -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onOpenTrash: () -> Unit,
) {
    // CreateDocument وOpenDocument لا يحتاجان أي إذن؛ المستخدم يختار الملف بنفسه
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) onExport(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings_lock_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = lockEnabled, onCheckedChange = onToggleLock)
                }
                Text(stringResource(R.string.settings_lock_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.backup_body), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { exportLauncher.launch("ideabridge-backup.json") }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.backup_button))
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.import_button))
                }
                Text(
                    stringResource(R.string.import_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_trash_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_trash_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onOpenTrash, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_trash_open))
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
