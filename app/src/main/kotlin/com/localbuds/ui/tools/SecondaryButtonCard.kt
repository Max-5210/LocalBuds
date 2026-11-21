package com.localbuds.ui.tools

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.localbuds.R
import com.localbuds.core.ButtonAction
import com.localbuds.core.ButtonAssignment
import com.localbuds.shortcut.ShortcutAction
import com.localbuds.shortcut.ShortcutButtonPolicy
import com.localbuds.ui.common.SectionCard

/**
 * Secondary Bose `[1.9]` button card (e.g. Right Earbud (Shortcut 2)):
 * offers Phone Assistant, Cycle EQ Preset (Experimental), Launch an app (Experimental),
 * Immersive Audio, and Off — independently configuring Right Earbud without "Cycle through modes"
 * or "Voice memo".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SecondaryButtonCard(
    button: ButtonAssignment,
    index: Int,
    selectedAction: ShortcutAction,
    earbuds: Boolean,
    onSelectAction: (ShortcutAction) -> Unit,
    onPickApp: () -> Unit,
    onPickAssistant: () -> Unit,
) {
    val currentChoice = selectedAction.choice().let {
        if (it == ShortcutChoice.CYCLE_MODES) ShortcutChoice.PHONE_ASSISTANT else it
    }
    val nativeOptions = ShortcutButtonPolicy.nativeOptions(button.supportedActions)
        .filter { it != ButtonAction.MODES_CAROUSEL && it != ButtonAction.ANC_CYCLE }
    val supportsAppActions = ButtonAction.CLIENT_INTERACTION in button.supportedActions ||
        ButtonAction.VOICE_ASSISTANT in button.supportedActions ||
        button.supportedActions.isEmpty()

    val options: List<ShortcutChoice> = if (supportsAppActions) {
        ShortcutChoice.secondaryOptions(nativeOptions, currentChoice)
    } else {
        buildList {
            addAll(nativeOptions.mapNotNull(ShortcutChoice::forNative).filter { it != ShortcutChoice.CYCLE_MODES })
            if (currentChoice != ShortcutChoice.CYCLE_MODES && currentChoice !in this) add(currentChoice)
        }
    }

    var expanded by remember { mutableStateOf(false) }
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                buttonTitle(button, index),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (currentChoice.isExperimental) {
                ExperimentalShortcutBadge(Modifier.padding(start = 6.dp))
            }
        }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = choiceLabel(currentChoice, earbuds),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                enabled = options.size > 1,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { i, option ->
                    if (option.isNative && (i == 0 || !options[i - 1].isNative)) HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(choiceLabel(option, earbuds)) },
                        onClick = {
                            expanded = false
                            when (option) {
                                ShortcutChoice.CYCLE_MODES -> onSelectAction(ShortcutAction.CycleModes)
                                ShortcutChoice.PHONE_ASSISTANT -> {
                                    onSelectAction(selectedAction as? ShortcutAction.OpenAssistant ?: ShortcutAction.OpenAssistant())
                                }
                                ShortcutChoice.CYCLE_EQ -> onSelectAction(ShortcutAction.CycleEq)
                                ShortcutChoice.LAUNCH_APP -> onPickApp()
                                ShortcutChoice.IMMERSIVE_AUDIO -> onSelectAction(ShortcutAction.HeadphoneButton(ButtonAction.SPATIAL_AUDIO.storedId))
                                ShortcutChoice.OFF -> onSelectAction(ShortcutAction.HeadphoneButton(ButtonAction.DISABLED.storedId))
                            }
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
        if (currentChoice.isExperimental) {
            Text(
                text = stringResource(R.string.shortcut_experimental_interception_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp),
            )
        }
        when (selectedAction) {
            is ShortcutAction.LaunchApp -> AppRowSummary(selectedAction, onChange = onPickApp)
            is ShortcutAction.OpenAssistant -> {
                val label = selectedAction.label ?: stringResource(R.string.shortcut_assistant_auto)
                AssistantRowSummary(label, onClick = onPickAssistant)
            }
            else -> Unit
        }
        Text(
            stringResource(R.string.shortcut_secondary_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )
    }
}

@Composable
private fun AppRowSummary(action: ShortcutAction.LaunchApp, onChange: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            action.label ?: action.packageName ?: stringResource(R.string.shortcut_no_app_chosen),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.TextButton(onClick = onChange) {
            Text(stringResource(R.string.shortcut_change))
        }
    }
}

@Composable
private fun AssistantRowSummary(label: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text(stringResource(R.string.shortcut_assistant_row, label))
    }
}
