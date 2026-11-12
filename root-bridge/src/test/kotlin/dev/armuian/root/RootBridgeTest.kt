package dev.armuian.root

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RootBridgeTest {
    private class Fake(val result: ShellResult) : ShellRunner {
        var last: List<String>? = null
        override suspend fun run(argv: List<String>, timeoutMs: Long): ShellResult { last = argv; return result }
    }

    private fun detect(r: ShellResult, installed: Set<String> = emptySet()) = runBlocking {
        RootBridge(Fake(r), ManagerDetector { it in installed }).detect()
    }

    @Test fun grantedWhenUidZero() {
        val s = detect(ShellResult(0, "uid=0(root) gid=0(root)", ""), setOf("me.weishu.kernelsu"))
        assertEquals(RootStatus.Granted(0, RootManager.KERNELSU), s)
    }

    @Test fun uidNotZeroIsFailure() {
        assertTrue(detect(ShellResult(0, "uid=2000(shell)", "")) is RootStatus.Failed)
    }

    @Test fun suMissing() = assertEquals(RootStatus.SuNotFound, detect(ShellResult(127, "", "x")))

    @Test fun denied() {
        assertTrue(detect(ShellResult(1, "", "Permission denied")) is RootStatus.Denied)
        assertTrue(detect(ShellResult(-1, "", "timeout", timedOut = true)) is RootStatus.Denied)
    }

    @Test fun executeUsesCommandOfOp() {
        val f = Fake(ShellResult(0, "", ""))
        runBlocking { RootBridge(f).execute(PrivilegedOp.Id) }
        assertEquals(listOf("su", "-c", "id"), f.last)
    }

    @Test fun pathGuardRejectsUnsafe() {
        val root = "/data/adb/armuian"
        PrivilegedOp.MkdirP("$root/images/redroid-16")
        listOf("/etc", "$root/../etc", "$root/a b", "$root/x;rm", "$root/", "relative", "/data/adb/armuian2/x")
            .forEach { p -> assertThrows(IllegalArgumentException::class.java) { PrivilegedOp.MkdirP(p) } }
    }

    @Test fun removeTreeProtectsTopLevel() {
        val root = "/data/adb/armuian"
        assertThrows(IllegalArgumentException::class.java) { PrivilegedOp.RemoveTree(root) }
        assertThrows(IllegalArgumentException::class.java) { PrivilegedOp.RemoveTree("$root/images") }
        assertEquals("rm -rf $root/images/redroid-16", PrivilegedOp.RemoveTree("$root/images/redroid-16").command())
    }

    @Test fun privilegeLevels() {
        assertEquals(null, (null as RootStatus?).privilegeLevel())
        assertEquals(PrivilegeLevel.ROOT, RootStatus.Granted(0, RootManager.KERNELSU).privilegeLevel())
        assertEquals(PrivilegeLevel.TEMPORARY_ROOT, RootStatus.Granted(0, RootManager.NOT_DETECTED).privilegeLevel())
        assertEquals(PrivilegeLevel.NON_ROOT, RootStatus.SuNotFound.privilegeLevel())
        assertEquals(PrivilegeLevel.NON_ROOT, RootStatus.Denied("x").privilegeLevel())
    }

    @Test fun scriptOpsAreGuarded() {
        val base = "/data/user/0/dev.armuian.app/files/armuian"
        assertEquals("sh $base/run/shared/start.sh", PrivilegedOp.RunScript("$base/run/shared/start.sh", base).command())
        assertThrows(IllegalArgumentException::class.java) { PrivilegedOp.RunScript("/system/bin/sh", base) }
        assertThrows(IllegalArgumentException::class.java) { PrivilegedOp.RunScript("$base/run/x.txt", base) }
        assertThrows(IllegalArgumentException::class.java) { PrivilegedOp.RunScript("$base/../x.sh", base) }
        assertThrows(IllegalArgumentException::class.java) { PrivilegedOp.TailFile("$base/run/a.log", base, lines = 0) }
    }

    @Test fun nodesAreWhitelisted() {
        assertEquals("cat /proc/filesystems", PrivilegedOp.CatNode(KernelNode.PROC_FILESYSTEMS).command())
    }
}
