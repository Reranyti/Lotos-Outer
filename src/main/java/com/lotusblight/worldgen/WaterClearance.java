package com.lotusblight.worldgen;

import com.lotusblight.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluids;

/**
 * The main lotus anchor (LotusMainBlock) is now visually a big lily pad that
 * spans well beyond its own 1x1 footprint — placing it right at a shore
 * would clip into land. This checks that a candidate water position has open
 * water on every side within {@link #REQUIRED_RADIUS} blocks before any
 * anchor-placing code commits to it. Ordinary decorative shoots (LOTUS_SHOOT)
 * are NOT subject to this — they're small and fine right at the water's edge.
 */
public final class WaterClearance {
    public static final int REQUIRED_RADIUS = 2;

    private WaterClearance() {}

    /** Was Fluids.WATER only - a pond partway infected (INFECTED_WATER) failed this check on tiles that are still very much open water, just the wrong fluid type, making seeds refuse to plant on perfectly good water near any existing outbreak. */
    public static boolean hasClearWaterAround(ServerLevel level, BlockPos waterPos, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                var fluid = level.getFluidState(waterPos.offset(dx, 0, dz));
                if (!fluid.is(Fluids.WATER) && !fluid.is(ModFluids.INFECTED_WATER.get())) {
                    return false;
                }
            }
        }
        return true;
    }
}
