package com.kouwis.alzkr.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouwis.alzkr.R
import com.kouwis.alzkr.data.AppFonts
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private val Gold = Color(0xFFF1D08A)

/**
 * The opening: a night sky whose stars light up one by one, the logo springing in and floating,
 * the name «الذِّكر», then ﴿إِنَّ لِلْمُتَّقِينَ مَفَازًا﴾ in the Quran's script. A tap skips it.
 */
@Composable
fun Splash(onDone: () -> Unit) {
    val stars = remember {
        val r = Random(7)
        List(70) { Triple(Offset(r.nextFloat(), r.nextFloat()), 0.6f + r.nextFloat() * 1.6f, r.nextFloat()) }
    }
    val sky = remember { Animatable(0f) }      // stars appearing
    val logo = remember { Animatable(0f) }
    val name = remember { Animatable(0f) }
    val verse = remember { Animatable(0f) }
    val out = remember { Animatable(1f) }
    val twinkle = rememberInfiniteTransition(label = "tw")
    val phase by twinkle.animateFloat(0f, 1f, infiniteRepeatable(tween(2400), RepeatMode.Restart), label = "phase")
    val bob by twinkle.animateFloat(-1f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")

    LaunchedEffect(Unit) {
        launch { sky.animateTo(1f, tween(1400)) }
        launch { logo.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 180f)) }
        kotlinx.coroutines.delay(500)
        launch { name.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        kotlinx.coroutines.delay(550)
        verse.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
        kotlinx.coroutines.delay(900)
        out.animateTo(0f, tween(450))
        onDone()
    }

    Box(
        Modifier.fillMaxSize().alpha(out.value)
            .background(Brush.verticalGradient(listOf(Color(0xFF0A1E33), Color(0xFF123A5E), Color(0xFF0B2236))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            stars.forEachIndexed { i, (p, r, seed) ->
                // each star lights in its turn, then twinkles
                val shown = ((sky.value * 1.4f - seed * 0.9f) * 3f).coerceIn(0f, 1f)
                val tw = 0.55f + 0.45f * sin(2 * PI * (phase + seed)).toFloat()
                val a = shown * tw
                if (a > 0.01f) {
                    val c = Offset(p.x * size.width, p.y * size.height)
                    drawCircle(Color.White.copy(alpha = a * 0.25f), r * 3.2f * density, c)
                    drawCircle((if (i % 5 == 0) Gold else Color.White).copy(alpha = a), r * density, c)
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(contentAlignment = Alignment.Center) {
                // a soft gold glow behind the logo
                Canvas(Modifier.size(220.dp).alpha(logo.value.coerceIn(0f, 1f))) {
                    drawCircle(Brush.radialGradient(listOf(Gold.copy(alpha = 0.28f), Color.Transparent)), size.minDimension / 2)
                }
                Image(
                    painterResource(R.drawable.ic_launcher_fg), null,
                    Modifier.size(200.dp).graphicsLayer { translationY = bob * 6.dp.toPx() }
                        .scale(0.4f + 0.6f * logo.value).alpha(logo.value.coerceIn(0f, 1f)),
                )
            }
            Text(
                "الذِّكر", color = Gold, fontSize = 54.sp, fontFamily = AppFonts.naskhBold, lineHeight = 80.sp,
                modifier = Modifier.alpha(name.value).graphicsLayer { translationY = (1f - name.value) * 24.dp.toPx() },
            )
            Text(
                // the verse in the Quran's script; its ornate brackets from the naskh font (the Quran font has none)
                androidx.compose.ui.text.buildAnnotatedString {
                    val br = androidx.compose.ui.text.SpanStyle(fontFamily = AppFonts.naskh, color = Gold)
                    withStyle(br) { append("\uFD3E ") }
                    append("إِنَّ لِلْمُتَّقِينَ مَفَازًا")
                    withStyle(br) { append(" \uFD3F") }
                }, color = Color.White.copy(alpha = 0.92f), fontSize = 26.sp,
                fontFamily = AppFonts.hafs, textAlign = TextAlign.Center, lineHeight = 48.sp,
                modifier = Modifier.padding(horizontal = 24.dp).alpha(verse.value).graphicsLayer { translationY = (1f - verse.value) * 18.dp.toPx() },
            )
        }
    }
}
