package dev.armuian.container

import dev.armuian.detection.SelinuxMode
import dev.armuian.root.KernelNode
import dev.armuian.root.RootStatus

object CompatibilityChecker {
    private const val MIN_FREE = 4L * 1024 * 1024 * 1024
    private val BINDER_FS = Regex("""(?m)\bbinder(fs)?\s*$""")

    fun evaluate(kind: BackendKind, implemented: Boolean, experimental: Boolean, c: EvalContext): BackendCapability {
        val checks = mutableListOf<CompatCheck>()
        checks += CompatCheck(CheckId.ARM64, if (c.host.isArm64) CheckLevel.OK else CheckLevel.FAIL, c.host.primaryAbi, true)

        when (kind) {
            BackendKind.NATIVE_CONTAINER, BackendKind.SHARED_PID -> {
                checks += root(c)
                checks += image(c)
                checks += tools(c)
                checks += namespace(c, "mnt", CheckId.NS_MNT, required = true)
                if (kind == BackendKind.NATIVE_CONTAINER) {
                    checks += namespace(c, "pid", CheckId.NS_PID, required = true)
                    checks += otherNamespaces(c)
                }
            }
            BackendKind.VIRTUAL_MACHINE -> {
                checks += kvm(c)
                checks += CompatCheck(CheckId.AVF, if (c.host.avfFeature) CheckLevel.OK else CheckLevel.UNKNOWN, null, false)
                val crosvm = c.probe?.let { KernelNode.CROSVM in it.visibleNodes }
                checks += CompatCheck(CheckId.CROSVM, when (crosvm) { true -> CheckLevel.OK; false -> CheckLevel.WARN; null -> CheckLevel.UNKNOWN }, null, false)
            }
        }

        // Эти возможности проверяются отдельно и не означают готовность графики/Binder.
        checks += CompatCheck(CheckId.CGROUPS, if (c.probe?.cgroups != null) CheckLevel.OK else CheckLevel.UNKNOWN, null, false)
        checks += selinux(c)
        checks += storage(c)
        checks += binder(c)
        checks += CompatCheck(CheckId.GRAPHICS, CheckLevel.WARN, null, false) // графический мост не подключён
        checks += imageVersion(c)

        val required = checks.filter { it.required }
        val hardware = when {
            required.any { it.level == CheckLevel.FAIL } -> HardwareVerdict.MISSING
            required.all { it.level == CheckLevel.OK } -> HardwareVerdict.OK
            else -> HardwareVerdict.UNKNOWN
        }
        val availability = when {
            !implemented -> Availability.NOT_IMPLEMENTED
            hardware == HardwareVerdict.MISSING -> Availability.UNSUPPORTED
            hardware == HardwareVerdict.UNKNOWN -> Availability.UNKNOWN
            experimental -> Availability.EXPERIMENTAL
            else -> Availability.AVAILABLE
        }
        return BackendCapability(kind, implemented, experimental, availability, hardware, checks)
    }

    private fun root(c: EvalContext) = when (c.root) {
        is RootStatus.Granted -> CompatCheck(CheckId.ROOT, CheckLevel.OK, "uid=0", true)
        null -> CompatCheck(CheckId.ROOT, CheckLevel.UNKNOWN, null, true)
        else -> CompatCheck(CheckId.ROOT, CheckLevel.FAIL, null, true)
    }

    private fun image(c: EvalContext): CompatCheck {
        val img = c.image ?: return CompatCheck(CheckId.IMAGE, CheckLevel.FAIL, ImageIssue.NO_ROOTFS.name, true)
        val blocking = img.issues.filter { it != ImageIssue.NO_INIT }
        return if (img.usable) CompatCheck(CheckId.IMAGE, CheckLevel.OK, img.androidRelease?.let { "Android $it" }, true)
        else CompatCheck(CheckId.IMAGE, CheckLevel.FAIL, blocking.joinToString(",") { it.name }, true)
    }

    private fun tools(c: EvalContext): CompatCheck {
        val p = c.probe ?: return CompatCheck(CheckId.TOOLS, CheckLevel.UNKNOWN, null, true)
        val missing = p.tools.filterValues { !it }.keys.toMutableList()
        if (KernelNode.APEX !in p.visibleNodes) missing += "/apex"
        if (KernelNode.LINKERCONFIG !in p.visibleNodes) missing += "/linkerconfig"
        if (p.tools.isEmpty()) return CompatCheck(CheckId.TOOLS, CheckLevel.UNKNOWN, null, true)
        return if (missing.isEmpty()) CompatCheck(CheckId.TOOLS, CheckLevel.OK, null, true)
        else CompatCheck(CheckId.TOOLS, CheckLevel.FAIL, missing.joinToString(" "), true)
    }

