package dev.armuian.image

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.armuian.core.ArmLog
import dev.armuian.core.toErrorInfo
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Загрузка и распаковка образа в фоне (WorkManager + foreground-уведомление).
 * После перезапуска приложения/процесса работа продолжается, частично скачанные слои докачиваются.
 */
class PullImageWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        const val KEY_REF = "ref"
        const val KEY_BASE = "base"
        const val KEY_NAME = "name"
        const val KEY_REPLACE = "replace"
        const val KEY_ERROR = "error"
        const val KEY_ERROR_CODE = "error_code"
        const val KEY_ERROR_ARGS = "error_args"
        const val KEY_DIGEST = "digest"
        const val P_STAGE = "stage"
        const val P_DETAIL = "detail"
        const val P_DONE = "done"
        const val P_TOTAL = "total"
        const val P_SPEED = "speed"
        private const val CHANNEL = "armuian_pull"
        private const val NOTIF_ID = 4101

        fun uniqueName(name: String) = "pull-$name"

        /** Вызывать только ПОСЛЕ явного подтверждения пользователем (экран подтверждения установки). */
        fun enqueue(context: Context, imageRef: String, baseDir: File, name: String, unmeteredOnly: Boolean, replace: Boolean = false) {
            val req = OneTimeWorkRequestBuilder<PullImageWorker>()
                .setInputData(workDataOf(KEY_REF to imageRef, KEY_BASE to baseDir.path, KEY_NAME to name, KEY_REPLACE to replace))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(uniqueName(name))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(uniqueName(name), ExistingWorkPolicy.KEEP, req)
        }

        fun cancel(context: Context, name: String) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueName(name))
        }
    }

    override suspend fun doWork(): Result {
        val ref = inputData.getString(KEY_REF) ?: return Result.failure()
        val base = File(inputData.getString(KEY_BASE) ?: return Result.failure())
        val name = inputData.getString(KEY_NAME) ?: return Result.failure()
        val target = PullTarget(File(base, "cache/blobs"), File(base, "images/$name/rootfs"), File(base, "metadata/$name"))

        runCatching { setForeground(foregroundInfo(null)) } // может быть запрещено системой — не критично
        var last = 0L
        return try {
            val meta = OciImagePuller(HttpClients.create()).pull(ref, target, inputData.getBoolean(KEY_REPLACE, false)) { p ->
                val now = SystemClock.elapsedRealtime()
                if (now - last > 500) {
                    last = now
                    setProgress(workDataOf(
                        P_STAGE to p.stage.ordinal, P_DETAIL to (p.detail ?: ""), P_DONE to p.downloadedBytes,
                        P_TOTAL to (p.totalBytes ?: -1L), P_SPEED to p.bytesPerSecond,
                    ))
                    runCatching { setForeground(foregroundInfo(p.percent)) }
                }
            }
            Result.success(workDataOf(KEY_DIGEST to meta.manifestDigest))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ChecksumMismatchException) {
            ArmLog.e("PullWorker", "error_detail", e.message ?: ""); fail(e)
        } catch (e: UnsupportedImageException) {
            ArmLog.e("PullWorker", "error_detail", e.message ?: ""); fail(e)
        } catch (e: HttpStatusException) {
            // 4xx (нет тега, нет доступа) повторять бессмысленно; 5xx — временный сбой сервера.
            ArmLog.w("PullWorker", "worker_retry", runAttemptCount, e.message ?: "")
            if (e.status >= 500 && runAttemptCount < 3) Result.retry() else fail(e)
        } catch (e: IOException) {
            ArmLog.w("PullWorker", "worker_retry", runAttemptCount, e.message ?: "")
            if (runAttemptCount < 3) Result.retry() else fail(e)
        } catch (e: Exception) {
            ArmLog.e("PullWorker", "error_detail", e.message ?: e.javaClass.simpleName); fail(e)
        }
    }

    private fun fail(e: Exception): Result {
        val info = e.toErrorInfo()
        return Result.failure(Data.Builder()
            .putString(KEY_ERROR, info.message).putString(KEY_ERROR_CODE, info.code.name)
            .putStringArray(KEY_ERROR_ARGS, info.args.toTypedArray()).build())
    }

    private fun foregroundInfo(percent: Int?): ForegroundInfo {
        val ctx = applicationContext
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.pull_channel), NotificationManager.IMPORTANCE_LOW))
        }
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(ctx.getString(R.string.pull_title))
            .setOngoing(true).setOnlyAlertOnce(true)
            .apply { if (percent != null) setProgress(100, percent, false) else setProgress(0, 0, true) }
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIF_ID, n)
    }
}
