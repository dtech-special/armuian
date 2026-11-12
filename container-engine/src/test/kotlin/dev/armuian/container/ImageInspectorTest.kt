package dev.armuian.container

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class ImageInspectorTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun elf(machine: Int, cls: Int = 2): ByteArray {
        val b = ByteArray(64)
        b[0] = 0x7f; b[1] = 'E'.code.toByte(); b[2] = 'L'.code.toByte(); b[3] = 'F'.code.toByte()
        b[4] = cls.toByte(); b[5] = 1
        b[18] = (machine and 0xff).toByte(); b[19] = (machine shr 8).toByte()
        return b
    }

    private fun rootfs(withToybox: ByteArray?, linker: Boolean = true, init: Boolean = true): File {
        val r = File(tmp.root, "rootfs"); File(r, "system/bin").mkdirs()
        withToybox?.let { File(r, "system/bin/toybox").writeBytes(it) }
        if (linker) Files.createSymbolicLink(File(r, "system/bin/linker64").toPath(), java.nio.file.Paths.get("/apex/com.android.runtime/bin/linker64"))
        if (init) File(r, "init").writeBytes(ByteArray(4))
        File(r, "system/build.prop").writeText("# c\nro.build.version.release=16\nro.build.version.sdk=36\n")
        return r
    }

    @Test fun validArm64Image() {
        val rep = ImageInspector.inspect(rootfs(elf(0xB7)))
        assertTrue(rep.usable)
        assertEquals("16", rep.androidRelease); assertEquals(36, rep.apiLevel)
    }

    @Test fun rejectsNonArmBinary() {
        val rep = ImageInspector.inspect(rootfs(elf(0x3E))) // x86-64
        assertFalse(rep.usable); assertTrue(ImageIssue.TOYBOX_NOT_ARM64 in rep.issues)
    }

    @Test fun missingPartsAreReported() {
        val rep = ImageInspector.inspect(rootfs(null, linker = false, init = false))
        assertTrue(ImageIssue.NO_TOYBOX in rep.issues && ImageIssue.NO_LINKER in rep.issues && ImageIssue.NO_INIT in rep.issues)
        assertFalse(rep.usable)
    }

    @Test fun missingInitAloneDoesNotBlock() {
        assertTrue(ImageInspector.inspect(rootfs(elf(0xB7), init = false)).usable)
    }

    @Test fun noRootfs() {
        val rep = ImageInspector.inspect(File(tmp.root, "none"))
        assertEquals(listOf(ImageIssue.NO_ROOTFS), rep.issues)
    }
}
