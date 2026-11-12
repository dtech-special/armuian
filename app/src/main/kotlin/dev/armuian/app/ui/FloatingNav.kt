package dev.armuian.app.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.armuian.app.data.NavBarStyle

/** Сколько места снизу занимает плавающая панель — списки добавляют его к своему нижнему отступу. */
val LocalBottomBarPadding = compositionLocalOf { 0.dp }

val NavBarHeight = 56.dp
val NavBarMargin = 12.dp

private const val DURATION = 260

/** Размытие доступно только на Android 12+ (RenderEffect). */
val BlurSupported: Boolean get() = Build.VERSION.SDK_INT >= 31

/** Радиус размытия по настройке 0..100 → 4..40 dp. */
private fun blurDp(percent: Int) = 4f + percent.coerceIn(0, 100) * 0.36f

/**
 * Плавающая панель у нижнего края экрана с учётом системной навигации. Геометрия и поведение не зависят от стиля.
 */
@Composable
fun FloatingNavBar(
    tabs: List<Tab>, selected: Tab, onSelect: (Tab) -> Unit,
    style: NavBarStyle, blurPercent: Int, backdrop: BackdropState?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = NavBarMargin, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        NavCapsule(tabs, selected, onSelect, style, blurPercent, backdrop)
    }
}

/**
 * Сама капсула. Используется и в приложении, и в превью настроек — размеры у всех трёх стилей одинаковы:
 *  - Frosted Glass: полупрозрачная поверхность + размытие фона (интенсивность настраивается); без API 31 — плотнее;
 *  - Glass: прозрачная поверхность без размытия, содержимое под панелью частично видно;
 *  - Solid: непрозрачная поверхность.
 */
@Composable
fun NavCapsule(
    tabs: List<Tab>, selected: Tab, onSelect: (Tab) -> Unit,
    style: NavBarStyle, blurPercent: Int, backdrop: BackdropState?,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val blurActive = style == NavBarStyle.FROSTED_GLASS && BlurSupported && backdrop != null
    val surfaceColor = when (style) {
        NavBarStyle.FROSTED_GLASS -> cs.surfaceContainerHigh.copy(alpha = if (blurActive) 0.42f else 0.90f) // fallback — плотнее
        NavBarStyle.GLASS -> cs.surfaceContainerHigh.copy(alpha = 0.26f)
        NavBarStyle.SOLID -> cs.surfaceContainerHighest
    }
    val borderColor = cs.outlineVariant.copy(alpha = if (style == NavBarStyle.SOLID) 0.5f else 0.85f)
    val radiusPx = with(LocalDensity.current) { blurDp(blurPercent).dp.toPx() }
    // Тень не рисуем у «Glass»: сквозь прозрачную поверхность она просвечивала бы тёмным пятном.
    val shadowed = if (style == NavBarStyle.GLASS) Modifier else
        Modifier.shadow(14.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.35f))

    Surface(
        modifier = modifier
            .then(shadowed)
            .widthIn(max = 520.dp)
            .animateContentSize(tween(DURATION, easing = FastOutSlowInEasing))
            .backdropBlur(backdrop, radiusPx, enabled = blurActive),
        shape = CircleShape,
        color = surfaceColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            tabs.forEach { t -> NavItem(t, selected = t == selected, onClick = { onSelect(t) }) }
        }
    }
}

@Composable
private fun NavItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val label = stringResource(tab.titleRes)
    val transition = updateTransition(targetState = selected, label = "nav-${tab.name}")
    val borderColor by transition.animateColor(transitionSpec = { tween(DURATION) }, label = "border") { if (it) cs.primary.copy(alpha = 0.55f) else Color.Transparent }
    val fill by transition.animateColor(transitionSpec = { tween(DURATION) }, label = "fill") { if (it) cs.primary.copy(alpha = 0.10f) else Color.Transparent }
    val tint by transition.animateColor(transitionSpec = { tween(DURATION) }, label = "tint") { if (it) cs.primary else cs.onSurfaceVariant }

    Row(
        modifier = Modifier
            .heightIn(min = 48.dp).widthIn(min = 48.dp)          // область нажатия не меньше 48dp
            .clip(CircleShape)
            .border(1.dp, borderColor, CircleShape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .background(fill)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        transition.AnimatedVisibility(
            visible = { it },
            enter = expandHorizontally(tween(DURATION, easing = FastOutSlowInEasing), expandFrom = Alignment.Start) +
                fadeIn(tween(DURATION)) + slideInHorizontally(tween(DURATION)) { -it / 4 },
            exit = shrinkHorizontally(tween(DURATION, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Start) +
                fadeOut(tween(DURATION / 2)) + slideOutHorizontally(tween(DURATION)) { -it / 4 },
        ) {
            Row {
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
