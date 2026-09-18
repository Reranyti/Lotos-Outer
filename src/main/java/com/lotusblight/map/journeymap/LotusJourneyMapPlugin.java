package com.lotusblight.map.journeymap;

import com.lotusblight.map.ClientMapCache;
import com.lotusblight.map.MapMarker;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.event.MappingEvent;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.ClientEventRegistry;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.awt.Color;

/**
 * Replaces the mod's own minimap/atlas (LotusHudOverlay/LotusAtlasScreen)
 * with waypoints in JourneyMap, sourced from the same ClientMapCache the
 * removed HUD used to read from — the server-side pipeline
 * (OutbreakSavedData -> MapSyncManager -> MapSyncPacket) is unchanged.
 *
 * Only ever loaded if JourneyMap is actually installed (soft dependency,
 * see mods.toml) — JourneyMap itself finds this class via the
 * @JourneyMapPlugin annotation + its ServiceLoader-style plugin discovery,
 * not through anything lotusblight registers manually.
 *
 * The first version of this class drove syncWaypoints() from an independent
 * Forge ClientTickEvent, completely disconnected from JourneyMap's own
 * lifecycle — waypoints never reliably appeared. JourneyMap's own official
 * testmod (TeamJM/journeymap-api, ClientEventListener#spawnSampleOverlays)
 * only ever adds waypoints/overlays from inside its
 * ClientEventRegistry.MAPPING_EVENT handler on Stage.MAPPING_STARTED, and
 * clears them via IClientAPI#removeAll when mapping stops. This now follows
 * that same pattern instead of ticking independently.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public class LotusJourneyMapPlugin implements IClientPlugin {

    private static final String MOD_ID = "lotusblight";

    private static LotusJourneyMapPlugin instance;

    private IClientAPI journeyMapClientApi;

    public static LotusJourneyMapPlugin instance() {
        return instance;
    }

    @Override
    public void initialize(IClientAPI jmClientApi) {
        this.journeyMapClientApi = jmClientApi;
        instance = this;
        ClientEventRegistry.MAPPING_EVENT.subscribe(MOD_ID, this::onMappingEvent);
    }

    @Override
    public String getModId() {
        return MOD_ID;
    }

    private void onMappingEvent(MappingEvent event) {
        if (event.getStage() == MappingEvent.Stage.MAPPING_STARTED) {
            syncWaypoints(event.dimension);
        } else {
            journeyMapClientApi.removeAll(MOD_ID);
        }
    }

    /** Called on MAPPING_STARTED, and again periodically (see JourneyMapSyncTicker) whenever ClientMapCache updates. */
    public void syncWaypoints() {
        if (journeyMapClientApi == null) return;
        ResourceKey<Level> dimension = net.minecraft.client.Minecraft.getInstance().level != null
                ? net.minecraft.client.Minecraft.getInstance().level.dimension() : null;
        if (dimension == null) return;
        syncWaypoints(dimension);
    }

    private void syncWaypoints(ResourceKey<Level> dimension) {
        if (journeyMapClientApi == null) return;
        journeyMapClientApi.removeAll(MOD_ID);
        for (MapMarker marker : ClientMapCache.markers()) {
            BlockPos pos = marker.pos();
            Waypoint waypoint = WaypointFactory.createWaypoint(MOD_ID, pos, dimension, true);
            waypoint.setName(marker.heartAnchor() ? "Сердце лотоса" : "Очаг лотоса (фаза " + marker.phase() + ")");
            waypoint.setColor(marker.heartAnchor() ? Color.MAGENTA.getRGB() : Color.RED.getRGB());
            journeyMapClientApi.addWaypoint(MOD_ID, waypoint);
        }
    }
}
