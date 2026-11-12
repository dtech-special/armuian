package dev.armuian.core

/** Состояния контейнера (см. ТЗ 4.3). */
enum class ContainerState {
    NOT_INSTALLED, DOWNLOADING, UNPACKING, PREPARING, READY, RUNNING, STOPPING, ERROR, UNSUPPORTED
}

/** Код ошибки: библиотеки не содержат переводимых текстов, UI подставляет строку на языке устройства по коду. */
enum class ErrorCode {
    UNKNOWN,
    STORAGE_PATH_EMPTY, STORAGE_PATH_NOT_ABSOLUTE, STORAGE_PATH_DOTDOT, STORAGE_NOT_DIRECTORY,
    STORAGE_MKDIR_FAILED, STORAGE_DELETE_FAILED, STORAGE_BAD_IMAGE_NAME,
    HTTP_STATUS, RANGE_REJECTED, CHECKSUM_MISMATCH, RATE_LIMIT, NO_ARCH_VARIANT, MANIFEST_NO_CONFIG, MANIFEST_NO_LAYERS,
    EMPTY_RESPONSE, INCOMPLETE_DOWNLOAD, RENAME_FAILED, REGISTRY_TOKEN, UNSAFE_ARCHIVE_PATH, ZSTD_UNSUPPORTED,
    ROOTFS_EXISTS, BAD_SYSTEM_NAME, EMPTY_IMAGE_REF, NO_REPOSITORY, ONLY_SHA256,
    NOT_INSTALLED, IMAGE_NOT_INSTALLED, UNSUPPORTED_CONFIG, NOT_IMPLEMENTED, NOT_RUNNING,
    ROOT_REQUIRED, ROOT_LOST, GUEST_START_FAILED, GUEST_ALREADY_RUNNING, GUEST_STOP_FAILED, IMAGE_INVALID, BACKEND_NOT_AVAILABLE, IMAGE_IN_USE,
}

/** Любая ошибка с кодом и аргументами для локализованного сообщения. */
interface CodedError {
    val code: ErrorCode
    val args: List<String>
}

/** Исключение с кодом; [message] — технический текст для логов, не для пользователя. */
class ArmException(override val code: ErrorCode, message: String, override val args: List<String> = emptyList()) : Exception(message), CodedError

data class ErrorInfo(val code: ErrorCode, val args: List<String>, val message: String)

fun Throwable.toErrorInfo(): ErrorInfo =
    if (this is CodedError) ErrorInfo(code, args, message ?: code.name) else ErrorInfo(ErrorCode.UNKNOWN, emptyList(), message ?: javaClass.simpleName)

/** Типизированный результат вместо исключений в бизнес-логике. */
sealed interface ArmResult<out T> {
    data class Ok<T>(val value: T) : ArmResult<T>
    data class Fail(
        val message: String,
        val cause: Throwable? = null,
        val code: ErrorCode = ErrorCode.UNKNOWN,
        val args: List<String> = emptyList(),
    ) : ArmResult<Nothing> {
        fun toInfo() = ErrorInfo(code, args, message)
    }
}

fun armFail(code: ErrorCode, message: String, vararg args: String) = ArmResult.Fail(message, null, code, args.toList())

inline fun <T> runArm(block: () -> T): ArmResult<T> =
    try { ArmResult.Ok(block()) } catch (e: ArmException) {
        ArmResult.Fail(e.message ?: e.code.name, e, e.code, e.args)
    } catch (t: Throwable) { ArmResult.Fail(t.message ?: t.javaClass.simpleName, t) }
