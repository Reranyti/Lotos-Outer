package com.lotusblight.registry;

import com.lotusblight.LotusBlight;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

public final class ModVillagers {
    public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(ForgeRegistries.POI_TYPES, LotusBlight.MODID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(ForgeRegistries.VILLAGER_PROFESSIONS, LotusBlight.MODID);

    public static final RegistryObject<PoiType> LOTUS_BOTANIST_POI = POI_TYPES.register("lotus_botanist_poi", () -> new PoiType(
            Set.copyOf(ModBlocks.LOTUS_HEART.get().getStateDefinition().getPossibleStates()), 1, 1));
    public static final RegistryObject<VillagerProfession> LOTUS_BOTANIST = PROFESSIONS.register("lotus_botanist", () -> new VillagerProfession(
            "lotus_botanist", holder -> holder.is(LOTUS_BOTANIST_POI.getKey()), holder -> holder.is(LOTUS_BOTANIST_POI.getKey()),
            ImmutableSet.of(), ImmutableSet.of(), net.minecraft.sounds.SoundEvents.VILLAGER_WORK_FARMER));

    private ModVillagers() {}
}
