package dev.armuian.container

import dev.armuian.core.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestScriptsTest {
    private val root = "/data/user/0/dev.armuian.app/files/armuian/images/redroid-16/rootfs"
    private val run = "/data/user/0/dev.armuian.app/files/armuian/run/shared"

    private fun noPlaceholders(s: String) = assertFalse("плейсхолдеры должны быть заменены", s.contains("@ROOT@") || s.contains("@RUN@") || s.contains('§'))

    @Test fun startScriptHasSingleInstanceGuardAndEntersNamespace() {
        val s = GuestScripts.start(root, run, "-m", "shared_pid")
        noPlaceholders(s)
        assertTrue(s.startsWith("#!/system/bin/sh"))
        assertTrue(s.contains("unshare -m --"))
        assertTrue("повторный запуск не должен плодить процессы", s.contains("not starting a second one"))
        assertTrue(s.contains("enter.sh"))
        assertFalse("Android init не запускается", s.contains("/init"))
    }

    @Test fun nativeFlagsAccepted() {
        assertTrue(GuestScripts.start(root, run, "-m -p -f -i -u -n", "native").contains("unshare -m -p -f -i -u -n --"))
    }

    @Test fun enterMakesMountsPrivateBeforeAnyBindAndNeverRelyOnFstab() {
        val s = GuestScripts.enter(root, run)
        noPlaceholders(s)
        val firstBind = s.indexOf("mount -o rbind")
        // все попытки сделать propagation private — до первого bind-монтирования
        listOf("mount --make-rprivate /", "mount -o rprivate none /").forEach { cmd ->
            assertTrue("нет варианта $cmd", s.contains(cmd))
            assertTrue("$cmd должен быть раньше bind", s.indexOf(cmd) in 0 until firstBind)
        }
        // результат проверяется по mountinfo, а не только по коду возврата
        assertTrue(s.contains("! grep -q ' shared:' /proc/self/mountinfo"))
        assertTrue("нечитаемый mountinfo не считается private", s.contains("[ -r /proc/self/mountinfo ]"))
        // отказ до монтирований при неудаче
        assertTrue(s.indexOf("exit 21") in 0 until firstBind)
        // проверка изоляции от mount namespace хоста
        assertTrue(s.contains("/proc/1/mountinfo"))
        // реальный вывод mount попадает в журнал
        assertTrue(s.contains("echo \"[armuian] mount: "))
        assertFalse(s.contains("/init"))
    }

    @Test fun rejectsInjection() {
        assertThrows(IllegalArgumentException::class.java) { GuestScripts.start("$root; rm -rf /", run, "-m", "x") }
        assertThrows(IllegalArgumentException::class.java) { GuestScripts.start(root, run, "-m; reboot", "x") }
        assertThrows(IllegalArgumentException::class.java) { GuestScripts.stop("/x y", run) }
        assertThrows(IllegalArgumentException::class.java) { GuestScripts.enter(root, "$run'") }
    }

    @Test fun parsesStatus() {
        val running = GuestScripts.parseStatus("uid=0\nprocs=1\npid=4321\nstarted=100\nnow=160\n")
        assertTrue(running.running); assertEquals(4321, running.pid); assertEquals(60L, running.uptimeSec)
        val stopped = GuestScripts.parseStatus("uid=0\nprocs=0\npid=\nnow=160\n")
        assertFalse(stopped.running); assertEquals(null, stopped.uptimeSec)
    }

    @Test fun parsesStopAndLeaks() {
        assertEquals(GuestScripts.StopResult(true, 0), GuestScripts.parseStop("stopped=ok\nleaked_mounts=0\n"))
        assertEquals(GuestScripts.StopResult(true, 3), GuestScripts.parseStop("stopped=none\nleaked_mounts=3\n"))
        assertFalse(GuestScripts.parseStop("garbage").stopped)
    }

    @Test fun guestLogLevels() {
        val parsed = GuestLogParser.parse(listOf(
            "[armuian] step: propagation: trying mount --make-rprivate /",
            "[armuian] mount: mount: bad /etc/fstab: No such file or directory",
            "[armuian] warn: mount --make-rprivate / failed (rc=1)",
            "[armuian] error: cannot make mounts private",
            "chroot: exec failed",
            "",
        ))
        assertEquals(listOf(LogLevel.INFO, LogLevel.WARN, LogLevel.WARN, LogLevel.ERROR, LogLevel.WARN), parsed.map { it.level })
        assertEquals("mount: bad /etc/fstab: No such file or directory", parsed[1].text.removePrefix("mount: "))
    }
}
