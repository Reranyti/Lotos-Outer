package com.lotusblight.world;

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
        // Was a one-shot "trap": apply an effect, then turn into a harmless decorative
        // ModBlocks.LOTUS_SHOOT and never act again (bug #22 - "это не ловушка а реальное
        // существо", the mimic is lore-established as the lotus's own creature, disguised as an
        // ordinary block). A follow-up attempt at this replaced the block with a spawned Silverfish
        // entity - explicitly rejected too, the mimic itself must stay put and keep triggering
        // (it does not vanish/despawn when it catches something), so this is back to only the
        // effect/particles/sound, with no block-state change at all.
    }
}

