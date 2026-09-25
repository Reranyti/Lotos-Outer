package com.lotusblight.client;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-biome fog color gradient for InfectionFogRenderer, dictated biome-by-biome rather than
 * computed - each biome gets its own list of 1+ colors describing what the "sick" fog looks like
 * as density ramps from 0 (clean air) to 1 (deep in an outbreak). A single color just fades in to
 * that one tone; 2+ colors interpolate across evenly spaced stops as density increases, so a
 * biome can have as many stops as its own description actually needs (e.g. Old Growth Birch
 * Forest's three colors) instead of being forced into a fixed pair.
 */
public final class BiomeFogColors {
    private static final Map<ResourceLocation, int[]> GRADIENTS = new HashMap<>();

    static {
        put("minecraft:plains", 0x3F6B33, 0x0C140C);
        put("minecraft:sunflower_plains", 0xC9B93A, 0x3F6B33);
        put("minecraft:forest", 0x3F6B33, 0x5C4224);
        put("minecraft:flower_forest", 0xD46FC9, 0x3F6B33);
        put("minecraft:birch_forest", 0xD8E0C8, 0x3F6B33);
        put("minecraft:old_growth_birch_forest", 0xD8E0C8, 0x3F6B33, 0x5C4224);
        put("minecraft:dark_forest", 0x1C2E17, 0x0A120A);
        put("minecraft:meadow", 0x9ACD32);
        put("minecraft:taiga", 0xE8EDEB, 0x5C4224);
        put("minecraft:snowy_taiga", 0xE8EDEB);
        put("minecraft:old_growth_pine_taiga", 0xE8EDEB);
        put("minecraft:old_growth_spruce_taiga", 0xE8EDEB);
        put("minecraft:snowy_plains", 0xE8EDEB);
        put("minecraft:ice_spikes", 0xE8EDEB, 0x3A6FC9);
        put("minecraft:grove", 0xE8EDEB);
        put("minecraft:snowy_slopes", 0xE8EDEB);
        put("minecraft:frozen_peaks", 0xE8EDEB, 0x6FC9D4);
        put("minecraft:jagged_peaks", 0xE8EDEB, 0x6FC9D4, 0xC93A3A);
        put("minecraft:stony_peaks", 0xE8EDEB, 0x8C8C8C);
        put("minecraft:snowy_beach", 0xE8EDEB, 0xC9B93A);
        put("minecraft:frozen_river", 0xE8EDEB, 0x3A6FC9);
        put("minecraft:desert", 0xD4B843);
        put("minecraft:savanna", 0xD4823A);
        put("minecraft:savanna_plateau", 0xD4823A, 0xE8EDEB);
        put("minecraft:badlands", 0x3A2418);
        put("minecraft:eroded_badlands", 0x2E1C14, 0x120A08);
        put("minecraft:wooded_badlands", 0x3A2418);
        put("minecraft:windswept_hills", 0xE8EDEB, 0x6FC9D4);
        put("minecraft:windswept_gravelly_hills", 0xE8EDEB, 0x8C8C8C);
        put("minecraft:windswept_forest", 0xE8EDEB, 0x5C4224);
        put("minecraft:windswept_savanna", 0xE8EDEB, 0xD4823A);
        put("minecraft:jungle", 0x3F6B33);
        put("minecraft:sparse_jungle", 0x3F6B33, 0xE8EDEB);
        put("minecraft:bamboo_jungle", 0x3F6B33, 0xE8EDEB, 0x9ACD32);
        put("minecraft:swamp", 0x3F6B33, 0x0C140C);
        put("minecraft:mangrove_swamp", 0x3F6B33, 0x6B1F2E);
        put("minecraft:river", 0x3A6FC9);
        put("minecraft:beach", 0x3A6FC9, 0xC9B93A);
        put("minecraft:stony_shore", 0x8C8C8C, 0x3A6FC9);
        put("minecraft:mushroom_fields", 0xC93A3A, 0xE8EDEB, 0x5C4224);
        put("minecraft:ocean", 0x3A6FC9);
        put("minecraft:deep_ocean", 0x1A3A6B);
        put("minecraft:warm_ocean", 0xE8EDEB, 0x3A6FC9);
        put("minecraft:lukewarm_ocean", 0x3A6FC9);
        put("minecraft:deep_lukewarm_ocean", 0x3A6FC9, 0x0C1420);
        put("minecraft:cold_ocean", 0xE8EDEB, 0x3A6FC9);
        put("minecraft:deep_cold_ocean", 0xE8EDEB, 0x3A6FC9, 0x0C1420);
        put("minecraft:frozen_ocean", 0x6FC9D4, 0x3A6FC9);
        put("minecraft:deep_frozen_ocean", 0x0C1420, 0x6FC9D4, 0x3A6FC9);
        put("streamsreflowing:stream", 0xE8EDEB, 0x6FC9D4);

        // Was missing entirely - fell through to the generic default sick color instead of a real
        // dictated gradient, "туман должен быть во всех биомов".
        put("minecraft:cherry_grove", 0xF3C6D6, 0x3F6B33);
        put("minecraft:dripstone_caves", 0x5C4224, 0x2E1C14);
        put("minecraft:lush_caves", 0x3F6B33, 0x1C2E17);
        put("minecraft:deep_dark", 0x0A120A, 0x1A2A40);
        put("minecraft:the_void", 0x0C140C);

        // "лотос никогда не будет выглядеть так как выглядит наш биом" - our own two biomes were
        // completely missing from this table, meaning infection color/fog inside our OWN signature
        // biomes fell through to the generic fallback (0x08331A, a fixed dark green - what actually
        // read as "the water is always green no matter what" in a heavily-infected world where
        // lotus_marsh is common) instead of ever reflecting their own real palette. Anchored on
        // these two biomes' own biome-json effects colors (grass_color/water_color), not arbitrary
        // picks, so this is finally the actual lotus_marsh/blessing_tundra identity, not a guess.
        put("lotusblight:lotus_marsh", 0x328C78, 0x2EFE0D);
        put("lotusblight:blessing_tundra", 0x6C6772, 0x55FFFF);
    }

    private BiomeFogColors() {}

    private static void put(String biomeId, int... colors) {
        GRADIENTS.put(new ResourceLocation(biomeId), colors);
    }

    /** Null if this biome has no dictated gradient yet - caller falls back to the default sick color. */
    public static int[] gradientFor(ResourceLocation biomeId) {
        return GRADIENTS.get(biomeId);
    }

    /** Samples the gradient at t in [0,1], interpolating across its stops (a single-color gradient just returns that color). */
    public static int sample(int[] gradient, float t) {
        if (gradient.length == 1) return gradient[0];
        float scaled = Math.min(0.999999f, Math.max(0f, t)) * (gradient.length - 1);
        int i = (int) scaled;
        float localT = scaled - i;
        return lerpColor(gradient[i], gradient[i + 1], localT);
    }

    private static int lerpColor(int from, int to, float t) {
        int fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int r = Math.round(fr + (tr - fr) * t);
        int g = Math.round(fg + (tg - fg) * t);
        int b = Math.round(fb + (tb - fb) * t);
        return (r << 16) | (g << 8) | b;
    }
}
