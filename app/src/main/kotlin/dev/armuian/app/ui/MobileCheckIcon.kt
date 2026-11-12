package dev.armuian.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Material Symbols «mobile_check» (Outlined, weight 400). Символа с именем device_check в Material Symbols нет;
 * mobile_check — телефон с галочкой. В библиотеке Compose Icons её нет, поэтому строим из официального SVG
 * (github.com/google/material-design-icons, Apache 2.0; см. licenses/MaterialSymbols-Apache-2.0.txt).
 */
val MobileCheckIcon: ImageVector by lazy {
    val d = "M260-40q-24.75 0-42.37-17.63Q200-75.25 200-100v-760q0-24 18-42t42-18h438q24.75 0 42.38 17.62Q758-884.75 758-860v150q18 3 30 16.95 12 13.96 12 31.63V-587q0 19-12 33t-30 17v437q0 24.75-17.62 42.37Q722.75-40 698-40H260Zm0-60h438v-760H260v760Zm0 0v-760 760Zm182-260 198-198-42-42-156 156-70-70-42 42 112 112Z"
    ImageVector.Builder(
        name = "MobileCheck", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 960f, viewportHeight = 960f,
    ).apply {
        // viewBox исходного SVG: "0 -960 960 960" — сдвигаем по Y на 960.
        addGroup(translationY = 960f)
        addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
        clearGroup()
    }.build()
}
