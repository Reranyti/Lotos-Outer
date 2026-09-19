package com.lotusblight.spread;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Clean->mossy conversion table for the Mossy Glands (see MossyGlandSpreadEngine) - unlike
 * SpreadTables (which converts into our OWN custom lotus blocks), this converts straight into
 * real, unmodified vanilla Lush Caves blocks. The whole point of Mossy Glands is "literally lush
 * caves, just on the surface" - no new textures, no new blocks, just vanilla moss/tuff/clay
 * spreading somewhere it doesn't normally reach.
 */
public final class MossyGroundTables {
    private MossyGroundTables() {}

    private static final Map<BlockState, BlockState> GROUND_TABLE = new LinkedHashMap<>();

    static {
        BlockState moss = Blocks.MOSS_BLOCK.defaultBlockState();
        for (var dirtLike : new net.minecraft.world.level.block.Block[]{
                Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.ROOTED_DIRT,
                Blocks.GRASS_BLOCK, Blocks.MUD}) {
            GROUND_TABLE.put(dirtLike.defaultBlockState(), moss);
        }

        BlockState tuff = Blocks.TUFF.defaultBlockState();
        for (var stoneLike : new net.minecraft.world.level.block.Block[]{
                Blocks.STONE, Blocks.ANDESITE, Blocks.GRANITE, Blocks.DIORITE, Blocks.DEEPSLATE,
                Blocks.COBBLED_DEEPSLATE, Blocks.COBBLESTONE, Blocks.CALCITE}) {
            GROUND_TABLE.put(stoneLike.defaultBlockState(), tuff);
        }

        BlockState clay = Blocks.CLAY.defaultBlockState();
        for (var sandLike : new net.minecraft.world.level.block.Block[]{
                Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL, Blocks.SANDSTONE, Blocks.RED_SANDSTONE}) {
            GROUND_TABLE.put(sandLike.defaultBlockState(), clay);
        }
    }

    public static BlockState mossyReplacement(BlockState clean) {
        return GROUND_TABLE.get(clean);
    }

    public static boolean isCleanGround(BlockState state) {
        return GROUND_TABLE.containsKey(state);
    }

    public static boolean isMossyGround(BlockState state) {
        return state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.TUFF) || state.is(Blocks.CLAY);
    }
}
