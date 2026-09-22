package com.lotusblight.item;

import com.lotusblight.registry.ModItems;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.Supplier;

/**
 * "ресурсы ЛУЧШЕ алмазов (альтернатива незериту)" - stats split the difference toward netherite
 * rather than matching it exactly (netherite {level 4, uses 2031, speed 9.0, damage 4.0, ench. 15},
 * diamond {level 3, 1561, 8.0, 3.0, 10}), but mining level matches netherite's 4 outright, so a
 * meteorite pickaxe is never blocked from a block a netherite one could break (obsidian, ancient
 * debris, etc).
 */
public enum MeteoriteTier implements Tier {
    METEORITE(1800, 8.5f, 3.5f, 12, () -> Ingredient.of(ModItems.METEORITE_INGOT.get()));

    private final int uses;
    private final float speed;
    private final float attackDamageBonus;
    private final int enchantmentValue;
    private final Supplier<Ingredient> repairIngredient;

    MeteoriteTier(int uses, float speed, float attackDamageBonus, int enchantmentValue, Supplier<Ingredient> repairIngredient) {
        this.uses = uses;
        this.speed = speed;
        this.attackDamageBonus = attackDamageBonus;
        this.enchantmentValue = enchantmentValue;
        this.repairIngredient = repairIngredient;
    }

    @Override
    public int getUses() {
        return this.uses;
    }

    @Override
    public float getSpeed() {
        return this.speed;
    }

    @Override
    public float getAttackDamageBonus() {
        return this.attackDamageBonus;
    }

    @Override
    public int getLevel() {
        return 4;
    }

    @Override
    public int getEnchantmentValue() {
        return this.enchantmentValue;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return this.repairIngredient.get();
    }
}
