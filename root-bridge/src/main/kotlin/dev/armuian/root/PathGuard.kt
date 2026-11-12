package dev.armuian.root

import dev.armuian.core.AppInfo

/**
 * Проверка путей для привилегированных операций.
 * Разрешено только внутри каталога хранилища ArmUian, только безопасные символы,
 * никаких «..». Благодаря этому команда не может содержать shell-метасимволы.
 */
object PathGuard {
    private val SAFE = Regex("^[A-Za-z0-9_./-]+$")

    fun require(path: String, root: String = AppInfo.STORAGE_ROOT, minDepth: Int = 0): String {
        require(SAFE.matches(path)) { "Unsafe characters in path: $path" }
        require(!path.endsWith("/")) { "Path must not end with a slash: $path" }
        require(!path.contains("//")) { "Double slash in path: $path" }
        require(path.split('/').none { it == ".." || it == "." }) { "Path contains . or ..: $path" }
        require(path == root || path.startsWith("$root/")) { "Path is outside the ArmUian storage: $path" }
        val depth = if (path == root) 0 else path.removePrefix("$root/").split('/').size
        require(depth >= minDepth) { "Path is too close to the storage root: $path" }
        return path
    }
}
