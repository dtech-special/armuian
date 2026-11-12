package dev.armuian.container

import dev.armuian.core.ArmLog
import dev.armuian.detection.HostDetector
import dev.armuian.detection.HostInfo
import dev.armuian.root.KernelNode
import dev.armuian.root.PrivilegedOp
import dev.armuian.root.RootBridge
import dev.armuian.root.RootStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Собирает данные об устройстве один раз и оценивает все backend; выбирает подходящий. */
class BackendManager(
    private val detector: HostDetector,
    private val root: RootBridge,
    private val baseDir: File,
) {
    data class Snapshot(
        val host: HostInfo,
        val root: RootStatus?,
        val image: ImageReport?,
        val capabilities: Map<BackendKind, BackendCapability>,
    )

    private val backends: Map<BackendKind, ContainerBackend> = listOf<ContainerBackend>(
        GuestBackend(BackendKind.NATIVE_CONTAINER, experimental = false, unshareFlags = "-m -p -f -i -u -n", root = root, baseDir = baseDir),
        GuestBackend(BackendKind.SHARED_PID, experimental = true, unshareFlags = "-m", root = root, baseDir = baseDir),
        VirtualMachineBackend(),
    ).associateBy { it.kind }

    val all: List<ContainerBackend> get() = backends.values.toList()
    fun backend(kind: BackendKind): ContainerBackend = backends.getValue(kind)

    @Volatile private var lastRoot: RootStatus? = null

    /**
     * @param askRoot true — запросить root у менеджера (покажет окно KernelSU при необходимости);
     * false — использовать последний известный результат, без обращения к su.
     */
    suspend fun refresh(imageName: String?, imageMajor: Int?, askRoot: Boolean): Snapshot {
        val host = withContext(Dispatchers.IO) { detector.detect() }
        if (askRoot) lastRoot = root.detect()
        val rs = lastRoot
        val probe = if (rs is RootStatus.Granted) probeKernel() else null
        val image = imageName?.let { withContext(Dispatchers.IO) { ImageInspector.inspect(File(baseDir, "images/$it/rootfs")) } }
        val ctx = EvalContext(host, rs, probe, image, imageMajor)
        val caps = backends.mapValues { (_, b) -> b.evaluate(ctx) }
        caps.values.forEach { c -> ArmLog.i("Backends", "backend_capability", c.kind.name, c.availability.name, c.hardware.name) }
        return Snapshot(host, rs, image, caps)
    }

    fun rootStatus(): RootStatus? = lastRoot

    private suspend fun probeKernel(): KernelProbe {
        suspend fun cat(n: KernelNode) = root.execute(PrivilegedOp.CatNode(n)).takeIf { it.ok }?.stdout
        val ns = root.execute(PrivilegedOp.ListDirNode(KernelNode.PROC_INIT_NS)).takeIf { it.ok }
            ?.stdout?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }
        val visible = mutableSetOf<KernelNode>()
        for (n in listOf(KernelNode.DEV_BINDER, KernelNode.DEV_BINDERFS, KernelNode.DEV_KVM, KernelNode.APEX, KernelNode.LINKERCONFIG, KernelNode.CROSVM)) {
            if (root.execute(PrivilegedOp.ListNode(n)).ok) visible += n
        }
        val tools = root.execute(PrivilegedOp.ProbeTools).takeIf { it.ok }?.stdout?.lines()
            ?.mapNotNull { l -> l.split(':').takeIf { it.size == 2 }?.let { it[0].trim() to (it[1].trim() == "ok") } }?.toMap() ?: emptyMap()
        return KernelProbe(cat(KernelNode.PROC_FILESYSTEMS), cat(KernelNode.PROC_CGROUPS), ns, visible, cat(KernelNode.SELINUX_ENFORCE), tools)
    }

    fun resolve(choice: BackendChoice, caps: Map<BackendKind, BackendCapability>): BackendKind = BackendSelector.resolve(choice, caps)
}

object BackendSelector {
    /**
     * AUTO: Native Container, если он реально доступен; иначе Shared PID (ему нужен только mount namespace — самое слабое требование к ядру);
     * иначе VM, если доступна; иначе Shared PID (чтобы показать конкретные причины).
     */
    fun resolve(choice: BackendChoice, caps: Map<BackendKind, BackendCapability>): BackendKind = when (choice) {
        BackendChoice.NATIVE_CONTAINER -> BackendKind.NATIVE_CONTAINER
        BackendChoice.SHARED_PID -> BackendKind.SHARED_PID
        BackendChoice.VIRTUAL_MACHINE -> BackendKind.VIRTUAL_MACHINE
        BackendChoice.AUTO -> when {
            caps[BackendKind.NATIVE_CONTAINER]?.availability == Availability.AVAILABLE -> BackendKind.NATIVE_CONTAINER
            caps[BackendKind.SHARED_PID]?.canStart == true -> BackendKind.SHARED_PID
            caps[BackendKind.VIRTUAL_MACHINE]?.availability == Availability.AVAILABLE -> BackendKind.VIRTUAL_MACHINE
            else -> BackendKind.SHARED_PID
        }
    }
}
