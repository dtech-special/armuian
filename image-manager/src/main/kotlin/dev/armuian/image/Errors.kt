package dev.armuian.image

import dev.armuian.core.CodedError
import dev.armuian.core.ErrorCode
import java.io.IOException

class HttpStatusException(val status: Int, val url: String) : IOException("HTTP $status: $url"), CodedError {
    override val code = ErrorCode.HTTP_STATUS
    override val args = listOf(status.toString(), url)
}

class RangeRejectedException : IOException("Server rejected the Range request"), CodedError {
    override val code = ErrorCode.RANGE_REJECTED
    override val args = emptyList<String>()
}

class ChecksumMismatchException(val expected: String, val actual: String) :
    IOException("SHA256 mismatch: expected $expected, got $actual"), CodedError {
    override val code = ErrorCode.CHECKSUM_MISMATCH
    override val args = listOf(expected, actual)
}

class RateLimitException(val resetEpochSeconds: Long?) : IOException("GitHub API rate limit exhausted"), CodedError {
    override val code = ErrorCode.RATE_LIMIT
    override val args = listOf(resetEpochSeconds?.toString() ?: "")
}

class UnsupportedImageException(override val code: ErrorCode, message: String, override val args: List<String> = emptyList()) :
    IOException(message), CodedError

/** Обычная ошибка ввода-вывода с кодом для локализованного сообщения. */
class CodedIOException(override val code: ErrorCode, message: String, override val args: List<String> = emptyList()) :
    IOException(message), CodedError
