package com.localbuds.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.localbuds.R

/**
 * Pure, untouched lossless bitmap PNG artwork for the Left ([R.drawable.earbud_left]) and Right
 * ([R.drawable.earbud_right]) Bose QuietComfort Ultra Earbuds, cropped directly from
 * `app/custom_icons/changeearbudicon.png` with zero vectorization or Canvas drawing overlays.
 */
@Composable
fun BoseUltraEarbudGraphic(
    isRight: Boolean,
    modifier: Modifier = Modifier.size(52.dp),
    @Suppress("UNUSED_PARAMETER") accentColor: Color = Color(0xFF38BDF8),
    @Suppress("UNUSED_PARAMETER") isCharging: Boolean = false,
) {
    Image(
        painter = painterResource(if (isRight) R.drawable.earbud_right else R.drawable.earbud_left),
        contentDescription = if (isRight) "Right earbud" else "Left earbud",
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}
