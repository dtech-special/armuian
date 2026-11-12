package dev.armuian.container

import dev.armuian.core.ArmException
import dev.armuian.core.ArmLog
import dev.armuian.core.ArmResult
import dev.armuian.core.ErrorCode
import dev.armuian.core.armFail
import dev.armuian.core.runArm
import dev.armuian.root.PrivilegedOp
import dev.armuian.root.RootBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Общая реализация для двух вариантов без собственного ядра: отличаются только набором namespace.
 *  - Native Container: mount + PID + IPC + UTS + network namespaces, собственный PID 1;
 *  - Shared PID: только mount namespace, PID общий с хостом (экспериментально).
 *
 * Все сообщения журнала — результат реального выполнения: строки guest.log (их пишут скрипты и сами утилиты)
 * переносятся в журнал приложения с уровнями INFO/WARN/ERROR.
 */
class GuestBackend(
    override val kind: BackendKind,
    override val experimental: Boolean,
    private val unshareFlags: String,
    private val root: RootBridge,
    private val baseDir: File,
) : ContainerBackend {

    override val implemented = true
    private val tag = "Backend-${kind.name}"
    private val runDir = File(baseDir, "run/${if (kind == BackendKind.NATIVE_CONTAINER) "native" else "shared"}")
    private val baseRoot get() = baseDir.path

    private fun rootfs(name: String) = File(baseDir, "images/$name/rootfs")
    private fun script(n: String) = File(runDir, n)

    override fun evaluate(ctx: EvalContext) = CompatibilityChecker.evaluate(kind, implemented, experimental, ctx)

    override fun hasSession() = File(runDir, "guest.pid").isFile

    override fun currentImage(): String? = runCatching { File(runDir, "current").readText().trim().ifEmpty { null } }.getOrNull()

    override suspend fun prepare(imageName: String): ArmResult<Unit> = withContext(Dispatchers.IO) {
        runArm {
            val rf = rootfs(imageName)
            val report = ImageInspector.inspect(rf)
            ArmLog.i(tag, "image_inspect", imageName, report.issues.joinToString(",").ifEmpty { "ok" })
            if (!report.usable) {
                throw ArmException(ErrorCode.IMAGE_INVALID, "Image is not usable: ${report.issues}", listOf(report.issues.joinToString(",") { it.name }))
            }
            check(runDir.isDirectory || runDir.mkdirs()) { "Cannot create ${runDir.path}" }
            val canonRoot = rf.canonicalPath
            script("start.sh").writeText(GuestScripts.start(canonRoot, runDir.path, unshareFlags, kind.name.lowercase()))
            script("enter.sh").writeText(GuestScripts.enter(canonRoot, runDir.path))
            script("stop.sh").writeText(GuestScripts.stop(canonRoot, runDir.path))
            script("status.sh").writeText(GuestScripts.status(canonRoot, runDir.path))
            File(runDir, "current").writeText(imageName)
            ArmLog.i(tag, "guest_prepare", runDir.path)
        }
    }

    override suspend fun start(imageName: String): ArmResult<Unit> {
        val prepared = prepare(imageName)
        if (prepared is ArmResult.Fail) return prepared

        // Повторный запуск не создаёт дублей: если гость уже работает — просто сообщаем об этом.
        val before = status()
        if (before.errorCode != null) return armFail(ErrorCode.ROOT_REQUIRED, "Root is not available")
        if (before.running) return armFail(ErrorCode.GUEST_ALREADY_RUNNING, "Guest is already running")

        ArmLog.i(tag, "guest_start", kind.name)
        root.execute(PrivilegedOp.StartScript(script("start.sh").path, baseRoot), 15_000)

        // Ждём реального результата: опрашиваем статус до ~5 с (раньше был фиксированный сон).
        var st = status()
        var waited = 0
        while (!st.running && waited < 5_000 && !guestFinished()) {
            delay(500); waited += 500
            st = status()
        }
        val lines = logs()
        mirrorToAppLog(lines)

        // RUNNING возвращаем только если гостевой процесс фактически существует и его root = наш rootfs.
        if (st.running) {
            ArmLog.i(tag, "guest_started", st.pid ?: -1, st.processes)
            return ArmResult.Ok(Unit)
        }
        val errors = GuestLogParser.parse(lines).filter { it.level == dev.armuian.core.LogLevel.ERROR || it.level == dev.armuian.core.LogLevel.WARN }
        val reason = errors.takeLast(4).joinToString(" | ") { it.text }.ifEmpty { "no output" }
        ArmLog.e(tag, "guest_start_failed", reason)
        return armFail(ErrorCode.GUEST_START_FAILED, "Guest process did not start: $reason", reason)
    }

    /** Скрипт завершился с ошибкой (в журнале есть error:), ждать дальше бессмысленно. */
    private suspend fun guestFinished(): Boolean = logs().any { it.startsWith("[armuian] error:") }

    /** Переносит реальные строки guest.log в журнал приложения (уровни по префиксу). */
    private fun mirrorToAppLog(lines: List<String>) {
        GuestLogParser.parse(lines).forEach { ArmLog.log(it.level, "guest", "raw", listOf(it.text)) }
    }

    override suspend fun stop(): ArmResult<Unit> {
        if (!File(runDir, "stop.sh").isFile) return armFail(ErrorCode.NOT_RUNNING, "Container is not running")
        val r = root.execute(PrivilegedOp.RunScript(script("stop.sh").path, baseRoot), 30_000)
        if (!r.ok) return armFail(ErrorCode.ROOT_LOST, "Cannot run stop script: ${r.stderr.trim()}")
        val res = GuestScripts.parseStop(r.stdout)
        if (res.leakedMounts > 0) ArmLog.w(tag, "guest_mount_leak", res.leakedMounts)
        val st = status()
        return if (st.running) {
            ArmLog.e(tag, "guest_stop_failed", st.processes)
            armFail(ErrorCode.GUEST_STOP_FAILED, "Guest processes are still alive: ${st.processes}", st.processes.toString())
        } else {
            ArmLog.i(tag, "guest_stopped")
            ArmResult.Ok(Unit)
        }
    }

    override suspend fun restart(imageName: String): ArmResult<Unit> {
        val s = stop()
        if (s is ArmResult.Fail && s.code != ErrorCode.NOT_RUNNING) return s
        return start(imageName)
    }

    override suspend fun status(): ContainerStatus {
        if (!File(runDir, "status.sh").isFile) return ContainerStatus(running = false)
        val r = root.execute(PrivilegedOp.RunScript(script("status.sh").path, baseRoot), 15_000)
        if (!r.ok || !r.stdout.contains("uid=0")) return ContainerStatus(running = false, errorCode = ErrorCode.ROOT_LOST)
        return GuestScripts.parseStatus(r.stdout)
    }

    override suspend fun logs(): List<String> {
        val f = File(runDir, "guest.log")
        if (!f.exists()) return emptyList()
        val r = root.execute(PrivilegedOp.TailFile(f.path, baseRoot, 80), 10_000)
        return if (r.ok) r.stdout.lines().filter { it.isNotBlank() } else emptyList()
    }
}
