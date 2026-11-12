package dev.armuian.image

import dev.armuian.core.ArmLog
import kotlinx.serialization.json.Json
import java.io.File

/** Общий кэш слоёв (cache/blobs). Слой может использоваться несколькими образами. */
object LayerCache {
    private val json = Json { ignoreUnknownKeys = true }
    private val HEX64 = Regex("^[0-9a-f]{64}$")

    /** Имена каталогов метаданных всех установленных образов, кроме [except]. */
    private fun otherInstalled(baseDir: File, except: String): List<String> =
        File(baseDir, "metadata").listFiles()?.filter { it.isDirectory && it.name != except && File(it, "image.json").isFile }?.map { it.name } ?: emptyList()

    private fun digestsOf(baseDir: File, name: String): List<String> = runCatching {
        json.decodeFromString<ImageMetadata>(File(baseDir, "metadata/$name/image.json").readText()).layerDigests
    }.getOrDefault(emptyList())

    /**
     * Удаляет слои образа [name] (его digest-ы переданы заранее — метаданные к этому моменту могут быть удалены),
     * которые НЕ нужны другим установленным образам. Незавершённые .part этих слоёв тоже удаляются.
     * Возвращает освобождённые байты. Имена файлов проверяются (только 64 hex), выход за cache/blobs невозможен.
     */
    fun releaseLayers(baseDir: File, name: String, myDigests: List<String>): Long {
        val blobs = File(baseDir, "cache/blobs")
        if (!blobs.isDirectory) return 0L
        val inUse = otherInstalled(baseDir, name).flatMap { digestsOf(baseDir, it) }.map { it.removePrefix("sha256:") }.toSet()
        var freed = 0L
        var count = 0
        for (d in myDigests.map { it.removePrefix("sha256:") }) {
            if (!HEX64.matches(d) || d in inUse) continue
            for (f in listOf(File(blobs, d), File(blobs, "$d.part"))) {
                if (f.isFile && f.parentFile == blobs) { freed += f.length(); if (f.delete()) count++ }
            }
        }
        ArmLog.i("LayerCache", "layers_released", name, count, freed)
        return freed
    }
}
