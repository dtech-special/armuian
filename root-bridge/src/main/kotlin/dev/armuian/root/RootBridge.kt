package dev.armuian.root

import dev.armuian.core.ArmLog

/**
 * Единственная точка, через которую ArmUian обращается к root основной системы
 * (KernelSU, уже установленный пользователем). Собственного root-менеджера нет.
 */
class RootBridge(
    private val shell: ShellRunner = ProcessShellRunner(),
    private val managers: ManagerDetector = ManagerDetector { false },
) {
    private companion object {
        const val TAG = "RootBridge"
        val UID = Regex("""uid=(\d+)""")
        const val PROMPT_TIMEOUT_MS = 30_000L // даём время ответить на запрос KernelSU
    }

    /** Запрашивает доступ у менеджера root и проверяет, что UID действительно 0. Вызывайте при каждом запуске. */
    suspend fun detect(): RootStatus {
        ArmLog.i(TAG, "root_check_start")
        val r = shell.run(listOf("su", "-c", "id"), PROMPT_TIMEOUT_MS)
        val status = when {
            r.timedOut -> RootStatus.Denied("No response from the root manager (timeout)")
            r.exitCode == 127 -> RootStatus.SuNotFound
            r.ok -> {
                val uid = UID.find(r.stdout)?.groupValues?.get(1)?.toIntOrNull()
                when (uid) {
                    0 -> RootStatus.Granted(0, managers.detect())
                    null -> RootStatus.Failed("Cannot parse id output: ${r.stdout.trim()}")
                    else -> RootStatus.Failed("su returned uid=$uid instead of 0")
                }
            }
            else -> RootStatus.Denied(r.stderr.trim().ifBlank { "su exited with code ${r.exitCode}" })
        }
        when (status) {
            is RootStatus.Granted -> ArmLog.i(TAG, "root_granted", status.manager)
            RootStatus.SuNotFound -> ArmLog.w(TAG, "su_not_found")
            is RootStatus.Denied -> ArmLog.w(TAG, "root_denied_log", status.details)
            is RootStatus.Failed -> ArmLog.e(TAG, "root_failed_log", status.message)
        }
        return status
    }

    /** Выполняет одну из заранее определённых операций. */
    suspend fun execute(op: PrivilegedOp, timeoutMs: Long = 60_000): ShellResult {
        val cmd = op.command()
        ArmLog.d(TAG, "su_command", cmd)
        return shell.run(listOf("su", "-c", cmd), timeoutMs)
    }
}
