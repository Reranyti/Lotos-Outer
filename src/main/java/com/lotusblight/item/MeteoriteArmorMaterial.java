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
 * Armor material for the meteorite tool/armor tier (see {@link MeteoriteTier}). Defense per slot
 * is identical to vanilla diamond/netherite (they share the same values); durability, toughness,
 * enchantability and knockback resistance split the difference toward netherite {37, 3.0, 15, 0.1}
 * from diamond {33, 2.0, 10, 0.0}.
 */
public enum MeteoriteArmorMaterial implements ArmorMaterial {
    METEORITE("lotusblight:meteorite", 35, buildDefenseMap(), 12, SoundEvents.ARMOR_EQUIP_DIAMOND, 2.5f, 0.05f,
            () -> Ingredient.of(ModItems.METEORITE_INGOT.get()));

    private static final Map<ArmorItem.Type, Integer> BASE_DURABILITY = buildBaseDurability();

    private final String name;
    private final int durabilityMultiplier;
    private final Map<ArmorItem.Type, Integer> defenseForType;
    private final int enchantmentValue;
    private final SoundEvent equipSound;
    private final float toughness;
    private final float knockbackResistance;
    private final Supplier<Ingredient> repairIngredient;

    MeteoriteArmorMaterial(String name, int durabilityMultiplier, Map<ArmorItem.Type, Integer> defenseForType,
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
        map.put(ArmorItem.Type.BOOTS, 3);
        map.put(ArmorItem.Type.LEGGINGS, 6);
        map.put(ArmorItem.Type.CHESTPLATE, 8);
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
