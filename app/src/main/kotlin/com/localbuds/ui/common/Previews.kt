package com.localbuds.ui.common

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.localbuds.core.Appearance
import com.localbuds.ui.LocalUiDependencies
import com.localbuds.ui.UiDependencies
import com.localbuds.ui.fakeUiDependencies
import com.localbuds.ui.theme.LocalBudsTheme
import com.localbuds.ui.theme.earshotBackdrop

/** Light + dark phone previews in one annotation. */
@Preview(name = "Light", showBackground = true, heightDp = 860)
@Preview(name = "Dark", showBackground = true, heightDp = 860, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class LightDarkPreview

/**
 * Theme + fake dependencies for `@Preview`s. Field unless a preview asks for another look, never
 * the wallpaper palette, so previews are stable.
 */
@Composable
fun EarshotPreview(
    dependencies: UiDependencies = fakeUiDependencies(),
    appearance: Appearance = Appearance.FROSTED_GLASS,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalUiDependencies provides dependencies) {
        LocalBudsTheme(appearance = appearance) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .earshotBackdrop(),
            ) { content() }
        }
    }
}
