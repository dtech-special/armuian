package dev.armuian.image

import dev.armuian.core.ArmLog
import dev.armuian.core.ErrorCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Потоковая загрузка в файл: докачка (Range), проверка SHA256, пропуск уже скачанного,
 * отмена через корутину. В память целиком ничего не читается.
 */
class FileDownloader(private val http: OkHttpClient) {

    suspend fun download(
        url: String,
        dest: File,
        headers: Map<String, String> = emptyMap(),
        expectedSha256: String? = null,
        stage: Stage = Stage.DOWNLOADING,
        detail: String? = null,
        onProgress: ProgressListener = {},
    ): File {
        if (expectedSha256 != null && dest.isFile) {
            val have = withContext(Dispatchers.IO) { Sha256.hexOf(dest) }
            if (have.equals(expectedSha256, ignoreCase = true)) {
                ArmLog.i("Downloader", "already_downloaded", dest.name)
                return dest
            }
            dest.delete()
        }
        dest.parentFile?.mkdirs()
        val part = File(dest.path + ".part")
        try {
            transfer(url, headers, part, stage, detail, resume = true, onProgress)
        } catch (_: RangeRejectedException) {
            part.delete()
            transfer(url, headers, part, stage, detail, resume = false, onProgress)
        }
        if (expectedSha256 != null) {
            onProgress(DownloadProgress(Stage.VERIFYING, detail, part.length(), part.length(), 0L, null))
            val actual = withContext(Dispatchers.IO) { Sha256.hexOf(part) }
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                part.delete()
                throw ChecksumMismatchException(expectedSha256, actual)
            }
        }
        withContext(Dispatchers.IO) {
            dest.delete()
            if (!part.renameTo(dest)) throw CodedIOException(ErrorCode.RENAME_FAILED, "Failed to rename ${part.name}", listOf(part.name))
        }
        return dest
    }

    private suspend fun transfer(
        url: String, headers: Map<String, String>, part: File,
        stage: Stage, detail: String?, resume: Boolean, onProgress: ProgressListener,
    ) = coroutineScope {
        val resumeFrom = if (resume && part.isFile) part.length() else 0L
        val rb = Request.Builder().url(url)
        headers.forEach { (k, v) -> rb.header(k, v) }
        if (resumeFrom > 0) rb.header("Range", "bytes=$resumeFrom-")
        val call = http.newCall(rb.build())
        // При отмене корутины немедленно рвём сетевой вызов, чтобы не ждать таймаута чтения.
        val watcher = launch(Dispatchers.Default) { try { awaitCancellation() } finally { call.cancel() } }
        try {
            withContext(Dispatchers.IO) {
                call.execute().use { resp ->
                    if (resp.code == 416 && resumeFrom > 0) throw RangeRejectedException()
                    if (!resp.isSuccessful) throw HttpStatusException(resp.code, url)
                    val body = resp.body ?: throw CodedIOException(ErrorCode.EMPTY_RESPONSE, "Empty server response")
                    val append = resp.code == 206 && resumeFrom > 0
                    val startOffset = if (append) resumeFrom else 0L
                    val len = body.contentLength()
                    val total = if (len >= 0) startOffset + len else null
                    val tracker = ProgressTracker(total, startOffset)
                    FileOutputStream(part, append).use { out ->
                        body.byteStream().use { input ->
                            val buf = ByteArray(64 * 1024)
                            var done = startOffset
                            var lastEmit = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                ensureActive()
                                out.write(buf, 0, n)
                                done += n
                                val now = System.nanoTime()
                                if (now - lastEmit > 250_000_000L) {
                                    lastEmit = now
                                    onProgress(tracker.update(done, stage, detail))
                                }
                            }
                            onProgress(tracker.update(done, stage, detail))
                            if (total != null && done != total) throw CodedIOException(ErrorCode.INCOMPLETE_DOWNLOAD, "Incomplete download: $done of $total", listOf(done.toString(), total.toString()))
                        }
                    }
                }
            }
        } finally {
            watcher.cancel()
        }
    }
}
