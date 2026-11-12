package dev.armuian.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.armuian.app.R
import dev.armuian.container.Availability
import dev.armuian.container.BackendKind
import dev.armuian.container.CheckId
import dev.armuian.container.ImageIssue
import dev.armuian.container.IssueKind
import dev.armuian.root.PrivilegeLevel

@Composable
fun backendName(k: BackendKind): String = stringResource(
    when (k) {
        BackendKind.NATIVE_CONTAINER -> R.string.backend_native
        BackendKind.SHARED_PID -> R.string.backend_shared
        BackendKind.VIRTUAL_MACHINE -> R.string.backend_vm
    }
)

@Composable
fun backendDescription(k: BackendKind): String = stringResource(
    when (k) {
        BackendKind.NATIVE_CONTAINER -> R.string.backend_native_desc
        BackendKind.SHARED_PID -> R.string.backend_shared_desc
        BackendKind.VIRTUAL_MACHINE -> R.string.backend_vm_desc
    }
)

@Composable
fun availabilityText(a: Availability): String = stringResource(
    when (a) {
        Availability.AVAILABLE -> R.string.av_available
        Availability.EXPERIMENTAL -> R.string.av_experimental
        Availability.UNSUPPORTED -> R.string.av_unsupported
        Availability.NOT_IMPLEMENTED -> R.string.av_not_implemented
        Availability.UNKNOWN -> R.string.av_unknown
    }
)

fun availabilityTone(a: Availability): Tone = when (a) {
    Availability.AVAILABLE -> Tone.GOOD
    Availability.EXPERIMENTAL -> Tone.WARN
    Availability.UNSUPPORTED -> Tone.BAD
    Availability.NOT_IMPLEMENTED, Availability.UNKNOWN -> Tone.NEUTRAL
}

@Composable
fun privilegeText(p: PrivilegeLevel): String = stringResource(
    when (p) {
        PrivilegeLevel.NON_ROOT -> R.string.priv_non_root
        PrivilegeLevel.TEMPORARY_ROOT -> R.string.priv_temp
        PrivilegeLevel.ROOT -> R.string.priv_root
    }
)

@Composable
fun checkTitle(id: CheckId): String = stringResource(
    when (id) {
        CheckId.ARM64 -> R.string.check_arm64
        CheckId.ROOT -> R.string.row_root
        CheckId.IMAGE -> R.string.check_image_files
        CheckId.TOOLS -> R.string.check_tools
        CheckId.NS_MNT -> R.string.check_ns_mnt
        CheckId.NS_PID -> R.string.check_ns_pid
        CheckId.NS_OTHER -> R.string.check_ns_other
        CheckId.CGROUPS -> R.string.check_cgroups
        CheckId.SELINUX -> R.string.row_selinux
        CheckId.STORAGE -> R.string.check_storage
        CheckId.BINDER -> R.string.check_binder
        CheckId.GRAPHICS -> R.string.check_graphics
        CheckId.KVM -> R.string.check_kvm
        CheckId.AVF -> R.string.check_avf
        CheckId.CROSVM -> R.string.check_crosvm
        CheckId.IMAGE_VERSION -> R.string.check_image
    }
)

/** Для проверки образа детали — коды проблем; переводим их, остальное показываем как есть (технические значения). */
@Composable
fun checkDetail(id: CheckId, detail: String?): String? {
    if (detail == null) return null
    if (id != CheckId.IMAGE) return detail

    val translated = mutableListOf<String>()

    for (code in detail.split(',')) {
        val text = when (runCatching { ImageIssue.valueOf(code) }.getOrNull()) {
            ImageIssue.NO_ROOTFS -> stringResource(R.string.img_no_rootfs)
            ImageIssue.NO_TOYBOX -> stringResource(R.string.img_no_toybox)
            ImageIssue.TOYBOX_NOT_ARM64 -> stringResource(R.string.img_not_arm64)
            ImageIssue.NO_LINKER -> stringResource(R.string.img_no_linker)
            ImageIssue.NO_INIT -> stringResource(R.string.img_no_init)
            null -> code
        }

        translated.add(text)
    }

    return translated.joinToString("; ")
}

@Composable
fun issueKindTitle(k: IssueKind): String = stringResource(
    when (k) {
        IssueKind.HARDWARE -> R.string.issue_hardware
        IssueKind.KERNEL -> R.string.issue_kernel
        IssueKind.PRIVILEGE -> R.string.issue_privilege
        IssueKind.ENVIRONMENT -> R.string.issue_env
        IssueKind.IMAGE -> R.string.issue_image
        IssueKind.NOT_IMPLEMENTED -> R.string.issue_not_implemented
        IssueKind.UNKNOWN -> R.string.issue_unknown
    }
)
