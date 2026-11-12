package dev.armuian.container

import dev.armuian.core.ErrorCode
import dev.armuian.detection.HostInfo
import dev.armuian.root.KernelNode
import dev.armuian.root.RootStatus

enum class BackendKind { NATIVE_CONTAINER, SHARED_PID, VIRTUAL_MACHINE }

/** Выбор пользователя: автоматически или конкретный backend. */
enum class BackendChoice { AUTO, NATIVE_CONTAINER, SHARED_PID, VIRTUAL_MACHINE }

/** Не путать: NOT_IMPLEMENTED — функции нет в приложении; UNSUPPORTED — устройство не подходит. */
enum class Availability { AVAILABLE, EXPERIMENTAL, UNSUPPORTED, NOT_IMPLEMENTED, UNKNOWN }

enum class HardwareVerdict { OK, MISSING, UNKNOWN }

enum class CheckLevel { OK, WARN, FAIL, UNKNOWN }

enum class CheckId { ARM64, ROOT, IMAGE, TOOLS, NS_MNT, NS_PID, NS_OTHER, CGROUPS, SELINUX, STORAGE, BINDER, GRAPHICS, KVM, AVF, CROSVM, IMAGE_VERSION }

/** Категория причины — чтобы не называть устройство несовместимым, когда не хватает лишь реализации. */
enum class IssueKind { HARDWARE, KERNEL, PRIVILEGE, ENVIRONMENT, IMAGE, NOT_IMPLEMENTED, UNKNOWN }

data class CompatCheck(val id: CheckId, val level: CheckLevel, val detail: String?, val required: Boolean) {
    val issueKind: IssueKind
        get() = if (level == CheckLevel.UNKNOWN) IssueKind.UNKNOWN else when (id) {
            CheckId.ARM64, CheckId.KVM -> IssueKind.HARDWARE
            CheckId.NS_MNT, CheckId.NS_PID, CheckId.NS_OTHER, CheckId.BINDER, CheckId.CGROUPS -> IssueKind.KERNEL
            CheckId.ROOT -> IssueKind.PRIVILEGE
            CheckId.TOOLS, CheckId.AVF, CheckId.CROSVM -> IssueKind.ENVIRONMENT
            CheckId.IMAGE -> IssueKind.IMAGE
            else -> IssueKind.KERNEL
        }
}

data class BackendCapability(
    val kind: BackendKind,
    val implemented: Boolean,
    val experimental: Boolean,
    val availability: Availability,
    val hardware: HardwareVerdict,
    val checks: List<CompatCheck>,
) {
    /** Обязательные проверки, которые не пройдены. */
    val blockers: List<CompatCheck> get() = checks.filter { it.required && it.level != CheckLevel.OK }
    val canStart: Boolean get() = availability == Availability.AVAILABLE || availability == Availability.EXPERIMENTAL
}

/** Данные, прочитанные с root-правами (null/пусто — прочитать не удалось). */
data class KernelProbe(
    val filesystems: String?,
    val cgroups: String?,
    val initNamespaces: List<String>?,
    val visibleNodes: Set<KernelNode>,
    val selinuxEnforce: String?,
    val tools: Map<String, Boolean>,
)

enum class ImageIssue { NO_ROOTFS, NO_TOYBOX, TOYBOX_NOT_ARM64, NO_LINKER, NO_INIT }

data class ImageReport(
    val usable: Boolean,
    val issues: List<ImageIssue>,
    val androidRelease: String?,
    val apiLevel: Int?,
    val initPresent: Boolean,
    val apexFlattened: Boolean?,
)

data class EvalContext(
    val host: HostInfo,
    val root: RootStatus?,
    val probe: KernelProbe?,
    val image: ImageReport?,
    val imageMajor: Int?,
)

data class ContainerStatus(
    val running: Boolean,
    val pid: Int? = null,
    val uptimeSec: Long? = null,
    val processes: Int = 0,
    /** Если статус получить не удалось (например, потерян root). */
    val errorCode: ErrorCode? = null,
)
