package dev.armuian.image

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NetworkTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private val http = OkHttpClient()

    @Before fun up() { server = MockWebServer() }
    @After fun down() { server.shutdown() }

    private fun base() = server.url("/").toString().trimEnd('/')

    @Test fun downloaderChecksAndResumes() = runBlocking {
        val data = ByteArray(10_000) { (it % 251).toByte() }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range")
                return if (range != null) {
                    val from = range.removePrefix("bytes=").removeSuffix("-").toInt()
                    MockResponse().setResponseCode(206).setBody(okio.Buffer().write(data.copyOfRange(from, data.size)))
                } else MockResponse().setBody(okio.Buffer().write(data))
            }
        }
        val dest = File(tmp.root, "blob")
        File(tmp.root, "blob.part").writeBytes(data.copyOfRange(0, 4000)) // «прерванная» загрузка
        FileDownloader(http).download(server.url("/x").toString(), dest, expectedSha256 = Sha256.hexOf(data))
        assertTrue(data.contentEquals(dest.readBytes()))
    }

    @Test fun downloaderRejectsWrongChecksum() {
        server.enqueue(MockResponse().setBody("hello"))
        val dest = File(tmp.root, "bad")
        assertThrows(ChecksumMismatchException::class.java) {
            runBlocking { FileDownloader(http).download(server.url("/x").toString(), dest, expectedSha256 = "0".repeat(64)) }
        }
        assertTrue(!dest.exists())
    }

    @Test fun registryResolvesArm64WithBearerToken() = runBlocking {
        val layerBytes = "layer".toByteArray()
        val configBytes = "{}".toByteArray()
        fun d(b: ByteArray) = "sha256:" + Sha256.hexOf(b)
        val manifest = """{"mediaType":"application/vnd.oci.image.manifest.v1+json",
            "config":{"digest":"${d(configBytes)}","size":${configBytes.size}},
            "layers":[{"digest":"${d(layerBytes)}","size":${layerBytes.size}}]}""".toByteArray()
        val index = """{"manifests":[
            {"digest":"sha256:${"1".repeat(64)}","size":1,"platform":{"os":"linux","architecture":"amd64"}},
            {"digest":"${d(manifest)}","size":${manifest.size},"platform":{"os":"linux","architecture":"arm64"}}]}""".toByteArray()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                if (path.startsWith("/token")) return MockResponse().setBody("""{"token":"abc"}""")
                if (request.getHeader("Authorization") != "Bearer abc")
                    return MockResponse().setResponseCode(401)
                        .setHeader("WWW-Authenticate", """Bearer realm="${base()}/token",service="test"""")
                return when {
                    path.endsWith("/manifests/tag") -> MockResponse().setBody(okio.Buffer().write(index))
                    path.endsWith("/manifests/${d(manifest)}") -> MockResponse().setBody(okio.Buffer().write(manifest))
                    path.endsWith("/blobs/${d(layerBytes)}") -> MockResponse().setBody(okio.Buffer().write(layerBytes))
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val client = RegistryClient(http, base())
        val img = client.resolve("redroid/redroid", "tag")
        assertEquals(d(manifest), img.manifestDigest)
        assertEquals(1, img.layers.size)
        val out = File(tmp.root, "layer")
        client.downloadBlob("redroid/redroid", img.layers[0], out)
        assertEquals("layer", out.readText())
    }

    @Test fun registryFailsWithoutArm64() {
        val idx = """{"manifests":[{"digest":"sha256:${"1".repeat(64)}","size":1,"platform":{"os":"linux","architecture":"amd64"}}]}"""
        server.enqueue(MockResponse().setBody(idx))
        assertThrows(UnsupportedImageException::class.java) { runBlocking { RegistryClient(http, base()).resolve("a/b", "t") } }
    }

    @Test fun githubRateLimitAndDigest() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setHeader("X-RateLimit-Remaining", "0").setHeader("X-RateLimit-Reset", "1234"))
        val gh = GitHubClient(http, base())
        val e = runCatching { gh.releases("o", "r") }.exceptionOrNull()
        assertTrue(e is RateLimitException && e.resetEpochSeconds == 1234L)

        server.enqueue(MockResponse().setBody("""[{"tag_name":"v1","assets":[{"name":"a.zip","size":3,"browser_download_url":"http://x/a.zip","digest":"sha256:${"c".repeat(64)}"}]}]"""))
        val rel = gh.releases("o", "r").single()
        assertEquals("c".repeat(64), gh.expectedSha256(rel, rel.assets[0]))
    }
}
