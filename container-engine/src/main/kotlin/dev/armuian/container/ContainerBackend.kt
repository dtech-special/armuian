package dev.armuian.container

import dev.armuian.core.ArmResult

/** Общая абстракция запуска гостевого userspace. Реализации не знают друг о друге. */
interface ContainerBackend {
    val kind: BackendKind
    val implemented: Boolean
    val experimental: Boolean

    /** Проверка возможностей на текущем устройстве (чистая функция от собранных данных). */
    fun evaluate(ctx: EvalContext): BackendCapability

    /** Подготовка: проверка образа и генерация служебных файлов. */
    suspend fun prepare(imageName: String): ArmResult<Unit>
    suspend fun start(imageName: String): ArmResult<Unit>
    suspend fun stop(): ArmResult<Unit>
    suspend fun restart(imageName: String): ArmResult<Unit>
    suspend fun status(): ContainerStatus

    /** Есть ли следы прошлой сессии (чтобы восстановить состояние без лишнего запроса root). */
    fun hasSession(): Boolean

    /** Имя образа, из которого запущена (или была запущена) последняя сессия. */
    fun currentImage(): String?

    /** Последние строки журнала гостя. */
    suspend fun logs(): List<String>
}
