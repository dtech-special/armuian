package dev.armuian.root

/** Узлы ядра/устройств, которые разрешено читать с root-правами. Ничего другого читать нельзя. */
enum class KernelNode(val path: String) {
    SELINUX_ENFORCE("/sys/fs/selinux/enforce"),
    PROC_FILESYSTEMS("/proc/filesystems"),
    PROC_CGROUPS("/proc/cgroups"),
    PROC_INIT_NS("/proc/1/ns"),
    DEV_BINDER("/dev/binder"),
    DEV_BINDERFS("/dev/binderfs"),
    DEV_KVM("/dev/kvm"),
    APEX("/apex"),
    LINKERCONFIG("/linkerconfig"),
    CROSVM("/apex/com.android.virt/bin/crosvm"),
}

/** Закрытый список привилегированных операций. Произвольные команды выполнить нельзя. */
sealed class PrivilegedOp {
    abstract fun command(): String

    data object Id : PrivilegedOp() { override fun command() = "id" }

    data class MkdirP(val path: String) : PrivilegedOp() {
        init { PathGuard.require(path, minDepth = 0) }
        override fun command() = "mkdir -p $path"
    }

    data class DiskUsage(val path: String) : PrivilegedOp() {
        init { PathGuard.require(path, minDepth = 0) }
        override fun command() = "du -sk $path"
    }

    /** Удаление поддерева. Только на глубине ≥ 2 (например images/redroid-16); корень и верхние папки не удалить. */
    data class RemoveTree(val path: String) : PrivilegedOp() {
        init { PathGuard.require(path, minDepth = 2) }
        override fun command() = "rm -rf $path"
    }

    /** Проверка наличия утилит, нужных для запуска гостевого userspace. Команда постоянная, без параметров. */
    data object ProbeTools : PrivilegedOp() {
        override fun command() = "for t in unshare chroot mount readlink ps; do command -v \$t >/dev/null 2>&1 && echo \$t:ok || echo \$t:missing; done"
    }

    /** Запуск скрипта, сгенерированного ArmUian внутри каталога [root] (путь проверяется, имя *.sh). */
    data class RunScript(val path: String, val root: String) : PrivilegedOp() {
        init { PathGuard.require(path, root, minDepth = 1); require(path.endsWith(".sh")) { "Not a .sh script: $path" } }
        override fun command() = "sh $path"
    }

    /** То же, но в фоне и отвязанно от su (для долгоживущего гостевого процесса). */
    data class StartScript(val path: String, val root: String) : PrivilegedOp() {
        init { PathGuard.require(path, root, minDepth = 1); require(path.endsWith(".sh")) { "Not a .sh script: $path" } }
        override fun command() = "nohup sh $path >/dev/null 2>&1 &"
    }

    /** Последние строки файла журнала внутри [root]. */
    data class TailFile(val path: String, val root: String, val lines: Int = 60) : PrivilegedOp() {
        init { PathGuard.require(path, root, minDepth = 1); require(lines in 1..500) }
        override fun command() = "tail -n $lines $path"
    }

    data class CatNode(val node: KernelNode) : PrivilegedOp() { override fun command() = "cat ${node.path}" }
    data class ListDirNode(val node: KernelNode) : PrivilegedOp() { override fun command() = "ls ${node.path}" }
    data class ListNode(val node: KernelNode) : PrivilegedOp() { override fun command() = "ls -ld ${node.path}" }
}
