package com.lotusblight.map.journeymap;

import com.lotusblight.map.ClientMapCache;
import com.lotusblight.map.MapMarker;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.event.MappingEvent;
import journeymap.api.v2.client.model.MapPolygonWithHoles;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.util.PolygonHelper;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.ClientEventRegistry;
import journeymap.api.v2.client.event.RegistryEvent;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

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
    private LotusJourneyMapOptions options;

    public static LotusJourneyMapPlugin instance() {
        return instance;
    }

    @Override
    public void initialize(IClientAPI jmClientApi) {
        this.journeyMapClientApi = jmClientApi;
        instance = this;
        ClientEventRegistry.MAPPING_EVENT.subscribe(MOD_ID, this::onMappingEvent);
        // A dedicated options category, same as every other JourneyMap-integrated mod exposes —
        // before this the markers/overlay were unconditional with no way to turn either off from
        // JourneyMap's own options screen.
        ClientEventRegistry.OPTIONS_REGISTRY_EVENT.subscribe(MOD_ID, (RegistryEvent.OptionsRegistryEvent event) -> options = new LotusJourneyMapOptions());
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
        if (options == null || options.showOutbreakMarkers.get()) {
            for (MapMarker marker : ClientMapCache.markers()) {
                BlockPos pos = marker.pos();
                Waypoint waypoint = WaypointFactory.createWaypoint(MOD_ID, pos, dimension, true);
                waypoint.setName(marker.heartAnchor() ? "Сердце лотоса" : "Очаг лотоса (фаза " + marker.phase() + ")");
                waypoint.setColor(marker.heartAnchor() ? Color.MAGENTA.getRGB() : Color.RED.getRGB());
                journeyMapClientApi.addWaypoint(MOD_ID, waypoint);
            }
        }
        if (options == null || options.showInfectionArea.get()) {
            syncInfectionArea(dimension);
        }
    }

    /**
     * The map used to only ever place a single point per outbreak, no matter how far its actual
     * infected footprint had spread - "самая главная [функция] ОТОБРАЖАТЬ ЗАРАЖЕНИЕ" (display the
     * infection itself, not just a marker). OutbreakSavedData already tracks every infected chunk
     * incrementally; this merges that chunk set into real filled polygons via JourneyMap's own
     * PolygonHelper instead of drawing one shape per chunk.
     */
    private void syncInfectionArea(ResourceKey<Level> dimension) {
        List<Long> keys = ClientMapCache.infectedChunkKeys();
        if (keys.isEmpty()) return;

        List<ChunkPos> chunks = new ArrayList<>(keys.size());
        for (long key : keys) {
            chunks.add(new ChunkPos(key));
        }

        ShapeProperties shapeProperties = new ShapeProperties()
                .setStrokeWidth(2)
                .setStrokeColor(0x2ecc40).setStrokeOpacity(0.8f)
                .setFillColor(0x2ecc40).setFillOpacity(0.22f);

        for (MapPolygonWithHoles polygon : PolygonHelper.createChunksPolygon(chunks, 64)) {
            PolygonOverlay overlay = new PolygonOverlay(MOD_ID, dimension, shapeProperties, polygon);
            overlay.setOverlayGroupName("Заражение лотоса");
            overlay.setLabel("Заражённая территория");
            try {
                journeyMapClientApi.show(overlay);
            } catch (Exception ignored) {
                // JourneyMap's own show() is declared to throw - nothing useful to recover here,
                // and one bad polygon shouldn't stop the rest of the sync.
            }
        }
    }
}
