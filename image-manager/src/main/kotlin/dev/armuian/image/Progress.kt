package dev.armuian.image

enum class Stage { RESOLVING, DOWNLOADING, VERIFYING, EXTRACTING, SAVING }

/** Прогресс без готовых строк: UI сам переводит [stage] на нужный язык. */
data class DownloadProgress(
    val stage: Stage,
    val detail: String?,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val bytesPerSecond: Long,
    val etaSeconds: Long?,
) {
    val percent: Int? get() = totalBytes?.takeIf { it > 0 }?.let { (downloadedBytes * 100 / it).toInt().coerceIn(0, 100) }
}

typealias ProgressListener = suspend (DownloadProgress) -> Unit

/** Считает скорость и оставшееся время. Скорость — только по байтам, скачанным в этой сессии (resume не завышает её). */
class ProgressTracker(
    private val total: Long?,
    private val startBytes: Long,
    private val nowNs: () -> Long = System::nanoTime,
) {
    private val t0 = nowNs()

    fun update(done: Long, stage: Stage, detail: String?): DownloadProgress {
        val elapsed = (nowNs() - t0) / 1e9
        val speed = if (elapsed > 0.5) ((done - startBytes) / elapsed).toLong() else 0L
        val eta = if (total != null && speed > 0) ((total - done) / speed).coerceAtLeast(0) else null
        return DownloadProgress(stage, detail, done, total, speed, eta)
    }
}
