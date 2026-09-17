package com.lotusblight.registry;

import com.lotusblight.LotusBlight;
import com.lotusblight.world.LotusCrownBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, LotusBlight.MODID);

    public static final RegistryObject<BlockEntityType<LotusCrownBlockEntity>> LOTUS_CROWN =
            BLOCK_ENTITIES.register("lotus_crown", () -> BlockEntityType.Builder.of(
                    LotusCrownBlockEntity::new, ModBlocks.INFECTED_LOTUS.get()).build(null));

    private ModBlockEntities() {}
}
