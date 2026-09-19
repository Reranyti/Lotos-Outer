package com.lotusblight.client;

import net.minecraft.client.color.block.BlockColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared BlockColor for every infected-ground block (infected_soil, lotus_stone, lotus_sand,
 * lotus_gravel, lotus_terracotta). First tried reusing vanilla's per-biome grass color
 * (BiomeColors.getAverageGrassColor) as a cheap "works on any biome for free" accent, but that
 * collapses most biomes into the same narrow green band grass color already lives in - real
 * biome distinctiveness needs an explicit hand-picked color per biome instead (see
 * BiomeInfectionColors), not a derived value that happens to exist already.
 *
 * Blended at BLEND_STRENGTH rather than replacing the texture's color outright - the goal is a
 * biome-appropriate accent (infected stone in a taiga reads different from infected stone in a
 * badlands), not overriding the block's own established identity.
 */
public final class InfectedGroundColor implements BlockColor {
    public static final InfectedGroundColor INSTANCE = new InfectedGroundColor();

    /** Was 0.35 - with all the effort that went into picking distinct per-biome/per-material colors, that read as barely-there ("чуть светлее чем обычная"), not an actual color difference. */
    private static final float BLEND_STRENGTH = 0.75f;

    private InfectedGroundColor() {}

    @Override
    public int getColor(BlockState state, BlockAndTintGetter level, BlockPos pos, int tintIndex) {
        // No world context (inventory/item-frame render) - fall back to the texture's own color
        // untouched (white multiplier) rather than guessing at a biome.
        if (!(level instanceof LevelReader reader) || pos == null) return BiomeInfectionColors.NO_TINT;
        InfectedMaterial material = InfectedMaterial.of(state.getBlock());
        if (material == null) return BiomeInfectionColors.NO_TINT;
        Holder<Biome> biome = reader.getBiome(pos);
        net.minecraft.resources.ResourceLocation biomeId = biome.unwrapKey()
                .map(net.minecraft.resources.ResourceKey::location)
                .orElse(null);
        if (biomeId == null) return BiomeInfectionColors.NO_TINT;
        int biomeColor = BiomeInfectionColors.colorFor(biomeId, material);
        return blendTowardWhite(biomeColor, BLEND_STRENGTH);
    }

    /** Lerps a color toward white (0xFFFFFF) by (1 - strength), so strength=1 is the pure biome color and strength=0 is no tint at all. */
    private static int blendTowardWhite(int color, float strength) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int blendedR = Math.round(255 + (r - 255) * strength);
        int blendedG = Math.round(255 + (g - 255) * strength);
        int blendedB = Math.round(255 + (b - 255) * strength);
        return (blendedR << 16) | (blendedG << 8) | blendedB;
    }
}
