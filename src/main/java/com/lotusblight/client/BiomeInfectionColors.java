package com.lotusblight.client;

import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-biome, per-material color table for the infected ground tint (see InfectedGroundColor).
 * 49 biomes x 5 materials would be 245 hand-dictated entries - instead, biomes are grouped into
 * the same handful of families already used for the fog gradient list (Лес/равнины, Тайга,
 * Холодные, ...), and colors are dictated once per (group, material) pair. A biome with no group
 * assignment yet falls back to NO_TINT.
 *
 * Some biomes stand out enough from their group's shared look that they shouldn't just inherit
 * it (e.g. Ice Spikes inside the "cold" group) - those are listed in GRADIENT_OVERRIDE and instead
 * derive their color straight from that biome's own fog gradient (see BiomeFogColors), sampled at
 * a fixed position per material so the 5 materials still read as related shades of the same
 * biome rather than 5 unrelated colors.
 */
public final class BiomeInfectionColors {
    public static final int NO_TINT = 0xFFFFFF;

    /** Where along a biome's own fog gradient (0=first stop, 1=last stop) each material samples, for GRADIENT_OVERRIDE biomes. */
    private static final Map<InfectedMaterial, Float> GRADIENT_SAMPLE_POINT = Map.of(
            InfectedMaterial.SOIL, 0.15f,
            InfectedMaterial.SAND, 0.35f,
            InfectedMaterial.GRAVEL, 0.5f,
            InfectedMaterial.STONE, 0.7f,
            InfectedMaterial.TERRACOTTA, 0.9f
    );

    private static final Map<ResourceLocation, String> BIOME_GROUP = new HashMap<>();
    private static final Map<String, EnumMap<InfectedMaterial, Integer>> GROUP_COLORS = new HashMap<>();
    private static final Set<ResourceLocation> GRADIENT_OVERRIDE = new HashSet<>();
    /** Per-biome, per-material overrides (e.g. Dark Forest's own wood/terracotta differs from the rest of forest_plains) - everything NOT overridden here still falls through to the biome's group color. */
    private static final Map<ResourceLocation, EnumMap<InfectedMaterial, Integer>> BIOME_OVERRIDE = new HashMap<>();

