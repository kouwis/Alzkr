package com.kouwis.alzkr.ui

import com.kouwis.alzkr.data.t
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RoundIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = LocalColors.current.text,
    size: Dp = 44.dp,
    haptic: Haptic = Haptic.Tap,
) {
    val c = LocalColors.current
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(c.card)
            .border(1.dp, c.border, CircleShape)
            .springClick(label = label, haptic = haptic, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val pop = popScale(icon)
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp).graphicsLayer { scaleX = pop; scaleY = pop })
    }
}

@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = LocalColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = c.card,
        border = BorderStroke(1.dp, c.border),
        content = content,
    )
}

/** Segmented control: equal-width options inside a soft track. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, height: Dp = 40.dp) {
    val c = LocalColors.current
    // one white pill that glides to the chosen tab (no flashing), text colours cross-fade
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.chip)
            .padding(4.dp)
            .height(height),
    ) {
        val segW = maxWidth / options.size
        val x by androidx.compose.animation.core.animateDpAsState(
            segW * selected,
            androidx.compose.animation.core.tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            label = "pill",
        )
        Box(
            Modifier
                .offset(x = x)
                .width(segW)
                .fillMaxHeight()
                .shadow(1.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(c.card),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                val col by androidx.compose.animation.animateColorAsState(
                    if (on) c.text else c.muted, androidx.compose.animation.core.tween(260), label = "segText",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .springClick(role = Role.Tab, haptic = Haptic.Tick, pressedScale = 0.96f, ripple = false) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = col,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** The diamond number badge used in lists. */
@Composable
fun NumberBadge(n: String) {
    val c = LocalColors.current
    Box(
        Modifier
            .size(34.dp)
            .rotate(45f)
            .clip(RoundedCornerShape(9.dp))
            .background(c.primarySoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(n, color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.rotate(-45f))
    }
}

@Composable
fun soon(): (String) -> Unit {
    val ctx = LocalContext.current
    return { what -> Toast.makeText(ctx, t("{0} — قريبًا إن شاء الله", what), Toast.LENGTH_SHORT).show() }
}

@Composable
fun RowScope.Spacer1() = androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))

/** Swipe a card left or right to delete it (favourites, bookmarks). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val c = LocalColors.current
    val view = androidx.compose.ui.platform.LocalView.current
    val state = androidx.compose.material3.rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v != androidx.compose.material3.SwipeToDismissBoxValue.Settled) {
                view.haptic(Haptic.Delete)
                onDelete(); true
            } else false
        },
    )
    // a light tick the moment the swipe passes the "delete" point (and back)
    val armed = state.targetValue != androidx.compose.material3.SwipeToDismissBoxValue.Settled
    androidx.compose.runtime.LaunchedEffect(armed) { if (armed) view.haptic(Haptic.Tick) }
    androidx.compose.material3.SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val toStart = state.dismissDirection == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.heart.copy(alpha = 0.14f))
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toStart) androidx.compose.foundation.layout.Arrangement.Start else androidx.compose.foundation.layout.Arrangement.End,
            ) {
                Icon(Ic.trash, t("حذف"), tint = c.heart, modifier = Modifier.size(22.dp))
                Text(t("  حذف"), color = c.heart, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        },
    ) { content() }
}
