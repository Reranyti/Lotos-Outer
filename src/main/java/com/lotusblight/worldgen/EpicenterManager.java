package com.lotusblight.worldgen;

import com.lotusblight.data.OutbreakRecord;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.registry.ModBlocks;
import com.lotusblight.spread.InfectionPhases;
import com.lotusblight.spread.InfectionSpreadEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Random;

/**
 * Guarantees the overworld always has at least one already-mature mini-biome
 * "epicenter" — a phase-4 outbreak with its lotus heart already grown — far
 * from spawn (5000-9000 blocks out), instead of every outbreak in the world
 * starting as a fresh seed that has to grow into a mini-biome on its own.
 * This is the actual "center of the Lotus infection" the lore calls for: a
 * long-standing, already-established outpost the player can eventually find,
 * not just wherever the player's own accidental spread happens to reach
 * phase 4 first.
 *
 * Runs once per overworld load: forces a handful of chunks near a random
 * distant point to generate (a one-time cost, same order as a player
 * teleporting there) to find water, then registers and places the epicenter
 * directly as a phase-4 heart. If no water is found nearby, it retries with a
 * new random point on a later tick rather than blocking the server for a
 * long synchronous search.
 */
public final class EpicenterManager {
    private static final Random RANDOM = new Random();
    private static final int MIN_DISTANCE = 5000;
    private static final int MAX_DISTANCE = 9000;
    private static final int WATER_SEARCH_RADIUS = 64;
    private static final int WATER_SEARCH_ATTEMPTS = 40;
    private static final int RETRY_INTERVAL_TICKS = 100;
    private static final int MAX_ATTEMPTS = 12;

    private boolean triedThisRun = false;
    private int attemptsLeft = MAX_ATTEMPTS;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (attemptsLeft <= 0) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % RETRY_INTERVAL_TICKS != 0) return;

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        OutbreakSavedData data = OutbreakSavedData.get(overworld);
        if (data.isEpicenterResolved()) {
            attemptsLeft = 0;
            return;
        }

        attemptsLeft--;
        if (tryPlaceEpicenter(overworld, data)) {
            data.markEpicenterResolved();
            attemptsLeft = 0;
        } else if (attemptsLeft <= 0) {
            // Give up rather than retry forever on a world with unusual terrain (e.g. no water
            // anywhere near several random distant points) — mark resolved so we don't keep
            // forcing chunk generation every 100 ticks for the rest of the server's life.
            data.markEpicenterResolved();
        }
    }

    private boolean tryPlaceEpicenter(ServerLevel level, OutbreakSavedData data) {
        double angle = RANDOM.nextDouble() * Math.PI * 2;
        int distance = MIN_DISTANCE + RANDOM.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1);
        int centerX = (int) Math.round(Math.cos(angle) * distance);
        int centerZ = (int) Math.round(Math.sin(angle) * distance);
        BlockPos center = new BlockPos(centerX, 64, centerZ);

        // Forces generation of whatever chunk each sampled column lands in — a bounded, one-time
        // cost, identical in kind to a player simply flying out here.
        BlockPos water = findNearestWater(level, center, WATER_SEARCH_RADIUS);
        if (water == null) return false;

        BlockPos flowerPos = water.above().immutable();
        if (!level.getBlockState(flowerPos).isAir()) return false;

        level.setBlock(flowerPos, ModBlocks.INFECTED_LOTUS.get().defaultBlockState(), 3);
        InfectionSpreadEngine.promoteAnchorToHeart(level, flowerPos);
        OutbreakRecord epicenter = data.registerOutbreak(flowerPos, level.getGameTime(), true)
                .withPhase(4)
                .withInfectedBlockCount(InfectionPhases.minBlockCountForPhase(4));
        data.updateOutbreak(epicenter);
        return true;
    }

    private BlockPos findNearestWater(ServerLevel level, BlockPos center, int radius) {
        int top = Math.min(level.getMaxBuildHeight() - 2, 160);
        int bottom = Math.max(level.getMinBuildHeight() + 1, 32);
        for (int attempt = 0; attempt < WATER_SEARCH_ATTEMPTS; attempt++) {
            int x = center.getX() + RANDOM.nextInt(radius * 2 + 1) - radius;
            int z = center.getZ() + RANDOM.nextInt(radius * 2 + 1) - radius;
            for (int y = top; y >= bottom; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (level.getFluidState(pos).is(Fluids.WATER) && level.getFluidState(pos).isSource()
                        && level.getBlockState(pos.above()).isAir()) {
                    return pos;
                }
            }
        }
        return null;
    }
}
