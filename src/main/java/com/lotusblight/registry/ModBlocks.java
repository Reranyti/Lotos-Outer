package com.lotusblight.registry;

import com.lotusblight.LotusBlight;
import com.lotusblight.world.BlessingNoduleBlock;
import com.lotusblight.world.LotusBloomBlock;
import com.lotusblight.world.LotusMainBlock;
import com.lotusblight.world.LotusShootBlock;
import com.lotusblight.world.LotusMimicBlock;
import com.lotusblight.world.GlowBerryBushBlock;
import com.lotusblight.world.LotusRootsBlock;
import com.lotusblight.world.LianaBarrierBlock;
import com.lotusblight.world.LianaWeakPointBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, LotusBlight.MODID);

    public static final RegistryObject<Block> INFECTED_LOTUS = BLOCKS.register("infected_lotus", () -> new LotusMainBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(-1.0f, 3600000.0f).sound(SoundType.GRASS).noOcclusion()));
    public static final RegistryObject<Block> LOTUS_MIMIC = BLOCKS.register("lotus_mimic", () -> new LotusMimicBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.25f).sound(SoundType.GRASS).noOcclusion()));
    public static final RegistryObject<Block> LOTUS_SHOOT = BLOCKS.register("lotus_shoot", () -> new LotusShootBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(-1.0f, 3600000.0f).sound(SoundType.GRASS).noOcclusion()));
    public static final RegistryObject<Block> INFECTED_SOIL = BLOCKS.register("infected_soil", () -> new com.lotusblight.world.InfectedGroundBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.6f).sound(SoundType.GRASS)));
    public static final RegistryObject<Block> LOTUS_STONE = BLOCKS.register("lotus_stone", () -> new com.lotusblight.world.InfectedGroundBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(1.5f).sound(SoundType.STONE)));
    public static final RegistryObject<Block> LOTUS_SAND = BLOCKS.register("lotus_sand", () -> new com.lotusblight.world.InfectedGroundBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(0.5f).sound(SoundType.SAND)));
    public static final RegistryObject<Block> LOTUS_TERRACOTTA = BLOCKS.register("lotus_terracotta", () -> new com.lotusblight.world.InfectedGroundBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(1.25f).sound(SoundType.STONE)));
    public static final RegistryObject<Block> LOTUS_GRAVEL = BLOCKS.register("lotus_gravel", () -> new com.lotusblight.world.InfectedGroundBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.6f).sound(SoundType.GRAVEL)));
    public static final RegistryObject<Block> LOTUS_ORE = BLOCKS.register("lotus_ore", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(3.0f, 6.0f).sound(SoundType.STONE)));
    public static final RegistryObject<Block> LOTUS_ROOTS = BLOCKS.register("lotus_roots", () -> new LotusRootsBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.4f).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Block> LOTUS_HEART = BLOCKS.register("lotus_heart", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(3.0f).sound(SoundType.AMETHYST).lightLevel(state -> 6)));
    public static final RegistryObject<Block> GLOW_BERRIES = BLOCKS.register("glow_berries", () -> new GlowBerryBushBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.1f).sound(SoundType.GRASS).lightLevel(net.minecraft.world.level.block.CaveVines.emission(8)).noCollission().noOcclusion()));
    public static final RegistryObject<Block> BLESSING_NODULE = BLOCKS.register("blessing_nodule", () -> new BlessingNoduleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.0f).sound(SoundType.AMETHYST).lightLevel(state -> 4)));
    public static final RegistryObject<Block> BLESSING_SOIL = BLOCKS.register("blessing_soil", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.7f).sound(SoundType.GRAVEL)));
    public static final RegistryObject<Block> BLESSING_SAND = BLOCKS.register("blessing_sand", () -> new com.lotusblight.world.BlessingSandBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.5f).sound(SoundType.SAND)));
    // "ультра сильный блесковый биом" from a StarFall meteor impact (war branch, no grafting rod) - a
    // stronger, purple-toned variant, not the ordinary Blessing patch.
    public static final RegistryObject<Block> BLESSING_SOIL_PURPLE = BLOCKS.register("blessing_soil_purple", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(0.7f).sound(SoundType.GRAVEL)));
    public static final RegistryObject<Block> BLESSING_SAND_PURPLE = BLOCKS.register("blessing_sand_purple", () -> new com.lotusblight.world.BlessingSandBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(0.5f).sound(SoundType.SAND)));
    public static final RegistryObject<Block> METEORITE_STONE = BLOCKS.register("meteorite_stone", () -> new com.lotusblight.world.MeteoriteStoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(4.0f).requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK)));
    public static final RegistryObject<Block> LOTUS_LOG = BLOCKS.register("lotus_log", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(2.0f).sound(SoundType.WOOD)));
    public static final RegistryObject<Block> LOTUS_LEAVES = BLOCKS.register("lotus_leaves", () -> new com.lotusblight.world.LotusLeavesBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.2f).sound(SoundType.GRASS).noOcclusion()));
    public static final RegistryObject<Block> BLESSING_LEAVES = BLOCKS.register("blessing_leaves", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.2f).sound(SoundType.GLASS).noOcclusion()));
    public static final RegistryObject<Block> LIANA_BARRIER = BLOCKS.register("liana_barrier", () -> new LianaBarrierBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(-1.0f, 3600000.0f).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Block> LIANA_WEAK_POINT = BLOCKS.register("liana_weak_point", () -> new LianaWeakPointBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(1.5f).sound(SoundType.WOOD).noOcclusion()));

    private ModBlocks() {}
}