    private fun namespaces(c: EvalContext): List<String>? = c.probe?.initNamespaces ?: c.host.namespaces

    private fun namespace(c: EvalContext, name: String, id: CheckId, required: Boolean): CompatCheck {
        val ns = namespaces(c) ?: return CompatCheck(id, CheckLevel.UNKNOWN, null, required)
        return if (name in ns) CompatCheck(id, CheckLevel.OK, name, required) else CompatCheck(id, CheckLevel.FAIL, name, required)
    }

    private fun otherNamespaces(c: EvalContext): CompatCheck {
        val ns = namespaces(c) ?: return CompatCheck(CheckId.NS_OTHER, CheckLevel.UNKNOWN, null, true)
        val missing = listOf("ipc", "uts", "net").filter { it !in ns }
        return if (missing.isEmpty()) CompatCheck(CheckId.NS_OTHER, CheckLevel.OK, "ipc uts net", true)
        else CompatCheck(CheckId.NS_OTHER, CheckLevel.FAIL, missing.joinToString(" "), true)
    }

    private fun kvm(c: EvalContext): CompatCheck {
        val p = c.probe
        return when {
            p != null -> if (KernelNode.DEV_KVM in p.visibleNodes) CompatCheck(CheckId.KVM, CheckLevel.OK, null, true)
                else CompatCheck(CheckId.KVM, CheckLevel.FAIL, null, true)
            c.host.kvmVisible -> CompatCheck(CheckId.KVM, CheckLevel.OK, null, true)
            else -> CompatCheck(CheckId.KVM, CheckLevel.UNKNOWN, null, true) // без root узел может быть скрыт
        }
    }

    private fun selinux(c: EvalContext): CompatCheck {
        val se = c.host.selinux ?: c.probe?.selinuxEnforce?.trim()?.let { if (it == "1") SelinuxMode.ENFORCING else if (it == "0") SelinuxMode.PERMISSIVE else null }
        return when (se) {
            SelinuxMode.ENFORCING -> CompatCheck(CheckId.SELINUX, CheckLevel.WARN, "Enforcing", false)
            SelinuxMode.PERMISSIVE -> CompatCheck(CheckId.SELINUX, CheckLevel.OK, "Permissive", false)
            null -> CompatCheck(CheckId.SELINUX, CheckLevel.UNKNOWN, null, false)
        }
    }

    private fun storage(c: EvalContext): CompatCheck {
        val free = c.host.freeStorageBytes
        return when {
            free == null -> CompatCheck(CheckId.STORAGE, CheckLevel.UNKNOWN, null, false)
            free >= MIN_FREE -> CompatCheck(CheckId.STORAGE, CheckLevel.OK, null, false)
            else -> CompatCheck(CheckId.STORAGE, CheckLevel.WARN, null, false)
        }
    }

    private fun binder(c: EvalContext): CompatCheck {
        val p = c.probe
        val found = if (p != null) KernelNode.DEV_BINDER in p.visibleNodes || KernelNode.DEV_BINDERFS in p.visibleNodes ||
            (p.filesystems?.let { BINDER_FS.containsMatchIn(it) } == true)
        else c.host.binderDeviceVisible || c.host.binderFsVisible
        return when {
            found -> CompatCheck(CheckId.BINDER, CheckLevel.OK, null, false)
            p == null -> CompatCheck(CheckId.BINDER, CheckLevel.UNKNOWN, null, false)
            else -> CompatCheck(CheckId.BINDER, CheckLevel.WARN, null, false)
        }
    }

    private fun imageVersion(c: EvalContext): CompatCheck {
        val host = c.host.androidMajor
        val img = c.imageMajor
        return when {
            img == null || host == null -> CompatCheck(CheckId.IMAGE_VERSION, CheckLevel.UNKNOWN, null, false)
            img == host -> CompatCheck(CheckId.IMAGE_VERSION, CheckLevel.OK, "Android $host", false)
            else -> CompatCheck(CheckId.IMAGE_VERSION, CheckLevel.WARN, "host $host / image $img", false)
        }
    }
}
