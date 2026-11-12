package dev.armuian.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.armuian.app.BuildConfig
import dev.armuian.app.R
import dev.armuian.app.ui.LocalBottomBarPadding
import dev.armuian.app.ui.MobileCheckIcon
import dev.armuian.app.ui.MainViewModel
import dev.armuian.app.ui.TwoStepButton
import dev.armuian.app.ui.backendName
import dev.armuian.app.ui.fmtBytes
import dev.armuian.container.Availability
import dev.armuian.container.BackendChoice
import dev.armuian.core.AppInfo
import dev.armuian.core.ContainerState
import dev.armuian.image.InstallState
import dev.armuian.root.PrivilegeLevel
import dev.armuian.root.RootStatus

@Composable
fun stateText(s: ContainerState): String = stringResource(
    when (s) {
        ContainerState.NOT_INSTALLED -> R.string.state_not_installed
        ContainerState.DOWNLOADING -> R.string.state_downloading
        ContainerState.UNPACKING -> R.string.state_unpacking
        ContainerState.PREPARING -> R.string.state_preparing
        ContainerState.READY -> R.string.state_ready
        ContainerState.RUNNING -> R.string.state_running
        ContainerState.STOPPING -> R.string.state_stopping
        ContainerState.ERROR -> R.string.state_error
        ContainerState.UNSUPPORTED -> R.string.state_unsupported
    }
)

@Composable
private fun rootValue(root: RootStatus?, level: PrivilegeLevel?, busy: Boolean): String = when {
    busy -> stringResource(R.string.root_checking)
    root == null || level == null -> stringResource(R.string.root_unchecked)
    level == PrivilegeLevel.ROOT -> stringResource(R.string.priv_root)
    level == PrivilegeLevel.TEMPORARY_ROOT -> stringResource(R.string.priv_temp)
    root is RootStatus.SuNotFound -> stringResource(R.string.root_no_su)
    root is RootStatus.Denied -> stringResource(R.string.root_denied)
    else -> stringResource(R.string.root_failed)
}

