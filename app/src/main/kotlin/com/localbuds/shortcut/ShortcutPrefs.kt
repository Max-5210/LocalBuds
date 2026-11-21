package com.localbuds.shortcut

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.shortcutDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "headset_shortcuts",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Controls screen settings: default action, per-headset choices, spoken feedback, whether a real
 * headset press has reached Earshot yet, detected shortcuts and the other Bose buttons' modes.
 */
class ShortcutPrefs(context: Context) {
    private val store = context.applicationContext.shortcutDataStore

    val settings: Flow<ShortcutSettings> = store.data
        .catch { e ->
            if (e is IOException) { Log.w(TAG, "shortcut store unreadable, using defaults", e); emit(emptyPreferences()) } else throw e
        }
        .map(::decode)

    suspend fun current(): ShortcutSettings = settings.first()

    suspend fun update(transform: (ShortcutSettings) -> ShortcutSettings) {
        store.edit { p ->
            val after = transform(decode(p))
            p[DEFAULT_ACTION] = ShortcutCodec.encodeAction(after.defaultAction)
            p[OVERRIDES] = ShortcutCodec.encodeOverrides(after.overrides)
            p[SPOKEN_FEEDBACK] = after.spokenFeedback
            p[SEEN_HEADSET_PRESS] = after.seenHeadsetPress
            p[DETECTED] = ShortcutExtrasCodec.encodeDetected(after.detected)
            p[BUTTON_CHOICES] = ShortcutExtrasCodec.encodeButtonChoices(after.buttonChoices)
            p[SECONDARY_ACTIONS] = ShortcutExtrasCodec.encodeSecondaryActions(after.secondaryActions)
        }
    }

    /** "Detect a button" saw [address]'s assistant gesture at [at]. */
    suspend fun markShortcutDetected(address: String, at: Long) = update { it.withDetected(address, at) }

    /** Records that a real headset press reached the trigger (once; later calls don't write). */
    suspend fun markHeadsetPressSeen() {
        store.edit { p -> if (p[SEEN_HEADSET_PRESS] != true) p[SEEN_HEADSET_PRESS] = true }
    }

    private fun decode(p: Preferences): ShortcutSettings =
        ShortcutCodec.decodeSettings(
            p[DEFAULT_ACTION],
            p[OVERRIDES],
            p[SPOKEN_FEEDBACK],
            p[SEEN_HEADSET_PRESS],
            p[DETECTED],
            p[BUTTON_CHOICES],
            p[SECONDARY_ACTIONS],
        )

    private companion object {
        const val TAG = "LocalBuds/Shortcut"
        val DEFAULT_ACTION = stringPreferencesKey("default_action")
        val OVERRIDES = stringPreferencesKey("overrides")
        val SPOKEN_FEEDBACK = booleanPreferencesKey("spoken_feedback")
        val SEEN_HEADSET_PRESS = booleanPreferencesKey("seen_headset_press")
        val DETECTED = stringPreferencesKey("detected_shortcuts")
        val BUTTON_CHOICES = stringPreferencesKey("button_choices")
        val SECONDARY_ACTIONS = stringPreferencesKey("secondary_actions")
    }
}
