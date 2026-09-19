package com.lotusblight.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The thematic opposite of Lotoniriya: clarity instead of confusion. Granted
 * by eating a glowing_berry (see GlowingBerryItem). Mechanically it actively
 * pushes back against the infection's own effect — strips Lotus Spores off
 * the player early — and grants Night Vision/Regeneration for the duration,
 * "seeing clearly" while it lasts.
 */
public class TrueLightEffect extends MobEffect {
    /** ~20 minutes 10 seconds — the bonus absorption hearts GlowingBerryItem grants track this exactly. */
    public static final int DURATION_TICKS = 24200;

    public TrueLightEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFDD55);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // Only needs to run often enough to catch freshly (re)applied Lotus Spores - Night
        // Vision/Regeneration are granted once for the full duration in GlowingBerryItem instead
        // of being topped up here in short bursts, which used to read as constant screen flicker
        // every time a top-up briefly lapsed.
        return duration % 20 == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity instanceof Player player)) return;
        if (player.hasEffect(com.lotusblight.registry.ModEffects.LOTUS_SPORES.get())) {
            player.removeEffect(com.lotusblight.registry.ModEffects.LOTUS_SPORES.get());
        }
    }
}
