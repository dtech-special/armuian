package dev.armuian.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LayerCacheTest {
    @get:Rule val tmp = TemporaryFolder()
    private val shared = "a".repeat(64)
    private val onlyA = "b".repeat(64)
    private val onlyB = "c".repeat(64)

    private fun install(base: File, name: String, layers: List<String>) {
        val meta = File(base, "metadata/$name").apply { mkdirs() }
        val list = layers.joinToString(",") { "\"sha256:$it\"" }
        File(meta, "image.json").writeText(
            """{"sourceRef":"x","manifestDigest":"sha256:m","configDigest":"sha256:c","layerDigests":[$list],"totalLayerBytes":1,"pulledAtMs":1}"""
        )
        layers.forEach { File(base, "cache/blobs/$it").apply { parentFile.mkdirs() }.writeBytes(ByteArray(10)) }
    }

    @Test fun keepsSharedLayersAndDeletesOwnOnes() {
        val base = tmp.newFolder("armuian")
        install(base, "imgA", listOf(shared, onlyA))
        install(base, "imgB", listOf(shared, onlyB))
        File(base, "cache/blobs/$onlyA.part").writeBytes(ByteArray(5))

        val freed = LayerCache.releaseLayers(base, "imgA", listOf("sha256:$shared", "sha256:$onlyA"))

        assertEquals(15L, freed) // onlyA (10) + onlyA.part (5)
        assertTrue(File(base, "cache/blobs/$shared").exists())
        assertFalse(File(base, "cache/blobs/$onlyA").exists())
        assertTrue(File(base, "cache/blobs/$onlyB").exists())
    }

    @Test fun ignoresMalformedDigestsFromTamperedMetadata() {
        val base = tmp.newFolder("armuian2")
        val victim = File(base, "cache/blobs/keep.txt").apply { parentFile.mkdirs(); writeText("x") }
        val outside = File(base, "outside.txt").apply { writeText("x") }
        LayerCache.releaseLayers(base, "img", listOf("sha256:../../outside.txt", "sha256:keep.txt", "../outside.txt"))
        assertTrue(victim.exists()); assertTrue(outside.exists())
    }
}
