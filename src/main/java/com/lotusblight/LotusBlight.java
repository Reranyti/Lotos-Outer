package com.lotusblight;

import com.mojang.logging.LogUtils;
import com.lotusblight.registry.ModBlocks;
import com.lotusblight.registry.ModBlockEntities;
import com.lotusblight.registry.ModItems;
import com.lotusblight.registry.ModEffects;
import com.lotusblight.registry.ModVillagers;
import com.lotusblight.registry.ModFluids;
import com.lotusblight.registry.ModBiomes;
import net.minecraftforge.common.BiomeManager;
import com.lotusblight.world.LotusEvents;
import com.lotusblight.spread.InfectionSpreadEngine;
import com.lotusblight.spread.BarrierEvents;
import com.lotusblight.spread.roots.RootGrowthEngine;
import com.lotusblight.worldgen.GuaranteedSpawnManager;
import com.lotusblight.advancement.SingleBiomeWorldTrigger;
import com.lotusblight.map.journeymap.JourneyMapSyncTicker;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(LotusBlight.MODID)
public class LotusBlight {
    public static final String MODID = "lotusblight";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final RegistryObject<CreativeModeTab> LOTUS_TAB = TABS.register("lotus_tab", () -> CreativeModeTab.builder()
            .title(net.minecraft.network.chat.Component.translatable("itemGroup.lotusblight.lotus_tab"))
            .icon(() -> ModItems.LOTUS_MAP.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.LOTUS_SEED.get());
                output.accept(ModItems.LOTUS_WIKI.get());
                output.accept(ModItems.LOTUS_MIMIC_ITEM.get());
                output.accept(ModItems.LOTUS_MAP.get());
                output.accept(ModItems.INFECTED_WATER_BUCKET.get());
                output.accept(ModItems.CLEANSING_POWDER.get());
                output.accept(ModItems.LOTUS_ORE_ITEM.get());
                output.accept(ModItems.LOTUS_ALLOY.get());
                output.accept(ModItems.LOTUS_PICKAXE.get());
                output.accept(ModBlocks.LOTUS_HEART.get());
                output.accept(ModBlocks.INFECTED_LOTUS.get());
                output.accept(ModBlocks.INFECTED_SOIL.get());
                output.accept(ModBlocks.LOTUS_ROOTS.get());
                output.accept(ModItems.BLOSSOM_GRASS_ITEM.get());
                output.accept(ModItems.GLOW_BERRIES_ITEM.get());
                output.accept(ModItems.BLESSING_NODULE_ITEM.get());
                output.accept(ModItems.BLESSING_SOIL_ITEM.get());
                output.accept(ModItems.LOTUS_LOG_ITEM.get());
                output.accept(ModItems.LOTUS_LEAVES_ITEM.get());
                output.accept(ModItems.BLESSING_LOG_ITEM.get());
                output.accept(ModItems.BLESSING_LEAVES_ITEM.get());
                output.accept(ModItems.LOTUS_AXE.get());
                output.accept(ModItems.LOTUS_SWORD.get());
                output.accept(ModItems.LOTUS_HOE.get());
                output.accept(ModItems.LOTUS_HELMET.get());
                output.accept(ModItems.LOTUS_CHESTPLATE.get());
                output.accept(ModItems.LOTUS_LEGGINGS.get());
                output.accept(ModItems.LOTUS_BOOTS.get());
                output.accept(ModItems.LOTUS_GRAFTING_ROD.get());
                output.accept(ModItems.LOTUS_SEER_LENS.get());
            }).build());

    public LotusBlight(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        ModFluids.FLUID_TYPES.register(modBus);
        ModFluids.FLUIDS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        ModVillagers.POI_TYPES.register(modBus);
        ModVillagers.PROFESSIONS.register(modBus);
        BiomeManager.addAdditionalOverworldBiomes(ModBiomes.LOTUS_BIOME);
        BiomeManager.addAdditionalOverworldBiomes(ModBiomes.BLESSING_BIOME);
        TABS.register(modBus);
        modBus.addListener(this::addVanillaCreativeItems);
        modBus.addListener(this::registerRenderers);
        context.registerConfig(ModConfig.Type.COMMON, LotusConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(new LotusEvents());
        MinecraftForge.EVENT_BUS.register(new InfectionSpreadEngine());
        MinecraftForge.EVENT_BUS.register(new BarrierEvents());
        MinecraftForge.EVENT_BUS.register(new RootGrowthEngine());
        MinecraftForge.EVENT_BUS.register(new GuaranteedSpawnManager());
        com.lotusblight.map.NetworkHandler.register();
        CriteriaTriggers.register(SingleBiomeWorldTrigger.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new JourneyMapSyncTicker());

        // Config-screen button in the mods list — soft dependency, only touches YACL classes
        // (client-only, and absent unless the player installed YACL themselves) after confirming
        // both the physical side and the mod's actual presence.
        if (ModList.get().isLoaded("yet_another_config_lib_v3")) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                            () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> com.lotusblight.client.config.LotusConfigScreen.create(parent))));
        }
    }

    private void registerRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.LOTUS_CROWN.get(), context -> new com.lotusblight.client.gecko.LotusCrownBlockRenderer());
    }

    private void addVanillaCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.LOTUS_MAP.get());
            event.accept(ModItems.LOTUS_WIKI.get());
            event.accept(ModItems.CLEANSING_POWDER.get());
        }
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(ModItems.LOTUS_SEED.get());
        }
    }
}
