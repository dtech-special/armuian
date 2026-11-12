package dev.armuian.core

/**
 * Единое место для данных об авторе и проекте.
 * Меняйте значения здесь — они попадут на страницу «О проекте».
 */
object AppInfo {
    const val APP_NAME = "ArmUian"
    const val PACKAGE_NAME = "dev.armuian.app"
    const val LICENSE = "Apache License 2.0"

    // ---- Автор ----
    const val AUTHOR_TIKTOK = "@techdelta"
    const val AUTHOR_GITHUB = "@dtech-special"

    const val TIKTOK_URL = "https://www.tiktok.com/$AUTHOR_TIKTOK"
    const val GITHUB_PROFILE_URL = "https://github.com/dtech-special"
    const val GITHUB_REPO_URL = "https://github.com/dtech-special/ArmUian"

    // ---- Хранилище и образ по умолчанию ----
    const val STORAGE_ROOT = "/data/adb/armuian"
    const val DEFAULT_IMAGE = "redroid/redroid:16.0.0_64only-latest"
}
