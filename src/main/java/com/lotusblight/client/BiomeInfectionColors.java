package com.lotusblight.client;

import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Per-biome, per-material color table for the infected ground tint (see InfectedGroundColor).
 * "создать КУЧА биомов и каждый биом что есть своя вариация заражения используя всё что у нас
 * есть" - instead of 8 shared group buckets, every biome with an entry in {@link BiomeFogColors}
 * (~50 of them, already hand-authored, distinct per biome) now derives its own SOIL/TERRACOTTA/
 * LOG/LEAVES colors by sampling ITS OWN gradient at a different point per material, so the 4
 * organic materials read as related-but-distinct shades of that one biome's own palette instead of
 * one of 8 shared looks. STONE/SAND/GRAVEL stay a single shared color everywhere on purpose - see
 * UNIFORM_STONE/SAND/GRAVEL - these are plain minerals, not biome-tied organics. A specific biome
 * override (BIOME_OVERRIDE, e.g. birch wood, mangrove mud) still wins over its gradient-derived
 * default when one exists. The old 8-bucket GROUP_COLORS table is now only a fallback for a biome
 * with no fog gradient dictated at all (a modded biome this mod doesn't know about yet).
 */
public final class BiomeInfectionColors {
    public static final int NO_TINT = 0xFFFFFF;

    /** Shared, non-biome-tied color for STONE/SAND/GRAVEL - see the class doc on why these three don't get per-biome variety like the organic materials do. */
    private static final int UNIFORM_STONE = 0x7A6B7F;
    private static final int UNIFORM_SAND = 0x5C4266;
    private static final int UNIFORM_GRAVEL = 0x6E5C75;

    /** Where along a biome's own fog gradient (0=first stop, 1=last stop) each organic material samples - spread out so all four read as distinct shades within the same biome, not four copies of one point. */
    private static final Map<InfectedMaterial, Float> GRADIENT_SAMPLE_POINT = Map.of(
            InfectedMaterial.SOIL, 0.15f,
            InfectedMaterial.TERRACOTTA, 0.5f,
            InfectedMaterial.LOG, 0.75f,
            InfectedMaterial.LEAVES, 0.35f
    );

    private static final Map<ResourceLocation, String> BIOME_GROUP = new HashMap<>();
    private static final Map<String, EnumMap<InfectedMaterial, Integer>> GROUP_COLORS = new HashMap<>();
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

        // Stone/sand/gravel are plain minerals, not biome-tied organics like grass/leaves/wood -
        // there's no real reason infected stone should look different in a taiga than in a jungle,
        // so unlike SOIL/TERRACOTTA/LOG/LEAVES (which genuinely do vary per biome below), these
        // three get ONE shared, deliberately non-blue color used everywhere. The original bug
        // wasn't "not enough per-biome variety" - it was that this shared color happened to be a
        // washed-out blue (0x24305C for sand) instead of something that actually reads as infected.
        colors("forest_plains",
                InfectedMaterial.STONE, UNIFORM_STONE,
                InfectedMaterial.SAND, UNIFORM_SAND,
                InfectedMaterial.GRAVEL, UNIFORM_GRAVEL,
                InfectedMaterial.SOIL, 0xAB92CC,        // grass_block_top.png (tinted with plains grass color, since the raw texture is a near-grey biome-tint mask)
                InfectedMaterial.TERRACOTTA, 0x67A1BB,  // terracotta.png
                InfectedMaterial.LOG, 0x92AACC,         // oak_log.png
                InfectedMaterial.LEAVES, 0xD699F1);     // oak_leaves.png (tinted with default foliage color, same mask situation as grass_block_top)

        colors("taiga",
                InfectedMaterial.STONE, UNIFORM_STONE,
                InfectedMaterial.SAND, UNIFORM_SAND,
                InfectedMaterial.GRAVEL, UNIFORM_GRAVEL,
                InfectedMaterial.SOIL, 0xAB92CC,        // grass_block_top.png (tinted plains grass)
                InfectedMaterial.TERRACOTTA, 0x2D4D5E,  // white_terracotta.png
                InfectedMaterial.LOG, 0xC4D9EE,         // spruce_log.png
                InfectedMaterial.LEAVES, 0xD699F1);     // oak_leaves.png (default foliage tint)

        colors("cold",
                InfectedMaterial.STONE, UNIFORM_STONE,
                InfectedMaterial.SAND, UNIFORM_SAND,
                InfectedMaterial.GRAVEL, UNIFORM_GRAVEL,
                InfectedMaterial.SOIL, 0x060101,        // snow.png
                InfectedMaterial.TERRACOTTA, 0x78949D,  // light_gray_terracotta.png
                InfectedMaterial.LOG, 0xC4D9EE,         // spruce_log.png
                InfectedMaterial.LEAVES, 0xD699F1);     // oak_leaves.png (default foliage tint)

        colors("hot_dry",
                InfectedMaterial.STONE, UNIFORM_STONE,
                InfectedMaterial.SAND, UNIFORM_SAND,
                InfectedMaterial.GRAVEL, UNIFORM_GRAVEL,
                InfectedMaterial.SOIL, 0x799FBC,        // dirt.png
                InfectedMaterial.TERRACOTTA, 0x5DABD9,  // orange_terracotta.png
                InfectedMaterial.LOG, 0x989EA8,         // acacia_log.png
                InfectedMaterial.LEAVES, 0xD699F1);     // oak_leaves.png (default foliage tint)

        // Badlands doesn't actually look like the rest of hot_dry for its ORGANIC materials - it's
        // dominated by a rainbow of terracotta color bands, not one flat orange. Blended across
        // orange/yellow/red/white/brown/plain terracotta instead of picking just one color. Sand
        // itself stays the shared UNIFORM_SAND, same reasoning as everywhere else.
        for (String badlandsVariant : new String[]{"minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands"}) {
            biomeColor(badlandsVariant,
                    InfectedMaterial.TERRACOTTA, 0x649BBF); // blend of orange/yellow/red/white/brown/plain terracotta
        }

        colors("hills",
                InfectedMaterial.STONE, UNIFORM_STONE,
                InfectedMaterial.SAND, UNIFORM_SAND,
                InfectedMaterial.GRAVEL, UNIFORM_GRAVEL,
                InfectedMaterial.SOIL, 0xAB92CC,        // grass_block_top.png (tinted plains grass)
                InfectedMaterial.TERRACOTTA, 0xC5D5DB,  // gray_terracotta.png
                InfectedMaterial.LOG, 0x92AACC,         // oak_log.png
                InfectedMaterial.LEAVES, 0xD699F1);     // oak_leaves.png (default foliage tint)

        colors("jungle",
                InfectedMaterial.STONE, UNIFORM_STONE,
                InfectedMaterial.SAND, UNIFORM_SAND,
                InfectedMaterial.GRAVEL, UNIFORM_GRAVEL,
                InfectedMaterial.SOIL, 0xAB92CC,        // grass_block_top.png (tinted plains grass)
                InfectedMaterial.TERRACOTTA, 0xB3ACD5,  // green_terracotta.png
                InfectedMaterial.LOG, 0xAABBE6,         // jungle_log.png
                InfectedMaterial.LEAVES, 0xD699F1);     // oak_leaves.png (default foliage tint)

        colors("swamp_water",
                InfectedMaterial.STONE, UNIFORM_STONE,
                InfectedMaterial.SAND, UNIFORM_SAND,
                InfectedMaterial.GRAVEL, UNIFORM_GRAVEL,
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
                InfectedMaterial.STONE, UNIFORM_STONE,
                InfectedMaterial.SAND, UNIFORM_SAND,
                InfectedMaterial.GRAVEL, UNIFORM_GRAVEL,
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

    /** One biome's own color for specific materials only - everything else it still inherits from its group. */
    private static void biomeColor(String biomeId, Object... materialColorPairs) {
        EnumMap<InfectedMaterial, Integer> map = BIOME_OVERRIDE.computeIfAbsent(new ResourceLocation(biomeId), b -> new EnumMap<>(InfectedMaterial.class));
        for (int i = 0; i < materialColorPairs.length; i += 2) {
            map.put((InfectedMaterial) materialColorPairs[i], (Integer) materialColorPairs[i + 1]);
        }
    }

    public static int colorFor(ResourceLocation biomeId, InfectedMaterial material) {
        // Plain minerals, not biome-tied organics - same tone everywhere, see the class doc.
        if (material == InfectedMaterial.STONE) return UNIFORM_STONE;
        if (material == InfectedMaterial.SAND) return UNIFORM_SAND;
        if (material == InfectedMaterial.GRAVEL) return UNIFORM_GRAVEL;

        EnumMap<InfectedMaterial, Integer> ownColors = BIOME_OVERRIDE.get(biomeId);
        if (ownColors != null && ownColors.containsKey(material)) {
            return ownColors.get(material);
        }
        // Every biome with a dictated fog gradient (basically all of them, see BiomeFogColors) now
        // derives its organic-material colors straight from ITS OWN gradient by default - true
        // per-biome variety instead of 8 shared group looks, reusing color data this mod already
        // hand-authored rather than inventing ~200 new entries from scratch.
        int[] gradient = BiomeFogColors.gradientFor(biomeId);
        if (gradient != null) {
            Float samplePoint = GRADIENT_SAMPLE_POINT.get(material);
            if (samplePoint != null) {
                return BiomeFogColors.sample(gradient, samplePoint);
            }
        }
        // No fog gradient dictated for this biome at all (a modded biome this mod doesn't know
        // about yet) - fall back to the old 8-bucket group table, or NO_TINT if it's not in one either.
        String groupId = BIOME_GROUP.get(biomeId);
        if (groupId == null) return NO_TINT;
        EnumMap<InfectedMaterial, Integer> materials = GROUP_COLORS.get(groupId);
        if (materials == null) return NO_TINT;
        return materials.getOrDefault(material, NO_TINT);
    }
}
