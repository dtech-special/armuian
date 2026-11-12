package dev.armuian.detection

/** Описание образа в каталоге (минимум для подбора версии). */
data class CatalogImage(val name: String, val androidMajor: Int, val abi: String, val sizeBytes: Long?)

sealed interface Match {
    /** Есть образ той же основной версии Android, что и у хоста. */
    data class Exact(val image: CatalogImage) : Match
    /** Точного совпадения нет — только ближайшие версии (автоскачивание запрещено). */
    data class Nearest(val candidates: List<CatalogImage>) : Match
    data object None : Match
}

object AndroidVersionMatcher {
    /** "16" -> 16, "14.0.1" -> 14, "abc" -> null */
    fun majorOf(release: String): Int? = release.trim().substringBefore('.').toIntOrNull()

    /**
     * Приоритеты ТЗ 17.1: 1) версия Android, 2) ABI arm64, 3) (backend — проверяется отдельно),
     * 4) минимальный размер, 5) без 32-бит (64only предпочтительнее — учитывается именем).
     */
    fun select(hostMajor: Int, catalog: List<CatalogImage>): Match {
        val arm = catalog.filter { it.abi == "arm64-v8a" }
        val exact = arm.filter { it.androidMajor == hostMajor }
            .sortedWith(compareBy({ !it.name.contains("64only") }, { it.sizeBytes ?: Long.MAX_VALUE }))
        if (exact.isNotEmpty()) return Match.Exact(exact.first())
        val nearest = arm.sortedBy { kotlin.math.abs(it.androidMajor - hostMajor) }.take(3)
        return if (nearest.isEmpty()) Match.None else Match.Nearest(nearest)
    }
}
