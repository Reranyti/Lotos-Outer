package com.lotusblight.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class ColdBlightEffect extends MobEffect {
    public ColdBlightEffect() {
        super(MobEffectCategory.HARMFUL, 0xB7D4C8);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 40 == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        // Every application of this effect in the codebase uses amplifier 0 (see
        // BlessingNoduleBlock#stepOn) - gating on amplifier > 0 meant the "dangerous" block's
        // effect never actually did anything beyond showing an icon, ever.
        if (entity.tickCount % 80 == 0) {
            entity.hurt(entity.damageSources().freeze(), 0.5f + amplifier * 0.5f);
        }
    }
}
