package dev.armuian.container

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/** Проверка распакованного образа без root: архитектура бинарников, linker, init, версия Android. */
object ImageInspector {
    private val BLOCKING = setOf(ImageIssue.NO_ROOTFS, ImageIssue.NO_TOYBOX, ImageIssue.TOYBOX_NOT_ARM64, ImageIssue.NO_LINKER)

    fun inspect(rootfs: File): ImageReport {
        if (!rootfs.isDirectory) return ImageReport(false, listOf(ImageIssue.NO_ROOTFS), null, null, false, null)
        val issues = mutableListOf<ImageIssue>()

        val toybox = File(rootfs, "system/bin/toybox")
        when {
            !toybox.isFile -> issues += ImageIssue.NO_TOYBOX
            !isArm64Elf(toybox) -> issues += ImageIssue.TOYBOX_NOT_ARM64
        }

        // linker64 в образе — симлинк на /apex/..., вне chroot он «битый», поэтому проверяем сам факт наличия записи.
        val linker = File(rootfs, "system/bin/linker64").toPath()
        if (!Files.exists(linker, LinkOption.NOFOLLOW_LINKS)) issues += ImageIssue.NO_LINKER

        val init = File(rootfs, "init").toPath()
        val sysInit = File(rootfs, "system/bin/init").toPath()
        val initPresent = Files.exists(init, LinkOption.NOFOLLOW_LINKS) || Files.exists(sysInit, LinkOption.NOFOLLOW_LINKS)
        if (!initPresent) issues += ImageIssue.NO_INIT

        val props = readProps(File(rootfs, "system/build.prop"))
        val apex = File(rootfs, "system/apex").listFiles()
        val flattened = when {
            apex == null -> null
            apex.any { it.extension == "apex" || it.extension == "capex" } -> false
            apex.any { it.isDirectory } -> true
            else -> null
        }
        return ImageReport(
            usable = issues.none { it in BLOCKING }, issues = issues,
            androidRelease = props["ro.build.version.release"], apiLevel = props["ro.build.version.sdk"]?.toIntOrNull(),
            initPresent = initPresent, apexFlattened = flattened,
        )
    }

    /** ELF64, little-endian, e_machine = AArch64 (183). */
    fun isArm64Elf(f: File): Boolean = runCatching {
        val h = ByteArray(20)
        f.inputStream().use { if (it.read(h) < 20) return false }
        h[0] == 0x7f.toByte() && h[1] == 'E'.code.toByte() && h[2] == 'L'.code.toByte() && h[3] == 'F'.code.toByte() &&
            h[4] == 2.toByte() && h[5] == 1.toByte() && (h[18].toInt() and 0xff) == 0xB7 && h[19].toInt() == 0
    }.getOrDefault(false)

    private fun readProps(f: File): Map<String, String> = runCatching {
        if (!f.isFile) emptyMap() else f.useLines { lines ->
            lines.mapNotNull { l -> l.split('=', limit = 2).takeIf { it.size == 2 && !l.startsWith("#") }?.let { it[0].trim() to it[1].trim() } }.toMap()
        }
    }.getOrDefault(emptyMap())
}
