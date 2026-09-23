package com.lotusblight.item;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * "предмет, связанный с этой памятью" - a lore-important quest item, not just a stat stick. Heals,
 * boosts speed and lights up the area on drink, matching the described role - its actual narrative
 * weight comes from the Honcho quest (see HonchoEntity), not the item's own mechanics.
 */
public class StarLightVialItem extends Item {
    private static final DustParticleOptions GOLD_SPARKLE = new DustParticleOptions(new Vector3f(1.0f, 0.85f, 0.4f), 1.2f);
    private static final int SPEED_DURATION_TICKS = 20 * 10;
    private static final int GLOW_DURATION_TICKS = 20 * 20;
    private static final float HEAL_AMOUNT = 10.0f;

    public StarLightVialItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return result;

        player.heal(HEAL_AMOUNT);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SPEED_DURATION_TICKS, 1));
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_DURATION_TICKS, 0));
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(GOLD_SPARKLE, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.4, 0.6, 0.4, 0.02);
        }
        return result;
    }
}
