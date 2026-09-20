package com.lotusblight.registry;

import com.lotusblight.LotusBlight;
import com.lotusblight.worldgen.feature.BlessingSandPillarFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, LotusBlight.MODID);

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> BLESSING_SAND_PILLAR =
            FEATURES.register("blessing_sand_pillar", () -> new BlessingSandPillarFeature(NoneFeatureConfiguration.CODEC));

    private ModFeatures() {}
}
