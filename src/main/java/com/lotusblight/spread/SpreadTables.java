package com.lotusblight.spread;

import com.lotusblight.registry.ModBlocks;
import com.lotusblight.world.InfectedGroundBlock;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
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

    private static final Map<BlockState, BlockState> GROUND_TABLE = new LinkedHashMap<>();
    /** infected block type -> its origin list, indexed by InfectedGroundBlock.LOTUS_ORIGIN. Lets cleansing powder find the exact original block instead of one fixed guess (see LotusEvents#cleanReplacementFor). */
    private static final Map<Block, Block[]> ORIGINS_BY_INFECTED_BLOCK = new HashMap<>();

    /**
     * Registers one clean->infected category: every entry in {@code origins} converts to
     * {@code infected} with LOTUS_ORIGIN set to its own index in that array, and the array itself
     * is kept so {@link #originalGroundBlock} can map back from (infected block, origin index) to
     * the exact vanilla block that was there before.
     */
    private static void registerCategory(Block infected, Block... origins) {
        ORIGINS_BY_INFECTED_BLOCK.put(infected, origins);
        for (int i = 0; i < origins.length; i++) {
            GROUND_TABLE.put(origins[i].defaultBlockState(),
                    infected.defaultBlockState().setValue(InfectedGroundBlock.LOTUS_ORIGIN, i));
        }
    }

    static {
        // Used to only cover 9 exact vanilla blocks - fine on the swamp biome it was written
        // against, but on anything else (badlands, mountains, ...) most of what's actually on the
        // ground (granite, diorite, deepslate, colored terracotta, podzol, mycelium, sandstone)
        // simply wasn't in the table at all, so the infection silently skipped converting the
        // ground entirely and only the decorative extras (mini-trees/blossom grass) showed up
        // sitting on untouched terrain - which is what actually looked broken, not the decorations
        // themselves. This is the broad "base" pass across common natural terrain categories,
        // reusing the existing 5 infected ground blocks rather than new ones (see the pending
        // per-biome table for dedicated analogs later).
        registerCategory(ModBlocks.INFECTED_SOIL.get(),
                Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.MUD, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.ROOTED_DIRT,
                Blocks.GRASS_BLOCK);
        registerCategory(ModBlocks.LOTUS_SAND.get(),
                Blocks.SAND, Blocks.RED_SAND, Blocks.SANDSTONE, Blocks.RED_SANDSTONE);
        registerCategory(ModBlocks.LOTUS_GRAVEL.get(),
                Blocks.GRAVEL);
        registerCategory(ModBlocks.LOTUS_STONE.get(),
                Blocks.STONE, Blocks.ANDESITE, Blocks.GRANITE, Blocks.DIORITE, Blocks.DEEPSLATE,
                Blocks.COBBLED_DEEPSLATE, Blocks.COBBLESTONE, Blocks.CALCITE, Blocks.TUFF);
        registerCategory(ModBlocks.LOTUS_TERRACOTTA.get(),
                Blocks.TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.MAGENTA_TERRACOTTA,
                Blocks.LIGHT_BLUE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.LIME_TERRACOTTA, Blocks.PINK_TERRACOTTA,
                Blocks.GRAY_TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.CYAN_TERRACOTTA, Blocks.PURPLE_TERRACOTTA,
                Blocks.BLUE_TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.GREEN_TERRACOTTA, Blocks.RED_TERRACOTTA,
                Blocks.BLACK_TERRACOTTA);
    }

    /** Returns the infected replacement for a clean block (with LOTUS_ORIGIN set), or {@code null} if it is not infectable at all (Terraria's "immune" blocks). */
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

    /**
     * The exact vanilla block this infected ground block replaced, read back from its own
     * LOTUS_ORIGIN state (see InfectedGroundBlock). Null for a block type with no registered
     * category (e.g. LOTUS_DIRT, which the biome's own worldgen places directly and the spread
     * engine never converts into) or an out-of-range index (shouldn't happen, but a future
     * chatoverhaul-style jar/data mismatch is exactly the kind of thing this guards against).
     */
    public static BlockState originalGroundBlock(BlockState infected) {
        Block[] origins = ORIGINS_BY_INFECTED_BLOCK.get(infected.getBlock());
        if (origins == null || !(infected.getBlock() instanceof InfectedGroundBlock)) return null;
        int index = infected.getValue(InfectedGroundBlock.LOTUS_ORIGIN);
        if (index < 0 || index >= origins.length) return null;
        return origins[index].defaultBlockState();
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

    /**
     * Decorative plant OVERLAYS only - grass/tall grass/ferns, which occupy their own position
     * standing on top of a separate ground block, so "is my supporting soil already infected"
     * is a meaningful question for them. GRASS_BLOCK itself used to be lumped in here too, but a
     * grass block IS the ground (not a plant sitting on separate soil) - checking the block below
     * IT meant checking whatever is several layers further down (usually untouched stone), which
     * spread almost never reaches from the side at the same Y level. That made grass block
     * conversion practically never happen ("трава остаётся нетронутой"). It's registered in the
     * generic GROUND_TABLE now instead, converting the same direct way stone/dirt/sand do.
     */
    public static boolean isCleanGrass(BlockState state) {
        return state.is(Blocks.GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN);
    }

    public static BlockState infectedGrass(BlockState clean) {
        return null; // Tall grass/ferns just die off when their soil infects rather than converting to a lotus analogue.
    }
}
