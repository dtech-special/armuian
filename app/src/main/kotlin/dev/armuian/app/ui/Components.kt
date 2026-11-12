package dev.armuian.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class Tone { NEUTRAL, GOOD, WARN, BAD }

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable
fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End,
            fontFamily = if (mono) LocalMonoFont.current else null, modifier = Modifier.weight(0.6f))
    }
}

@Composable
fun StatusPill(text: String, tone: Tone) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        Tone.NEUTRAL -> cs.surfaceVariant to cs.onSurfaceVariant
        Tone.GOOD -> cs.tertiaryContainer to cs.onTertiaryContainer
        Tone.WARN -> cs.secondaryContainer to cs.onSecondaryContainer
        Tone.BAD -> cs.errorContainer to cs.onErrorContainer
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(50)) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** Двухэтапная кнопка: первое нажатие «взводит» её, второе подтверждает; через 3 с возвращается в исходное состояние. */
@Composable
fun TwoStepButton(
    idle: String, confirm: String, onConfirm: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, icon: ImageVector? = null,
) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(3000); armed = false } }
    LaunchedEffect(enabled) { if (!enabled) armed = false }
    val cs = MaterialTheme.colorScheme
    val container by animateColorAsState(if (armed) cs.tertiary else cs.primary, label = "container")
    val content by animateColorAsState(if (armed) cs.onTertiary else cs.onPrimary, label = "content")
    Button(
        onClick = { if (armed) { armed = false; onConfirm() } else armed = true },
        enabled = enabled,
        modifier = modifier.height(56.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        AnimatedContent(targetState = armed, label = "label") { a ->
            Text(if (a) confirm else idle, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}

fun fmtBytes(b: Long): String {
    val gb = b / 1024.0 / 1024 / 1024
    return when {
        gb >= 1 -> "%.2f GB".format(gb)
        b >= 1024 * 1024 -> "%.0f MB".format(b / 1024.0 / 1024)
        b >= 1024 -> "%.0f KB".format(b / 1024.0)
        else -> "$b B"
    }
}

fun fmtDuration(totalSec: Long): String {
    val h = totalSec / 3600; val m = (totalSec % 3600) / 60; val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
