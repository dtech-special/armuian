package dev.armuian.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * Запись журнала хранит КЛЮЧ сообщения и аргументы, а не готовый текст:
 * интерфейс подставляет строку `log_<key>` на языке устройства при показе.
 */
data class LogEntry(
    val timeMs: Long, val level: LogLevel, val tag: String, val key: String,
    val args: List<String> = emptyList(),
    /** Stack trace исключения (если запись создана из исключения). */
    val trace: String? = null,
) {
    /** Технический вид без перевода (запасной вариант, logcat, тесты). */
    val message: String get() = if (args.isEmpty()) key else "$key ${args.joinToString(" ")}"
}

/** Реальный журнал приложения: последние 1000 записей в памяти + logcat. */
object ArmLog {
    private const val MAX = 1000
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun log(level: LogLevel, tag: String, key: String, args: List<String> = emptyList(), trace: String? = null) {
        val e = LogEntry(System.currentTimeMillis(), level, tag, key, args, trace)
        _entries.value = (_entries.value + e).takeLast(MAX)
        runCatching {
            when (level) {
                LogLevel.DEBUG -> Log.d(tag, e.message)
                LogLevel.INFO -> Log.i(tag, e.message)
                LogLevel.WARN -> Log.w(tag, e.message)
                LogLevel.ERROR -> Log.e(tag, e.message)
            }
        }
    }

    fun d(tag: String, key: String, vararg args: Any?) = log(LogLevel.DEBUG, tag, key, args.map { it.toString() })
    fun i(tag: String, key: String, vararg args: Any?) = log(LogLevel.INFO, tag, key, args.map { it.toString() })
    fun w(tag: String, key: String, vararg args: Any?) = log(LogLevel.WARN, tag, key, args.map { it.toString() })
    fun e(tag: String, key: String, vararg args: Any?) = log(LogLevel.ERROR, tag, key, args.map { it.toString() })

    /** Ошибка из исключения: сообщение + полный stack trace (показывается в просмотре журнала). */
    fun ex(tag: String, t: Throwable) = log(LogLevel.ERROR, tag, "error_detail", listOf(t.message ?: t.javaClass.simpleName), t.stackTraceToString())

    fun clear() { _entries.value = emptyList() }
}
