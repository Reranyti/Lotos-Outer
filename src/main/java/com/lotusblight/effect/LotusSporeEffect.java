package com.lotusblight.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;

/**
 * Used to do ~0.5 damage every 4 seconds and nothing else - against a full health bar that's
 * genuinely imperceptible over the effect's whole 240-tick duration (see
 * InfectionSpreadEngine#infectNearbyLiving), so it existed as a mechanic nobody actually felt.
 * Now also saps Weakness for as long as the spores are active - a real, felt combat/mining
 * penalty instead of chip damage alone - and ticks damage twice as often.
 */
public class LotusSporeEffect extends MobEffect {
    private static final DustParticleOptions PINK_SPORES = new DustParticleOptions(new Vector3f(1.0f, 0.25f, 0.55f), 1.0f);

    public LotusSporeEffect() {
        super(MobEffectCategory.HARMFUL, 0xE83E83);
    }

    /**
     * Gated on the entity's own tickCount below instead of the remaining duration: roots, leaves and
     * every nearby conversion keep re-applying the effect, which resets the duration, and pairing a
     * duration check with a tickCount check meant both only lined up about once in 40 applications.
     */
    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.tickCount % Math.max(10, 40 >> Math.min(amplifier, 2)) != 0) return;
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(PINK_SPORES, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(), 4 + amplifier * 2, 0.3, 0.4, 0.3, 0.01);
        }
        // Every application of this effect in the codebase uses amplifier 0 (roots, leaves,
        // nearby-living spread contact all pass 0) - gating the damage on amplifier > 0 meant it
        // never actually hurt anyone, same bug ColdBlightEffect had.
        entity.hurt(entity.damageSources().magic(), 0.5f + amplifier * 0.5f);
        if (!entity.hasEffect(MobEffects.WEAKNESS)) {
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, amplifier, true, false));
        }
    }
}
