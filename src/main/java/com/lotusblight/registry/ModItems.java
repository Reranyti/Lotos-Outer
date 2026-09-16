package com.lotusblight.registry;

import com.lotusblight.LotusBlight;
import com.lotusblight.item.LotusMapItem;
import com.lotusblight.item.LotusSeedItem;
import com.lotusblight.item.LotusWikiItem;
import com.lotusblight.item.LotusPickaxeItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.food.FoodProperties;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, LotusBlight.MODID);
    public static final RegistryObject<Item> LOTUS_SEED = ITEMS.register("lotus_seed", () -> new LotusSeedItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> INFECTED_WATER_BUCKET = ITEMS.register("infected_water_bucket", () -> new BucketItem(ModFluids.INFECTED_WATER, new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET)));
    public static final RegistryObject<Item> LOTUS_MAP = ITEMS.register("lotus_map", () -> new LotusMapItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> LOTUS_WIKI = ITEMS.register("lotus_wiki", () -> new LotusWikiItem(new Item.Properties()));
    public static final RegistryObject<Item> LOTUS_MIMIC_ITEM = ITEMS.register("lotus_mimic", () -> new BlockItem(ModBlocks.LOTUS_MIMIC.get(), new Item.Properties()));
    public static final RegistryObject<Item> CLEANSING_POWDER = ITEMS.register("cleansing_powder", () -> new Item(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> INFECTED_LOTUS_ITEM = ITEMS.register("infected_lotus", () -> new BlockItem(ModBlocks.INFECTED_LOTUS.get(), new Item.Properties()));
    public static final RegistryObject<Item> INFECTED_SOIL_ITEM = ITEMS.register("infected_soil", () -> new BlockItem(ModBlocks.INFECTED_SOIL.get(), new Item.Properties()));
    public static final RegistryObject<Item> LOTUS_ORE_ITEM = ITEMS.register("lotus_ore", () -> new BlockItem(ModBlocks.LOTUS_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> LOTUS_ALLOY = ITEMS.register("lotus_alloy", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> LOTUS_PICKAXE = ITEMS.register("lotus_pickaxe", () -> new LotusPickaxeItem(Tiers.DIAMOND, 4, -2.7f, new Item.Properties().durability(900)));
    public static final RegistryObject<Item> LOTUS_ROOTS_ITEM = ITEMS.register("lotus_roots", () -> new BlockItem(ModBlocks.LOTUS_ROOTS.get(), new Item.Properties()));
    public static final RegistryObject<Item> LOTUS_HEART_ITEM = ITEMS.register("lotus_heart", () -> new BlockItem(ModBlocks.LOTUS_HEART.get(), new Item.Properties()));
    public static final RegistryObject<Item> BLOSSOM_GRASS_ITEM = ITEMS.register("blossom_grass", () -> new BlockItem(ModBlocks.BLOSSOM_GRASS.get(), new Item.Properties()));
    public static final RegistryObject<Item> GLOW_BERRIES_ITEM = ITEMS.register("glow_berries", () -> new BlockItem(ModBlocks.GLOW_BERRIES.get(), new Item.Properties()));
    public static final RegistryObject<Item> GLOW_BERRY_FOOD = ITEMS.register("glowing_berry", () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(3).saturationMod(0.35f).alwaysEat().build())));
    public static final RegistryObject<Item> BLESSING_NODULE_ITEM = ITEMS.register("blessing_nodule", () -> new BlockItem(ModBlocks.BLESSING_NODULE.get(), new Item.Properties()));
    public static final RegistryObject<Item> BLESSING_SOIL_ITEM = ITEMS.register("blessing_soil", () -> new BlockItem(ModBlocks.BLESSING_SOIL.get(), new Item.Properties()));
    public static final RegistryObject<Item> LOTUS_LOG_ITEM = ITEMS.register("lotus_log", () -> new BlockItem(ModBlocks.LOTUS_LOG.get(), new Item.Properties()));
    public static final RegistryObject<Item> LOTUS_LEAVES_ITEM = ITEMS.register("lotus_leaves", () -> new BlockItem(ModBlocks.LOTUS_LEAVES.get(), new Item.Properties()));
    public static final RegistryObject<Item> BLESSING_LOG_ITEM = ITEMS.register("blessing_log", () -> new BlockItem(ModBlocks.BLESSING_LOG.get(), new Item.Properties()));
    public static final RegistryObject<Item> BLESSING_LEAVES_ITEM = ITEMS.register("blessing_leaves", () -> new BlockItem(ModBlocks.BLESSING_LEAVES.get(), new Item.Properties()));

    private ModItems() {}
}
