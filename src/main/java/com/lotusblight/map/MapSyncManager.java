package com.lotusblight.map;

import com.lotusblight.LotusBlight;
import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.data.OutbreakRecord;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.registry.ModBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        long last = lastSyncTick.getOrDefault(serverPlayer.getUUID(), Long.MIN_VALUE);
        if (tick - last < SYNC_INTERVAL_TICKS) {
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

        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new MapSyncPacket(markers));
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                new PlayerStateSyncPacket(LotusPlayerState.getDialogueBranch(serverPlayer), fullVisibility));
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        lastSyncTick.remove(event.getEntity().getUUID());
    }

    /** Resolved once per outbreak (cached) so repeated syncs don't repeatedly force-load distant chunks. */
    private static boolean isHeartAnchor(ServerLevel level, OutbreakRecord record) {
        Boolean cached = heartAnchorCache.get(record.id());
        if (cached != null) {
            return cached;
        }
        if (!level.hasChunkAt(record.pos())) {
            // Chunk not loaded yet — don't force-load it just to classify a marker; ask again next sync.
            return false;
        }
        boolean isHeart = level.getBlockState(record.pos()).is(ModBlocks.LOTUS_HEART.get());
        heartAnchorCache.put(record.id(), isHeart);
        return isHeart;
    }
}
