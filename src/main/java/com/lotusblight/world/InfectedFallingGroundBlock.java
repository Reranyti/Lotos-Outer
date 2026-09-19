package com.lotusblight.world;

import com.lotusblight.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A gravity-affected infected ground block (currently just lotus_sand) that still carries
 * InfectedGroundBlock's own LOTUS_ORIGIN property (see that class) - reuses the exact same
 * property instance so the blockstate/model files don't need a separate definition.
 *
 * Real sand physics is the point - it's meant to behave "странно" (oddly/dangerously) like real
 * sand normally does, not float in place like the other 4 infected ground types. The one twist:
 * glow_berries' vines are meant to hold it up, same as how a real plant's roots bind loose soil -
 * a block with this touching any face skips falling entirely for that check.
 */
public class InfectedFallingGroundBlock extends FallingBlock {
    public InfectedFallingGroundBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(InfectedGroundBlock.LOTUS_ORIGIN, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(InfectedGroundBlock.LOTUS_ORIGIN);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (heldByVines(level, pos)) return;
        super.tick(state, level, pos, random);
    }

    private static boolean heldByVines(LevelReader level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(pos.relative(direction)).is(ModBlocks.GLOW_BERRIES.get())) {
                return true;
            }
        }
        return false;
    }
}
