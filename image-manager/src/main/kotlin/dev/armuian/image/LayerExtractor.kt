package dev.armuian.image

import dev.armuian.core.ArmLog
import dev.armuian.core.ErrorCode
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.GZIPInputStream

data class ExtractStats(var files: Int = 0, var dirs: Int = 0, var symlinks: Int = 0, var whiteouts: Int = 0, var skipped: Int = 0)

/**
 * Распаковка слоя OCI (tar / tar+gzip) поверх rootfs с правилами слоёв:
 * `.wh.<имя>` удаляет файл нижнего слоя, `.wh..wh..opq` очищает каталог нижних слоёв.
 * Защита от path traversal. Владельцы (uid/gid) и xattr/SELinux-метки без root не применяются —
 * для рабочего контейнера финальную распаковку нужно выполнять через Root Bridge.
 */
object LayerExtractor {
    private const val TAG = "LayerExtractor"
    private const val WH = ".wh."
    private const val OPAQUE = ".wh..wh..opq"

    fun extract(layer: File, rootfs: File, checkCancelled: () -> Unit = {}): ExtractStats {
        rootfs.mkdirs()
        val root = rootfs.canonicalFile
        val stats = ExtractStats()
        val created = HashSet<String>()
        openTar(layer).use { tar ->
            while (true) {
                checkCancelled()
                val entry = tar.nextEntry as TarArchiveEntry? ?: break
                handle(tar, entry, root, created, stats)
            }
        }
        ArmLog.i(TAG, "layer_done", layer.name.take(12), stats.files, stats.dirs, stats.symlinks, stats.whiteouts, stats.skipped)
        return stats
    }

    private fun openTar(layer: File): TarArchiveInputStream {
        val raw = BufferedInputStream(layer.inputStream(), 128 * 1024)
        raw.mark(4)
        val b0 = raw.read(); val b1 = raw.read(); val b2 = raw.read(); val b3 = raw.read()
        raw.reset()
        val stream: InputStream = when {
            b0 == 0x1f && b1 == 0x8b -> GZIPInputStream(raw, 64 * 1024)
            b0 == 0x28 && b1 == 0xb5 && b2 == 0x2f && b3 == 0xfd -> {
                raw.close(); throw UnsupportedImageException(ErrorCode.ZSTD_UNSUPPORTED, "zstd layers are not supported yet")
            }
            else -> raw
        }
        return TarArchiveInputStream(stream)
    }

    private fun normalize(name: String): String {
        val parts = name.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." }) throw CodedIOException(ErrorCode.UNSAFE_ARCHIVE_PATH, "Unsafe path in archive: $name", listOf(name))
        return parts.joinToString("/")
    }

    private fun handle(tar: TarArchiveInputStream, e: TarArchiveEntry, root: File, created: HashSet<String>, st: ExtractStats) {
        val rel = normalize(e.name)
        if (rel.isEmpty()) return
        val name = rel.substringAfterLast('/')
        val parentRel = rel.substringBeforeLast('/', "")
        val parent = if (parentRel.isEmpty()) root else File(root, parentRel)

        if (name == OPAQUE) {
            if (inside(root, parent)) parent.listFiles()?.forEach { c ->
                if (root.toPath().relativize(c.toPath()).toString() !in created) deleteNoFollow(c)
            }
            st.whiteouts++
            return
        }
        if (name.startsWith(WH)) {
            val victim = File(parent, name.removePrefix(WH))
            if (inside(root, parent)) deleteNoFollow(victim)
            st.whiteouts++
            return
        }

        val dest = File(root, rel)
        if (!inside(root, dest.parentFile)) {
            ArmLog.w(TAG, "skip_symlink", rel); st.skipped++; return
        }
        when {
            e.isDirectory -> {
                if (dest.exists() && !Files.isDirectory(dest.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) deleteNoFollow(dest)
                dest.mkdirs(); st.dirs++
            }
            e.isSymbolicLink -> {
                dest.parentFile?.mkdirs(); deleteNoFollow(dest)
                Files.createSymbolicLink(dest.toPath(), Paths.get(e.linkName)); st.symlinks++
            }
            e.isLink -> {
                val target = File(root, normalize(e.linkName))
                dest.parentFile?.mkdirs(); deleteNoFollow(dest)
                if (inside(root, target.parentFile) && target.exists()) { Files.createLink(dest.toPath(), target.toPath()); st.files++ }
                else { ArmLog.w(TAG, "skip_hardlink", rel, e.linkName); st.skipped++ }
            }
            e.isFile -> {
                dest.parentFile?.mkdirs(); deleteNoFollow(dest)
                dest.outputStream().use { out -> tar.copyTo(out, 64 * 1024) }
                dest.setReadable(true, false)
                if (e.mode and 0b001_001_001 != 0) dest.setExecutable(true, false)
                st.files++
            }
            else -> { ArmLog.w(TAG, "skip_special", rel); st.skipped++ }
        }
        created.add(rel)
    }

    private fun inside(root: File, f: File?): Boolean {
        if (f == null) return false
        val c = f.canonicalFile.path
        return c == root.path || c.startsWith(root.path + File.separator)
    }

    /** Удаляет файл/каталог, не переходя по симлинкам. */
    fun deleteNoFollow(f: File) {
        val p = f.toPath()
        if (!Files.exists(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
        if (Files.isDirectory(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) f.listFiles()?.forEach { deleteNoFollow(it) }
        Files.delete(p)
    }
}
