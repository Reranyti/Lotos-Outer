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
    /** Was 40 (once every 2s) with only 2 swaps - "скорость всё ещё медленная". Shuffling itself is the whole point of this effect now (see class doc), so it runs often enough to actually keep the hotbar unusable instead of settling for seconds at a time between scrambles. */
    private static final int SHUFFLE_INTERVAL_TICKS = 10;
    private static final int SWAPS_PER_TICK = 3;

    public LotoniriyaEffect() {
        super(MobEffectCategory.HARMFUL, 0x6DEB75);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide || !(entity instanceof Player player)) return;
        if (entity.tickCount % SHUFFLE_INTERVAL_TICKS != 0) return;

        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.CONFUSION, 45, 0, true, false));
        var inventory = player.getInventory();

        // The infection scrambles carried items but never touches armor or the offhand.
        for (int i = 0; i < SWAPS_PER_TICK; i++) {
            int first = player.getRandom().nextInt(36);
            int second = player.getRandom().nextInt(36);
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

