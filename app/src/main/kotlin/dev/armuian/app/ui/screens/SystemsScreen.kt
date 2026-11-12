package dev.armuian.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.armuian.app.ui.LocalMonoFont
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.armuian.app.R
import dev.armuian.app.data.SystemEntry
import dev.armuian.app.ui.LocalBottomBarPadding
import dev.armuian.app.ui.MainViewModel
import dev.armuian.app.ui.SizeState
import dev.armuian.app.ui.StatusPill
import dev.armuian.app.ui.Tone
import dev.armuian.app.ui.errorText
import dev.armuian.app.ui.fmtBytes
import dev.armuian.app.ui.fmtDuration
import dev.armuian.image.DownloadProgress
import dev.armuian.image.InstallState
import dev.armuian.image.Stage
import kotlinx.coroutines.delay

private enum class Phase { IDLE, BUSY, PAUSED, DONE, FAILED }

private fun phaseOf(s: InstallState) = when (s) {
    InstallState.NotInstalled -> Phase.IDLE
    InstallState.Queued, is InstallState.Running -> Phase.BUSY
    InstallState.Paused -> Phase.PAUSED
    is InstallState.Installed -> Phase.DONE
    is InstallState.Failed -> Phase.FAILED
}

@Composable
fun SystemsScreen(vm: MainViewModel, listState: LazyListState) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val installs by vm.installStates.collectAsStateWithLifecycle()
    val host by vm.host.collectAsStateWithLifecycle()
    val sizes by vm.installedSizes.collectAsStateWithLifecycle()
    val updates by vm.updates.collectAsStateWithLifecycle()
    val hostMajor = host?.androidMajor
    val introPlayed = remember { vm.imagesIntroPlayed } // решение «анимировать ли» фиксируется при первом показе

    // После завершения «вступления» больше не проигрываем его при возвращении на вкладку и мелких обновлениях.
    LaunchedEffect(Unit) { delay(entries.size * 70L + 500L); vm.imagesIntroPlayed = true }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + LocalBottomBarPadding.current),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(entries, key = { _, e -> e.id }) { index, e ->
            var visible by remember(e.id) { mutableStateOf(introPlayed) }
            LaunchedEffect(e.id) { if (!introPlayed) { delay(index * 70L); visible = true } }
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 6 },
            ) {
                SystemCard(
                    entry = e,
                    state = installs[e.id] ?: InstallState.NotInstalled,
                    recommended = hostMajor != null && e.androidMajor == hostMajor,
                    hasUpdate = e.id in updates,
                    size = sizes[e.id],
                    onDownload = { vm.beginInstall(e) },
                    onPause = { vm.pause(e) },
                    onCancel = { vm.cancel(e) },
                    onDelete = { vm.requestDelete(e) },
                )
            }
        }
    }
}

@Composable
private fun SystemCard(
    entry: SystemEntry, state: InstallState, recommended: Boolean, hasUpdate: Boolean, size: SizeState?,
    onDownload: () -> Unit, onPause: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val phase = phaseOf(state)
    // Удаление: установленный образ или любой пользовательский (удаляет и источник). Во время загрузки отменяет её.
    val canDelete = entry.custom || phase == Phase.DONE

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (phase == Phase.PAUSED) StatusPill(stringResource(R.string.paused), Tone.WARN)
            }
            val versionLabel = entry.androidMajor?.let { "Android $it" } ?: stringResource(R.string.custom_source)
            Text("$versionLabel · ARM64", style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(if (entry.id == "redroid-16") R.string.desc_pixel else R.string.desc_redroid),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
            Text(entry.ref, style = MaterialTheme.typography.labelSmall, fontFamily = LocalMonoFont.current, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (recommended) StatusPill(stringResource(R.string.recommended), Tone.GOOD)
                if (hasUpdate) StatusPill(stringResource(R.string.update_available), Tone.WARN)
            }
            if (phase == Phase.DONE) SizeLine(size)
            Spacer(Modifier.height(4.dp))

            AnimatedContent(
                targetState = phase, label = "action",
                transitionSpec = { (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 4 }) togetherWith fadeOut(tween(140)) },
            ) { p ->
                when (p) {
                    Phase.IDLE -> Button(onClick = onDownload) {
                        Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.download), maxLines = 1, softWrap = false)
                    }
                    Phase.BUSY -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BusyBlock((state as? InstallState.Running)?.progress, queued = state == InstallState.Queued)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state is InstallState.Running) FilledTonalButton(onClick = onPause) { Text(stringResource(R.string.pause), maxLines = 1, softWrap = false) }
                            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel), maxLines = 1, softWrap = false) }
                        }
                    }
                    Phase.PAUSED -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onDownload) { Text(stringResource(R.string.resume), maxLines = 1, softWrap = false) }
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel), maxLines = 1, softWrap = false) }
                    }
                    Phase.DONE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = {}, enabled = false) {
                            Icon(Icons.Filled.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.installed), maxLines = 1, softWrap = false)
                        }
                        if (hasUpdate) Button(onClick = onDownload) { Text(stringResource(R.string.update), maxLines = 1, softWrap = false) }
                    }
                    Phase.FAILED -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (state as? InstallState.Failed)?.let {
                            Text(stringResource(R.string.failed_fmt, errorText(it.error)), color = cs.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = onDownload) {
                            Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.retry), maxLines = 1, softWrap = false)
                        }
                    }
                }
            }

            if (canDelete) {
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error),
                    border = BorderStroke(1.dp, cs.error),
                ) {
                    Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.delete), maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** Три разных состояния: идёт вычисление / готово / ошибка. Ноль не используется как «ещё считаем». */
@Composable
private fun SizeLine(size: SizeState?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (size) {
            null, SizeState.Computing -> {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.computing), style = MaterialTheme.typography.bodySmall)
            }
            is SizeState.Ready -> Text(stringResource(R.string.on_disk_fmt, fmtBytes(size.bytes)), style = MaterialTheme.typography.bodySmall)
            SizeState.Failed -> Text(stringResource(R.string.size_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun BusyBlock(p: DownloadProgress?, queued: Boolean) {
    val pct = p?.percent
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Индикатор отражает реальный прогресс; если процент неизвестен — неопределённый.
        if (pct != null) CircularProgressIndicator(progress = { pct / 100f }, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        else CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        val title = when {
            queued || p == null -> stringResource(R.string.queued)
            else -> when (p.stage) {
                Stage.RESOLVING -> stringResource(R.string.stage_resolving)
                Stage.DOWNLOADING -> stringResource(R.string.stage_downloading, p.detail ?: "")
                Stage.VERIFYING -> stringResource(R.string.stage_verifying)
                Stage.EXTRACTING -> stringResource(R.string.stage_extracting, p.detail ?: "")
                Stage.SAVING -> stringResource(R.string.stage_saving)
            }
        }
        Text(title, style = MaterialTheme.typography.bodyMedium)
    }
    if (pct != null) LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth())
    // Для распаковки счётчик — слои, а не байты: байты и скорость показываем только при загрузке.
    if (p != null && (p.stage == Stage.DOWNLOADING || p.stage == Stage.VERIFYING)) {
        val total = p.totalBytes?.let(::fmtBytes) ?: stringResource(R.string.size_unknown)
        val line = buildList {
            add("${fmtBytes(p.downloadedBytes)} / $total")
            if (pct != null) add("$pct%")
            if (p.bytesPerSecond > 0) add(fmtBytes(p.bytesPerSecond) + "/s")
        }.joinToString(" · ")
        Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        p.etaSeconds?.let { Text(stringResource(R.string.eta_fmt, fmtDuration(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
