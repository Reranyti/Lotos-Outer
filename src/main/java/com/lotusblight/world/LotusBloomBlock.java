package com.lotusblight.world;

import org.joml.Vector3f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class LotusBloomBlock extends Block {
    private static final DustParticleOptions PETAL_DUST = new DustParticleOptions(new Vector3f(1.0f, 0.18f, 0.55f), 0.55f);

    public LotusBloomBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) == 0) {
            level.addParticle(PETAL_DUST,
                    pos.getX() + 0.35 + random.nextDouble() * 0.3,
                    pos.getY() + 0.8 + random.nextDouble() * 0.5,
                    pos.getZ() + 0.35 + random.nextDouble() * 0.3,
                    0.0, 0.012, 0.0);
        }
    }
}
