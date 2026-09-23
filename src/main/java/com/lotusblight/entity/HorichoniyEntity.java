package com.lotusblight.entity;

import com.lotusblight.registry.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

/**
 * "торговец от звёздного света" - Horichoniy, a Star Light-affiliated trader. WanderingTrader is
 * the right vanilla base for this (already peaceful, already just wanders and opens a trade
 * screen) rather than a full Villager with its work-schedule AI this character has no use for.
 */
public class HorichoniyEntity extends WanderingTrader {
    public HorichoniyEntity(EntityType<? extends WanderingTrader> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    @Override
    protected void updateTrades() {
        MerchantOffers offers = this.getOffers();
        addOffer(offers, new ItemStack(Items.EMERALD, 6), new ItemStack(ModItems.GLOW_BERRY_FOOD.get(), 8), 8);
        addOffer(offers, new ItemStack(Items.EMERALD, 3), new ItemStack(ModItems.VITAMIN.get(), 4), 12);
        addOffer(offers, new ItemStack(Items.EMERALD, 20), new ItemStack(ModItems.METEORITE_INGOT.get(), 1), 4);
        addOffer(offers, new ItemStack(Items.EMERALD, 12), new ItemStack(ModItems.BLESSING_SAND_PURPLE_ITEM.get(), 4), 6);
    }

    private static void addOffer(MerchantOffers offers, ItemStack cost, ItemStack result, int maxUses) {
        offers.add(new MerchantOffer(cost, result, maxUses, 2, 0.05f));
    }
}
