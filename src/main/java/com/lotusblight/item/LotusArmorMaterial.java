package com.lotusblight.item;

import com.lotusblight.registry.ModItems;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Armor material for the lotus_alloy armor set. Stats are intentionally
 * balanced between iron and diamond tier (see vanilla {@code ArmorMaterials}
 * for the reference values this splits the difference of):
 *   - defense per slot:   iron {2,5,6,2} / diamond {3,6,8,3} -> lotus {2,6,7,3}
 *   - durability multiplier: iron 15 / diamond 33 -> lotus 24
 *   - toughness:          iron 0.0 / diamond 2.0 -> lotus 1.0
 *   - enchantability:     iron 9 / diamond 10 -> lotus 9
 */
public enum LotusArmorMaterial implements ArmorMaterial {
    LOTUS_ALLOY_ARMOR("lotusblight_lotus_alloy", 24, buildDefenseMap(), 9, SoundEvents.ARMOR_EQUIP_IRON, 1.0f, 0.05f,
            () -> Ingredient.of(ModItems.LOTUS_ALLOY.get()));

    // Base per-slot durability points, mirroring vanilla's own base table, scaled by durabilityMultiplier.
    private static final Map<ArmorItem.Type, Integer> BASE_DURABILITY = buildBaseDurability();

    private final String name;
    private final int durabilityMultiplier;
    private final Map<ArmorItem.Type, Integer> defenseForType;
    private final int enchantmentValue;
    private final SoundEvent equipSound;
    private final float toughness;
    private final float knockbackResistance;
    private final Supplier<Ingredient> repairIngredient;

    LotusArmorMaterial(String name, int durabilityMultiplier, Map<ArmorItem.Type, Integer> defenseForType,
                        int enchantmentValue, SoundEvent equipSound, float toughness, float knockbackResistance,
                        Supplier<Ingredient> repairIngredient) {
        this.name = name;
        this.durabilityMultiplier = durabilityMultiplier;
        this.defenseForType = defenseForType;
        this.enchantmentValue = enchantmentValue;
        this.equipSound = equipSound;
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
        this.repairIngredient = repairIngredient;
    }

    private static Map<ArmorItem.Type, Integer> buildDefenseMap() {
        Map<ArmorItem.Type, Integer> map = new EnumMap<>(ArmorItem.Type.class);
        map.put(ArmorItem.Type.BOOTS, 2);
        map.put(ArmorItem.Type.LEGGINGS, 6);
        map.put(ArmorItem.Type.CHESTPLATE, 7);
        map.put(ArmorItem.Type.HELMET, 3);
        return map;
    }

    private static Map<ArmorItem.Type, Integer> buildBaseDurability() {
        Map<ArmorItem.Type, Integer> map = new EnumMap<>(ArmorItem.Type.class);
        map.put(ArmorItem.Type.BOOTS, 13);
        map.put(ArmorItem.Type.LEGGINGS, 15);
        map.put(ArmorItem.Type.CHESTPLATE, 16);
        map.put(ArmorItem.Type.HELMET, 11);
        return map;
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return BASE_DURABILITY.get(type) * this.durabilityMultiplier;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return this.defenseForType.get(type);
    }

    @Override
    public int getEnchantmentValue() {
        return this.enchantmentValue;
    }

    @Override
    public SoundEvent getEquipSound() {
        return this.equipSound;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return this.repairIngredient.get();
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public float getToughness() {
        return this.toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return this.knockbackResistance;
    }
}
