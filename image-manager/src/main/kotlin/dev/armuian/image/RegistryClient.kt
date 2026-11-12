package dev.armuian.image

import dev.armuian.core.ArmException
import dev.armuian.core.ArmLog
import dev.armuian.core.ErrorCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException

@Serializable data class Platform(val architecture: String? = null, val os: String? = null, val variant: String? = null)
@Serializable data class Descriptor(val mediaType: String? = null, val digest: String, val size: Long, val platform: Platform? = null)
/** Один тип для image index и image manifest: заполнены либо `manifests`, либо `config`+`layers`. */
@Serializable data class ManifestDoc(
    val mediaType: String? = null,
    val manifests: List<Descriptor>? = null,
    val config: Descriptor? = null,
    val layers: List<Descriptor>? = null,
)
@Serializable private data class TokenResponse(val token: String? = null, @SerialName("access_token") val accessToken: String? = null)
@Serializable private data class TagList(val name: String? = null, val tags: List<String>? = null)

data class ResolvedImage(
    val repository: String,
    val manifestDigest: String,
    val manifestJson: String,
    val config: Descriptor,
    val layers: List<Descriptor>,
)

/** Клиент Docker Registry HTTP API v2 (Docker Hub и совместимые), анонимный pull с Bearer-токеном. */
class RegistryClient(
    private val http: OkHttpClient,
    baseUrl: String = "https://registry-1.docker.io",
    private val downloader: FileDownloader = FileDownloader(http),
) {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }
    private val tokens = HashMap<String, String>()

    private companion object {
        const val ACCEPT = "application/vnd.oci.image.index.v1+json, application/vnd.oci.image.manifest.v1+json, " +
            "application/vnd.docker.distribution.manifest.list.v2+json, application/vnd.docker.distribution.manifest.v2+json"
        val CHALLENGE_PARAM = Regex("""(\w+)="([^"]*)"""")
    }

    private fun obtainToken(challenge: String, repo: String): String? {
        val params = CHALLENGE_PARAM.findAll(challenge).associate { it.groupValues[1] to it.groupValues[2] }
        val realm = params["realm"] ?: return null
        val url = realm.toHttpUrl().newBuilder().apply {
            params["service"]?.let { addQueryParameter("service", it) }
            addQueryParameter("scope", "repository:$repo:pull")
        }.build()
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw HttpStatusException(r.code, url.toString())
            val t = json.decodeFromString<TokenResponse>(r.body?.string() ?: throw CodedIOException(ErrorCode.EMPTY_RESPONSE, "Empty token response"))
            return t.token ?: t.accessToken
        }
    }

    /** Блокирующий GET с авторизацией: при 401 получает токен и повторяет запрос один раз. */
    private fun get(url: String, repo: String, accept: String? = null): Response {
        fun build(): Request {
            val b = Request.Builder().url(url)
            accept?.let { b.header("Accept", it) }
            synchronized(tokens) { tokens[repo] }?.let { b.header("Authorization", "Bearer $it") }
            return b.build()
        }
        var resp = http.newCall(build()).execute()
        if (resp.code == 401) {
            val challenge = resp.header("WWW-Authenticate")
            resp.close()
            if (challenge == null || !challenge.startsWith("Bearer", ignoreCase = true)) throw HttpStatusException(401, url)
            val token = obtainToken(challenge, repo) ?: throw CodedIOException(ErrorCode.REGISTRY_TOKEN, "Failed to get registry token")
            synchronized(tokens) { tokens[repo] = token }
            resp = http.newCall(build()).execute()
        }
        return resp
    }

    private class Raw(val bytes: ByteArray, val digest: String)

    private fun fetchRaw(repo: String, reference: String): Raw {
        val url = "$base/v2/$repo/manifests/$reference"
        get(url, repo, ACCEPT).use { r ->
            if (!r.isSuccessful) throw HttpStatusException(r.code, url)
            val bytes = r.body?.bytes() ?: throw CodedIOException(ErrorCode.EMPTY_RESPONSE, "Empty manifest")
            val actual = "sha256:" + Sha256.hexOf(bytes)
            if (reference.startsWith("sha256:") && actual != reference) throw ChecksumMismatchException(reference, actual)
            val header = r.header("Docker-Content-Digest")
            if (header != null && header != actual) throw ChecksumMismatchException(header, actual)
            return Raw(bytes, actual)
        }
    }

    /** Получает манифест и, если это index, выбирает вариант linux/[arch]. */
    suspend fun resolve(repo: String, reference: String, arch: String = "arm64"): ResolvedImage = withContext(Dispatchers.IO) {
        var raw = fetchRaw(repo, reference)
        var doc = json.decodeFromString<ManifestDoc>(raw.bytes.decodeToString())
        val index = doc.manifests
        if (index != null) {
            val pick = index.firstOrNull { it.platform?.os == "linux" && it.platform?.architecture == arch }
                ?: throw UnsupportedImageException(ErrorCode.NO_ARCH_VARIANT, "Image has no linux/$arch variant", listOf(arch))
            ArmLog.i("Registry", "arch_selected", arch, pick.digest)
            raw = fetchRaw(repo, pick.digest)
            doc = json.decodeFromString<ManifestDoc>(raw.bytes.decodeToString())
        }
        val config = doc.config ?: throw UnsupportedImageException(ErrorCode.MANIFEST_NO_CONFIG, "Manifest has no config (legacy format?)")
        val layers = doc.layers ?: throw UnsupportedImageException(ErrorCode.MANIFEST_NO_LAYERS, "Manifest has no layers")
        ResolvedImage(repo, raw.digest, raw.bytes.decodeToString(), config, layers)
    }

    /** Первая страница списка тегов (Docker Hub отдаёт остальное через Link — пока не используется). */
    suspend fun listTags(repo: String): List<String> = withContext(Dispatchers.IO) {
        val url = "$base/v2/$repo/tags/list"
        get(url, repo).use { r ->
            if (!r.isSuccessful) throw HttpStatusException(r.code, url)
            json.decodeFromString<TagList>(r.body?.string() ?: "{}").tags ?: emptyList()
        }
    }

    private fun authHeaders(repo: String): Map<String, String> =
        synchronized(tokens) { tokens[repo] }?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()

    /** Скачивает blob с проверкой digest. Если токен истёк (401) — обновляет его и пробует ещё раз. */
    suspend fun downloadBlob(
        repo: String, desc: Descriptor, dest: File,
        stage: Stage = Stage.DOWNLOADING, detail: String? = null, onProgress: ProgressListener = {},
    ) {
        if (!desc.digest.startsWith("sha256:")) throw ArmException(ErrorCode.ONLY_SHA256, "Only sha256 is supported: ${desc.digest}", listOf(desc.digest))
        val expected = desc.digest.removePrefix("sha256:")
        val url = "$base/v2/$repo/blobs/${desc.digest}"
        for (attempt in 0..1) {
            try {
                downloader.download(url, dest, authHeaders(repo), expected, stage, detail, onProgress)
                return
            } catch (e: HttpStatusException) {
                if (e.status != 401 || attempt == 1) throw e
                synchronized(tokens) { tokens.remove(repo) }
                withContext(Dispatchers.IO) { get("$base/v2/$repo/tags/list?n=1", repo).close() }
            }
        }
    }
}
