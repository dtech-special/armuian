package dev.armuian.detection

/**
 * Снимок параметров хоста. null = «Недоступно» (значение не удалось получить,
 * мы его не придумываем).
 */
data class HostInfo(
    val deviceModel: String,
    val soc: String?,
    val androidRelease: String,
    val apiLevel: Int,
    val primaryAbi: String,
    val supportedAbis: List<String>,
    val kernelVersion: String?,
    val cpuCores: Int,
    val totalRamBytes: Long?,
    val freeStorageBytes: Long?,
    val selinux: SelinuxMode?,
    /** true только если узел реально виден приложению; SELinux может скрывать его от не-root. */
    val binderDeviceVisible: Boolean,
    val binderFsVisible: Boolean,
    val kvmVisible: Boolean,
    val namespaces: List<String>?,
    /** Системная функция Android Virtualization Framework (android.software.virtualization_framework). */
    val avfFeature: Boolean = false,
) {
    val isArm64: Boolean get() = primaryAbi == "arm64-v8a"
    val androidMajor: Int? get() = AndroidVersionMatcher.majorOf(androidRelease)
}

enum class SelinuxMode { ENFORCING, PERMISSIVE }
