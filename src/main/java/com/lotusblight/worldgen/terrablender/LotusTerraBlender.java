package com.lotusblight.worldgen.terrablender;

import com.lotusblight.LotusBlight;
import net.minecraft.resources.ResourceLocation;
import terrablender.api.Regions;

/**
 * The only place outside this package that touches TerraBlender types. LotusBlight used to build
 * the LotusRegion right in its own method, and verifying LotusBlight then needed
 * terrablender.api.Region on the classpath before any isLoaded check could run.
 */
public final class LotusTerraBlender {
    private LotusTerraBlender() {}

    public static void registerRegions() {
        Regions.register(new LotusRegion(new ResourceLocation(LotusBlight.MODID, "overworld"), 2));
    }
}
