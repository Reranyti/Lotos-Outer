package com.lotusblight.item;

import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;

/**
 * A normal, correctly-tiered pickaxe. Previously this class afflicted its
 * own wielder with {@code ModEffects.LOTUS_SPORES} on hit and while held —
 * that self-harm behavior has been removed; it is now a plain mining tool.
 */
public final class LotusPickaxeItem extends PickaxeItem {
    public LotusPickaxeItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }
}
