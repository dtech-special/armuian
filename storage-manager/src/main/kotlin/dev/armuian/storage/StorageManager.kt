package dev.armuian.storage

import dev.armuian.core.ArmLog
import dev.armuian.core.ArmException
import dev.armuian.core.ArmResult
import dev.armuian.core.ErrorCode
import dev.armuian.core.armFail
import dev.armuian.core.runArm
import java.io.File

/**
 * Отдельное хранилище ArmUian. Корень по умолчанию — /data/adb/armuian
 * (недоступен обычному приложению: создание там будет выполнять root-bridge).
 * Здесь — вся логика, работающая с любым корнем (тестируется на временной папке).
 */
class StorageManager(val root: File) {

    companion object {
        val LAYOUT = listOf(
            "images/redroid-16", "images/custom", "containers/default",
            "cache", "downloads", "logs", "config", "metadata",
        )
        private const val TAG = "StorageManager"
    }

    fun dir(relative: String) = File(root, relative)

    /** Проверка пути перед созданием (ТЗ 8): не пустой, абсолютный, без «..». Тексты ошибок — в UI по [ErrorCode]. */
    fun validateRoot(): ArmResult<Unit> {
        val p = root.path
        return when {
            p.isBlank() -> armFail(ErrorCode.STORAGE_PATH_EMPTY, "Storage path is empty")
            !root.isAbsolute -> armFail(ErrorCode.STORAGE_PATH_NOT_ABSOLUTE, "Path must be absolute: $p", p)
            p.split('/').contains("..") -> armFail(ErrorCode.STORAGE_PATH_DOTDOT, "Path contains '..': $p", p)
            root.exists() && !root.isDirectory -> armFail(ErrorCode.STORAGE_NOT_DIRECTORY, "Not a directory: $p", p)
            else -> ArmResult.Ok(Unit)
        }
    }

    /** Создаёт структуру каталогов. Существующие данные не затрагивает. */
    fun ensureLayout(): ArmResult<Unit> {
        val v = validateRoot()
        if (v is ArmResult.Fail) return v
        return runArm {
            for (rel in LAYOUT) {
                val d = dir(rel)
                if (!d.exists() && !d.mkdirs()) throw ArmException(ErrorCode.STORAGE_MKDIR_FAILED, "Failed to create ${d.path}", listOf(d.path))
            }
            ArmLog.i(TAG, "storage_ready", root.path)
        }
    }

    fun usedBytes(relative: String? = null): Long {
        val base = if (relative == null) root else dir(relative)
        return if (base.exists()) base.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
    }

    fun freeBytes(): Long? {
        var f: File? = root
        while (f != null && !f.exists()) f = f.parentFile
        return f?.usableSpace
    }

    /** Очищает только cache/; данные контейнеров и образы не трогает. */
    fun clearCache(): ArmResult<Long> = runArm {
        val cache = dir("cache")
        val freed = usedBytes("cache")
        cache.listFiles()?.forEach { it.deleteRecursively() }
        ArmLog.i(TAG, "cache_cleared", freed)
        freed
    }

    /** Удаление образа — только каталога этого образа. Вызывать после подтверждения пользователя. */
    fun deleteImage(name: String): ArmResult<Long> = runArm {
        if (name.isBlank() || name.contains('/') || name.contains("..")) {
            throw ArmException(ErrorCode.STORAGE_BAD_IMAGE_NAME, "Invalid image name: $name", listOf(name))
        }
        val target = dir("images/$name")
        val size = usedBytes("images/$name")
        if (target.exists() && !target.deleteRecursively()) {
            throw ArmException(ErrorCode.STORAGE_DELETE_FAILED, "Failed to delete ${target.path}", listOf(target.path))
        }
        ArmLog.w(TAG, "image_deleted", name, size)
        size
    }
}
