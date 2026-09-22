package com.lotusblight.item;

import com.lotusblight.escape.LotusChaseEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * "Витаминки для ускорения" — a short, strong Speed burst. Usable any time, but its real purpose
 * is helping a player outrun the expanding radius during a {@link LotusChaseEvent}.
 */
public class VitaminItem extends Item {
    private static final int SPEED_DURATION_TICKS = 20 * 8;
    private static final int SPEED_AMPLIFIER = 2;

    public VitaminItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return result;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SPEED_DURATION_TICKS, SPEED_AMPLIFIER));
        return result;
    }
}
