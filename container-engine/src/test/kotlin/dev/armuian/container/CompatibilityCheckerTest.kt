package dev.armuian.container

import dev.armuian.detection.HostInfo
import dev.armuian.detection.SelinuxMode
import dev.armuian.root.KernelNode
import dev.armuian.root.RootManager
import dev.armuian.root.RootStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityCheckerTest {
    private fun host(abi: String = "arm64-v8a", release: String = "15", ns: List<String>? = listOf("mnt", "pid", "net", "ipc", "uts", "user")) = HostInfo(
        "Test", null, release, 35, abi, listOf(abi), "5.15", 8, 8L shl 30, 64L shl 30,
        SelinuxMode.ENFORCING, false, false, false, ns,
    )
    private val granted = RootStatus.Granted(0, RootManager.KERNELSU)
    private val goodImage = ImageReport(true, emptyList(), "16", 36, true, true)
    private val allNodes = setOf(KernelNode.DEV_BINDERFS, KernelNode.APEX, KernelNode.LINKERCONFIG, KernelNode.DEV_KVM)
    private val tools = mapOf("unshare" to true, "chroot" to true, "mount" to true, "readlink" to true, "ps" to true)
    private val probe = KernelProbe("nodev\tbinder\n", "x", listOf("mnt", "pid", "net", "ipc", "uts"), allNodes, "1", tools)

    private fun ctx(host: HostInfo = host(), root: RootStatus? = granted, probe: KernelProbe? = this.probe, image: ImageReport? = goodImage) =
        EvalContext(host, root, probe, image, 16)

    private fun eval(kind: BackendKind, impl: Boolean, exp: Boolean, c: EvalContext) = CompatibilityChecker.evaluate(kind, impl, exp, c)
    private fun level(r: BackendCapability, id: CheckId) = r.checks.first { it.id == id }.level

    @Test fun sharedPidIsExperimentalWhenEverythingIsOk() {
        val r = eval(BackendKind.SHARED_PID, true, true, ctx())
        assertEquals(Availability.EXPERIMENTAL, r.availability)
        assertTrue(r.canStart)
    }

    @Test fun nativeNeedsPidNamespaceButSharedDoesNot() {
        val c = ctx(probe = probe.copy(initNamespaces = listOf("mnt", "net", "ipc", "uts")))
        assertEquals(Availability.UNSUPPORTED, eval(BackendKind.NATIVE_CONTAINER, true, false, c).availability)
        assertEquals(Availability.EXPERIMENTAL, eval(BackendKind.SHARED_PID, true, true, c).availability)
    }

    @Test fun vmIsNotImplementedEvenWhenHardwareIsFine() {
        val r = eval(BackendKind.VIRTUAL_MACHINE, false, false, ctx())
        assertEquals(Availability.NOT_IMPLEMENTED, r.availability)
        assertEquals(HardwareVerdict.OK, r.hardware) // не путаем «не реализован» с «железо не подходит»
    }

    @Test fun vmWithoutKvmIsHardwareMissingButStillNotImplemented() {
        val r = eval(BackendKind.VIRTUAL_MACHINE, false, false, ctx(probe = probe.copy(visibleNodes = emptySet())))
        assertEquals(Availability.NOT_IMPLEMENTED, r.availability)
        assertEquals(HardwareVerdict.MISSING, r.hardware)
        assertEquals(IssueKind.HARDWARE, r.blockers.first().issueKind)
    }

    @Test fun unknownRootGivesUnknownAvailability() {
        val r = eval(BackendKind.SHARED_PID, true, true, ctx(root = null, probe = null))
        assertEquals(Availability.UNKNOWN, r.availability)
    }

    @Test fun noRootIsUnsupported() {
        val r = eval(BackendKind.SHARED_PID, true, true, ctx(root = RootStatus.SuNotFound))
        assertEquals(Availability.UNSUPPORTED, r.availability)
        assertEquals(IssueKind.PRIVILEGE, r.blockers.first { it.id == CheckId.ROOT }.issueKind)
    }

    @Test fun nonArmIsHardwareIssue() {
        val r = eval(BackendKind.SHARED_PID, true, true, ctx(host = host("x86_64")))
        assertEquals(CheckLevel.FAIL, level(r, CheckId.ARM64))
        assertEquals(HardwareVerdict.MISSING, r.hardware)
    }

    @Test fun missingImageAndToolsAreSeparateKinds() {
        val r = eval(BackendKind.SHARED_PID, true, true, ctx(image = null, probe = probe.copy(tools = tools + ("unshare" to false))))
        val kinds = r.blockers.map { it.issueKind }.toSet()
        assertTrue(IssueKind.IMAGE in kinds && IssueKind.ENVIRONMENT in kinds)
    }

    @Test fun versionMismatchIsWarningOnly() {
        val r = eval(BackendKind.SHARED_PID, true, true, ctx(host = host(release = "14")))
        assertEquals(CheckLevel.WARN, level(r, CheckId.IMAGE_VERSION))
        assertTrue(r.canStart)
    }

    private fun caps(native: Availability, shared: Availability, vm: Availability) = mapOf(
        BackendKind.NATIVE_CONTAINER to BackendCapability(BackendKind.NATIVE_CONTAINER, true, false, native, HardwareVerdict.OK, emptyList()),
        BackendKind.SHARED_PID to BackendCapability(BackendKind.SHARED_PID, true, true, shared, HardwareVerdict.OK, emptyList()),
        BackendKind.VIRTUAL_MACHINE to BackendCapability(BackendKind.VIRTUAL_MACHINE, false, false, vm, HardwareVerdict.OK, emptyList()),
    )

    @Test fun autoPrefersNativeThenSharedThenFallsBackToShared() {
        val sel = BackendSelector::resolve
        assertEquals(BackendKind.NATIVE_CONTAINER, sel(BackendChoice.AUTO, caps(Availability.AVAILABLE, Availability.EXPERIMENTAL, Availability.NOT_IMPLEMENTED)))
        assertEquals(BackendKind.SHARED_PID, sel(BackendChoice.AUTO, caps(Availability.UNSUPPORTED, Availability.EXPERIMENTAL, Availability.NOT_IMPLEMENTED)))
        assertEquals(BackendKind.SHARED_PID, sel(BackendChoice.AUTO, caps(Availability.UNSUPPORTED, Availability.UNSUPPORTED, Availability.NOT_IMPLEMENTED)))
        assertEquals(BackendKind.VIRTUAL_MACHINE, sel(BackendChoice.AUTO, caps(Availability.UNSUPPORTED, Availability.UNSUPPORTED, Availability.AVAILABLE)))
    }

    @Test fun manualChoiceIsRespectedEvenIfUnavailable() {
        assertEquals(BackendKind.VIRTUAL_MACHINE,
            BackendSelector.resolve(BackendChoice.VIRTUAL_MACHINE, caps(Availability.AVAILABLE, Availability.EXPERIMENTAL, Availability.NOT_IMPLEMENTED)))
    }
}
