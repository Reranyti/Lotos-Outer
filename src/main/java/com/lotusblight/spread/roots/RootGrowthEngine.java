package com.lotusblight.spread.roots;

import com.lotusblight.data.OutbreakRecord;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.lib.LotusLib;
import com.lotusblight.lib.LotusTaskQueue;
import com.lotusblight.registry.ModBlocks;
import com.lotusblight.spread.SpreadTables;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "Roots and mini-lotuses" system.
 *
 * For every active outbreak (phase &gt;= 2) this periodically grows a bounded
 * root chain outward from the anchor across adjacent infected/soil ground
 * and underwater positions, reusing {@code LotusRootsBlock}-style
 * waterlogging for the cosmetic land segments and the new
 * {@link com.lotusblight.world.TangledRootsBlock} (real collision + a brief
 * slowness debuff) for the underwater segments, so an infected waterway
 * visibly gets harder to swim through over time.
 *
 * As the chain grows it has a small chance to seed a hidden "child" outbreak
 * a short distance from the parent — a mini-lotus that starts its own
 * (capped) growth without instantly matching the parent's scale.
 *
 * Data-model note: {@link OutbreakRecord} has no "max phase" field and nothing
 * else needs one, so rather than extending the shared record/SavedData this
 * class keeps its own lightweight {@code childPhaseCaps} map (outbreak id ->
 * max phase) and actively re-clamps any capped outbreak's phase every pass.
 * This is intentionally decoupled from {@code InfectionSpreadEngine} (not
 * edited here) — the cap is enforced independently, after the fact, so it
 * works regardless of who advanced the phase.
 *
 * Work is budgeted through {@link LotusTaskQueue} / {@link LotusLib#process}
 * exactly like the worldgen/spread queues elsewhere in the mod: at most one
 * pending task per outbreak is enqueued on the periodic sweep, and only a
 * small number of tasks are drained per server tick.
 */
public final class RootGrowthEngine {

    private static final DustParticleOptions ROOT_GREEN = new DustParticleOptions(new Vector3f(0.15f, 0.6f, 0.3f), 0.9f);

    /** How often (in ticks) we sweep outbreaks and enqueue at most one growth task each. */
    private static final int SWEEP_INTERVAL_TICKS = 40;
    /** Max queued growth tasks actually processed per server tick — keeps this bounded like MAX_SPREAD_TASKS_PER_TICK. */
    private static final int MAX_ROOT_TASKS_PER_TICK = 4;

    private static final int MAX_CHAIN_LENGTH = 24;
    private static final int MAX_ROOTS_PER_OUTBREAK = 48;

    private static final double CHILD_OUTBREAK_CHANCE = 0.05;
    private static final int CHILD_MIN_CHAIN_LENGTH = 3;
    private static final int CHILD_MAX_PHASE = 2;

    private final LotusTaskQueue<RootGrowthTask> queue = new LotusTaskQueue<>();
    private final Set<UUID> queuedOutbreaks = new HashSet<>();
    private final Map<UUID, RootChainState> chains = new HashMap<>();
    private final Map<UUID, Integer> childPhaseCaps = new HashMap<>();

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();

        if (server.getTickCount() % SWEEP_INTERVAL_TICKS == 0) {
            for (ServerLevel level : server.getAllLevels()) {
                sweepLevel(level);
            }
        }

        LotusLib.process(queue, MAX_ROOT_TASKS_PER_TICK, task -> growOnce(server, task));
    }

    private void sweepLevel(ServerLevel level) {
        OutbreakSavedData data = OutbreakSavedData.get(level);
        enforcePhaseCaps(level, data);

        double activeBlocks = com.lotusblight.LotusConfig.ACTIVE_CHUNK_RADIUS.get() * 16.0;
        double activeRangeSq = activeBlocks * activeBlocks;

        for (OutbreakRecord outbreak : new ArrayList<>(data.allOutbreaks())) {
            if (outbreak.phase() < 2) continue;
            if (!level.hasChunkAt(outbreak.pos())) continue;
            if (!withinActiveRange(level, outbreak.pos(), activeRangeSq)) continue;
            RootChainState chain = chains.get(outbreak.id());
            if (chain != null && (chain.exhausted || chain.chainLength >= MAX_CHAIN_LENGTH || chain.rootsGrown >= MAX_ROOTS_PER_OUTBREAK)) {
                continue;
            }
            if (!queuedOutbreaks.add(outbreak.id())) continue; // at most one pending task per outbreak at a time
            // Unlike GuaranteedSpawnManager/LotusEvents' worldgen queues, this previously called
            // the uncapped offer(T) overload - with drain fixed at MAX_ROOT_TASKS_PER_TICK/tick,
            // enough concurrently-eligible outbreaks (e.g. many phase 2+ anchors near a player)
            // grew this queue every sweep with nothing ever rejecting an offer. Capped the same
            // way the sibling worldgen queues already are.
            queue.offer(new RootGrowthTask(outbreak.id(), level.dimension()), com.lotusblight.LotusConfig.MAX_PENDING_WORLDGEN_TASKS.get());
        }
    }

    private boolean withinActiveRange(ServerLevel level, BlockPos pos, double rangeSq) {
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(pos) <= rangeSq) return true;
        }
        return false;
    }

    private void enforcePhaseCaps(ServerLevel level, OutbreakSavedData data) {
        if (childPhaseCaps.isEmpty()) return;
        for (Map.Entry<UUID, Integer> entry : childPhaseCaps.entrySet()) {
            OutbreakRecord record = data.getOutbreak(entry.getKey());
            if (record == null) continue;
            int cap = entry.getValue();
            if (record.phase() > cap) {
                float clampedProgress = Math.min(record.progress(), 0.99f);
                data.updateOutbreak(record.withPhase(cap).withProgress(clampedProgress));
            }
        }
    }

    private void growOnce(MinecraftServer server, RootGrowthTask task) {
        queuedOutbreaks.remove(task.outbreakId());
        ServerLevel level = server.getLevel(task.dimension());
        if (level == null) return;

        OutbreakSavedData data = OutbreakSavedData.get(level);
        OutbreakRecord outbreak = data.getOutbreak(task.outbreakId());
        if (outbreak == null) return;

        RootChainState chain = chains.computeIfAbsent(task.outbreakId(), id -> new RootChainState(outbreak.pos()));
        if (chain.exhausted || chain.chainLength >= MAX_CHAIN_LENGTH || chain.rootsGrown >= MAX_ROOTS_PER_OUTBREAK) {
            chain.exhausted = true;
            return;
        }
        if (!level.hasChunkAt(chain.tip)) return;

        BlockPos next = findNextRootPos(level, chain.tip);
        if (next == null) {
            chain.exhausted = true; // dead end — stop trying to extend this outbreak's chain
            return;
        }

        boolean underwater = level.getFluidState(next).is(Fluids.WATER) && level.getFluidState(next).isSource();
        if (underwater) {
            level.setBlock(next, ModBlocks.TANGLED_ROOTS.get().defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true), 3);
            data.incrementChunkCount(new ChunkPos(next), 1);
        } else {
            level.setBlock(next, ModBlocks.LOTUS_ROOTS.get().defaultBlockState(), 3);
        }
        level.sendParticles(ROOT_GREEN, next.getX() + 0.5, next.getY() + 0.4, next.getZ() + 0.5, 3, 0.2, 0.15, 0.2, 0.01);
        level.playSound(null, next, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.2f, 0.7f);

        chain.tip = next.immutable();
        chain.chainLength++;
        chain.rootsGrown++;

        maybeSpawnChildOutbreak(level, data, outbreak, chain, next);
    }

    /**
     * Registering a child outbreak used to place no block at all — the mini-lotus was
     * invisible data until InfectionSpreadEngine happened to convert something near it, which
     * (before that engine's own pillar bug was fixed) meant the first visible sign of a child
     * outbreak was a full-size anchor pillar. Now the child gets its own small LOTUS_SHOOT the
     * moment it's registered, directly above the root tip that spawned it, so a mini-lotus is
     * always a visible flower first and a growth source second.
     */
    private void maybeSpawnChildOutbreak(ServerLevel level, OutbreakSavedData data, OutbreakRecord parent, RootChainState chain, BlockPos at) {
        if (chain.chainLength < CHILD_MIN_CHAIN_LENGTH) return;
        if (level.random.nextDouble() >= CHILD_OUTBREAK_CHANCE) return;

        BlockPos padPos = at.above();
        BlockPos flowerPos = padPos.above();
        if (!level.getBlockState(padPos).isAir() || !level.getBlockState(flowerPos).isAir() || hasNearbyShoot(level, flowerPos)) return;

        OutbreakRecord child = data.registerOutbreak(flowerPos.immutable(), level.getGameTime(), true);
        childPhaseCaps.put(child.id(), CHILD_MAX_PHASE);
        level.setBlock(padPos, net.minecraft.world.level.block.Blocks.LILY_PAD.defaultBlockState(), 3);
        level.setBlock(flowerPos, ModBlocks.LOTUS_SHOOT.get().defaultBlockState(), 3);
        level.sendParticles(ROOT_GREEN, flowerPos.getX() + 0.5, flowerPos.getY() + 0.6, flowerPos.getZ() + 0.5, 10, 0.4, 0.3, 0.4, 0.02);
    }

    private static final int SHOOT_SPACING = 2;

    /** Same spacing protection InfectionSpreadEngine uses for ordinary spread — this code path spawns shoots independently and was missing it entirely. */
    private boolean hasNearbyShoot(ServerLevel level, BlockPos pos) {
        for (BlockPos check : BlockPos.betweenClosed(pos.offset(-SHOOT_SPACING, -1, -SHOOT_SPACING), pos.offset(SHOOT_SPACING, 1, SHOOT_SPACING))) {
            if (check.equals(pos)) continue;
            var state = level.getBlockState(check);
            if (state.is(ModBlocks.LOTUS_SHOOT.get()) || state.is(ModBlocks.INFECTED_LOTUS.get()) || state.is(ModBlocks.LOTUS_HEART.get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Looks one step outward (a random horizontal direction from the current
     * tip) for the next valid root position: either an air block directly
     * above infected/clean ground, or a water-source block whose floor is
     * infected/clean ground. Returns {@code null} if nothing usable is found
     * nearby, which ends that outbreak's chain.
     */
    private BlockPos findNextRootPos(ServerLevel level, BlockPos tip) {
        List<Direction> directions = new ArrayList<>(List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST));
        java.util.Collections.shuffle(directions, new java.util.Random(level.random.nextLong()));

        // Checked in this order (same level, then gently down/up) instead of strictly top-to-
        // bottom (+1..-2): scanning top-down let the chain jump straight to a candidate 2-3
        // blocks below the tip whenever the same-level spot didn't happen to pass first, so
        // consecutive links could differ by 3 blocks of height on a 1-block horizontal step —
        // reads as the thread skipping through a block instead of winding continuously.
        int[] dyOrder = {0, -1, 1, -2};
        for (Direction dir : directions) {
            BlockPos base = tip.relative(dir);
            for (int dy : dyOrder) {
                BlockPos probe = base.offset(0, dy, 0);
                if (!level.hasChunkAt(probe)) continue;

                BlockState below = level.getBlockState(probe.below());
                boolean groundOk = SpreadTables.isInfectedGround(below) || SpreadTables.isCleanGround(below);
                if (!groundOk) continue;

                BlockState here = level.getBlockState(probe);
                boolean underwaterSource = level.getFluidState(probe).is(Fluids.WATER) && level.getFluidState(probe).isSource();
                if (underwaterSource && !here.is(ModBlocks.TANGLED_ROOTS.get()) && !here.is(ModBlocks.LOTUS_ROOTS.get())) {
                    return probe.immutable();
                }
                if (here.isAir()) {
                    return probe.immutable();
                }
            }
        }
        return null;
    }
}
