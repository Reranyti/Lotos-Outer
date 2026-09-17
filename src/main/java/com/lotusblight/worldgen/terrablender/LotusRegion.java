package com.lotusblight.worldgen.terrablender;

import com.lotusblight.registry.ModBiomes;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import terrablender.api.ParameterUtils.Continentalness;
import terrablender.api.ParameterUtils.Depth;
import terrablender.api.ParameterUtils.Erosion;
import terrablender.api.ParameterUtils.Humidity;
import terrablender.api.ParameterUtils.ParameterPointListBuilder;
import terrablender.api.ParameterUtils.Temperature;
import terrablender.api.ParameterUtils.Weirdness;
import terrablender.api.Region;
import terrablender.api.RegionType;
import terrablender.api.VanillaParameterOverlayBuilder;

import java.util.function.Consumer;

/**
 * Only ever loaded/registered if TerraBlender is actually present (soft
 * dependency, see LotusBlight's ModList.isLoaded("terrablender") guard) —
 * without it, both biomes still work via the plain BiomeManager fallback,
 * just with less controlled placement/compatibility with other worldgen mods.
 *
 * lotus_marsh: warm, humid, low-lying — near water, matching its design as a
 * river/lowland outbreak biome. blessing_tundra: cold, dry, further inland —
 * matching its "almost empty cold plains" description in BIOMES_DESIGN.md.
 */
public class LotusRegion extends Region {

    public LotusRegion(ResourceLocation name, int weight) {
        super(name, RegionType.OVERWORLD, weight);
    }

    @Override
    public void addBiomes(Registry<Biome> registry, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        VanillaParameterOverlayBuilder builder = new VanillaParameterOverlayBuilder();

        new ParameterPointListBuilder()
                .temperature(Temperature.span(Temperature.WARM, Temperature.HOT))
                .humidity(Humidity.span(Humidity.WET, Humidity.HUMID))
                .continentalness(Continentalness.span(Continentalness.COAST, Continentalness.NEAR_INLAND))
                .erosion(Erosion.span(Erosion.EROSION_0, Erosion.EROSION_2))
                .depth(Depth.SURFACE)
                .weirdness(Weirdness.FULL_RANGE)
                .build().forEach(point -> builder.add(point, ModBiomes.LOTUS_BIOME));

        new ParameterPointListBuilder()
                .temperature(Temperature.span(Temperature.ICY, Temperature.FROZEN))
                .humidity(Humidity.span(Humidity.ARID, Humidity.DRY))
                .continentalness(Continentalness.span(Continentalness.MID_INLAND, Continentalness.FAR_INLAND))
                .erosion(Erosion.span(Erosion.EROSION_0, Erosion.EROSION_2))
                .depth(Depth.SURFACE)
                .weirdness(Weirdness.FULL_RANGE)
                .build().forEach(point -> builder.add(point, ModBiomes.BLESSING_BIOME));

        builder.build().forEach(mapper::accept);
    }
}
