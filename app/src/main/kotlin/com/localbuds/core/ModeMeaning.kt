package com.localbuds.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * What a listening mode *does*, independent of the slot number a headset keeps it in. Slot numbers
 * disagree between makers (slot 1 is Aware on a Bose, Off on the Sleep A30; slot 2 is Immersion on
 * a Bose, Off on the Crusher, Sennheiser and Sony), so anything saved (rules, the tile cycle) keeps
 * the meaning and resolves it to a slot on whatever headset is connected ([ModeResolver]).
 *
 * The stored names are pinned and lower-case; an unknown name (written by a newer build) reads
 * back as [CUSTOM] instead of making the whole rule unreadable.
 */
@Serializable(with = ModeMeaningSerializer::class)
enum class ModeMeaning(val storedName: String) {
    /** Noise cancelling at its strongest: Bose Quiet, Soundcore/Sennheiser/Sony noise cancelling. */
    QUIET("quiet"),
    /** Hear the world: Bose Aware, transparency, Sony ambient sound. */
    AWARE("aware"),
    /** Neither cancelling nor transparency: Crusher/Sennheiser/Sony Off, Soundcore Normal. */
    OFF("off"),
    /** Reserved for headsets that switch by themselves (AirPods, JBL); no driver produces it yet. */
    ADAPTIVE("adaptive"),
    /** Anything else (Bose Immersion, Cinema, Stereo and the user's own modes): matched by name. */
    CUSTOM("custom"),
    ;

    companion object {
        fun fromStoredName(name: String): ModeMeaning = entries.firstOrNull { it.storedName == name } ?: CUSTOM
    }
}

internal object ModeMeaningSerializer : KSerializer<ModeMeaning> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("com.localbuds.core.ModeMeaning", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ModeMeaning) = encoder.encodeString(value.storedName)
    override fun deserialize(decoder: Decoder): ModeMeaning = ModeMeaning.fromStoredName(decoder.decodeString())
}

/**
 * A saved reference to a listening mode: its [meaning], plus where it was picked so the same
 * headset gets exactly the same slot back.
 *
 * Persisted inside the automation rules and the tile cycle; every field after [meaning] is optional
 * so older and newer builds can read each other's JSON.
 */
@Serializable(with = ModeRefSerializer::class)
data class ModeRef(
    val meaning: ModeMeaning,
    /** For [ModeMeaning.CUSTOM] the match key (case-insensitive); otherwise only for display. */
    val name: String? = null,
    /** The headset it was picked on (upper-case Bluetooth address). */
    val address: String? = null,
    /** Its slot on [address]; trusted only on that headset. */
    val slot: Int? = null,
    /**
     * The bare slot number a 0.75 rule or tile entry stored, set only by the 0.8 migration: when the
     * meaning finds nothing on a headset, the slot is used as 0.75 did, so a migrated rule never does
     * less than before. A ref picked in 0.8 has none.
     */
    val legacySlot: Int? = null,
    /**
     * The stored meaning when a newer build wrote one this build doesn't know: [meaning] is then
     * [ModeMeaning.CUSTOM] for resolving, and this is written back unchanged, so the newer build
     * still finds its meaning after a downgrade and a rewrite of the rules.
     */
    val unknownMeaning: String? = null,
)

/** [ModeRef]'s stored shape, with the meaning as its raw name (see [ModeRef.unknownMeaning]). */
@Serializable
private class StoredModeRef(
    val meaning: String,
    val name: String? = null,
    val address: String? = null,
    val slot: Int? = null,
    val legacySlot: Int? = null,
)

