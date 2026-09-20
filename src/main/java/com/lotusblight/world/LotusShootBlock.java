package com.lotusblight.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
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

    /**
     * LotusBloomBlock (like plain Block) has no support check at all - this is meant to sit on
     * top of the lily pad InfectionSpreadEngine places under it, but with nothing checking that,
     * removing the pad (cleansing powder, a player breaking it, ice forming) left the shoot just
     * floating in mid-air forever (bug #2).
     */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.is(Blocks.LILY_PAD) || below.isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }
}
