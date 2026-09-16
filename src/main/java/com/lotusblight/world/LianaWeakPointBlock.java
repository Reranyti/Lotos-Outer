package com.lotusblight.world;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * A cuttable point embedded in a vine barrier (see LianaBarrierBlock). Unlike
 * the wall itself, this block breaks normally — cutting all weak points of a
 * barrier is what brings the whole structure down (handled in
 * spread.BarrierEvents, not here, so this block stays a dumb, cheap prop).
 */
public class LianaWeakPointBlock extends Block {

    private static final DustParticleOptions PINK = new DustParticleOptions(new Vector3f(1.0f, 0.2f, 0.55f), 1.2f);

    public LianaWeakPointBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.world.item.ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);
        level.sendParticles(PINK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
    }
}
