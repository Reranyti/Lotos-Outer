package com.lotusblight.map;

import com.lotusblight.LotusBlight;
import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.data.OutbreakRecord;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.registry.ModBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side heart of the map system. Every {@link #SYNC_INTERVAL_TICKS}
 * ticks, for every player:
 *
 * 1. reveals (permanently, via {@link OutbreakSavedData#updateOutbreak}) any
 *    hidden outbreak the player has physically walked within
 *    {@link #DISCOVERY_RADIUS} of;
 * 2. sends that player a {@link MapSyncPacket} with the markers they're
 *    currently allowed to see: every known outbreak if they have the full
 *    visibility modifier ({@link LotusPlayerState#hasFullMapVisibility}),
 *    otherwise only outbreaks that are no longer hidden.
 *
 * All queries go through {@link OutbreakSavedData}'s indexed lookups
 * (O(outbreak count), not O(world volume)) — nothing here scans blocks in a
 * loop the way the old map/HUD code did.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MapSyncManager {
    private static final int SYNC_INTERVAL_TICKS = 30;
    private static final double DISCOVERY_RADIUS = 24.0;

    /** Last game-tick each player was synced, so different players can be staggered/throttled independently. */
    private static final Map<UUID, Long> lastSyncTick = new HashMap<>();
    /** Lazily-resolved "is this outbreak's anchor a lotus_heart block" cache, keyed by outbreak id. */
    private static final Map<UUID, Boolean> heartAnchorCache = new HashMap<>();
    /**
     * Chunk -> nearest outbreak id, shared across every player's sync instead of recomputed per
     * player per sync. Outbreak anchors never move once registered, so a chunk's nearest owner
     * only ever changes when a new outbreak is registered (never on phase/progress updates) -
     * tracking the outbreak count is enough to know when to invalidate.
     */
    private static final Map<ChunkPos, UUID> nearestOwnerCache = new HashMap<>();
    private static int nearestOwnerCacheOutbreakCount = -1;

    private MapSyncManager() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!(serverPlayer.level() instanceof ServerLevel level)) {
            return;
        }

        long tick = level.getGameTime();
        // A Long.MIN_VALUE sentinel + subtraction overflowed (tick - Long.MIN_VALUE wraps to a huge
        // negative number via two's-complement), so a never-synced player's very first check always
        // read as "not due yet" and the map entry that would fix that on the next tick was never
        // created - every sync packet (map markers, player state, gland waypoints) silently never
        // reached that player, for their entire session. Null-check instead of subtracting against a
        // sentinel avoids the overflow entirely.
        Long last = lastSyncTick.get(serverPlayer.getUUID());
        if (last != null && tick - last < SYNC_INTERVAL_TICKS) {
            return;
        }
        lastSyncTick.put(serverPlayer.getUUID(), tick);

        OutbreakSavedData data = OutbreakSavedData.get(level);

        // Proximity discovery: permanently reveal hidden outbreaks the player has walked near.
        for (OutbreakRecord record : data.outbreaksWithin(serverPlayer.blockPosition(), DISCOVERY_RADIUS, false)) {
            if (record.hidden()) {
                data.updateOutbreak(record.withHidden(false));
            }
        }

        boolean fullVisibility = LotusPlayerState.hasFullMapVisibility(serverPlayer);
        List<OutbreakRecord> toSend = fullVisibility ? List.copyOf(data.allOutbreaks()) : data.visibleOutbreaks();

        List<MapMarker> markers = toSend.stream()
                .map(record -> new MapMarker(
                        record.id(),
                        record.pos(),
                        record.phase(),
                        record.progress(),
                        record.infectedBlockCount(),
                        isHeartAnchor(level, record),
                        record.hidden()
                ))
                .toList();

        List<Long> infectedChunkKeys = visibleInfectedChunkKeys(data, toSend);

        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new MapSyncPacket(markers, infectedChunkKeys));
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                new PlayerStateSyncPacket(LotusPlayerState.getDialogueBranch(serverPlayer), fullVisibility, LotusPlayerState.hasHeardInnerVoice(serverPlayer), LotusPlayerState.hasSeenGuardian(serverPlayer), LotusPlayerState.getAllianceCleanseUses(serverPlayer)));

        // "показывай вейпойнты" на местах падения метеоритов - reuses the exact same per-player sync
        // cadence as outbreak markers, just from a different SavedData source (positions only).
        List<net.minecraft.core.BlockPos> meteoritePositions = com.lotusblight.data.MeteoriteSpreadSavedData.get(level).allSources().stream()
                .map(com.lotusblight.data.MeteoriteSpreadRecord::pos)
                .toList();
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new MeteoriteMarkerSyncPacket(meteoritePositions));

        // Mossy Glands aren't secret like hidden outbreaks - there are only NATURAL_GLAND_COUNT of
        // them and just finding them at all was the reported problem (bug #21), so every player
        // always gets the full list, no discovery gating.
        List<net.minecraft.core.BlockPos> glandPositions = com.lotusblight.data.MossyGlandSavedData.get(level)
                .allGlands().stream().map(com.lotusblight.data.MossyGlandRecord::pos).toList();
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new GlandSyncPacket(glandPositions));
    }

    /**
     * Per-chunk infection counts aren't tagged with which outbreak grew them, so a chunk's
     * visibility is inherited from whichever known outbreak's anchor is physically closest to it
     * — the same "you have to have discovered/be allowed to see this lotus" rule markers already
     * follow, extended to the area around it instead of just its point. This is what finally makes
     * the map show the actual infection footprint instead of a single marker dot.
     */
    private static List<Long> visibleInfectedChunkKeys(OutbreakSavedData data, List<OutbreakRecord> visibleOutbreaks) {
        if (visibleOutbreaks.isEmpty()) {
            return List.of();
        }
        Set<ChunkPos> infected = data.infectedChunks();
        if (infected.isEmpty()) {
            return List.of();
        }
        List<OutbreakRecord> allOutbreaks = List.copyOf(data.allOutbreaks());
        Set<UUID> visibleIds = new java.util.HashSet<>();
        for (OutbreakRecord record : visibleOutbreaks) {
            visibleIds.add(record.id());
        }

        if (allOutbreaks.size() != nearestOwnerCacheOutbreakCount) {
            // A new outbreak can only ever make some chunk's nearest owner CLOSER, never farther -
            // the cheapest correct response is to drop the whole cache and let it rebuild lazily
            // below, rather than track which chunks are actually affected.
            nearestOwnerCache.clear();
            nearestOwnerCacheOutbreakCount = allOutbreaks.size();
        }

        List<Long> result = new ArrayList<>();
        for (ChunkPos chunkPos : infected) {
            UUID ownerId = nearestOwnerCache.computeIfAbsent(chunkPos, cp -> nearestOwnerId(cp, allOutbreaks));
            if (ownerId != null && visibleIds.contains(ownerId)) {
                result.add(chunkPos.toLong());
            }
        }
        return result;
    }

    private static UUID nearestOwnerId(ChunkPos chunkPos, List<OutbreakRecord> allOutbreaks) {
        OutbreakRecord nearest = null;
        long nearestDistSq = Long.MAX_VALUE;
        long chunkCenterX = (long) (chunkPos.getMinBlockX() + 8);
        long chunkCenterZ = (long) (chunkPos.getMinBlockZ() + 8);
        for (OutbreakRecord candidate : allOutbreaks) {
            long dx = candidate.pos().getX() - chunkCenterX;
            long dz = candidate.pos().getZ() - chunkCenterZ;
            long distSq = dx * dx + dz * dz;
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = candidate;
            }
        }
        return nearest == null ? null : nearest.id();
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        lastSyncTick.remove(event.getEntity().getUUID());
    }

    /**
     * Resolved once per outbreak (cached) so repeated syncs don't repeatedly force-load distant
     * chunks. Only caches a `true` result: an anchor that isn't a heart yet can still be promoted to
     * one later (InfectionSpreadEngine#promoteAnchorToHeart at phase 4), and a cached stale `false`
     * would permanently hide that on the map/JourneyMap after the real block changed. Once it's
     * actually a heart it never reverts, so caching `true` forever is safe.
     */
    private static boolean isHeartAnchor(ServerLevel level, OutbreakRecord record) {
        Boolean cached = heartAnchorCache.get(record.id());
        if (cached != null && cached) {
            return true;
        }
        if (!level.hasChunkAt(record.pos())) {
            // Chunk not loaded yet — don't force-load it just to classify a marker; ask again next sync.
            return false;
        }
        boolean isHeart = level.getBlockState(record.pos()).is(ModBlocks.LOTUS_HEART.get());
        if (isHeart) {
            heartAnchorCache.put(record.id(), true);
        }
        return isHeart;
    }
}