internal object ModeRefSerializer : KSerializer<ModeRef> {
    override val descriptor: SerialDescriptor = StoredModeRef.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ModeRef) = encoder.encodeSerializableValue(
        StoredModeRef.serializer(),
        StoredModeRef(value.unknownMeaning ?: value.meaning.storedName, value.name, value.address, value.slot, value.legacySlot),
    )

    override fun deserialize(decoder: Decoder): ModeRef {
        val stored = decoder.decodeSerializableValue(StoredModeRef.serializer())
        val known = ModeMeaning.entries.firstOrNull { it.storedName == stored.meaning }
        return ModeRef(
            meaning = known ?: ModeMeaning.CUSTOM,
            name = stored.name,
            address = stored.address,
            slot = stored.slot,
            legacySlot = stored.legacySlot,
            unknownMeaning = stored.meaning.takeIf { known == null },
        )
    }
}

/**
 * Meaning ↔ slot, in core so no driver changes: `HeadphoneRepository.setMode(index)` stays as it is
 * and the slot is resolved just before it is called.
 */
object ModeResolver {
    /**
     * English names the frozen stacks give their built-in slots, for the stacks that don't set
     * [ListeningMode.meaning] yet: Bose (its preset names are the [31.6] prompt names, which the
     * firmware locks), the Bragi Crusher/Method (Quiet, Aware, Off) and the Sleep A30 (Noise
     * cancelling, Off). ModeResolverTest pins them: renaming one of these modes in its driver, or
     * localising the names, must first give that driver an explicit meaning.
     */
    private val QUIET_NAMES = setOf("Quiet", "Noise cancelling")
    private val AWARE_NAMES = setOf("Aware")
    private val OFF_NAMES = setOf("Off")

    /** [m]'s own meaning, else inferred from a built-in slot's name. A user's slot is always [ModeMeaning.CUSTOM]. */
    fun meaningOf(m: ListeningMode): ModeMeaning {
        m.meaning?.let { return it }
        // A custom slot is the user's, whatever it is called: a Bose custom mode named "Quiet" is not Quiet.
        if (!m.isPreset) return ModeMeaning.CUSTOM
        val name = m.name.trim()
        return when {
            QUIET_NAMES.any { it.equals(name, ignoreCase = true) } -> ModeMeaning.QUIET
            AWARE_NAMES.any { it.equals(name, ignoreCase = true) } -> ModeMeaning.AWARE
            OFF_NAMES.any { it.equals(name, ignoreCase = true) } -> ModeMeaning.OFF
            else -> ModeMeaning.CUSTOM
        }
    }

    /** The ref to save for [m] picked on the headset at [address]. */
    fun refFor(m: ListeningMode, address: String?): ModeRef = ModeRef(
        meaning = meaningOf(m),
        name = m.name.trim().ifEmpty { null },
        address = address?.uppercase(),
        slot = m.index,
    )

    /**
     * The ref for a bare 0.75 slot number: the mode in that slot on this headset, else a custom ref
     * that only knows the slot. Either way [ModeRef.legacySlot] keeps the number.
     */
    fun refForLegacySlot(slot: Int, modes: List<ListeningMode>, address: String?): ModeRef =
        modes.firstOrNull { it.index == slot && it.isConfigured }
            ?.let { refFor(it, address).copy(legacySlot = slot) }
            ?: ModeRef(ModeMeaning.CUSTOM, legacySlot = slot)

    fun slotFor(ref: ModeRef, state: HeadphoneState): Int? = slotFor(ref, state.modes, state.connection.deviceAddress)

