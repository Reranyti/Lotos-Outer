package com.lotusblight.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** A small surface shoot belonging to an existing main lotus anchor. */
public final class LotusShootBlock extends LotusBloomBlock {
    /** shootLines() was written for this block but never actually wired up anywhere - the pool of ambient lines sat completely unused. */
    private static final int AMBIENT_CHANCE = 30;
    private static final double AMBIENT_PLAYER_RADIUS = 12.0;

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

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(AMBIENT_CHANCE) != 0) return;
        Player nearest = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, AMBIENT_PLAYER_RADIUS, false);
        if (nearest == null) return;
        var outbreak = com.lotusblight.data.OutbreakSavedData.get(level).nearestOutbreak(pos, 64.0, false);
        int phase = outbreak != null ? outbreak.phase() : 1;
        var lines = com.lotusblight.dialogue.LotusDialogueLibrary.shootLines(phase);
        nearest.displayClientMessage(Component.literal(lines.get(random.nextInt(lines.size()))), true);
    }
}
