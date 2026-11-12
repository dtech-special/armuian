package dev.armuian.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidVersionMatcherTest {
    private val catalog = listOf(
        CatalogImage("redroid-16-64only", 16, "arm64-v8a", 1_200_000_000),
        CatalogImage("redroid-15", 15, "arm64-v8a", 1_100_000_000),
        CatalogImage("redroid-14", 14, "arm64-v8a", 1_000_000_000),
        CatalogImage("redroid-15-x86", 15, "x86_64", 900_000_000),
    )

    @Test fun parsesMajor() {
        assertEquals(16, AndroidVersionMatcher.majorOf("16"))
        assertEquals(14, AndroidVersionMatcher.majorOf("14.0.1"))
        assertEquals(null, AndroidVersionMatcher.majorOf("abc"))
    }

    @Test fun exactMatchNotNewest() {
        val m = AndroidVersionMatcher.select(14, catalog)
        assertTrue(m is Match.Exact && m.image.androidMajor == 14)
    }

    @Test fun ignoresNonArm() {
        val m = AndroidVersionMatcher.select(15, catalog)
        assertTrue(m is Match.Exact && m.image.abi == "arm64-v8a")
    }

    @Test fun noExactGivesNearestOnly() {
        val m = AndroidVersionMatcher.select(13, catalog)
        assertTrue(m is Match.Nearest && m.candidates.first().androidMajor == 14)
    }
}
