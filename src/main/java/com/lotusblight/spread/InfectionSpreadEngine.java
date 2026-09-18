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
        double activeRangeSq = activeChunkRangeSq();
        for (OutbreakRecord outbreak : outbreaks) {
            if (!level.hasChunkAt(outbreak.pos())) continue;
            // ACTIVE_CHUNK_RADIUS config used to be pure placebo — exposed in the config screen as
            // "active infection/scanning radius around players" but nothing ever read it. An
            // outbreak with no player within that radius now simply doesn't tick.
            if (!withinActiveRange(level, outbreak.pos(), activeRangeSq)) continue;
            tickOutbreak(level, data, outbreak);
        }
    }

    private double activeChunkRangeSq() {
        double blocks = LotusConfig.ACTIVE_CHUNK_RADIUS.get() * 16.0;
        return blocks * blocks;
    }

    private boolean withinActiveRange(ServerLevel level, BlockPos pos, double rangeSq) {
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(pos) <= rangeSq) return true;
        }
        return false;
    }

    private void tickOutbreak(ServerLevel level, OutbreakSavedData data, OutbreakRecord outbreak) {
        Deque<BlockPos> frontier = frontiers.computeIfAbsent(outbreak.id(), id -> {
            Deque<BlockPos> seeded = new ArrayDeque<>();
            seeded.add(outbreak.pos());
            return seeded;
        });

        int phase = outbreak.phase();
        boolean openOcean = !hasNearbyLand(level, outbreak.pos());
        int attempts = InfectionPhases.attemptsPerTick(phase);
        if (STREAMS_REFLOWING_LOADED && LotusConfig.STREAMS_COMPATIBILITY.get() && isNearWater(level, frontier, outbreak.pos())) {
            // A real, mixin-driven river/stream network is a much stronger signal than vanilla's flat water — lean on it harder.
            attempts += 2;
        }
        // The dialogue branch a nearby player locked in now actually does something to the world,
        // not just gate the grafting rod tool: an ALLIANCE player is actively helping this outbreak
        // grow, a RESISTANCE player is actively suppressing it just by presence.
        attempts = Math.max(0, attempts + branchInfluence(level, outbreak.pos()));
        // SPREAD_RADIUS used to have zero callers (InfectionPhases' own per-phase array did all the
        // actual work) despite being exposed as a tunable "search radius around an active lotus
        // heart". Treat its default (6) as a no-op baseline and apply the delta on top of the
        // normal phase-scaled radius, so the slider still means something without flattening the
        // phase 1->4 escalation into one fixed number.
        int radius = Math.max(1, InfectionPhases.spreadRadius(phase) + (LotusConfig.SPREAD_RADIUS.get() - 6));
        if (isInBlessingBiome(level, outbreak.pos())) {
            // The wiki has always claimed "Blessing slows the spread of the lotus" — until now
            // nothing anywhere actually checked the biome to make that true.
            attempts = Math.max(0, attempts - BLESSING_ATTEMPTS_PENALTY);
            radius = Math.max(1, radius - BLESSING_RADIUS_PENALTY);
        }
        if (openOcean) {
            // Open water is nothing but a spread medium for this infection, so it races through
            // it much faster than it eats through land — but "захват ближников"/mini-biome only
            // make sense once there are actual trees and ground to take over, so an outbreak stuck
            // in the middle of an ocean is capped below phase 3 until it actually finds a shore.
            attempts += OCEAN_ATTEMPTS_BONUS;
            radius += OCEAN_RADIUS_BONUS;
        }
        if (attempts == 0) return;
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
        if (openOcean) {
            newPhase = Math.min(newPhase, OCEAN_PHASE_CAP);
        }
        float progress = InfectionPhases.progressWithinPhase(newPhase, newCount);

        OutbreakRecord updated = outbreak.withInfectedBlockCount(newCount).withPhase(newPhase).withProgress(progress);
        if (newPhase > outbreak.phase()) {
            level.sendParticles(GREEN, outbreak.pos().getX() + 0.5, outbreak.pos().getY() + 1.0, outbreak.pos().getZ() + 0.5, 32, 1.4, 0.7, 1.4, 0.04);
            level.playSound(null, outbreak.pos(), SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.55f, 0.45f);
            maybeSpawnHeart(level, data, outbreak, newPhase);
        }
        data.updateOutbreak(updated);
    }

    private static final double BRANCH_INFLUENCE_RADIUS = 48.0;
    private static final int BRANCH_INFLUENCE_STRENGTH = 2;

    private static final int BLESSING_ATTEMPTS_PENALTY = 1;
    private static final int BLESSING_RADIUS_PENALTY = 1;

    private boolean isInBlessingBiome(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).is(com.lotusblight.registry.ModBiomes.BLESSING_BIOME);
    }

    private static final int OCEAN_ATTEMPTS_BONUS = 3;
    private static final int OCEAN_RADIUS_BONUS = 2;
    private static final int OCEAN_PHASE_CAP = 2;
    private static final int LAND_SEARCH_RADIUS = 32;
    private static final int LAND_SEARCH_SAMPLES = 8;

    /**
     * Cheap sample-based check: does any nearby column actually break the surface above water?
     * Uses the WORLD_SURFACE heightmap, whose topmost non-air block is the top of the water
     * column itself out in open ocean (water isn't air) — so if that block is water, this sample
     * found no land. A handful of random samples in a wide radius is enough to tell "still out at
     * sea" from "reached a shore" without scanning the whole area every tick.
     */
    private boolean hasNearbyLand(ServerLevel level, BlockPos anchor) {
        for (int i = 0; i < LAND_SEARCH_SAMPLES; i++) {
            int dx = level.random.nextInt(LAND_SEARCH_RADIUS * 2 + 1) - LAND_SEARCH_RADIUS;
            int dz = level.random.nextInt(LAND_SEARCH_RADIUS * 2 + 1) - LAND_SEARCH_RADIUS;
            int x = anchor.getX() + dx;
            int z = anchor.getZ() + dz;
            if (!level.hasChunkAt(new BlockPos(x, anchor.getY(), z))) continue;
            int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            if (!level.getFluidState(new BlockPos(x, surfaceY, z)).is(Fluids.WATER)) {
                return true;
            }
        }
        return false;
    }

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

    private static final int SHOOT_SPACING = 2;

    /**
     * Without this, both shoot-placement sites above kept refilling the exact same tiny patch of
     * water edge every tick — visually reads as "30 mini lotuses crammed into one square meter"
     * instead of the infection actually spreading outward. A shoot only places if nothing else in
     * a small radius already has one, forcing growth to walk outward across the frontier instead
     * of endlessly re-rolling the same crowded spot.
     */
    private boolean hasNearbyShoot(ServerLevel level, BlockPos pos) {
        for (BlockPos check : BlockPos.betweenClosed(pos.offset(-SHOOT_SPACING, -1, -SHOOT_SPACING), pos.offset(SHOOT_SPACING, 1, SHOOT_SPACING))) {
            if (check.equals(pos)) continue;
            BlockState state = level.getBlockState(check);
            if (state.is(ModBlocks.LOTUS_SHOOT.get()) || state.is(ModBlocks.INFECTED_LOTUS.get()) || state.is(ModBlocks.LOTUS_HEART.get())) {
                return true;
            }
        }
        return false;
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

        // Clean water source -> infected water. This is the "reincarnation" the wiki always
        // claimed but nothing ever actually did: INFECTED_WATER is a fully registered fluid
        // (own bucket, cleansing powder reverses it) that no code path ever placed, so natural
        // spread visually never touched water at all. Roots/shoots grow only once the water
        // here is already infected, one tick later.
        if (level.getFluidState(target).is(Fluids.WATER) && level.getFluidState(target).isSource()) {
            level.setBlock(target, ModBlocks.INFECTED_WATER.get().defaultBlockState(), 3);
            bloom(level, target, GREEN);
            return target;
        }

        if (level.getFluidState(target).is(ModFluids.INFECTED_WATER.get()) && level.getFluidState(target).isSource()) {
            if (!targetState.is(ModBlocks.LOTUS_ROOTS.get()) && level.random.nextInt(3) != 0) {
                // The fluid here is confirmed INFECTED_WATER by the branch condition above - mark
                // the root as such so it reports the real fluid back (see LotusRootsBlock#INFECTED)
                // instead of silently reverting this tile's rendered fluid to plain water.
                level.setBlock(target, ModBlocks.LOTUS_ROOTS.get().defaultBlockState()
                        .setValue(BlockStateProperties.WATERLOGGED, true).setValue(com.lotusblight.world.LotusRootsBlock.INFECTED, true), 3);
                bloom(level, target, GREEN);
                // This tile was already counted as infected the moment it became INFECTED_WATER
                // (the branch above) - decorating it with roots afterward is the same tile, not
                // new territory, so it must NOT return a position here or tickOutbreak would
                // increment infectedBlockCount a second time for it, inflating progress/phase
                // faster than the infection actually spread.
                return null;
            }
            BlockPos padPos = target.above();
            BlockPos flowerPos = padPos.above();
            if (level.getBlockState(padPos).isAir() && level.getBlockState(flowerPos).isAir() && !hasNearbyShoot(level, flowerPos)) {
                level.setBlock(padPos, net.minecraft.world.level.block.Blocks.LILY_PAD.defaultBlockState(), 3);
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
                // infectedGrass() returns null for tall grass/ferns/short grass by design - they
                // "die off" rather than convert (see its own doc comment) - but null used to mean
                // this branch did nothing at all, so they just sat there forever, untouched, even
                // on fully infected soil. Actually remove them instead of silently no-opping.
                level.setBlock(target, infected != null ? infected : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                bloom(level, target, GREEN);
                return target;
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
            BlockPos above = target.above();
            if (level.getBlockState(above).isAir()) {
                // Before this, the mini-biome (phase 4) never grew anything of its own — logs and
                // leaves only ever came from converting a vanilla tree that happened to already be
                // standing there. An outbreak that spread across open stone or sand had no way to
                // ever grow a single tree. growOwnVegetation gives it real, self-seeding flora.
                if (!growOwnVegetation(level, phase, above)) {
                    if (level.random.nextInt(4) == 0) {
                        level.setBlock(above, ModBlocks.LOTUS_ROOTS.get().defaultBlockState(), 3);
                    }
                }
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
        if (targetState.isAir() && level.getFluidState(target.below()).is(Fluids.WATER) && level.random.nextInt(3) == 0
                && !hasNearbyShoot(level, target) && level.getBlockState(target.above()).isAir()) {
            level.setBlock(target, net.minecraft.world.level.block.Blocks.LILY_PAD.defaultBlockState(), 3);
            level.setBlock(target.above(), ModBlocks.LOTUS_SHOOT.get().defaultBlockState(), 3);
            bloom(level, target.above(), PINK);
            return target.above();
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

    // "Заросли, а не декорация" — the mini-biome is supposed to read as a dense, oppressive
    // thicket (see the reference the user gave: overlapping canopies, thick undergrowth), not
    // scattered single trees every ~30 conversions. Tripled the odds and widened/heightened the
    // canopy so trees actually overlap into a real canopy instead of standing apart.
    private static final int MINI_TREE_CHANCE = 10;
    private static final int BLOSSOM_GRASS_CHANCE = 4;

    /**
     * Self-seeding flora for freshly-converted ground, instead of relying entirely on whatever
     * vanilla grass/trees happened to already be standing there. Phase 4 ground has a chance to
     * grow its own small lotus-log tree from scratch; any infected phase >= 2 ground has a chance
     * to grow blossom grass directly, not just via the separate "grass converts if soil below is
     * infected" rule (which only ever recolors pre-existing grass). Returns true if it planted
     * anything, so the caller doesn't also drop a root on the same spot.
     */
    private boolean growOwnVegetation(ServerLevel level, int phase, BlockPos above) {
        if (phase >= 4 && level.random.nextInt(MINI_TREE_CHANCE) == 0) {
            return tryGrowMiniTree(level, above);
        }
        if (phase >= 2 && level.random.nextInt(BLOSSOM_GRASS_CHANCE) == 0) {
            level.setBlock(above, ModBlocks.BLOSSOM_GRASS.get().defaultBlockState(), 3);
            return true;
        }
        return false;
    }

    /** A 3-5 tall lotus-log trunk with a thick, multi-layer leaf canopy — the mini-biome's own tree, grown rather than converted. */
    private boolean tryGrowMiniTree(ServerLevel level, BlockPos base) {
        int trunkHeight = 3 + level.random.nextInt(3);
        for (int i = 0; i < trunkHeight; i++) {
            if (!level.getBlockState(base.above(i)).isAir()) return false;
        }
        for (int i = 0; i < trunkHeight; i++) {
            level.setBlock(base.above(i), ModBlocks.LOTUS_LOG.get().defaultBlockState(), 3);
        }
        // Two overlapping canopy layers (wide lower ring + narrower top) so the leaves read as a
        // thick crown from a distance, not a single flat slab.
        BlockPos lowerRing = base.above(trunkHeight - 1);
        for (BlockPos leaf : BlockPos.betweenClosed(lowerRing.offset(-2, 0, -2), lowerRing.offset(2, 1, 2))) {
            if (level.getBlockState(leaf).isAir()) {
                level.setBlock(leaf, ModBlocks.LOTUS_LEAVES.get().defaultBlockState(), 3);
            }
        }
        BlockPos canopyCenter = base.above(trunkHeight + 1);
        for (BlockPos leaf : BlockPos.betweenClosed(canopyCenter.offset(-1, 0, -1), canopyCenter.offset(1, 1, 1))) {
            if (level.getBlockState(leaf).isAir()) {
                level.setBlock(leaf, ModBlocks.LOTUS_LEAVES.get().defaultBlockState(), 3);
            }
        }
        return true;
    }

    /**
     * Reaching phase 4 for the first time upgrades the outbreak's anchor pillar into the lotus
     * heart, replacing the whole 4-part stem/crown structure. Before this, LOTUS_HEART was only
     * ever placed by LotusSeedItem the instant a player planted a seed — completely disconnected
     * from phase progression, so a heart could exist next to a barely-infected patch while a
     * fully matured phase-4 outbreak never grew one at all.
     */
    private void maybeSpawnHeart(ServerLevel level, OutbreakSavedData data, OutbreakRecord outbreak, int newPhase) {
        if (newPhase < 4 || outbreak.phase() >= 4) return;
        // Only one lotus heart is meant to ever exist in the world, like the End portal - a
        // singular landmark, not something every sufficiently-grown outbreak earns. Whichever
        // outbreak gets here first claims it; every other one still reaches phase 4 (the
        // mini-biome itself, vegetation, vine barriers) but never grows its own heart block.
        if (!data.claimHeart()) return;
        promoteAnchorToHeart(level, outbreak.pos());
    }

    /**
     * Public/static so admin commands (see com.lotusblight.command.LotusCommands) can force this
     * same transition for testing without needing an InfectionSpreadEngine instance or waiting for
     * a real phase-4 transition. Returns false if there's no ordinary anchor pillar at pos to
     * upgrade (e.g. it's already a heart, or nothing's there).
     */
    public static boolean promoteAnchorToHeart(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(ModBlocks.INFECTED_LOTUS.get())) return false;
        level.setBlock(pos, ModBlocks.LOTUS_HEART.get().defaultBlockState(), 3);
        level.sendParticles(PINK, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 1.0, 1.0, 1.0, 0.05);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 0.6f);
        return true;
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
