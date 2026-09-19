package com.lotusblight.world;

import com.lotusblight.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Blessing biome's own dark sand - real gravity like vanilla sand, unlike the infection's own
 * ground blocks (see InfectedGroundBlock), which intentionally don't fall. This belongs to the
 * True Light/Resistance branch's biome, not the Lotus infection, so it deliberately does NOT
 * share InfectedGroundBlock/LOTUS_ORIGIN - that property exists to remember which vanilla block
 * an INFECTED tile replaced for cleansing powder, which has no meaning here.
 *
 * glow_berries' vines (also a Blessing-branch block, not an infection one) hold it up - a block
 * with glow_berries touching any face skips falling entirely, same idea as roots binding loose
 * soil in real life.
 */
public class BlessingSandBlock extends FallingBlock {
    public BlessingSandBlock(BlockBehaviour.Properties properties) {
        super(properties);
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
