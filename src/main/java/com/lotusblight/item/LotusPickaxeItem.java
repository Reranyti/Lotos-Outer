package com.lotusblight.item;

import com.lotusblight.registry.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class LotusPickaxeItem extends PickaxeItem {
    public LotusPickaxeItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof Player player && !player.level().isClientSide() && player.getRandom().nextInt(10) == 0) {
            player.addEffect(new MobEffectInstance(ModEffects.LOTUS_SPORES.get(), 80, 0));
            stack.hurtAndBreak(1, player, living -> living.broadcastBreakEvent(player.getUsedItemHand()));
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!level.isClientSide() && selected && entity instanceof Player player && level.getGameTime() % 200 == 0 && player.getRandom().nextInt(8) == 0) {
            player.addEffect(new MobEffectInstance(ModEffects.LOTUS_SPORES.get(), 60, 0));
            stack.hurtAndBreak(1, player, living -> living.broadcastBreakEvent(player.getUsedItemHand()));
        }
    }
}
