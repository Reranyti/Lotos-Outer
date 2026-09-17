package com.lotusblight.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
    public TrueLightEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFDD55);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity instanceof Player player)) return;
        if (player.hasEffect(com.lotusblight.registry.ModEffects.LOTUS_SPORES.get())) {
            player.removeEffect(com.lotusblight.registry.ModEffects.LOTUS_SPORES.get());
        }
        if (!player.hasEffect(MobEffects.NIGHT_VISION)) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, true, false));
        }
    }
}
