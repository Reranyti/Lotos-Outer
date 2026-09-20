package com.lotusblight.registry;

import com.lotusblight.LotusBlight;
import com.lotusblight.entity.WorldLotusGuardianWolf;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, LotusBlight.MODID);

    public static final RegistryObject<EntityType<WorldLotusGuardianWolf>> WORLD_LOTUS_GUARDIAN = ENTITY_TYPES.register(
            "world_lotus_guardian",
            () -> EntityType.Builder.of(WorldLotusGuardianWolf::new, MobCategory.MONSTER)
                    .sized(0.6f, 0.85f)
                    .fireImmune()
                    .build("world_lotus_guardian"));

    private ModEntities() {}
}
