package com.localbuds.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localbuds.core.fake.FakeHeadphoneRepository
import com.localbuds.ui.common.BannerTone
import com.localbuds.ui.common.EarshotPreview
import com.localbuds.ui.common.InfoBanner
import com.localbuds.ui.common.LightDarkPreview

/**
 * [com.localbuds.core.HeadphoneCapabilities.supportNote] at the top of Home: for a basic or
 * unsupported product it explains up front why some controls are missing, so their absence
 * reads as deliberate rather than broken.
 */
@Composable
fun SupportNoteCard(note: String, modifier: Modifier = Modifier) {
    InfoBanner(text = note, tone = BannerTone.INFO, icon = Icons.Outlined.Info, modifier = modifier)
}

@LightDarkPreview
@Composable
private fun SupportNotePreview() {
    EarshotPreview {
        SupportNoteCard(FakeHeadphoneRepository.basicTierState().capabilities.supportNote.orEmpty(), Modifier.padding(16.dp))
    }
}
