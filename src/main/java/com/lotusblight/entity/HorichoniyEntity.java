package com.lotusblight.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

/**
 * "Хоричоний... торговец" - a trading NPC, built on WanderingTrader for its already-complete
 * trade-GUI/offer machinery rather than writing that from scratch. Only the look (own texture, see
 * HorichoniyRenderer) and trade list are ours; everything else (wandering AI, despawn timer, trade
 * screen) is inherited as-is.
 */
public class HorichoniyEntity extends WanderingTrader {
    public HorichoniyEntity(EntityType<? extends WanderingTrader> type, Level level) {
        super(type, level);
    }

    @Override
    protected void updateTrades() {
        MerchantOffers offers = this.getOffers();
        // Placeholder trade set - swap for real ones once the actual trade list is decided.
        offers.add(new MerchantOffer(new ItemStack(Items.EMERALD, 8),
                new ItemStack(com.lotusblight.registry.ModItems.LOTUS_ALLOY.get()), 6, 2, 0.05f));
        offers.add(new MerchantOffer(new ItemStack(com.lotusblight.registry.ModItems.STAR_LIGHT_VIAL.get()),
                new ItemStack(Items.EMERALD, 12), 4, 5, 0.05f));
    }
}