@Composable
fun HomeScreen(vm: MainViewModel, listState: LazyListState, onOpenLaunchLog: () -> Unit) {
    val host by vm.host.collectAsStateWithLifecycle()
    val root by vm.rootStatus.collectAsStateWithLifecycle()
    val level by vm.privilege.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val state by vm.containerState.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val installs by vm.installStates.collectAsStateWithLifecycle()
    val used by vm.storageUsed.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val effective by vm.effectiveBackend.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val na = stringResource(R.string.unavailable)
    val computing = stringResource(R.string.computing)

    val current = entries.firstOrNull { installs[it.id] is InstallState.Installed }
    val running = installs.entries.firstNotNullOfOrNull { (id, s) -> (s as? InstallState.Running)?.let { id to it } }
    val runningEntry = running?.let { r -> entries.firstOrNull { it.id == r.first } }
    val cap = snapshot?.capabilities?.get(effective)
    val hasIssue = (cap != null && cap.availability != Availability.AVAILABLE) || state == ContainerState.ERROR
    val issueTint = if (cap?.availability == Availability.UNSUPPORTED || state == ContainerState.ERROR) cs.error else cs.tertiary
    val canStart = (state == ContainerState.READY || state == ContainerState.UNSUPPORTED || state == ContainerState.ERROR) && !busy
    val isRunning = state == ContainerState.RUNNING

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + LocalBottomBarPadding.current),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "hero") {
            Card(
                colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                    Text(AppInfo.APP_NAME, style = MaterialTheme.typography.headlineMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.version_fmt, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        AnimatedVisibility(visible = hasIssue) {
                            // Компактная иконка вместо большой красной плашки; по нажатию — причины в диалоге.
                            IconButton(onClick = { vm.showIssues() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Warning, stringResource(R.string.issues_title), tint = issueTint, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.active_system), style = MaterialTheme.typography.bodyLarge)
                    Text(current?.title ?: stringResource(R.string.no_system), style = MaterialTheme.typography.headlineLarge, maxLines = 2)
                    Text(host?.primaryAbi ?: na, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    TwoStepButton(
                        idle = stringResource(R.string.start_env), confirm = stringResource(R.string.sure),
                        onConfirm = vm::startEnvironment, modifier = Modifier.fillMaxWidth(), enabled = canStart,
                        icon = Icons.Filled.PlayArrow,
                    )
                    Spacer(Modifier.height(12.dp))
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val narrow = maxWidth < 330.dp
                        val stop: @Composable (Modifier) -> Unit = { m ->
                            FilledTonalButton(onClick = vm::stopEnvironment, enabled = isRunning, modifier = m.height(52.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                                Icon(Icons.Outlined.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.stop), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        val reboot: @Composable (Modifier) -> Unit = { m ->
                            FilledTonalButton(onClick = vm::restartEnvironment, enabled = isRunning, modifier = m.height(52.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                                Icon(Icons.Outlined.RestartAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.restart), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (narrow) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { stop(Modifier.fillMaxWidth()); reboot(Modifier.fillMaxWidth()) }
                        else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { stop(Modifier.weight(1f)); reboot(Modifier.weight(1f)) }
                    }
                }
            }
        }

        if (running != null) item(key = "progress") {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.install_progress_title) + (runningEntry?.let { ": ${it.title}" } ?: ""), style = MaterialTheme.typography.titleSmall)
                    val pct = running.second.progress?.percent
                    if (pct != null) LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        item(key = "status") {
            Card(
                colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                        Icon(MobileCheckIcon, null, tint = cs.primary, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.section_status), style = MaterialTheme.typography.titleLarge, color = cs.primary)
                    }
                    StatusRow("${stringResource(R.string.row_root)}: ${rootValue(root, level, busy)}") {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else TextButton(onClick = vm::checkRoot) {
                            Text(stringResource(R.string.root_check), maxLines = 1, softWrap = false)
                            Icon(Icons.Outlined.PlayArrow, null, Modifier.padding(start = 4.dp).size(18.dp))
                        }
                    }
                    StatusRow("${stringResource(R.string.row_container)}: ${stateText(state)}", leading = {
                        Crossfade(targetState = state, label = "state-icon") { s ->
                            when (s) {
                                ContainerState.RUNNING -> Icon(Icons.Filled.CheckCircle, null, tint = cs.tertiary, modifier = Modifier.size(20.dp))
                                ContainerState.ERROR, ContainerState.UNSUPPORTED -> Icon(Icons.Filled.Error, null, tint = cs.error, modifier = Modifier.size(20.dp))
                                ContainerState.DOWNLOADING, ContainerState.UNPACKING, ContainerState.PREPARING, ContainerState.STOPPING ->
                                    Icon(Icons.Outlined.Sync, null, tint = cs.secondary, modifier = Modifier.size(20.dp))
                                else -> Icon(Icons.Outlined.StopCircle, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            }
                        }
                    })
                    val auto = if (settings.backendChoice == BackendChoice.AUTO) " " + stringResource(R.string.auto_suffix) else ""
                    StatusRow("${stringResource(R.string.row_engine)}: ${backendName(effective)}$auto")
                    StatusRow("${stringResource(R.string.row_android)}: ${host?.let { "${it.androidRelease} (API ${it.apiLevel})" } ?: na}")
                    StatusRow("${stringResource(R.string.row_abi)}: ${host?.primaryAbi ?: na}")
                    StatusRow("${stringResource(R.string.row_storage_used)}: ${used?.let(::fmtBytes) ?: computing}")
                    StatusRow("${stringResource(R.string.row_free)}: ${host?.freeStorageBytes?.let(::fmtBytes) ?: na}", last = true)
                }
            }
        }

        if (current != null) item(key = "guest") { GuestCard(vm, onOpenLaunchLog) }
    }
}

@Composable
private fun StatusRow(
    text: String, last: Boolean = false, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = if (trailing != null) 2.dp else 11.dp), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) { leading(); Spacer(Modifier.width(10.dp)) }
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        trailing?.invoke()
    }
}
