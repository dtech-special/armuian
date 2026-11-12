package dev.armuian.image

import dev.armuian.core.ArmLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

@Serializable
data class GhAsset(
    val name: String,
    val size: Long,
    @SerialName("browser_download_url") val url: String,
    /** GitHub публикует digest вида "sha256:..." для новых assets. */
    val digest: String? = null,
    @SerialName("content_type") val contentType: String? = null,
)

@Serializable
data class GhRelease(
    @SerialName("tag_name") val tag: String,
    val name: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GhAsset> = emptyList(),
)

object ChecksumParser {
    private val LINE = Regex("""^([0-9a-fA-F]{64})\s+\*?(.+)$""")
    private val BARE = Regex("""^[0-9a-fA-F]{64}$""")

    fun isChecksumFile(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".sha256") || n.endsWith(".sha256sum") || n.contains("sha256sums") || n.contains("checksums")
    }

    /** Ищет SHA256 для [fileName] в тексте формата `sha256sum` либо файла с единственным хешем. */
    fun find(text: String, fileName: String): String? {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        for (l in lines) {
            val m = LINE.matchEntire(l) ?: continue
            if (m.groupValues[2].trim().substringAfterLast('/') == fileName) return m.groupValues[1].lowercase()
        }
        if (lines.size == 1 && BARE.matches(lines[0])) return lines[0].lowercase()
        return null
    }
}

/** GitHub Releases API с учётом rate limit. Токен необязателен (даёт больший лимит). */
class GitHubClient(
    private val http: OkHttpClient,
    private val apiBase: String = "https://api.github.com",
    private val token: String? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun releases(owner: String, repo: String, perPage: Int = 30): List<GhRelease> = withContext(Dispatchers.IO) {
        val url = "${apiBase.trimEnd('/')}/repos/$owner/$repo/releases?per_page=$perPage"
        val rb = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
        token?.let { rb.header("Authorization", "Bearer $it") }
        http.newCall(rb.build()).execute().use { r ->
            if (r.code == 403 || r.code == 429) {
                val exhausted = r.header("X-RateLimit-Remaining") == "0" || r.code == 429 || r.header("Retry-After") != null
                if (exhausted) {
                    val reset = r.header("X-RateLimit-Reset")?.toLongOrNull()
                    ArmLog.w("GitHub", "rate_limit", reset ?: "")
                    throw RateLimitException(reset)
                }
            }
            if (!r.isSuccessful) throw HttpStatusException(r.code, url)
            json.decodeFromString<List<GhRelease>>(r.body?.string() ?: "[]").filter { !it.draft }
        }
    }

    /** Ожидаемый SHA256: сначала digest самого asset, затем файлы контрольных сумм релиза. null — если не опубликован. */
    suspend fun expectedSha256(release: GhRelease, asset: GhAsset): String? {
        asset.digest?.takeIf { it.startsWith("sha256:") }?.let { return it.removePrefix("sha256:").lowercase() }
        for (c in release.assets) {
            if (c == asset || !ChecksumParser.isChecksumFile(c.name) || c.size > 1_000_000) continue
            val text = fetchText(c.url) ?: continue
            ChecksumParser.find(text, asset.name)?.let { return it }
        }
        return null
    }

    private suspend fun fetchText(url: String): String? = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
        } catch (_: IOException) { null }
    }
}