    static {
        group("forest_plains", "minecraft:plains", "minecraft:sunflower_plains", "minecraft:forest",
                "minecraft:flower_forest", "minecraft:birch_forest", "minecraft:old_growth_birch_forest",
                "minecraft:dark_forest", "minecraft:meadow");
        group("taiga", "minecraft:taiga", "minecraft:snowy_taiga",
                "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga");
        group("cold", "minecraft:snowy_plains", "minecraft:ice_spikes", "minecraft:grove",
                "minecraft:snowy_slopes", "minecraft:frozen_peaks", "minecraft:jagged_peaks",
                "minecraft:stony_peaks", "minecraft:snowy_beach", "minecraft:frozen_river");
        group("hot_dry", "minecraft:desert", "minecraft:savanna", "minecraft:savanna_plateau",
                "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands");
        group("hills", "minecraft:windswept_hills", "minecraft:windswept_gravelly_hills",
                "minecraft:windswept_forest", "minecraft:windswept_savanna");
        group("jungle", "minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle");
        group("swamp_water", "minecraft:swamp", "minecraft:mangrove_swamp", "minecraft:river",
                "minecraft:beach", "minecraft:stony_shore", "minecraft:mushroom_fields",
                "streamsreflowing:stream");
        group("ocean", "minecraft:ocean", "minecraft:deep_ocean", "minecraft:warm_ocean",
                "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean", "minecraft:cold_ocean",
                "minecraft:deep_cold_ocean", "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean");

        // Each group's colors are the INVERTED average color of a hand-picked vanilla texture per
        // material (see BiomeInfectionColors's own class doc) - not a directly dictated color.
        colors("forest_plains",
                InfectedMaterial.STONE, 0x818181,      // stone.png
                InfectedMaterial.SAND, 0x24305C,        // sand.png
                InfectedMaterial.GRAVEL, 0x7B8080,      // gravel.png
                InfectedMaterial.SOIL, 0xAB92CC,        // grass_block_top.png (tinted with plains grass color, since the raw texture is a near-grey biome-tint mask)
                InfectedMaterial.TERRACOTTA, 0x67A1BB,  // terracotta.png
                InfectedMaterial.LOG, 0x92AACC,         // oak_log.png
                InfectedMaterial.LEAVES, 0xD699F1);     // oak_leaves.png (tinted with default foliage color, same mask situation as grass_block_top)

        colors("taiga",
                InfectedMaterial.STONE, 0x818181,       // stone.png
                InfectedMaterial.SAND, 0x24305C,        // sand.png
                InfectedMaterial.GRAVEL, 0x7B8080,      // gravel.png
                InfectedMaterial.SOIL, 0xAB92CC,        // grass_block_top.png (tinted plains grass)
                InfectedMaterial.TERRACOTTA, 0x2D4D5E,  // white_terracotta.png
                InfectedMaterial.LOG, 0xC4D9EE,         // spruce_log.png
                InfectedMaterial.LEAVES, 0xD699F1);     // oak_leaves.png (default foliage tint)

        colors("cold",
                InfectedMaterial.STONE, 0x818181,       // stone.png
                InfectedMaterial.SAND, 0x24305C,        // sand.png
                InfectedMaterial.GRAVEL, 0x7B8080,      // gravel.png
                InfectedMaterial.SOIL, 0x060101,        // snow.png
                InfectedMaterial.TERRACOTTA, 0x78949D,  // light_gray_terracotta.png
                InfectedMaterial.LOG, 0xC4D9EE,         // spruce_log.png
                InfectedMaterial.LEAVES, 0xD699F1);     // oak_leaves.png (default foliage tint)

        // Ice Spikes is the class doc's own motivating example for GRADIENT_OVERRIDE, but useGradient()
        // was never actually called anywhere - it stood out enough from the rest of "cold" to be
        // called out in the doc, but every world still rendered it with the flat cold-group tint.
        useGradient("minecraft:ice_spikes");

        colors("hot_dry",
                InfectedMaterial.STONE, 0x818181,       // stone.png
                InfectedMaterial.SAND, 0x24305C,        // sand.png (real deserts are yellow sand, not red - red_sand is a badlands thing, see override below)
                InfectedMaterial.GRAVEL, 0x7B8080,      // gravel.png
                InfectedMaterial.SOIL, 0x799FBC,        // dirt.png
                InfectedMaterial.TERRACOTTA, 0x5DABD9,  // orange_terracotta.png
                InfectedMaterial.LOG, 0x989EA8,         // acacia_log.png
                InfectedMaterial.LEAVES, 0xD699F1);     // oak_leaves.png (default foliage tint)

        // Badlands doesn't actually look like the rest of hot_dry - it's dominated by RED sand
        // (not yellow) and a rainbow of terracotta color bands, not one flat orange. Blended
        // across orange/yellow/red/white/brown/plain terracotta instead of picking just one color.
        for (String badlandsVariant : new String[]{"minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands"}) {
            biomeColor(badlandsVariant,
                    InfectedMaterial.SAND, 0x4098DE,        // red_sand.png
                    InfectedMaterial.TERRACOTTA, 0x649BBF); // blend of orange/yellow/red/white/brown/plain terracotta
        }

        colors("hills",
                InfectedMaterial.STONE, 0x818181,       // stone.png
                InfectedMaterial.SAND, 0x24305C,        // sand.png
                InfectedMaterial.GRAVEL, 0x7B8080,      // gravel.png
                InfectedMaterial.SOIL, 0xAB92CC,        // grass_block_top.png (tinted plains grass)
                InfectedMaterial.TERRACOTTA, 0xC5D5DB,  // gray_terracotta.png
                InfectedMaterial.LOG, 0x92AACC,         // oak_log.png
                InfectedMaterial.LEAVES, 0xD699F1);     // oak_leaves.png (default foliage tint)

        colors("jungle",
                InfectedMaterial.STONE, 0x818181,       // stone.png
                InfectedMaterial.SAND, 0x24305C,        // sand.png
                InfectedMaterial.GRAVEL, 0x7B8080,      // gravel.png
                InfectedMaterial.SOIL, 0xAB92CC,        // grass_block_top.png (tinted plains grass)
                InfectedMaterial.TERRACOTTA, 0xB3ACD5,  // green_terracotta.png
                InfectedMaterial.LOG, 0xAABBE6,         // jungle_log.png
                InfectedMaterial.LEAVES, 0xD699F1);     // oak_leaves.png (default foliage tint)

        colors("swamp_water",
                InfectedMaterial.STONE, 0x818181,       // stone.png
                InfectedMaterial.SAND, 0x24305C,        // sand.png
                InfectedMaterial.GRAVEL, 0x7B8080,      // gravel.png
                InfectedMaterial.SOIL, 0xC2BEDE,        // grass_block_top.png (tinted with the special swamp grass/foliage color)
                InfectedMaterial.TERRACOTTA, 0xB2CCDB,  // brown_terracotta.png
                InfectedMaterial.LOG, 0x92AACC,         // oak_log.png (mangrove_swamp gets its own override below)
                InfectedMaterial.LEAVES, 0xD699F1);     // oak_leaves.png (default foliage tint)

        // Birch/Dark Forest have their own distinct wood - not oak like the rest of forest_plains.
        biomeColor("minecraft:birch_forest", InfectedMaterial.LOG, 0x26282D, InfectedMaterial.LEAVES, 0xBDAAD4);
        biomeColor("minecraft:old_growth_birch_forest", InfectedMaterial.LOG, 0x26282D, InfectedMaterial.LEAVES, 0xBDAAD4);
        biomeColor("minecraft:dark_forest", InfectedMaterial.LOG, 0xC3D0E5, InfectedMaterial.LEAVES, 0xD494F1);

        // Mangrove Swamp doesn't actually sit on the rest of swamp_water's grass - it generates on
        // real MUD, with its own tree (mangrove_log) and its own leaves texture (also a tint mask,
        // like grass_block_top, tinted with the same special swamp color here).
        biomeColor("minecraft:mangrove_swamp",
                InfectedMaterial.SOIL, 0xC3C6C2,    // mud.png
                InfectedMaterial.LOG, 0xABBCD6,     // mangrove_log.png
                InfectedMaterial.LEAVES, 0xC9C7E2); // mangrove_leaves.png (tinted with swamp color)

        colors("ocean",
                InfectedMaterial.STONE, 0x9C6368,       // prismarine.png
                InfectedMaterial.SAND, 0x24305C,        // sand.png
                InfectedMaterial.GRAVEL, 0x7B8080,      // gravel.png
                InfectedMaterial.SOIL, 0x799FBC,        // dirt.png
                InfectedMaterial.TERRACOTTA, 0xA8A4A4,  // cyan_terracotta.png
                InfectedMaterial.LOG, 0x92AACC,         // oak_log.png (no real trees in ocean, filler)
                InfectedMaterial.LEAVES, 0xD699F1);     // oak_leaves.png (default foliage tint)
    }

