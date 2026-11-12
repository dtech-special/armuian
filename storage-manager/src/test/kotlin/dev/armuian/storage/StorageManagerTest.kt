package dev.armuian.storage

import dev.armuian.core.ArmResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun createsLayout() {
        val sm = StorageManager(File(tmp.root, "armuian"))
        assertTrue(sm.ensureLayout() is ArmResult.Ok)
        StorageManager.LAYOUT.forEach { assertTrue(sm.dir(it).isDirectory) }
    }

    @Test fun rejectsRelativeAndDotDot() {
        assertTrue(StorageManager(File("relative/path")).validateRoot() is ArmResult.Fail)
        assertTrue(StorageManager(File("/a/../b")).validateRoot() is ArmResult.Fail)
    }

    @Test fun clearCacheKeepsContainers() {
        val sm = StorageManager(File(tmp.root, "a")); sm.ensureLayout()
        File(sm.dir("cache"), "x.bin").writeBytes(ByteArray(100))
        File(sm.dir("containers/default"), "data.bin").writeBytes(ByteArray(50))
        val r = sm.clearCache()
        assertEquals(100L, (r as ArmResult.Ok).value)
        assertEquals(50L, sm.usedBytes("containers"))
    }

    @Test fun deleteImageOnlyThatImage() {
        val sm = StorageManager(File(tmp.root, "b")); sm.ensureLayout()
        File(sm.dir("images/redroid-16"), "l.tar").writeBytes(ByteArray(10))
        File(sm.dir("images/custom"), "c.tar").writeBytes(ByteArray(7))
        sm.deleteImage("redroid-16")
        assertEquals(0L, sm.usedBytes("images/redroid-16"))
        assertEquals(7L, sm.usedBytes("images/custom"))
        assertTrue(sm.deleteImage("../x") is ArmResult.Fail)
    }
}
