package com.localbuds.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.localbuds.ui.common.rememberHaptics
import com.localbuds.ui.theme.LocalBudsTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Continuous draggable sliding pill selector with an animated spring indicator,
 * equal-weighted compartments (`Modifier.weight(1f)`), and crisp tactile haptics
 * on every compartment crossing, tap, and snap.
 */
@Composable
fun <T> SlidingPillSelector(
    items: List<T>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    cornerRadius: Dp = 18.dp,
    itemContent: @Composable (item: T, selectedFraction: Float) -> Unit,
) {
    if (items.isEmpty()) return

    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val outerShape = RoundedCornerShape(cornerRadius)
    val trackPadding = 4.dp
    val pillShape = RoundedCornerShape((cornerRadius - trackPadding).coerceAtLeast(8.dp))

    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentEnabled by rememberUpdatedState(enabled)
    val hasSelection = selectedIndex in items.indices
    val safeSelected = if (hasSelection) selectedIndex else 0

    var dragPos by remember { mutableStateOf<Float?>(null) }
    var lastHapticSlot by remember { mutableIntStateOf(safeSelected) }

    val showPill = hasSelection || dragPos != null
    val targetPos = dragPos ?: safeSelected.toFloat()
    val animatedPos by animateFloatAsState(
        targetValue = targetPos,
        animationSpec = if (dragPos != null) {
            snap()
        } else {
            spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "sliding_pill_pos",
    )

    val trackBg = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
    val colors = LocalBudsTheme.colors

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(outerShape)
            .background(trackBg)
            .border(1.dp, colors.hairline, outerShape),
    ) {
        val innerWidth = (maxWidth - trackPadding * 2).coerceAtLeast(1.dp)
        val slotWidth = innerWidth / items.size
        val slotWidthPx = with(density) { slotWidth.toPx() }.coerceAtLeast(1f)
        val padPx = with(density) { trackPadding.toPx() }
        val maxIndexFloat = items.lastIndex.toFloat()

        fun positionToContinuousSlot(xPx: Float): Float =
            ((xPx - padPx) / slotWidthPx - 0.5f).coerceIn(0f, maxIndexFloat)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(items.size) {
                    detectTapGestures { offset ->
                        if (!currentEnabled) return@detectTapGestures
                        val tapped = ((offset.x - padPx) / slotWidthPx).toInt().coerceIn(0, items.lastIndex)
                        haptics.click()
                        currentOnSelect(tapped)
                    }
                }
                .pointerInput(items.size) {
                    detectHorizontalDragGestures(
                        onDragStart = { startOffset ->
                            if (!currentEnabled) return@detectHorizontalDragGestures
                            val pos = positionToContinuousSlot(startOffset.x)
                            dragPos = pos
                            val slot = pos.roundToInt().coerceIn(0, items.lastIndex)
                            lastHapticSlot = slot
                            haptics.tick()
                        },
                        onHorizontalDrag = { change, _ ->
                            if (!currentEnabled) return@detectHorizontalDragGestures
                            change.consume()
                            val pos = positionToContinuousSlot(change.position.x)
                            dragPos = pos
                            val slot = pos.roundToInt().coerceIn(0, items.lastIndex)
                            if (slot != lastHapticSlot) {
                                lastHapticSlot = slot
                                haptics.tick()
                            }
                        },
                        onDragEnd = {
                            val pos = dragPos
                            dragPos = null
                            if (currentEnabled && pos != null) {
                                val target = pos.roundToInt().coerceIn(0, items.lastIndex)
                                haptics.confirm()
                                currentOnSelect(target)
                            }
                        },
                        onDragCancel = {
                            dragPos = null
                        },
                    )
                }
                .padding(trackPadding),
        ) {
            // Sliding illuminated active pill indicator (hidden when custom mode outside primary options is active)
            if (showPill) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset((animatedPos * slotWidthPx).roundToInt(), 0) }
                        .width(slotWidth)
                        .fillMaxHeight()
                        .clip(pillShape)
                        .background(colors.accentBrush)
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.08f))
                            ),
                            shape = pillShape,
                        ),
                )
            }

            // Strictly equal-weighted compartments
            Row(modifier = Modifier.fillMaxSize()) {
                items.forEachIndexed { index, item ->
                    val fraction = if (showPill) (1f - abs(animatedPos - index)).coerceIn(0f, 1f) else 0f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        itemContent(item, fraction)
                    }
                }
            }
        }
    }
}