    private BiomeInfectionColors() {}

    private static void group(String groupId, String... biomeIds) {
        for (String id : biomeIds) {
            BIOME_GROUP.put(new ResourceLocation(id), groupId);
        }
    }

    private static void colors(String groupId, Object... materialColorPairs) {
        EnumMap<InfectedMaterial, Integer> map = GROUP_COLORS.computeIfAbsent(groupId, g -> new EnumMap<>(InfectedMaterial.class));
        for (int i = 0; i < materialColorPairs.length; i += 2) {
            map.put((InfectedMaterial) materialColorPairs[i], (Integer) materialColorPairs[i + 1]);
        }
    }

    /** Marks biomes that skip their group's shared color and instead derive one from their own fog gradient (see class doc). */
    private static void useGradient(String... biomeIds) {
        for (String id : biomeIds) {
            GRADIENT_OVERRIDE.add(new ResourceLocation(id));
        }
    }

    /** One biome's own color for specific materials only - everything else it still inherits from its group. */
    private static void biomeColor(String biomeId, Object... materialColorPairs) {
        EnumMap<InfectedMaterial, Integer> map = BIOME_OVERRIDE.computeIfAbsent(new ResourceLocation(biomeId), b -> new EnumMap<>(InfectedMaterial.class));
        for (int i = 0; i < materialColorPairs.length; i += 2) {
            map.put((InfectedMaterial) materialColorPairs[i], (Integer) materialColorPairs[i + 1]);
        }
    }

    public static int colorFor(ResourceLocation biomeId, InfectedMaterial material) {
        EnumMap<InfectedMaterial, Integer> ownColors = BIOME_OVERRIDE.get(biomeId);
        if (ownColors != null && ownColors.containsKey(material)) {
            return ownColors.get(material);
        }
        // GRADIENT_SAMPLE_POINT only covers SOIL/SAND/GRAVEL/STONE/TERRACOTTA - LOG/LEAVES have no
        // sample point defined, so GRADIENT_SAMPLE_POINT.get(material) would be null and unboxing it
        // into sample(int[], float) would NPE. Those two materials fall through to the group color
        // like any other biome without a gradient override.
        if (GRADIENT_OVERRIDE.contains(biomeId) && GRADIENT_SAMPLE_POINT.containsKey(material)) {
            int[] gradient = BiomeFogColors.gradientFor(biomeId);
            if (gradient != null) {
                return BiomeFogColors.sample(gradient, GRADIENT_SAMPLE_POINT.get(material));
            }
            // Flagged for override but has no fog gradient of its own yet - fall through to the group.
        }
        String groupId = BIOME_GROUP.get(biomeId);
        if (groupId == null) return NO_TINT;
        EnumMap<InfectedMaterial, Integer> materials = GROUP_COLORS.get(groupId);
        if (materials == null) return NO_TINT;
        return materials.getOrDefault(material, NO_TINT);
    }
}
