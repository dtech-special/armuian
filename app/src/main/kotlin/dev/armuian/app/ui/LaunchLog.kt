package dev.armuian.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.armuian.app.R
import dev.armuian.core.ArmLog

/** Теги источников, которые участвуют в попытке запуска: root, образ, подготовка, mount-операции, гостевой процесс. */
private val LAUNCH_TAGS = setOf("guest", "Backends", "RootBridge", "App", "HostDetector")

/** Подробный журнал запуска: только записи, относящиеся к запуску (из реального выполнения команд и скриптов). */
@Composable
fun LaunchLogDialog(onDismiss: () -> Unit, onCopy: (String) -> Unit) {
    val all by ArmLog.entries.collectAsStateWithLifecycle()
    val entries = remember(all) { all.filter { it.tag in LAUNCH_TAGS || it.tag.startsWith("Backend-") } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.launch_log_title), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
                LogView(entries, onCopy, Modifier.weight(1f))
            }
        }
    }
}
