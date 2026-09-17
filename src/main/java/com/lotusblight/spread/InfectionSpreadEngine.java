package com.lotusblight.spread;

import com.lotusblight.LotusConfig;
import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.data.OutbreakRecord;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.registry.ModBlocks;
import com.lotusblight.registry.ModEffects;
import com.lotusblight.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The infection spread engine, replacing the old single-offset-per-anchor
 * scan in LotusEvents.
 *
 * Each active, chunk-loaded outbreak keeps a small in-memory "frontier" of
 * recently infected block positions. Every engine pass, each outbreak
 * spends a phase-scaled number of attempts: pick a random frontier block as
 * the source, look at a random neighbour within the phase's radius, and try
 * to convert it via {@link SpreadTables} — a real (bounded) tile-conversion
 * model instead of one fixed random offset from a single anchor point. Every
 * successful conversion updates {@link OutbreakSavedData} incrementally so
 * nothing downstream (boss bar, map, minimap) ever has to rescan a volume.
 */
public class InfectionSpreadEngine {

    private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.2f, 0.95f, 0.35f), 1.0f);
    private static final DustParticleOptions PINK = new DustParticleOptions(new Vector3f(1.0f, 0.2f, 0.55f), 1.0f);
    private static final int FRONTIER_CAP = 48;
    private static final double SPORE_RADIUS = 3.5;

    /**
     * Streams Reflowing has no public API, but it doesn't need one: it works by mixin-patching
     * vanilla's own FluidState#getFlow (WaterFluidMixin/FlowingFluidMixin), so any code that
     * calls the vanilla flow API — including findWaterDownstream below — already reads SR's
     * realistic river flow field for free once SR is installed. Detecting its presence here only
     * lets us decide to TRUST that flow more (chase it further per attempt, spend more attempts
     * on water) — a soft dependency with a strong effect when present, per design decision.
     */
    private static final boolean STREAMS_REFLOWING_LOADED = ModList.get().isLoaded("streamsreflowing");

    /** Non-persisted per-outbreak set of recently infected positions. Lazily reseeded from the anchor if missing (e.g. after a restart). */
    private final Map<UUID, Deque<BlockPos>> frontiers = new HashMap<>();
    private final Map<UUID, ServerBossEvent> bossBars = new HashMap<>();

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();

        if (server.getTickCount() % 10 == 0) {
            for (ServerLevel level : server.getAllLevels()) {
                for (ServerPlayer player : level.players()) {
                    updateBossBar(level, player);
                }
            }
        }

        if (server.getTickCount() % LotusConfig.SPREAD_INTERVAL_TICKS.get() != 0) return;
        for (ServerLevel level : server.getAllLevels()) {
            tickLevel(level);
        }
    }

    private void tickLevel(ServerLevel level) {
        OutbreakSavedData data = OutbreakSavedData.get(level);
        List<OutbreakRecord> outbreaks = new ArrayList<>(data.allOutbreaks());
        for (OutbreakRecord outbreak : outbreaks) {
            if (!level.hasChunkAt(outbreak.pos())) continue;
            tickOutbreak(level, data, outbreak);
        }
    }

    private void tickOutbreak(ServerLevel level, OutbreakSavedData data, OutbreakRecord outbreak) {
        Deque<BlockPos> frontier = frontiers.computeIfAbsent(outbreak.id(), id -> {
            Deque<BlockPos> seeded = new ArrayDeque<>();
            seeded.add(outbreak.pos());
            return seeded;
        });

        int phase = outbreak.phase();
        int attempts = InfectionPhases.attemptsPerTick(phase);
        if (STREAMS_REFLOWING_LOADED && LotusConfig.STREAMS_COMPATIBILITY.get() && isNearWater(level, frontier, outbreak.pos())) {
            // A real, mixin-driven river/stream network is a much stronger signal than vanilla's flat water — lean on it harder.
            attempts += 2;
        }
        // The dialogue branch a nearby player locked in now actually does something to the world,
        // not just gate the grafting rod tool: an ALLIANCE player is actively helping this outbreak
        // grow, a RESISTANCE player is actively suppressing it just by presence.
        attempts = Math.max(0, attempts + branchInfluence(level, outbreak.pos()));
        if (attempts == 0) return;
        int radius = InfectionPhases.spreadRadius(phase);
        int converted = 0;

        for (int i = 0; i < attempts; i++) {
            BlockPos source = pickFrontierSource(level, frontier, outbreak.pos());
            BlockPos convertedPos = trySpreadOnce(level, source, radius, phase);
            if (convertedPos != null) {
                pushFrontier(frontier, convertedPos);
                data.incrementChunkCount(new ChunkPos(convertedPos), 1);
                infectNearbyLiving(level, convertedPos);
                converted++;
            }
        }

        if (InfectionPhases.canGrowVineBarrier(phase) && level.random.nextDouble() < InfectionPhases.vineBarrierChance()) {
            BlockPos candidate = frontier.isEmpty() ? outbreak.pos() : new ArrayList<>(frontier).get(level.random.nextInt(frontier.size()));
            VineBarrierGenerator.tryGrow(level, outbreak.id(), candidate.above());
        }

        int newCount = outbreak.infectedBlockCount() + converted;
        int newPhase = InfectionPhases.phaseForBlockCount(newCount);
        float progress = InfectionPhases.progressWithinPhase(newPhase, newCount);

        OutbreakRecord updated = outbreak.withInfectedBlockCount(newCount).withPhase(newPhase).withProgress(progress);
        if (newPhase > outbreak.phase()) {
            level.sendParticles(GREEN, outbreak.pos().getX() + 0.5, outbreak.pos().getY() + 1.0, outbreak.pos().getZ() + 0.5, 32, 1.4, 0.7, 1.4, 0.04);
            level.playSound(null, outbreak.pos(), SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.55f, 0.45f);
        }
        data.updateOutbreak(updated);
    }

    private static final double BRANCH_INFLUENCE_RADIUS = 48.0;
    private static final int BRANCH_INFLUENCE_STRENGTH = 2;

    /** Sums +/- influence from every player within range who has locked in a dialogue branch. */
    private int branchInfluence(ServerLevel level, BlockPos anchor) {
        int influence = 0;
        AABB box = new AABB(anchor).inflate(BRANCH_INFLUENCE_RADIUS);
        for (ServerPlayer player : level.players()) {
            if (!box.contains(player.getX(), player.getY(), player.getZ())) continue;
            int branch = LotusPlayerState.getDialogueBranch(player);
            if (branch == LotusPlayerState.BRANCH_ALLIANCE) influence += BRANCH_INFLUENCE_STRENGTH;
            else if (branch == LotusPlayerState.BRANCH_RESISTANCE) influence -= BRANCH_INFLUENCE_STRENGTH;
        }
        return influence;
    }

    private BlockPos pickFrontierSource(ServerLevel level, Deque<BlockPos> frontier, BlockPos anchor) {
        if (frontier.isEmpty()) return anchor;
        int index = level.random.nextInt(frontier.size());
        int i = 0;
        for (BlockPos pos : frontier) {
            if (i++ == index) return pos;
        }
        return anchor;
    }

    private void pushFrontier(Deque<BlockPos> frontier, BlockPos pos) {
        frontier.addLast(pos.immutable());
        while (frontier.size() > FRONTIER_CAP) {
            frontier.pollFirst();
        }
    }

    /** Returns the converted position on success, or null if this attempt did nothing. */
    private BlockPos trySpreadOnce(ServerLevel level, BlockPos source, int radius, int phase) {
        BlockPos target = randomNeighbour(level, source, radius);
        if (!level.hasChunkAt(target)) return null;

        // Water has priority: infection follows the actual water layout (soft Streams Reflowing compatibility).
        if (LotusConfig.STREAMS_COMPATIBILITY.get()
                && (level.getFluidState(source).is(Fluids.WATER) || level.getFluidState(source).is(ModFluids.INFECTED_WATER.get()))) {
            // With Streams Reflowing loaded, vanilla's getFlow() reports its realistic river flow
            // field (direction AND speed — see STREAMS_REFLOWING_LOADED javadoc) instead of vanilla's
            // weak default. Fast-flowing water (a real river) lets infection chase the current several
            // hops in one attempt; still/slow water (a lake) behaves like before. This is what makes
            // the current directly drive how far and how fast the infection actually travels.
            int hops = STREAMS_REFLOWING_LOADED ? 1 + flowSpeedHops(level, source) : 1;
            target = source;
            for (int hop = 0; hop < hops; hop++) {
                BlockPos next = findWaterDownstream(level, target);
                if (next.equals(target)) break;
                target = next;
            }
        }

        BlockState targetState = level.getBlockState(target);

        // Water source -> infected water, followed by an occasional surface shoot.
        if (level.getFluidState(target).is(Fluids.WATER) && level.getFluidState(target).isSource()) {
            if (!targetState.is(ModBlocks.LOTUS_ROOTS.get()) && level.random.nextInt(3) != 0) {
                level.setBlock(target, ModBlocks.LOTUS_ROOTS.get().defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true), 3);
                bloom(level, target, GREEN);
                return target;
            }
            BlockPos flowerPos = target.above();
            if (level.getBlockState(flowerPos).isAir()) {
                level.setBlock(flowerPos, ModBlocks.LOTUS_SHOOT.get().defaultBlockState(), 3);
                bloom(level, flowerPos, PINK);
                return flowerPos;
            }
            return null;
        }

        // Grass follows its own rule: only converts once the soil beneath it is already infected.
        if (SpreadTables.isCleanGrass(targetState)) {
            BlockState below = level.getBlockState(target.below());
            if (SpreadTables.isInfectedGround(below)) {
                BlockState infected = SpreadTables.infectedGrass(targetState);
                if (infected != null) {
                    level.setBlock(target, infected, 3);
                    bloom(level, target, GREEN);
                    return target;
                }
            }
            return null;
        }

        // Logs/leaves only convert from phase 3 onward, matching the захват-ближников escalation.
        if (phase >= 3 && (SpreadTables.isCleanLog(targetState) || SpreadTables.isCleanLeaves(targetState))) {
            level.setBlock(target, SpreadTables.isCleanLog(targetState) ? SpreadTables.infectedLog() : SpreadTables.infectedLeaves(), 3);
            bloom(level, target, GREEN);
            return target;
        }

        // Generic ground table (dirt/sand/gravel/stone/terracotta -> infected analogue).
        BlockState groundReplacement = SpreadTables.infectedGroundReplacement(targetState);
        if (groundReplacement != null && Math.abs(target.getY() - source.getY()) <= 1) {
            level.setBlock(target, groundReplacement, 3);
            if (level.random.nextInt(4) == 0 && level.getBlockState(target.above()).isAir()) {
                level.setBlock(target.above(), ModBlocks.LOTUS_ROOTS.get().defaultBlockState(), 3);
            }
            bloom(level, target, GREEN);
            return target;
        }

        // A small shoot can bloom on open air directly above still-clean water reached at the
        // edge of the radius. This used to place the full 4-tall LOTUS_MAIN anchor here — the
        // same "one indestructible pillar per outbreak" block meant for GuaranteedSpawnManager —
        // so every ordinary spread tick was littering the world with duplicate anchor pillars
        // instead of small decorative flowers. LOTUS_SHOOT is the correct block: cosmetic, not
        // an anchor, and already used for the equivalent case a few lines up (water source ->
        // shoot). Throttled so it doesn't outbid ground conversion at every single attempt.
        if (targetState.isAir() && level.getFluidState(target.below()).is(Fluids.WATER) && level.random.nextInt(3) == 0) {
            level.setBlock(target, ModBlocks.LOTUS_SHOOT.get().defaultBlockState(), 3);
            bloom(level, target, PINK);
            return target;
        }

        return null;
    }

    /**
     * Extra downstream hops earned from the current's actual speed, not just its presence.
     * Vanilla's own flow magnitude is nearly flat; Streams Reflowing's mixin-patched getFlow()
     * reports real hydraulic speed, so a fast river genuinely pushes infection further per
     * attempt than a barely-moving lake edge does. Thresholds are heuristic since SR exposes no
     * documented scale — tuned to feel like "river" clearly outruns "lake" without either
     * degenerating to 0 or exploding to an unbounded chase.
     */
    private int flowSpeedHops(ServerLevel level, BlockPos pos) {
        var flow = level.getFluidState(pos).getFlow(level, pos);
        double speed = Math.sqrt(flow.x * flow.x + flow.z * flow.z);
        if (speed > 0.08) return 2;
        if (speed > 0.02) return 1;
        return 0;
    }

    /** Cheap sample of a handful of frontier positions to decide whether this outbreak currently touches water at all. */
    private boolean isNearWater(ServerLevel level, Deque<BlockPos> frontier, BlockPos anchor) {
        int checked = 0;
        for (BlockPos pos : frontier) {
            if (level.getFluidState(pos).is(Fluids.WATER) || level.getFluidState(pos.below()).is(Fluids.WATER)) return true;
            if (++checked >= 6) break;
        }
        return level.getFluidState(anchor).is(Fluids.WATER) || level.getFluidState(anchor.below()).is(Fluids.WATER);
    }

    private BlockPos randomNeighbour(ServerLevel level, BlockPos source, int radius) {
        int dx = level.random.nextInt(radius * 2 + 1) - radius;
        int dy = level.random.nextInt(3) - 1;
        int dz = level.random.nextInt(radius * 2 + 1) - radius;
        return source.offset(dx, dy, dz);
    }

    private BlockPos findWaterDownstream(ServerLevel level, BlockPos source) {
        var flow = level.getFluidState(source).getFlow(level, source);
        BlockPos best = source;
        double bestScore = -Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(source.offset(-1, -1, -1), source.offset(1, 0, 1))) {
            if (!level.getFluidState(candidate).is(Fluids.WATER)) continue;
            double dx = candidate.getX() - source.getX();
            double dz = candidate.getZ() - source.getZ();
            double score = dx * flow.x + dz * flow.z - Math.max(0, candidate.getY() - source.getY()) * 0.75;
            if (!candidate.equals(source) && score > bestScore) {
                best = candidate.immutable();
                bestScore = score;
            }
        }
        return best.equals(source) ? source.relative(Direction.getRandom(level.random)) : best;
    }

    private void infectNearbyLiving(ServerLevel level, BlockPos source) {
        AABB box = new AABB(source).inflate(SPORE_RADIUS);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity instanceof Player) continue;
            if (entity.getType().getCategory() == MobCategory.MISC) continue;
            entity.addEffect(new MobEffectInstance(ModEffects.LOTUS_SPORES.get(), 240, 0));
        }
    }

    private void bloom(ServerLevel level, BlockPos pos, DustParticleOptions color) {
        level.sendParticles(color, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 5, 0.3, 0.25, 0.3, 0.01);
        level.playSound(null, pos, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.3f, 1.5f);
    }

    private void updateBossBar(ServerLevel level, ServerPlayer player) {
        OutbreakSavedData data = OutbreakSavedData.get(level);
        OutbreakRecord nearest = data.nearestOutbreak(player.blockPosition(), 96.0, false);
        ServerBossEvent bar = bossBars.computeIfAbsent(player.getUUID(), id -> {
            ServerBossEvent created = new ServerBossEvent(Component.literal("Разрастание лотоса"), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
            created.setVisible(false);
            return created;
        });
        if (nearest == null) {
            bar.removePlayer(player);
            bar.setVisible(false);
            return;
        }
        bar.setName(Component.literal(InfectionPhases.phaseName(nearest.phase()) + " — " + Math.round(nearest.progress() * 100.0f) + "%"));
        bar.setProgress(Mth.clamp(nearest.progress(), 0.0f, 1.0f));
        bar.addPlayer(player);
        bar.setVisible(true);
    }
}
