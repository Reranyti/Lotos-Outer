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
