package com.lotusblight;

import net.minecraftforge.common.ForgeConfigSpec;

public final class LotusConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue SPREAD_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue SPREAD_RADIUS;
    public static final ForgeConfigSpec.IntValue SCAN_RADIUS;
    public static final ForgeConfigSpec.IntValue MAP_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.BooleanValue STREAMS_COMPATIBILITY;
    public static final ForgeConfigSpec.IntValue ACTIVE_CHUNK_RADIUS;
    public static final ForgeConfigSpec.IntValue MAX_PENDING_WORLDGEN_TASKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("lotus infection");
        SPREAD_INTERVAL_TICKS = builder.comment("Ticks between spread attempts. 140 ticks = 7 seconds.")
                .defineInRange("spreadIntervalTicks", 140, 20, 2400);
        SPREAD_RADIUS = builder.comment("Search radius around an active lotus heart.")
                .defineInRange("spreadRadius", 6, 2, 16);
        SCAN_RADIUS = builder.comment("Lotus map detection radius in blocks.")
                .defineInRange("mapScanRadius", 192, 32, 512);
        MAP_COOLDOWN_TICKS = builder.comment("Cooldown of the lotus infection map.")
                .defineInRange("mapCooldownTicks", 100, 20, 1200);
        STREAMS_COMPATIBILITY = builder.comment("Makes infection prefer and follow connected water courses.")
                .define("streamsReflowingCompatibility", true);
        ACTIVE_CHUNK_RADIUS = builder.comment("Maximum active infection and scanning radius around players, in chunks.")
                .defineInRange("activeChunkRadius", 32, 4, 32);
        MAX_PENDING_WORLDGEN_TASKS = builder.comment("Maximum queued natural lotus worldgen tasks.")
                .defineInRange("maxPendingWorldgenTasks", 64, 8, 256);
        builder.pop();
        SPEC = builder.build();
    }

    private LotusConfig() {}
}
