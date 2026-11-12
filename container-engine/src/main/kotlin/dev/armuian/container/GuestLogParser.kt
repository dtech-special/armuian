package dev.armuian.container

import dev.armuian.core.LogLevel

/** Разбор guest.log: уровень определяется префиксом, который пишут наши скрипты; всё остальное — вывод утилит (WARN). */
object GuestLogParser {
    data class Line(val level: LogLevel, val text: String)

    fun parse(lines: List<String>): List<Line> = lines.filter { it.isNotBlank() }.map { l ->
        val t = l.trim()
        when {
            t.startsWith("[armuian] step:") -> Line(LogLevel.INFO, t.removePrefix("[armuian] step:").trim())
            t.startsWith("[armuian] warn:") -> Line(LogLevel.WARN, t.removePrefix("[armuian] warn:").trim())
            t.startsWith("[armuian] error:") -> Line(LogLevel.ERROR, t.removePrefix("[armuian] error:").trim())
            t.startsWith("[armuian] mount:") -> Line(LogLevel.WARN, "mount: " + t.removePrefix("[armuian] mount:").trim())
            else -> Line(LogLevel.WARN, t)
        }
    }
}
