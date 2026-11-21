package com.localbuds.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.localbuds.ui.theme.LocalBudsTheme

/**
 * Determinate progress drawn like [FieldSlider]'s track, without Material 3's gap and end dot:
 * flat and square-ended in Field (olive on sand), rounded in Aurora. [progress] is read in the
 * draw phase, as Material's is.
 */
@Composable
fun FieldProgressBar(progress: () -> Float, modifier: Modifier = Modifier) {
    val square = LocalBudsTheme.style.squareControls
    LinearProgressIndicator(
        progress = progress,
        modifier = modifier.fillMaxWidth().height(if (square) 4.dp else 6.dp),
        color = LocalBudsTheme.colors.fill,
        trackColor = LocalBudsTheme.colors.track,
        strokeCap = if (square) StrokeCap.Butt else StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}
