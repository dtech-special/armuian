package dev.armuian.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.armuian.app.R
import dev.armuian.app.ui.InfoRow
import dev.armuian.app.ui.LocalBottomBarPadding
import dev.armuian.app.ui.MainViewModel
import dev.armuian.app.ui.SectionTitle
import dev.armuian.app.ui.StatusPill
import dev.armuian.app.ui.Tone
import dev.armuian.app.ui.availabilityText
import dev.armuian.app.ui.availabilityTone
import dev.armuian.app.ui.backendDescription
import dev.armuian.app.ui.backendName
import dev.armuian.app.ui.checkDetail
import dev.armuian.app.ui.checkTitle
import dev.armuian.app.ui.fmtBytes
import dev.armuian.app.ui.privilegeText
import dev.armuian.container.BackendChoice
import dev.armuian.container.BackendKind
import dev.armuian.container.CheckLevel
import dev.armuian.detection.SelinuxMode

private fun BackendKind.toChoice() = when (this) {
    BackendKind.NATIVE_CONTAINER -> BackendChoice.NATIVE_CONTAINER
    BackendKind.SHARED_PID -> BackendChoice.SHARED_PID
    BackendKind.VIRTUAL_MACHINE -> BackendChoice.VIRTUAL_MACHINE
}

/** Вкладка «Система»: среда выполнения, привилегии, проверки совместимости и данные об устройстве. */
@Composable
fun DeviceScreen(vm: MainViewModel, listState: LazyListState) {
    val host by vm.host.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val effective by vm.effectiveBackend.collectAsStateWithLifecycle()
    val recommended by vm.recommendedBackend.collectAsStateWithLifecycle()
    val level by vm.privilege.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val na = stringResource(R.string.unavailable)
    val yes = stringResource(R.string.visible)
    val no = stringResource(R.string.not_visible)
    val cs = MaterialTheme.colorScheme
    val cardColors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh)
    val shape = RoundedCornerShape(24.dp)

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + LocalBottomBarPadding.current),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ----- Среда выполнения -----
        item(key = "backends") {
            Card(Modifier.fillMaxWidth(), shape = shape, colors = cardColors) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionTitle(stringResource(R.string.section_runtime))
                    BackendRow(
                        selected = settings.backendChoice == BackendChoice.AUTO,
                        title = stringResource(R.string.backend_auto),
                        subtitle = stringResource(R.string.backend_auto_desc, backendName(effective)),
                        onSelect = { vm.setBackend(BackendChoice.AUTO) },
                    )
                    BackendKind.entries.forEach { k ->
                        val cap = snapshot?.capabilities?.get(k)
                        BackendRow(
                            selected = settings.backendChoice == k.toChoice(),
                            title = backendName(k),
                            subtitle = backendDescription(k),
                            status = cap?.availability?.let { availabilityText(it) },
                            tone = cap?.availability?.let { availabilityTone(it) } ?: Tone.NEUTRAL,
                            recommended = k == recommended,
                            onSelect = { vm.setBackend(k.toChoice()) },
                            onReasons = if (cap != null && !cap.canStart || k == BackendKind.SHARED_PID) ({ vm.showIssues(k) }) else null,
                        )
                    }
                    Text(stringResource(R.string.runtime_note), style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        // ----- Привилегии -----
        item(key = "privilege") {
            Card(Modifier.fillMaxWidth(), shape = shape, colors = cardColors) {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle(stringResource(R.string.section_privileges))
                    InfoRow(stringResource(R.string.row_priv_mode), level?.let { privilegeText(it) } ?: stringResource(R.string.root_unchecked))
                    Text(stringResource(R.string.priv_hint), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Button(onClick = vm::checkRoot, enabled = !busy, modifier = Modifier.padding(top = 12.dp)) {
                        Text(stringResource(R.string.check_compat), maxLines = 1, softWrap = false)
                    }
                }
            }
        }

        // ----- Проверки выбранной среды -----
        item(key = "checks") {
            Card(Modifier.fillMaxWidth(), shape = shape, colors = cardColors) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle(stringResource(R.string.section_compat) + " · " + backendName(effective))
                    }
                    val cap = snapshot?.capabilities?.get(effective)
                    if (cap == null) Text(stringResource(R.string.compat_unchecked), style = MaterialTheme.typography.bodyMedium)
                    cap?.checks?.forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                            val (icon, tint) = when (c.level) {
                                CheckLevel.OK -> Icons.Outlined.CheckCircle to cs.tertiary
                                CheckLevel.WARN -> Icons.Outlined.Warning to cs.secondary
                                CheckLevel.FAIL -> Icons.Outlined.Error to cs.error
                                CheckLevel.UNKNOWN -> Icons.Outlined.Info to cs.onSurfaceVariant
                            }
                            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(checkTitle(c.id) + if (c.required) " *" else "", style = MaterialTheme.typography.bodyMedium)
                                checkDetail(c.id, c.detail)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
                            }
                        }
                    }
                    if (cap != null) Text(stringResource(R.string.compat_legend), style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        // ----- Данные об устройстве -----
        item(key = "device") {
            Card(Modifier.fillMaxWidth(), shape = shape, colors = cardColors) {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle(stringResource(R.string.dev_section_device))
                    InfoRow(stringResource(R.string.row_device), host?.deviceModel ?: na)
                    InfoRow(stringResource(R.string.row_soc), host?.soc ?: na)
                    InfoRow(stringResource(R.string.row_cores), host?.cpuCores?.toString() ?: na)
                    InfoRow(stringResource(R.string.row_ram), host?.totalRamBytes?.let(::fmtBytes) ?: na)
                    InfoRow(stringResource(R.string.row_free), host?.freeStorageBytes?.let(::fmtBytes) ?: na)
                }
            }
        }
        item(key = "os") {
            Card(Modifier.fillMaxWidth(), shape = shape, colors = cardColors) {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle(stringResource(R.string.dev_section_os))
                    InfoRow(stringResource(R.string.row_android), host?.let { "${it.androidRelease} (API ${it.apiLevel})" } ?: na)
                    InfoRow(stringResource(R.string.row_abi), host?.supportedAbis?.joinToString(", ") ?: na)
                    InfoRow(stringResource(R.string.row_kernel), host?.kernelVersion ?: na)
                    InfoRow(stringResource(R.string.row_selinux), when (host?.selinux) {
                        SelinuxMode.ENFORCING -> "Enforcing"; SelinuxMode.PERMISSIVE -> "Permissive"; null -> na
                    })
                }
            }
        }
        item(key = "features") {
            Card(Modifier.fillMaxWidth(), shape = shape, colors = cardColors) {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle(stringResource(R.string.dev_section_features))
                    InfoRow("Binder", host?.let { if (it.binderDeviceVisible) yes else no } ?: na)
                    InfoRow("BinderFS", host?.let { if (it.binderFsVisible) yes else no } ?: na)
                    InfoRow("KVM", host?.let { if (it.kvmVisible) yes else no } ?: na)
                    InfoRow("AVF", host?.let { if (it.avfFeature) yes else no } ?: na)
                    InfoRow(stringResource(R.string.row_ns), host?.namespaces?.joinToString(", ") ?: na)
                    Text(stringResource(R.string.visibility_note), style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun BackendRow(
    selected: Boolean, title: String, subtitle: String, onSelect: () -> Unit,
    status: String? = null, tone: Tone = Tone.NEUTRAL, onReasons: (() -> Unit)? = null, recommended: Boolean = false,
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.weight(1f).padding(top = 12.dp, end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (recommended) StatusPill(stringResource(R.string.backend_recommended), Tone.GOOD)
            if (onReasons != null) TextButton(onClick = onReasons, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.reasons)) }
        }
        if (status != null) Column(Modifier.padding(top = 12.dp)) { StatusPill(status, tone) }
    }
}
