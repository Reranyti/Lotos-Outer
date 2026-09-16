package com.lotusblight.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

public class GlowBerryBushBlock extends BushBlock {
    private static final DustParticleOptions GLOW = new DustParticleOptions(new Vector3f(0.78f, 1.0f, 0.35f), 0.45f);

    public GlowBerryBushBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            level.addParticle(GLOW, pos.getX() + random.nextDouble(), pos.getY() + 0.55, pos.getZ() + random.nextDouble(), 0, 0.005, 0);
        }
    }
}
