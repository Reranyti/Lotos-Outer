package com.lotusblight.entity;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * The traitor-branch boss fight's toughest guardian tier - "Стражи мирового лотоса, пробивают
 * ДАЖЕ алмазную броню с зачарами". A plain Wolf's bite goes through the normal mobAttack damage
 * source, which armor points AND protection enchantments both reduce - neither should apply here.
 * Overrides doHurtTarget to hit with lotusblight:world_lotus_guardian instead, a damage type
 * this mod added to both #minecraft:bypasses_armor and #minecraft:bypasses_enchantments.
 */
public class WorldLotusGuardianWolf extends Wolf {
    public static final ResourceKey<DamageType> DAMAGE_TYPE_KEY =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("lotusblight", "world_lotus_guardian"));

    public WorldLotusGuardianWolf(EntityType<? extends Wolf> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        float damage = (float) this.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        Holder<DamageType> type = this.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE_TYPE_KEY);
        return target.hurt(new DamageSource(type, this), damage);
    }
}
