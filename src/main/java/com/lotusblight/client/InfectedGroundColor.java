package com.lotusblight.client;

import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared BlockColor for every infected-ground block (infected_soil, lotus_stone, lotus_sand,
 * lotus_gravel, lotus_terracotta). Reuses the same per-biome grass color vanilla already computes
 * for every biome (BiomeColors.getAverageGrassColor, the exact call grass_block itself uses) as
 * an accent tint over the block's own baked texture - instead of a separate hand-painted texture
 * per biome (the "static" approach), or a hardcoded per-biome color table we'd have to maintain
 * for every vanilla AND modded biome ourselves. This works on any biome, vanilla or modded, with
 * zero new art or data, because it's reading a value that already exists for every biome.
 *
 * Blended at BLEND_STRENGTH rather than replacing the texture's color outright - the goal is a
 * biome-appropriate accent (infected stone in a taiga reads slightly different from infected
 * stone in a badlands), not overriding the block's own established identity.
 */
public final class InfectedGroundColor implements BlockColor {
    public static final InfectedGroundColor INSTANCE = new InfectedGroundColor();

    private static final float BLEND_STRENGTH = 0.35f;

    private InfectedGroundColor() {}

    @Override
    public int getColor(BlockState state, BlockAndTintGetter level, BlockPos pos, int tintIndex) {
        // No world context (inventory/item-frame render) - fall back to the texture's own color
        // untouched (white multiplier) rather than guessing at a biome.
        if (level == null || pos == null) return 0xFFFFFF;
        int biomeGrass = BiomeColors.getAverageGrassColor(level, pos);
        return blendTowardWhite(biomeGrass, BLEND_STRENGTH);
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
