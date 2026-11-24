package com.kouwis.alzkr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/*
 * The screens are laid out for a phone about 392 dp wide and 740 dp tall. On a narrower or shorter
 * screen (a small phone, a wide or squat one, or a larger «display size» setting) the whole interface
 * is drawn a little smaller, in proportion, so nothing is cut off or pushed out. A very large font
 * setting is held back a little too, as the cards have room for only so much text.
 */
private const val DESIGN_WIDTH = 392f
private const val DESIGN_HEIGHT = 740f

@Composable
fun Responsive(content: @Composable () -> Unit) {
    val conf = LocalConfiguration.current
    val d = LocalDensity.current
    // the phone's short and long sides, whichever way it is held
    val short = minOf(conf.screenWidthDp, conf.screenHeightDp).toFloat()
    val long = maxOf(conf.screenWidthDp, conf.screenHeightDp).toFloat()
    val k = minOf(1f, short / DESIGN_WIDTH, long / DESIGN_HEIGHT).coerceAtLeast(0.78f)
    val fontScale = d.fontScale.coerceIn(0.85f, 1.15f)
    CompositionLocalProvider(LocalDensity provides Density(d.density * k, fontScale), content = content)
}
