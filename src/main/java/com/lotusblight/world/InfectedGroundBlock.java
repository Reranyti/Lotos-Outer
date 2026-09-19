package com.lotusblight.world;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A generic infected ground block (infected_soil, lotus_stone, lotus_sand, lotus_gravel,
 * lotus_terracotta) that remembers which specific vanilla block it replaced.
 *
 * Each of these blocks is a many-to-one conversion target - lotus_stone alone is what stone,
 * andesite, granite, diorite, deepslate, cobbled deepslate, cobblestone, calcite AND tuff all
 * become (see SpreadTables). Cleansing powder used to revert every infected block type to one
 * fixed hardcoded vanilla block regardless of what was actually there before - cleansing a
 * granite mountain turned it into plain grey stone, not granite. LOTUS_ORIGIN stores the index
 * into that source block's own SpreadTables category list, so cleansing can look the real
 * original block back up instead of guessing at one.
 *
 * The value range (0-16) is sized for the largest category (terracotta: 1 plain + 16 colors).
 * Categories with fewer entries just never use the higher indices. All origin values currently
 * render identically (see the blockstate JSON) - this is groundwork for later per-origin visual
 * variants, not a visual feature by itself yet.
 */
public class InfectedGroundBlock extends Block {
    public static final IntegerProperty LOTUS_ORIGIN = IntegerProperty.create("lotus_origin", 0, 16);

    public InfectedGroundBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LOTUS_ORIGIN, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LOTUS_ORIGIN);
    }
}