    /**
     * The slot [ref] means on the headset at [address] with [modes], or null when it has no such
     * mode. Only configured modes count. In order:
     *  1. the headset it was picked on: the same slot, if it still has that meaning (for a custom
     *     mode any custom mode, so renaming it there keeps the rule working);
     *  2. a built-in meaning: the lowest slot with that meaning (Sony keeps its slot numbers when
     *     it lacks noise cancelling, so this is by index, never by list position);
     *  3. a migrated ref on any other headset: its 0.75 slot number. The migration only guessed
     *     which headset a 0.75 rule was written for (the first one connected), so on the others
     *     the slot does what 0.75 did, rather than a custom mode of the same name;
     *  4. a custom mode: the custom mode with that name, ignoring case;
     *  5. the 0.75 slot number on the headset it was bound on.
     * A 0.75 slot number never lands on a slot that means Off unless the ref means Off: a Bose
     * Immersion rule must not switch the noise control off on a Crusher, Sennheiser or Sony,
     * where slot 2 is Off (0.75 did exactly that).
     */
    fun slotFor(ref: ModeRef, modes: List<ListeningMode>, address: String?): Int? {
        val configured = modes.filter { it.isConfigured }
        val onItsHeadset = ref.address != null && address != null && ref.address.equals(address, ignoreCase = true)
        if (onItsHeadset && ref.slot != null) {
            configured.firstOrNull { it.index == ref.slot && meaningOf(it) == ref.meaning }?.let { return it.index }
        }
        if (ref.meaning != ModeMeaning.CUSTOM) {
            configured.filter { meaningOf(it) == ref.meaning }.minByOrNull { it.index }?.let { return it.index }
        }
        val legacy = ref.legacySlot
            ?.let { slot -> configured.firstOrNull { it.index == slot } }
            ?.takeIf { meaningOf(it) != ModeMeaning.OFF || ref.meaning == ModeMeaning.OFF }
        if (legacy != null && !onItsHeadset) return legacy.index
        if (ref.meaning == ModeMeaning.CUSTOM) {
            val name = ref.name?.trim()?.takeIf { it.isNotEmpty() }
            if (name != null) {
                configured.filter { meaningOf(it) == ModeMeaning.CUSTOM && it.name.trim().equals(name, ignoreCase = true) }
                    .minByOrNull { it.index }?.let { return it.index }
            }
        }
        return legacy?.index
    }

    /**
     * All configured mode slots on the connected earbuds/headphones in ascending slot order
     * (`Quiet -> Aware -> Immersion -> Custom 1 -> Custom 2 -> ...`).
     */
    fun defaultCycle(modes: List<ListeningMode>): List<Int> =
        modes.filter { it.isConfigured }.sortedBy { it.index }.map { it.index }.distinct()

    /** A name for [ref] without a headset: its saved name, else its meaning. */
    fun label(ref: ModeRef): String =
        ref.name?.trim()?.takeIf { it.isNotEmpty() } ?: when (ref.meaning) {
            ModeMeaning.QUIET -> "Noise cancelling"
            ModeMeaning.AWARE -> "Aware"
            ModeMeaning.OFF -> "Off"
            ModeMeaning.ADAPTIVE -> "Adaptive"
            ModeMeaning.CUSTOM -> (ref.slot ?: ref.legacySlot)?.let { "mode $it" } ?: "Custom mode"
        }

    /**
     * The name to show for a "set mode" action: the mode it resolves to on the headset at
     * [address] (so a renamed mode shows its new name), else the saved ref's [label]. Null for an
     * unmigrated 0.75 action whose slot has no name here; callers show the bare number then.
     */
    fun nameOf(action: AutomationAction.SetMode, modes: List<ListeningMode>, address: String?): String? {
        val ref = action.mode
        val slot = if (ref != null) slotFor(ref, modes, address) else action.modeIndex
        val live = slot?.let { s -> modes.firstOrNull { it.index == s }?.name?.trim()?.takeIf { it.isNotEmpty() } }
        return live ?: ref?.let(::label)
    }

    /**
     * The slot a meaning-only ref (picked with no headset connected) writes as the legacy
     * `modeIndex`, which only a downgraded 0.75 build reads: 0 and 1 are Quiet and Aware there,
     * and 2 is Off on most other makers' layouts.
     */
    fun legacyIndexFor(meaning: ModeMeaning): Int = when (meaning) {
        ModeMeaning.AWARE -> 1
        ModeMeaning.OFF -> 2
        else -> 0
    }

