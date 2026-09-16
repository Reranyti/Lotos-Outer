package com.lotusblight.world;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** A small surface shoot belonging to an existing main lotus anchor. */
public final class LotusShootBlock extends LotusBloomBlock {
    public LotusShootBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, net.minecraft.core.BlockPos pos) {
        return 0.0f;
    }
}
