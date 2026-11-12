package dev.armuian.image

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageRefTest {
    @Test fun dockerHubWithTag() {
        val r = ImageRef.parse("redroid/redroid:16.0.0_64only-latest")
        assertEquals(ImageRef("docker.io", "redroid/redroid", "16.0.0_64only-latest"), r)
        assertEquals("https://registry-1.docker.io", r.registryBaseUrl)
    }
    @Test fun officialImageGetsLibrary() = assertEquals("library/alpine", ImageRef.parse("alpine").repository)
    @Test fun customRegistryAndDigest() {
        val r = ImageRef.parse("ghcr.io/foo/bar@sha256:abc")
        assertEquals(ImageRef("ghcr.io", "foo/bar", "sha256:abc"), r)
    }
    @Test fun registryWithPort() = assertEquals("localhost:5000", ImageRef.parse("localhost:5000/x/y:1").registry)
}
