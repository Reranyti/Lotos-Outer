package com.lotusblight.world;

import com.lotusblight.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * Mini-biome canopy is meant to be a real thicket you push through, not
 * scenery — walking through it has a chance to dose you with Lotus Spores,
 * same as the spore cloud a fresh conversion releases nearby.
 */
public class LotusLeavesBlock extends Block {
    private static final DustParticleOptions SPORE_DUST = new DustParticleOptions(new Vector3f(0.35f, 0.85f, 0.45f), 0.8f);

    public LotusLeavesBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity instanceof LivingEntity living && !(entity instanceof Player player && player.getAbilities().invulnerable)) {
            if (level.getRandom().nextInt(30) == 0) {
                living.addEffect(new MobEffectInstance(ModEffects.LOTUS_SPORES.get(), 100, 0));
            }
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) == 0) {
            level.addParticle(SPORE_DUST, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0.0, 0.01, 0.0);
        }
    }
}
