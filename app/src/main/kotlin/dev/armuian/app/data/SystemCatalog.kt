package dev.armuian.app.data

/** Запись каталога систем. [id] = имя каталога в хранилище (images/<id>). */
data class SystemEntry(val id: String, val title: String, val androidMajor: Int?, val ref: String, val custom: Boolean)

object SystemCatalog {
    /** Готовые образы redroid, только 64-bit ветка. Наличие тега проверяется при получении манифеста. */
    val builtIn: List<SystemEntry> = listOf(16, 15, 14, 13).map {
        SystemEntry("redroid-$it", if (it == 16) "Pixel-Like Android 16" else "Android $it", it, "redroid/redroid:$it.0.0_64only-latest", false)
    }

    fun custom(ref: String) = SystemEntry(
        id = "custom-" + Integer.toHexString(ref.hashCode()).trimStart('-'),
        title = ref.substringAfterLast('/'), androidMajor = null, ref = ref, custom = true,
    )
}
