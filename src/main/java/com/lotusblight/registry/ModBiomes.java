package com.lotusblight.registry;

import com.lotusblight.LotusBlight;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

public final class ModBiomes {
    public static final ResourceKey<Biome> LOTUS_BIOME = ResourceKey.create(
            Registries.BIOME, new ResourceLocation(LotusBlight.MODID, "lotus_marsh"));
    public static final ResourceKey<Biome> BLESSING_BIOME = ResourceKey.create(
            Registries.BIOME, new ResourceLocation(LotusBlight.MODID, "blessing_tundra"));

    private ModBiomes() {}
}
