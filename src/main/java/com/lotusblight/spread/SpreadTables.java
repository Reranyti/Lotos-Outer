package com.lotusblight.spread;

import com.lotusblight.registry.ModBlocks;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Terraria-style clean→infected block conversion table (see the biome-spread
 * reference: Corruption/Crimson/Hallow only ever convert a fixed set of
 * "infectable" block analogues, never arbitrary blocks). {@link
 * com.lotusblight.spread.InfectionSpreadEngine} looks a candidate block up
 * here before touching it — nothing outside this table can be converted,
 * which keeps the spread readable and bounded exactly like the reference.
 *
 * Grass is intentionally NOT in the generic table: it follows its own rule
 * (a grass block only converts if the soil directly below it is already
 * infected), mirroring the separate grass-spread table in the reference
 * instead of being treated like ordinary ground.
 */
public final class SpreadTables {

    private SpreadTables() {
    }

    private static Map<BlockState, BlockState> groundTable() {
        Map<BlockState, BlockState> table = new LinkedHashMap<>();
        table.put(Blocks.DIRT.defaultBlockState(), ModBlocks.INFECTED_SOIL.get().defaultBlockState());
        table.put(Blocks.COARSE_DIRT.defaultBlockState(), ModBlocks.INFECTED_SOIL.get().defaultBlockState());
        table.put(Blocks.MUD.defaultBlockState(), ModBlocks.INFECTED_SOIL.get().defaultBlockState());
        table.put(Blocks.SAND.defaultBlockState(), ModBlocks.LOTUS_SAND.get().defaultBlockState());
        table.put(Blocks.RED_SAND.defaultBlockState(), ModBlocks.LOTUS_SAND.get().defaultBlockState());
        table.put(Blocks.GRAVEL.defaultBlockState(), ModBlocks.LOTUS_GRAVEL.get().defaultBlockState());
        table.put(Blocks.STONE.defaultBlockState(), ModBlocks.LOTUS_STONE.get().defaultBlockState());
        table.put(Blocks.ANDESITE.defaultBlockState(), ModBlocks.LOTUS_STONE.get().defaultBlockState());
        table.put(Blocks.TERRACOTTA.defaultBlockState(), ModBlocks.LOTUS_TERRACOTTA.get().defaultBlockState());
        return table;
    }

    private static final Map<BlockState, BlockState> GROUND_TABLE = groundTable();

    /** Returns the infected replacement for a clean block, or {@code null} if it is not infectable at all (Terraria's "immune" blocks). */
    public static BlockState infectedGroundReplacement(BlockState clean) {
        return GROUND_TABLE.get(clean);
    }

    public static boolean isCleanGround(BlockState state) {
        return GROUND_TABLE.containsKey(state);
    }

    public static boolean isInfectedGround(BlockState state) {
        return state.is(ModBlocks.INFECTED_SOIL.get()) || state.is(ModBlocks.LOTUS_SAND.get())
                || state.is(ModBlocks.LOTUS_GRAVEL.get()) || state.is(ModBlocks.LOTUS_STONE.get())
                || state.is(ModBlocks.LOTUS_TERRACOTTA.get()) || state.is(ModBlocks.LOTUS_DIRT.get());
    }

    public static boolean isCleanLog(BlockState state) {
        return state.is(BlockTags.LOGS) && !state.is(ModBlocks.LOTUS_LOG.get()) && !state.is(ModBlocks.BLESSING_LOG.get());
    }

    public static boolean isCleanLeaves(BlockState state) {
        return state.is(BlockTags.LEAVES) && !state.is(ModBlocks.LOTUS_LEAVES.get()) && !state.is(ModBlocks.BLESSING_LEAVES.get());
    }

    public static BlockState infectedLog() {
        return ModBlocks.LOTUS_LOG.get().defaultBlockState();
    }

    public static BlockState infectedLeaves() {
        return ModBlocks.LOTUS_LEAVES.get().defaultBlockState();
    }

    /** Grass converts only onto ground that is ALREADY infected beneath it — never drives new ground conversion itself. */
    public static boolean isCleanGrass(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN);
    }

    public static BlockState infectedGrass(BlockState clean) {
        if (clean.is(Blocks.GRASS_BLOCK)) return ModBlocks.BLOSSOM_GRASS.get().defaultBlockState();
        return null; // Tall grass/ferns just die off when their soil infects rather than converting to a lotus analogue.
    }
}
