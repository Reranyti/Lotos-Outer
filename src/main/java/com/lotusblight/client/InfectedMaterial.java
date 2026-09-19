package com.lotusblight.client;

import com.lotusblight.registry.ModBlocks;
import net.minecraft.world.level.block.Block;

/** Which infected block type a BlockState is, for BiomeInfectionColors' per-material-per-group lookup. */
public enum InfectedMaterial {
    SOIL, SAND, GRAVEL, STONE, TERRACOTTA, LOG, LEAVES;

    public static InfectedMaterial of(Block block) {
        if (block == ModBlocks.INFECTED_SOIL.get()) return SOIL;
        if (block == ModBlocks.LOTUS_SAND.get()) return SAND;
        if (block == ModBlocks.LOTUS_GRAVEL.get()) return GRAVEL;
        if (block == ModBlocks.LOTUS_STONE.get()) return STONE;
        if (block == ModBlocks.LOTUS_TERRACOTTA.get()) return TERRACOTTA;
        if (block == ModBlocks.LOTUS_LOG.get()) return LOG;
        if (block == ModBlocks.LOTUS_LEAVES.get()) return LEAVES;
        return null;
    }
}
