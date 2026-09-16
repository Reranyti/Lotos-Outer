package com.lotusblight.world;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.core.BlockPos;

/**
 * Solid wall segment of a vine barrier. Not player-breakable by design — the
 * barrier only ever comes down when every {@link LianaWeakPointBlock} in its
 * {@code BarrierRecord} has been cut (see spread.VineBarrierGenerator /
 * spread.BarrierEvents). This mirrors the World Lotus reference: the vine
 * blocking the path is not a puzzle of one weak core, it is a wall that only
 * yields once several deliberate cut points are found and destroyed.
 */
public class LianaBarrierBlock extends Block {

    public LianaBarrierBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public float getDestroyProgress(BlockState state, net.minecraft.world.entity.player.Player player, BlockGetter level, BlockPos pos) {
        return 0.0f;
    }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.block();
    }
}
