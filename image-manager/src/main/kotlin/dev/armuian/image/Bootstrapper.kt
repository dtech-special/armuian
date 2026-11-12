package dev.armuian.image

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.armuian.core.ArmException
import dev.armuian.core.ArmLog
import dev.armuian.core.ErrorCode
import dev.armuian.core.ErrorInfo
import dev.armuian.core.ArmResult
import dev.armuian.core.runArm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File

/** План установки: всё, что нужно показать пользователю на экране подтверждения. */
data class BootstrapPlan(
    val name: String,
    val ref: String,
    val downloadBytes: Long?,
    val layerCount: Int,
    val connection: ConnectionType,
    val decision: DownloadDecision,
    val manifestDigest: String,
)

sealed interface InstallState {
    data object NotInstalled : InstallState
    data object Queued : InstallState
    data class Running(val progress: DownloadProgress?) : InstallState
    data object Paused : InstallState
    data class Installed(val digest: String?) : InstallState
    data class Failed(val error: ErrorInfo) : InstallState
}

/**
 * Собственный bootstrapper ArmUian. Стадии:
 * 1) plan — манифест из реестра, выбор linux/arm64, точный размер загрузки, сетевая политика;
 * 2) start — (только после подтверждения) фоновая задача: слои → SHA256 → распаковка → метаданные;
 * 3) observe — состояние из WorkManager и диска (переживает перезапуск приложения);
 * 4) pause / cancel / delete — управление без потери чужих данных.
 */
class Bootstrapper(
    private val context: Context,
    val baseDir: File,
    private val http: OkHttpClient = HttpClients.create(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val wm get() = WorkManager.getInstance(context)

    private val nameRegex = Regex("^[a-z0-9][a-z0-9._-]*$")
    private fun checkName(name: String) {
        if (!nameRegex.matches(name)) throw ArmException(ErrorCode.BAD_SYSTEM_NAME, "Invalid system name: $name", listOf(name))
    }

    private val blobsDir get() = File(baseDir, "cache/blobs")
    fun imageDir(name: String) = File(baseDir, "images/$name")
    fun metaDir(name: String) = File(baseDir, "metadata/$name")

    fun isInstalled(name: String) = File(metaDir(name), "image.json").isFile

    fun readMetadata(name: String): ImageMetadata? =
        runCatching { json.decodeFromString<ImageMetadata>(File(metaDir(name), "image.json").readText()) }.getOrNull()

    fun installedSize(name: String): Long =
        listOf(imageDir(name), metaDir(name)).sumOf { d -> if (d.exists()) d.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L }

    private fun hasPartial(): Boolean = blobsDir.listFiles { f -> f.name.endsWith(".part") }?.isNotEmpty() == true

    suspend fun plan(name: String, imageRef: String, policy: MobileDataPolicy, connection: ConnectionType): BootstrapPlan {
        checkName(name)
        val ref = ImageRef.parse(imageRef)
        val img = RegistryClient(http, ref.registryBaseUrl).resolve(ref.repository, ref.reference)
        val total = img.layers.sumOf { it.size } + img.config.size
        ArmLog.i("Bootstrapper", "plan_info", name, img.layers.size, total, connection)
        return BootstrapPlan(name, imageRef, total, img.layers.size, connection, NetworkPolicy.decide(policy, connection), img.manifestDigest)
    }

    /** Актуальный digest манифеста (для проверки обновлений). */
    suspend fun latestDigest(imageRef: String): String {
        val ref = ImageRef.parse(imageRef)
        return RegistryClient(http, ref.registryBaseUrl).resolve(ref.repository, ref.reference).manifestDigest
    }

    /** Вызывать только после явного подтверждения пользователем. */
    fun start(plan: BootstrapPlan, unmeteredOnly: Boolean, replace: Boolean = false) {
        checkName(plan.name)
        ArmLog.i("Bootstrapper", "install_start", plan.name, plan.ref)
        PullImageWorker.enqueue(context, plan.ref, baseDir, plan.name, unmeteredOnly, replace)
    }

    /** Пауза: задача отменяется, частично скачанные файлы (.part) остаются для докачки. */
    fun pause(name: String) {
        ArmLog.i("Bootstrapper", "install_pause", name)
        PullImageWorker.cancel(context, name)
    }

    /** Полная отмена: задача снимается, незавершённые .part-файлы удаляются. Готовые слои кэша не трогаем. */
    suspend fun cancel(name: String) {
        ArmLog.w("Bootstrapper", "install_cancel", name)
        PullImageWorker.cancel(context, name)
        delay(400)
        withContext(Dispatchers.IO) {
            blobsDir.listFiles { f -> f.name.endsWith(".part") }?.forEach { it.delete() }
            LayerExtractor.deleteNoFollow(File(imageDir(name), "rootfs.tmp"))
        }
    }

    /**
     * Удаляет выбранную систему: образ, метаданные и её слои, которые не нужны другим установленным образам.
     * Другие образы и общий кэш не затрагиваются. Работает только внутри каталога приложения (имя проверяется).
     */
    suspend fun delete(name: String): ArmResult<Long> = withContext(Dispatchers.IO) {
        checkName(name)
        runArm {
            PullImageWorker.cancel(context, name)
            val size = installedSize(name)
            val myLayers = readMetadata(name)?.layerDigests ?: emptyList()
            LayerExtractor.deleteNoFollow(imageDir(name))
            LayerExtractor.deleteNoFollow(metaDir(name))
            LayerCache.releaseLayers(baseDir, name, myLayers)
            ArmLog.w("Bootstrapper", "system_deleted", name, size)
            size
        }
    }

    fun observe(name: String): Flow<InstallState> =
        wm.getWorkInfosForUniqueWorkFlow(PullImageWorker.uniqueName(name)).map { list ->
            val info = list.lastOrNull()
            when (info?.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> InstallState.Queued
                WorkInfo.State.RUNNING -> InstallState.Running(progressOf(info))
                WorkInfo.State.FAILED -> if (isInstalled(name)) InstallState.Installed(readMetadata(name)?.manifestDigest)
                    else InstallState.Failed(errorOf(info))
                else -> idle(name)
            }
        }

    private fun errorOf(info: WorkInfo): ErrorInfo {
        val d = info.outputData
        val code = d.getString(PullImageWorker.KEY_ERROR_CODE)?.let { runCatching { ErrorCode.valueOf(it) }.getOrNull() } ?: ErrorCode.UNKNOWN
        return ErrorInfo(code, d.getStringArray(PullImageWorker.KEY_ERROR_ARGS)?.toList() ?: emptyList(), d.getString(PullImageWorker.KEY_ERROR) ?: "error")
    }

    private fun idle(name: String): InstallState = when {
        isInstalled(name) -> InstallState.Installed(readMetadata(name)?.manifestDigest)
        hasPartial() -> InstallState.Paused
        else -> InstallState.NotInstalled
    }

    private fun progressOf(info: WorkInfo): DownloadProgress? {
        val p = info.progress
        val stage = p.getInt(PullImageWorker.P_STAGE, -1)
        if (stage < 0) return null
        val done = p.getLong(PullImageWorker.P_DONE, 0)
        val total = p.getLong(PullImageWorker.P_TOTAL, -1).takeIf { it >= 0 }
        val speed = p.getLong(PullImageWorker.P_SPEED, 0)
        val eta = if (total != null && speed > 0) ((total - done) / speed).coerceAtLeast(0) else null
        return DownloadProgress(Stage.entries[stage], p.getString(PullImageWorker.P_DETAIL)?.ifEmpty { null }, done, total, speed, eta)
    }
}
