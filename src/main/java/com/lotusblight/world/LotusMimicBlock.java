package com.lotusblight.world;

import com.lotusblight.registry.ModBlocks;
import com.lotusblight.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

public class LotusMimicBlock extends Block {
    private static final DustParticleOptions MIMIC_DUST = new DustParticleOptions(new Vector3f(0.25f, 0.95f, 0.35f), 1.0f);
    // Matches the small pad+flower model's actual footprint instead of the default full cube.
    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE = Block.box(1, 0, 1, 15, 11, 15);

    public LotusMimicBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(entity instanceof LivingEntity living) || level.isClientSide) return;
        if (living.getPersistentData().getLong("LotusMimicCooldown") > level.getGameTime()) return;

        living.getPersistentData().putLong("LotusMimicCooldown", level.getGameTime() + 80L);
        living.addEffect(new MobEffectInstance(ModEffects.LOTONIRIYA.get(), 20 * 18, 0, false, true, true));
        for (int i = 0; i < 24; i++) {
            level.addParticle(MIMIC_DUST,
                    pos.getX() + 0.15 + level.random.nextDouble() * 0.7,
                    pos.getY() + 0.25 + level.random.nextDouble() * 0.45,
                    pos.getZ() + 0.15 + level.random.nextDouble() * 0.7,
                    0.0, 0.03, 0.0);
        }
        level.playSound(null, pos, SoundEvents.SLIME_ATTACK, SoundSource.BLOCKS, 0.65f, 0.75f);
        // This used to turn into ModBlocks.INFECTED_LOTUS - the big main anchor pad
        // (LotusMainBlock), placed here with no OutbreakSavedData registration at all. That's a
        // real anchor-shaped block (its own BlockEntity, oversized VoxelShape, idle animation)
        // sitting in the world completely disconnected from the infection system - an orphan.
        // The trap having "sprung" should just reveal a normal, harmless decorative flower.
        level.setBlock(pos, ModBlocks.LOTUS_SHOOT.get().defaultBlockState(), 3);
    }
}

