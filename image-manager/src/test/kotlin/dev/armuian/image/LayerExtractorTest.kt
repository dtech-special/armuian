package dev.armuian.image

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.zip.GZIPOutputStream

class LayerExtractorTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun layer(name: String, gz: Boolean = true, block: TarArchiveOutputStream.() -> Unit): File {
        val f = File(tmp.root, name)
        val raw = f.outputStream().let { if (gz) GZIPOutputStream(it) else it }
        TarArchiveOutputStream(raw).use { it.block() }
        return f
    }

    private fun TarArchiveOutputStream.file(path: String, text: String) {
        val b = text.toByteArray()
        putArchiveEntry(TarArchiveEntry(path).apply { size = b.size.toLong(); mode = 420 })
        write(b); closeArchiveEntry()
    }

    @Test fun extractsFilesAndAppliesWhiteout() {
        val root = File(tmp.root, "rootfs")
        LayerExtractor.extract(layer("l1.tar.gz") { file("etc/a.conf", "A"); file("etc/b.conf", "B"); file("keep.txt", "K") }, root)
        LayerExtractor.extract(layer("l2.tar.gz", gz = false) { file("etc/.wh.a.conf", "") }, root)
        assertFalse(File(root, "etc/a.conf").exists())
        assertEquals("B", File(root, "etc/b.conf").readText())
        assertEquals("K", File(root, "keep.txt").readText())
    }

    @Test fun opaqueWhiteoutClearsLowerLayerDirectory() {
        val root = File(tmp.root, "rootfs")
        LayerExtractor.extract(layer("l1.tar.gz") { file("d/old.txt", "old") }, root)
        LayerExtractor.extract(layer("l2.tar.gz") { file("d/.wh..wh..opq", ""); file("d/new.txt", "new") }, root)
        assertFalse(File(root, "d/old.txt").exists())
        assertEquals("new", File(root, "d/new.txt").readText())
    }

    @Test fun upperLayerOverwrites() {
        val root = File(tmp.root, "rootfs")
        LayerExtractor.extract(layer("l1.tar.gz") { file("f", "1") }, root)
        LayerExtractor.extract(layer("l2.tar.gz") { file("f", "2") }, root)
        assertEquals("2", File(root, "f").readText())
    }

    @Test fun rejectsPathTraversal() {
        val root = File(tmp.root, "rootfs")
        assertThrows(IOException::class.java) { LayerExtractor.extract(layer("bad.tar.gz") { file("../evil.txt", "x") }, root) }
        assertFalse(File(tmp.root, "evil.txt").exists())
    }

    @Test fun symlinkEscapeIsSkipped() {
        val root = File(tmp.root, "rootfs")
        val l = layer("s.tar.gz") {
            putArchiveEntry(TarArchiveEntry("link", TarConstants.LF_SYMLINK).apply { linkName = tmp.root.path })
            closeArchiveEntry()
            file("link/pwned.txt", "x")
        }
        val st = LayerExtractor.extract(l, root)
        assertTrue(st.skipped >= 1)
        assertFalse(File(tmp.root, "pwned.txt").exists())
    }
}
