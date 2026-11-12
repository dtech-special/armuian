package dev.armuian.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.armuian.app.R
import dev.armuian.container.Availability
import dev.armuian.container.HardwareVerdict
import dev.armuian.container.IssueKind
import dev.armuian.image.ConnectionType
import dev.armuian.image.DownloadDecision
import dev.armuian.image.ImageRef

@Composable
fun connectionText(c: ConnectionType) = stringResource(
    when (c) { ConnectionType.WIFI -> R.string.net_wifi; ConnectionType.MOBILE -> R.string.net_mobile; ConnectionType.NONE -> R.string.net_none }
)

/** Все шаги установки: предупреждение о версии → экран подтверждения → предупреждение о мобильной сети. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallFlowHost(vm: MainViewModel) {
    val flow by vm.installFlow.collectAsStateWithLifecycle()
    val host by vm.host.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    when (val f = flow) {
        InstallFlow.Idle -> Unit

        is InstallFlow.VersionWarning -> AlertDialog(
            onDismissRequest = vm::dismissFlow,
            title = { Text(stringResource(R.string.version_warn_title)) },
            text = { Text(stringResource(R.string.version_warn_text)) },
            confirmButton = { TextButton(onClick = { vm.continueAfterWarning(f.entry) }) { Text(stringResource(R.string.continue_)) } },
            dismissButton = { TextButton(onClick = vm::dismissFlow) { Text(stringResource(R.string.back)) } },
        )

        is InstallFlow.MobileWarning -> AlertDialog(
            onDismissRequest = vm::dismissFlow,
            title = { Text(stringResource(R.string.mobile_title)) },
            text = {
                val size = f.plan.downloadBytes?.let(::fmtBytes) ?: stringResource(R.string.size_unknown)
                Text(stringResource(R.string.mobile_text, f.entry.title + " ARM64", size))
            },
            confirmButton = { TextButton(onClick = { vm.confirmMobile(f.plan) }) { Text(stringResource(R.string.download_mobile)) } },
            dismissButton = { TextButton(onClick = vm::dismissFlow) { Text(stringResource(R.string.cancel)) } },
        )

        is InstallFlow.Loading -> ModalBottomSheet(onDismissRequest = vm::dismissFlow) {
            Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Text(stringResource(R.string.loading_plan))
            }
        }

        is InstallFlow.Error -> ModalBottomSheet(onDismissRequest = vm::dismissFlow) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.plan_error, errorText(f.info)), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = vm::dismissFlow, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.back)) }
            }
        }

        is InstallFlow.Confirm -> ModalBottomSheet(onDismissRequest = vm::dismissFlow) {
            val plan = f.plan
            val hostMajor = host?.androidMajor
            val mismatch = f.entry.androidMajor != null && hostMajor != null && f.entry.androidMajor != hostMajor
            val blocked = plan.decision == DownloadDecision.BlockedNoNetwork || plan.decision == DownloadDecision.BlockedWifiOnly
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.confirm_install_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                InfoRow(stringResource(R.string.row_name), f.entry.title)
                InfoRow(stringResource(R.string.row_android_ver), f.entry.androidMajor?.toString() ?: stringResource(R.string.unknown))
                InfoRow(stringResource(R.string.row_arch), "ARM64 (arm64-v8a)")
                InfoRow(stringResource(R.string.row_source), f.entry.ref, mono = true)
                InfoRow(stringResource(R.string.row_dl_size), plan.downloadBytes?.let(::fmtBytes) ?: stringResource(R.string.size_unknown))
                InfoRow(stringResource(R.string.row_unpacked), stringResource(R.string.unknown))
                InfoRow(stringResource(R.string.row_location), "${dev.armuian.app.ui.pathOf(vm)}/images/${f.entry.id}", mono = true)
                InfoRow(stringResource(R.string.row_network), connectionText(plan.connection))
                if (mismatch) Text(stringResource(R.string.warn_version_mismatch, f.entry.androidMajor.toString(), hostMajor.toString()),
                    color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                if (host?.isArm64 == false) Text(stringResource(R.string.warn_not_arm), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                if (blocked) Text(
                    stringResource(if (plan.decision == DownloadDecision.BlockedNoNetwork) R.string.blocked_none else R.string.blocked_wifi),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = vm::dismissFlow, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            vm.confirmInstall(f.entry, plan)
                        },
                        enabled = !blocked, modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.confirm_install)) }
                }
            }
        }
    }
}

fun pathOf(vm: MainViewModel): String = vm.baseDir.path

@Composable
fun DeleteDialog(vm: MainViewModel) {
    val req by vm.deleteRequest.collectAsStateWithLifecycle()
    val r = req ?: return
    val size = r.bytes?.let(::fmtBytes) ?: stringResource(R.string.unknown)
    AlertDialog(
        onDismissRequest = vm::dismissDelete,
        title = { Text(stringResource(R.string.delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.delete_text, r.entry.title, size, r.path))
                if (r.removeSource) Text(stringResource(R.string.delete_custom_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = vm::confirmDelete) { Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = vm::dismissDelete) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Причины, по которым выбранная среда выполнения не запускается (или предупреждение об экспериментальности).
 * Аппаратная несовместимость, нехватка возможностей ядра и «ещё не реализовано в приложении» — разные разделы.
 */
@Composable
fun IssuesDialog(vm: MainViewModel) {
    val kind by vm.issueDialog.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val k = kind ?: return
    val cap = snapshot?.capabilities?.get(k)
    AlertDialog(
        onDismissRequest = vm::dismissIssues,
        title = { Text(backendName(k)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (cap == null) {
                    Text(stringResource(R.string.issues_not_checked))
                } else {
                    StatusPill(availabilityText(cap.availability), availabilityTone(cap.availability))
                    val summary = when {
                        cap.availability == Availability.NOT_IMPLEMENTED && cap.hardware == HardwareVerdict.OK -> R.string.issues_not_impl_hw_ok
                        cap.availability == Availability.NOT_IMPLEMENTED -> R.string.issues_not_impl
                        cap.availability == Availability.UNSUPPORTED -> R.string.issues_unsupported
                        cap.availability == Availability.UNKNOWN -> R.string.issues_unknown
                        cap.availability == Availability.EXPERIMENTAL -> R.string.issues_experimental
                        else -> R.string.issues_none
                    }
                    Text(stringResource(summary), style = MaterialTheme.typography.bodyMedium)
                    if (cap.experimental) Text(stringResource(R.string.experimental_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)

                    // «Не реализовано» — отдельная категория, не смешиваем с несовместимостью устройства.
                    if (!cap.implemented) {
                        IssueGroup(IssueKind.NOT_IMPLEMENTED, listOf(stringResource(R.string.vm_not_implemented_detail)))
                    }
                    cap.blockers.groupBy { it.issueKind }.forEach { (kindOf, list) ->
                        IssueGroup(kindOf, list.map { c -> checkTitle(c.id) + (checkDetail(c.id, c.detail)?.let { " — $it" } ?: "") })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::dismissIssues) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun IssueGroup(kind: IssueKind, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(issueKindTitle(kind), style = MaterialTheme.typography.titleSmall)
        lines.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun AddSourceDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val valid = remember(text) { text.isNotBlank() && runCatching { ImageRef.parse(text) }.isSuccess }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_source)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.add_source_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text, onValueChange = { text = it.trim() }, singleLine = true,
                    isError = text.isNotBlank() && !valid,
                    supportingText = { if (text.isNotBlank() && !valid) Text(stringResource(R.string.invalid_ref)) },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(text); onDismiss() }, enabled = valid) { Text(stringResource(R.string.add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
