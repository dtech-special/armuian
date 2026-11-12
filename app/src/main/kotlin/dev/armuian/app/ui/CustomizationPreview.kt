package dev.armuian.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.armuian.app.R
import dev.armuian.app.data.AppFont
import dev.armuian.app.data.NavBarStyle

/**
 * Превью панели навигации: те же [NavCapsule], что и в приложении (одинаковая геометрия), поверх цветного фона,
 * чтобы было видно, как работают размытие / прозрачность / непрозрачная поверхность.
 */
@Composable
fun NavBarPreview(style: NavBarStyle, blurPercent: Int, modifier: Modifier = Modifier) {
    val backdrop = rememberBackdropState()
    val cs = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().height(124.dp).clip(RoundedCornerShape(20.dp))) {
        Box(
            Modifier.fillMaxSize()
                .backdropSource(backdrop, enabled = style == NavBarStyle.FROSTED_GLASS)
                .background(Brush.horizontalGradient(listOf(cs.primaryContainer, cs.tertiaryContainer, cs.secondaryContainer))),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("ArmUian", style = MaterialTheme.typography.headlineSmall, color = cs.onPrimaryContainer)
                Text("Pixel-Like Android 16", style = MaterialTheme.typography.titleMedium, color = cs.onPrimaryContainer)
                Text("arm64-v8a · Shared PID · 5.10", style = MaterialTheme.typography.bodyMedium, color = cs.onPrimaryContainer)
            }
        }
        NavCapsule(
            tabs = Tab.entries, selected = Tab.HOME, onSelect = {}, style = style, blurPercent = blurPercent, backdrop = backdrop,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
        )
    }
}

/** Превью выбранных шрифтов: название семейства, заголовок, обычный текст и техническая строка. */
@Composable
fun FontPreview(uiFont: AppFont, terminalFont: AppFont, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerLowest) {
        Column(Modifier.padding(16.dp)) {
            Text(uiFont.displayName, style = MaterialTheme.typography.labelLarge, color = cs.primary)
            Text(stringResource(R.string.font_sample_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.font_sample_body), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            Text(terminalFont.displayName, style = MaterialTheme.typography.labelLarge, color = cs.primary, modifier = Modifier.padding(top = 12.dp))
            Text("$ armuian start --backend shared_pid  [INFO]", fontFamily = LocalMonoFont.current, style = MaterialTheme.typography.bodySmall)
        }
    }
}
