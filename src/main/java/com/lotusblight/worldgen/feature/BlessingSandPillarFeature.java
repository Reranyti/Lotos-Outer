package com.lotusblight.worldgen.feature;

import com.lotusblight.registry.ModBlocks;
import com.lotusblight.world.GlowBerryBushBlock;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The Blessing biome's "waypoint" landmark - a standing pillar of blessing_sand that reads from a
 * distance as a marker, not just more ground. One glow_berries vine is pinned to a side face
 * roughly a third of the way up: BlessingSandBlock treats any face touching glow_berries as
 * support and skips falling, so every sand block ABOVE that vine is held up by it and everything
 * has ordinary ground/gravity support below it. Breaking that one vine collapses the whole column
 * above it - the vine is both the pillar's only visible "weak point" and its reward (see the
 * matching advancement for picking a glowing_berry off one of these).
 */
public class BlessingSandPillarFeature extends Feature<NoneFeatureConfiguration> {
    private static final int MIN_HEIGHT = 5;
    private static final int MAX_HEIGHT = 9;

    public BlessingSandPillarFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        if (!level.getBlockState(origin.below()).isAir() && level.getBlockState(origin.below()).getFluidState().isEmpty()) {
            // Needs solid ground under the very first block, same as any other standing column.
        } else {
            return false;
        }

        int height = MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1);
        int vineHeight = Math.max(1, height / 3);
        Direction vineFace = Direction.Plane.HORIZONTAL.getRandomDirection(random);

        for (int i = 0; i < height; i++) {
            BlockPos pos = origin.above(i);
            if (!level.getBlockState(pos).isAir()) break;
            setBlock(level, pos, ModBlocks.BLESSING_SAND.get().defaultBlockState());
            if (i == vineHeight) {
                BlockPos vinePos = pos.relative(vineFace);
                if (level.getBlockState(vinePos).isAir()) {
                    GlowBerryBushBlock vineBlock = (GlowBerryBushBlock) ModBlocks.GLOW_BERRIES.get();
                    setBlock(level, vinePos, vineBlock.wallMountedState(vineFace));
                }
            }
        }
        return true;
    }
}
