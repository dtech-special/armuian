package dev.armuian.image

import dev.armuian.core.ArmException
import dev.armuian.core.ArmLog
import dev.armuian.core.ErrorCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException

data class PullTarget(val blobsDir: File, val rootfsDir: File, val metadataDir: File)

@Serializable
data class ImageMetadata(
    val sourceRef: String,
    val manifestDigest: String,
    val configDigest: String,
    val layerDigests: List<String>,
    val totalLayerBytes: Long,
    val pulledAtMs: Long,
    val architecture: String = "arm64",
)

/**
 * Полный pull OCI-образа: manifest → выбор linux/arm64 → слои (с докачкой, проверкой digest и
 * общим кэшем blobs) → распаковка по порядку во временный каталог → атомарное переименование.
 */
class OciImagePuller(private val http: OkHttpClient) {
    private val json = Json { prettyPrint = true }

    suspend fun pull(
        imageRef: String, target: PullTarget, replaceExisting: Boolean = false, onProgress: ProgressListener = {},
    ): ImageMetadata {
        val ref = ImageRef.parse(imageRef)
        val registry = RegistryClient(http, ref.registryBaseUrl)
        ArmLog.i("Puller", "pull_start", imageRef)
        onProgress(DownloadProgress(Stage.RESOLVING, null, 0, null, 0, null))
        val image = registry.resolve(ref.repository, ref.reference)
        val total = image.layers.sumOf { it.size }
        val n = image.layers.size

        withContext(Dispatchers.IO) {
            target.blobsDir.mkdirs(); target.metadataDir.mkdirs()
            File(target.metadataDir, "manifest.json").writeText(image.manifestJson)
        }
        registry.downloadBlob(ref.repository, image.config, File(target.metadataDir, "config.json"))

        var base = 0L
        for ((i, layer) in image.layers.withIndex()) {
            val blob = File(target.blobsDir, layer.digest.removePrefix("sha256:"))
            val detail = "${i + 1}/$n"
            registry.downloadBlob(ref.repository, layer, blob, Stage.DOWNLOADING, detail) { p ->
                val done = base + p.downloadedBytes
                val eta = if (p.bytesPerSecond > 0) ((total - done) / p.bytesPerSecond).coerceAtLeast(0) else null
                onProgress(p.copy(downloadedBytes = done, totalBytes = total, etaSeconds = eta))
            }
            base += layer.size
            onProgress(DownloadProgress(Stage.DOWNLOADING, detail, base, total, 0, null))
        }

        val job = currentCoroutineContext().job
        withContext(Dispatchers.IO) {
            val tmp = File(target.rootfsDir.path + ".tmp")
            LayerExtractor.deleteNoFollow(tmp)
            if (target.rootfsDir.exists()) {
                if (!replaceExisting) throw ArmException(ErrorCode.ROOTFS_EXISTS, "System directory already exists: ${target.rootfsDir}", listOf(target.rootfsDir.path))
                LayerExtractor.deleteNoFollow(target.rootfsDir)
            }
            for ((i, layer) in image.layers.withIndex()) {
                job.ensureActive()
                LayerExtractor.extract(File(target.blobsDir, layer.digest.removePrefix("sha256:")), tmp) { job.ensureActive() }
                onProgressBlocking(onProgress, DownloadProgress(Stage.EXTRACTING, "${i + 1}/$n", i + 1L, n.toLong(), 0, null))
            }
            if (!tmp.renameTo(target.rootfsDir)) throw CodedIOException(ErrorCode.RENAME_FAILED, "Failed to rename ${tmp.name}", listOf(tmp.name))
        }

        val meta = ImageMetadata(imageRef, image.manifestDigest, image.config.digest, image.layers.map { it.digest }, total, System.currentTimeMillis())
        withContext(Dispatchers.IO) { File(target.metadataDir, "image.json").writeText(json.encodeToString(meta)) }
        onProgress(DownloadProgress(Stage.SAVING, null, total, total, 0, 0))
        ArmLog.i("Puller", "pull_done", image.manifestDigest)
        return meta
    }

    private suspend fun onProgressBlocking(l: ProgressListener, p: DownloadProgress) = l(p)
}
