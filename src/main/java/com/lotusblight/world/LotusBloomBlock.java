package com.lotusblight.world;

import org.joml.Vector3f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LotusBloomBlock extends Block {
    private static final DustParticleOptions PETAL_DUST = new DustParticleOptions(new Vector3f(1.0f, 0.18f, 0.55f), 0.55f);
    // Matches the small pad+flower model's actual footprint (infected_lotus_crown.json) instead
    // of the default full 16x16x16 cube — the selection/collision box used to be way bigger than
    // what's actually drawn.
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 11, 15);

    public LotusBloomBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
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
