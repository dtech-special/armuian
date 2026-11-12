package dev.armuian.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.armuian.app.R
import dev.armuian.core.LogEntry
import dev.armuian.core.LogLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Строка журнала в виде текста (для копирования): время, уровень, тег, сообщение и при наличии stack trace. */
fun LogEntry.asText(res: android.content.res.Resources, pkg: String): String {
    val f = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
    return buildString {
        append("[${f.format(Date(timeMs))}] $level $tag: ${localized(res, pkg)}")
        if (trace != null) append('\n').append(trace)
    }
}

/**
 * Просмотр журнала: фильтр по уровням, автопрокрутка к последней записи, копирование отдельной записи и всего
 * (отфильтрованного) журнала, читаемый stack trace. Обновляется автоматически вместе со списком [entries].
 */
@Composable
fun LogView(entries: List<LogEntry>, onCopy: (String) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val locale = LocalConfiguration.current // перерисовка при смене языка
    val mono = LocalMonoFont.current
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.ROOT) }

    // Фильтр сохраняется при повороте экрана (храним порядковые номера включённых уровней).
    var enabled by rememberSaveable { mutableStateOf(LogLevel.entries.map { it.ordinal }) }
    var autoScroll by rememberSaveable { mutableStateOf(true) }
    val shown = remember(entries, enabled) { entries.filter { it.level.ordinal in enabled } }
    val listState = rememberLazyListState()

    LaunchedEffect(shown.size, autoScroll) {
        if (autoScroll && shown.isNotEmpty()) listState.animateScrollToItem(shown.lastIndex)
    }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LogLevel.entries.forEach { lvl ->
                FilterChip(
                    selected = lvl.ordinal in enabled,
                    onClick = { enabled = if (lvl.ordinal in enabled) enabled - lvl.ordinal else enabled + lvl.ordinal },
                    label = { Text(lvl.name, maxLines = 1, softWrap = false) },
                )
            }
            FilterChip(selected = autoScroll, onClick = { autoScroll = !autoScroll }, label = { Text(stringResource(R.string.log_autoscroll), maxLines = 1, softWrap = false) })
            TextButton(onClick = { onCopy(shown.joinToString("\n") { it.asText(ctx.resources, ctx.packageName) }) }, enabled = shown.isNotEmpty()) {
                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                Text(stringResource(R.string.log_copy_all), Modifier.padding(start = 6.dp), maxLines = 1, softWrap = false)
            }
        }

        Surface(Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerLowest) {
            if (shown.isEmpty()) {
                Text(stringResource(R.string.console_empty), fontFamily = mono, fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            } else LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                itemsIndexed(shown, key = { i, e -> "${e.timeMs}-$i" }) { _, e ->
                    var showTrace by remember { mutableStateOf(false) }
                    val color = when (e.level) {
                        LogLevel.ERROR -> cs.error
                        LogLevel.WARN -> cs.tertiary
                        LogLevel.INFO -> cs.onSurface
                        LogLevel.DEBUG -> cs.onSurfaceVariant
                    }
                    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                "[${timeFmt.format(Date(e.timeMs))}] ${e.level.name.first()} ${e.tag}: ${e.localized(ctx.resources, ctx.packageName)}",
                                fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, color = color, modifier = Modifier.weight(1f),
                            )
                            // Копирование отдельного сообщения.
                            IconButton(onClick = { onCopy(e.asText(ctx.resources, ctx.packageName)) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Outlined.ContentCopy, stringResource(R.string.log_copy_one), Modifier.size(14.dp), tint = cs.onSurfaceVariant)
                            }
                        }
                        if (e.trace != null) {
                            TextButton(onClick = { showTrace = !showTrace }, contentPadding = PaddingValues(0.dp), modifier = Modifier.padding(0.dp)) {
                                Text(stringResource(if (showTrace) R.string.log_hide_trace else R.string.log_show_trace), fontSize = 12.sp)
                            }
                            if (showTrace) Surface(shape = RoundedCornerShape(10.dp), color = cs.errorContainer.copy(alpha = 0.35f)) {
                                Text(
                                    e.trace!!.lines().joinToString("\n") { l -> if (l.startsWith("\tat ")) "  " + l.trim() else l },
                                    fontFamily = mono, fontSize = 11.sp, lineHeight = 14.sp, color = cs.onSurface,
                                    modifier = Modifier.padding(8.dp).horizontalScroll(rememberScrollState()),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
