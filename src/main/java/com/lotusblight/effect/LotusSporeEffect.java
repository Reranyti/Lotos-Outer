package com.lotusblight.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;

public class LotusSporeEffect extends MobEffect {
    private static final DustParticleOptions PINK_SPORES = new DustParticleOptions(new Vector3f(1.0f, 0.25f, 0.55f), 1.0f);

    public LotusSporeEffect() {
        super(MobEffectCategory.HARMFUL, 0xE83E83);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % Math.max(10, 40 >> Math.min(amplifier, 2)) == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(PINK_SPORES, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(), 4 + amplifier * 2, 0.3, 0.4, 0.3, 0.01);
        }
        // Every application of this effect in the codebase uses amplifier 0 (roots, leaves,
        // nearby-living spread contact all pass 0) - gating the damage on amplifier > 0 meant it
        // never actually hurt anyone, same bug ColdBlightEffect had.
        if (entity.tickCount % 80 == 0) {
            entity.hurt(entity.damageSources().magic(), 0.5f + amplifier * 0.5f);
        }
    }
}
