package com.kouwis.alzkr.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The app's on/off switch: the knob springs across with a small overshoot, stretches while pressed,
 * and the track fades between the two colours. Follows the reading direction (on = the end side).
 */
@Composable
fun AnimatedToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val trackW = 54.dp; val trackH = 32.dp; val knob = 24.dp; val pad = 4.dp
    val knobW by animateDpAsState(if (pressed) knob + 6.dp else knob, spring(stiffness = Spring.StiffnessMedium), label = "knobW")
    val x by animateDpAsState(
        if (checked) trackW - pad - knobW else pad,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), label = "knobX",
    )
    val track by animateColorAsState(if (checked) c.gold else c.chip, tween(220), label = "track")
    val edge by animateColorAsState(if (checked) c.gold else c.border, tween(220), label = "edge")
    val knobColor by animateColorAsState(if (checked) Color.White else c.muted.copy(alpha = 0.85f), tween(220), label = "knob")
    val pop by animateFloatAsState(if (checked) 1f else 0.92f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium), label = "pop")
    Box(
        modifier.width(trackW).height(trackH).background(track, CircleShape).border(1.dp, edge, CircleShape)
            .toggleable(value = checked, interactionSource = source, indication = null, role = Role.Switch) { v ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(v)
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.offset(x = x).width(knobW).height(knob).scale(pop)
                .shadow(if (checked) 3.dp else 1.dp, CircleShape).background(knobColor, CircleShape),
        )
    }
}

/** Settings that depend on a switch: they slide open under it when it is on, and fold away when it is off. */
@Composable
fun Reveal(visible: Boolean, content: @Composable () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.expandVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) +
            androidx.compose.animation.fadeIn(tween(220, delayMillis = 60)),
        exit = androidx.compose.animation.shrinkVertically(tween(220)) + androidx.compose.animation.fadeOut(tween(150)),
    ) { androidx.compose.foundation.layout.Column { content() } }
}
