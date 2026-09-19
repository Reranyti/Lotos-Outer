package com.lotusblight.client;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Explicit per-biome color table for the infected ground tint (see InfectedGroundColor) -
 * hand-picked colors, not computed from grass color or a biome-name hash. Keyed by the biome's
 * own registry name (namespace:path), so entries for modded biomes (from whatever's actually in
 * the mods folder) work exactly the same way as vanilla ones - no special-casing needed per mod.
 *
 * A biome with no entry here falls back to NO_TINT (0xFFFFFF, i.e. the block's own baked texture
 * color untouched) rather than guessing at a color - silent/inert until it's actually filled in,
 * instead of picking a default that would need to be found and undone later.
 */
public final class BiomeInfectionColors {
    public static final int NO_TINT = 0xFFFFFF;

    private static final Map<ResourceLocation, Integer> COLORS = new HashMap<>();

    static {
        // put(new ResourceLocation("minecraft", "badlands"), 0xRRGGBB);
        // put(new ResourceLocation("modid", "some_biome"), 0xRRGGBB);
    }

    private BiomeInfectionColors() {}

    private static void put(ResourceLocation biomeId, int color) {
        COLORS.put(biomeId, color);
    }

    public static int colorFor(ResourceLocation biomeId) {
        return COLORS.getOrDefault(biomeId, NO_TINT);
    }
}