    /**
     * A slot number drawn on a surface (widget button, notification action) is only valid on the
     * headset it was drawn for. True when it was drawn for [connectedAddress], when it carries no
     * address (drawn by an older build), or when nothing is connected (the command then fails on
     * its own with the driver's message).
     */
    fun slotStillApplies(drawnFor: String?, connectedAddress: String?): Boolean =
        drawnFor == null || connectedAddress == null || drawnFor.equals(connectedAddress, ignoreCase = true)

    /** The meanings offered when no headset is connected to pick from. */
    val GENERIC_CHOICES: List<ModeMeaning> = listOf(ModeMeaning.QUIET, ModeMeaning.AWARE, ModeMeaning.OFF)

    /** The three core built-in Bose mode indices: Quiet (0), Aware (1), Immersion (2). */
    private val CORE_BUILT_IN_INDICES: Set<Int> = setOf(0, 1, 2)

    /**
     * Returns the core built-in mode cycle list strictly restricted to indices 0, 1, 2:
     * `Quiet (0) -> Aware (1) -> Immersion (2)`.
     * Never includes slot 3+ (`Cinema` or custom mode slots 4+) so left-earbud mode cycling
     * stops at Immersion (index 2) and never touches custom slots or triggers Spatial Audio
     * head-tracking balance drift on 1st Gen Bose QC Ultra Earbuds (`0x4072`).
     */
    fun orderedCycleModes(state: HeadphoneState): List<Int> =
        state.modes
            .filter {
                it.isConfigured &&
                    it.index in CORE_BUILT_IN_INDICES &&
                    !it.name.equals("Cinema", ignoreCase = true)
            }
            .sortedBy { it.index }
            .map { it.index }
            .distinct()

    /**
     * Resolves which mode slot in [pool] is currently active, even when firmware reports `0xFF`
     * (`NO_SAVED_MODE`) while in Immersion (`spatialAudio != OFF`) or Aware (`!ancEnabled`),
     * so cycling never gets stuck at Immersion.
     */
    fun resolveEffectiveModeIndex(
        state: HeadphoneState,
        pool: List<Int> = orderedCycleModes(state),
        fallbackIndex: Int? = null,
    ): Int? {
        if (pool.isEmpty()) return null
        val raw = state.currentModeIndex
        if (raw != null && raw != 0xFF && raw in pool) return raw
        if (fallbackIndex != null && fallbackIndex in pool) return fallbackIndex

        val noise = state.noise
        if (noise != null) {
            val matchedSlot = state.modes.firstOrNull { it.isConfigured && it.index in pool && it.settings == noise }?.index
            if (matchedSlot != null) return matchedSlot

            val immersionIdx = state.modes.firstOrNull { it.name.equals("Immersion", ignoreCase = true) }?.index
            val awareIdx = state.modes.firstOrNull { it.name.equals("Aware", ignoreCase = true) }?.index
            val quietIdx = state.modes.firstOrNull { it.name.equals("Quiet", ignoreCase = true) }?.index

            if (noise.spatialAudio != SpatialAudio.OFF && immersionIdx != null && immersionIdx in pool) {
                return immersionIdx
            }
            if ((!noise.ancEnabled || noise.cancellingLevel <= 2) && awareIdx != null && awareIdx in pool) {
                return awareIdx
            }
            if (quietIdx != null && quietIdx in pool) {
                return quietIdx
            }
        }
        return null
    }

    /**
     * Which mode a left-earbud "cycle" hold should go to: restricted strictly to the three core
     * built-in modes `Quiet (0) -> Aware (1) -> Immersion (2)` (and wrapping back to `Quiet (0)`
     * without ever advancing past Immersion into custom mode slots 3/4+).
     */
    fun nextModeIndex(state: HeadphoneState): Int? {
        val pool = orderedCycleModes(state)
        if (pool.isEmpty()) return null
        val current = resolveEffectiveModeIndex(state, pool)
        val at = if (current != null) pool.indexOf(current) else -1
        return pool[(at + 1) % pool.size]
    }
}
