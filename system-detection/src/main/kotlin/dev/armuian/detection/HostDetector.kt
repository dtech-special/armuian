package dev.armuian.detection

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import dev.armuian.core.ArmLog
import java.io.File

/** Определяет параметры хоста без root. Недоступное -> null. */
class HostDetector(private val context: Context) {

    fun detect(): HostInfo {
        val info = HostInfo(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN } else null,
            androidRelease = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            kernelVersion = System.getProperty("os.version")?.takeIf { it.isNotBlank() },
            cpuCores = Runtime.getRuntime().availableProcessors(),
            totalRamBytes = totalRam(),
            freeStorageBytes = freeStorage(),
            selinux = selinux(),
            binderDeviceVisible = File("/dev/binder").exists(),
            binderFsVisible = File("/dev/binderfs").exists(),
            kvmVisible = File("/dev/kvm").exists(),
            namespaces = runCatching { File("/proc/self/ns").list()?.sorted() }.getOrNull(),
            avfFeature = runCatching { context.packageManager.hasSystemFeature("android.software.virtualization_framework") }.getOrDefault(false),
        )
        ArmLog.i("HostDetector", "host_detected", info.androidRelease, info.apiLevel, info.primaryAbi, info.kernelVersion ?: "n/a")
        return info
    }

    private fun totalRam(): Long? = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem
    }.getOrNull()

    private fun freeStorage(): Long? = runCatching {
        StatFs(Environment.getDataDirectory().path).availableBytes
    }.getOrNull()

    private fun selinux(): SelinuxMode? = runCatching {
        when (File("/sys/fs/selinux/enforce").readText().trim()) {
            "1" -> SelinuxMode.ENFORCING
            "0" -> SelinuxMode.PERMISSIVE
            else -> null
        }
    }.getOrNull()
}
