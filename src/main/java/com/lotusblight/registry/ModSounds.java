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

    private ModSounds() {}
}
