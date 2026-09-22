package com.lotusblight.registry;

import com.lotusblight.LotusBlight;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, LotusBlight.MODID);

    public static final RegistryObject<SoundEvent> INNER_VOICE_THEME = SOUNDS.register("inner_voice_theme",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(LotusBlight.MODID, "inner_voice_theme")));

    /** Plays once, right as the World Lotus's lecture scene opens (see the traitor-branch flow). */
    public static final RegistryObject<SoundEvent> WORLD_LOTUS_LECTURE = SOUNDS.register("world_lotus_lecture",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(LotusBlight.MODID, "world_lotus_lecture")));

    /** The traitor-branch boss fight's own theme. */
    public static final RegistryObject<SoundEvent> TRAITOR_BOSS_THEME = SOUNDS.register("traitor_boss_theme",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(LotusBlight.MODID, "traitor_boss_theme")));

    /** "Побег от лотоса" - 2:14 total, the 1:50 mark is the escape deadline itself (LotusChaseEvent.DURATION_TICKS), the rest plays out as an outro on a successful escape. */
    public static final RegistryObject<SoundEvent> LOTUS_CHASE_THEME = SOUNDS.register("lotus_chase_theme",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(LotusBlight.MODID, "lotus_chase_theme")));

    private ModSounds() {}
}
