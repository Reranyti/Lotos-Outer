package com.lotusblight.spread;

import com.lotusblight.registry.ModBlocks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Clean->purple conversion table for the meteorite spread (see MeteoriteSpreadEngine) - "метеориты
 * распространяют биом абсолютно на любой блок делая его любым фиолетовым оттенком". Mirrors
 * MossyGroundTables' shape (same three ground categories: stone/dirt/sand-like), but converts into
 * this event's own purple blocks instead of vanilla ones.
 */
public final class MeteoriteGroundTables {
    private MeteoriteGroundTables() {}

    private static final Map<BlockState, BlockState> GROUND_TABLE = new LinkedHashMap<>();

    private static void buildTable() {
        BlockState stone = ModBlocks.METEORITE_STONE.get().defaultBlockState();
        for (var stoneLike : new net.minecraft.world.level.block.Block[]{
                Blocks.STONE, Blocks.ANDESITE, Blocks.GRANITE, Blocks.DIORITE, Blocks.DEEPSLATE,
                Blocks.COBBLED_DEEPSLATE, Blocks.COBBLESTONE, Blocks.CALCITE, Blocks.TUFF}) {
            GROUND_TABLE.put(stoneLike.defaultBlockState(), stone);
        }

        BlockState soil = ModBlocks.BLESSING_SOIL_PURPLE.get().defaultBlockState();
        for (var dirtLike : new net.minecraft.world.level.block.Block[]{
                Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.ROOTED_DIRT,
                Blocks.GRASS_BLOCK, Blocks.MUD, Blocks.MOSS_BLOCK}) {
            GROUND_TABLE.put(dirtLike.defaultBlockState(), soil);
        }

        BlockState sand = ModBlocks.BLESSING_SAND_PURPLE.get().defaultBlockState();
        for (var sandLike : new net.minecraft.world.level.block.Block[]{
                Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL, Blocks.SANDSTONE, Blocks.RED_SANDSTONE, Blocks.CLAY}) {
            GROUND_TABLE.put(sandLike.defaultBlockState(), sand);
        }
    }

    public static BlockState purpleReplacement(BlockState clean) {
        if (GROUND_TABLE.isEmpty()) buildTable();
        return GROUND_TABLE.get(clean);
    }

    public static boolean isPurpleBiome(BlockState state) {
        return state.is(ModBlocks.METEORITE_STONE.get())
                || state.is(ModBlocks.BLESSING_SOIL_PURPLE.get())
                || state.is(ModBlocks.BLESSING_SAND_PURPLE.get());
    }
}
