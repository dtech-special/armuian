package dev.armuian.app.ui

import android.content.res.Resources
import dev.armuian.core.LogEntry

/** Строка журнала на языке устройства: ресурс `log_<key>`; если его нет — технический вид. */
fun LogEntry.localized(res: Resources, pkg: String): String {
    @Suppress("DiscouragedApi")
    val id = res.getIdentifier("log_$key", "string", pkg)
    if (id == 0) return message
    return runCatching { res.getString(id, *args.toTypedArray()) }.getOrDefault(message)
}
