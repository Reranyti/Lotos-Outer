package com.lotusblight.worldgen.compat;

import com.mojang.logging.LogUtils;
import dev.streamsreflowing.core.RiverEngine;
import dev.streamsreflowing.core.plate.PlateKey;
import dev.streamsreflowing.worldgen.StreamsWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The only place that touches Streams Reflowing's classes (optional, modId "streamsreflowing") - the
 * caller (RiverPrebuildTrigger) checks it's loaded first.
 *
 * Streams Reflowing builds each river region ("plate") the first time the land around it generates,
 * and on a 4-core machine that meant 2-12 seconds per region on every core while the player walked
 * into new land - the whole PC choked on exactly the chunk loads. Each region is built once and
 * stored for good, so this builds every region around spawn up front, while the world is still
 * loading, spread over all cores but one. Only its public API is used (StreamsWorldgen#engine,
 * RiverEngine#platesNear / #networkFor / #isPlateReady); everything else in that jar is obfuscated.
 */
public final class StreamsRiverPrebuild {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Never keeps the world from opening longer than this, whatever the machine. */
    private static final long MAX_WAIT_MINUTES = 10;

    private StreamsRiverPrebuild() {}

    /** Builds every river region within {@code radius} blocks of {@code center}. Returns how many were built. */
    public static int prebuild(ServerLevel level, BlockPos center, double radius) {
        RiverEngine engine = StreamsWorldgen.engine(level);
        if (engine == null) return 0;
        List<PlateKey> todo = new ArrayList<>();
        for (PlateKey plate : engine.platesNear(center.getX(), center.getZ(), radius)) {
            if (!engine.isPlateReady(plate)) todo.add(plate);
        }
        if (todo.isEmpty()) return 0;

        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        LOGGER.info("Building {} river regions around spawn ahead of time ({} threads)", todo.size(), threads);
        long start = System.nanoTime();
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "lotusblight-river-prebuild");
            t.setDaemon(true);
            return t;
        });
        int built = 0;
        try {
            List<Future<?>> jobs = new ArrayList<>();
            for (PlateKey plate : todo) {
                jobs.add(pool.submit(() -> engine.networkFor(plate)));
            }
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(MAX_WAIT_MINUTES);
            for (Future<?> job : jobs) {
                long left = deadline - System.nanoTime();
                if (left <= 0) break;
                try {
                    job.get(left, TimeUnit.NANOSECONDS);
                    built++;
                } catch (Exception e) {
                    // One region failing or running long must not stop the world from opening -
                    // Streams Reflowing simply builds it later, the way it always did.
                    LOGGER.warn("River region prebuild skipped: {}", e.toString());
                }
            }
        } finally {
            pool.shutdownNow();
        }
        LOGGER.info("River regions ready: {}/{} in {} s", built, todo.size(), (System.nanoTime() - start) / 1_000_000_000L);
        return built;
    }
}
