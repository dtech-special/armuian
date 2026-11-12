package dev.armuian.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize

/**
 * НАСТОЯЩЕЕ размытие фона под панелью (backdrop blur), без сторонних библиотек.
 *
 * Modifier.blur() размывает собственное содержимое элемента, а не то, что лежит под ним, поэтому здесь другой приём:
 *  1. [backdropSource] записывает отрисовку основного содержимого в GraphicsLayer и рисует его как обычно;
 *  2. [backdropBlur] на панели рисует копию этого слоя, сдвинутую так, чтобы под панелью оказался ровно её фрагмент,
 *     применяет к копии BlurEffect (RenderEffect, Android 12+/API 31+) и обрезает по капсуле.
 * Ниже API 31 размытие недоступно — вызывающий код использует более плотную полупрозрачную поверхность.
 */
@Stable
class BackdropState(val layer: GraphicsLayer) {
    /** Позиция источника фона в координатах окна. */
    var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberBackdropState(): BackdropState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { BackdropState(layer) }
}

fun Modifier.backdropSource(state: BackdropState, enabled: Boolean): Modifier = this
    .onGloballyPositioned { state.origin = it.positionInRoot() }
    .drawWithContent {
        if (enabled) {
            val density: Density = this
            state.layer.record(density, layoutDirection, IntSize(size.width.toInt(), size.height.toInt())) {
                this@drawWithContent.drawContent()
            }
            drawLayer(state.layer)
        } else {
            drawContent()
        }
    }

@Composable
fun Modifier.backdropBlur(state: BackdropState?, radiusPx: Float, enabled: Boolean): Modifier {
    if (!enabled || state == null) return this
    val blurLayer = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { position = it.positionInRoot() }
        .drawBehind {
            val density: Density = this
            blurLayer.renderEffect = BlurEffect(radiusPx, radiusPx, TileMode.Clamp)
            blurLayer.record(density, layoutDirection, IntSize(size.width.toInt(), size.height.toInt())) {
                val shift = position - state.origin
                translate(left = -shift.x, top = -shift.y) { drawLayer(state.layer) }
            }
            val capsule = Path().apply {
                addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(size.height / 2f)))
            }
            clipPath(capsule) { drawLayer(blurLayer) }
        }
}
