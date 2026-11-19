package com.kouwis.alzkr.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role

/** Short, purposeful vibrations — used only where a physical "click" helps. */
enum class Haptic { None, Tap, Tick, Toggle, Confirm, Delete }

fun View.haptic(kind: Haptic) {
    val c = when (kind) {
        Haptic.None -> return
        Haptic.Tap -> HapticFeedbackConstants.VIRTUAL_KEY
        Haptic.Tick -> HapticFeedbackConstants.CLOCK_TICK
        Haptic.Toggle -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.VIRTUAL_KEY
        Haptic.Confirm -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
        Haptic.Delete -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
    }
    performHapticFeedback(c)
}

/**
 * clickable + a soft "press in" (the element shrinks a little while pressed and springs back)
 * + an optional haptic tick when it fires.
 */
fun Modifier.springClick(
    role: Role = Role.Button,
    label: String? = null,
    haptic: Haptic = Haptic.Tap,
    pressedScale: Float = 0.92f,
    onClick: () -> Unit,
): Modifier = composed {
    val view = LocalView.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = LocalIndication.current, role = role, onClickLabel = label) {
            view.haptic(haptic)
            onClick()
        }
}

/** A little "pop" (grow then settle) every time [trigger] changes — for icons that flip state. */
@Composable
fun popScale(trigger: Any?): Float {
    val a = remember { Animatable(1f) }
    var first by remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(trigger) {
        if (first) { first = false; return@LaunchedEffect }
        a.snapTo(0.7f)
        a.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
    }
    return a.value
}
