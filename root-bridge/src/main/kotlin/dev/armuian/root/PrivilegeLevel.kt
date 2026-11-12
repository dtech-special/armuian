package dev.armuian.root

/** Фактический уровень привилегий. Определяется проверкой `su -c id`, а не наличием приложения-менеджера. */
enum class PrivilegeLevel { NON_ROOT, TEMPORARY_ROOT, ROOT }

/**
 * null — root ещё не проверялся. Если `su` реально работает, но известного менеджера (KernelSU/Magisk/…) нет,
 * root считается временным (его могут отозвать); иначе — постоянным. Это оценка, не гарантия.
 */
fun RootStatus?.privilegeLevel(): PrivilegeLevel? = when (this) {
    null -> null
    is RootStatus.Granted -> if (manager == RootManager.NOT_DETECTED) PrivilegeLevel.TEMPORARY_ROOT else PrivilegeLevel.ROOT
    else -> PrivilegeLevel.NON_ROOT
}
