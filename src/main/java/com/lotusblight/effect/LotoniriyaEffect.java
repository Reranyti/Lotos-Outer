package com.lotusblight.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import com.lotusblight.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class LotoniriyaEffect extends MobEffect {
    public LotoniriyaEffect() {
        super(MobEffectCategory.HARMFUL, 0x6DEB75);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide || !(entity instanceof Player player)) return;
        if (entity.tickCount % 40 != 0) return;

        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.CONFUSION, 45, 0, true, false));
        var inventory = player.getInventory();

        // The infection scrambles carried items but never touches armor or the offhand.
        for (int i = 0; i < 2; i++) {
            int first = 0 + player.getRandom().nextInt(36);
            int second = 0 + player.getRandom().nextInt(36);
            ItemStack a = inventory.getItem(first).copy();
            inventory.setItem(first, inventory.getItem(second).copy());
            inventory.setItem(second, a);
        }

        // Lotus-cleaning supplies are the specific prey of Lotoniriya.
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.CLEANSING_POWDER.get())) {
                ItemStack dropped = stack.copy();
                inventory.setItem(slot, ItemStack.EMPTY);
                player.drop(dropped, true);
                player.displayClientMessage(Component.literal("Лотонирия вырывает очищающий порошок из твоих рук!"), true);
                break;
            }
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }
}

