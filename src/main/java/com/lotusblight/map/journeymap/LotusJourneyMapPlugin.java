package com.lotusblight.map.journeymap;

import com.lotusblight.map.ClientMapCache;
import com.lotusblight.map.MapMarker;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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
 * API usage confirmed against TeamJM/journeymap-api's own testmod
 * (branch 1.20.1_2.0, SampleWaypointFactory.java) — WaypointFactory.createWaypoint
 * is a static call, not an instantiated factory (unlike the internal,
 * @ApiStatus.Internal WaypointStore-based constructor mods can't touch).
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public class LotusJourneyMapPlugin implements IClientPlugin {

    private static final String MOD_ID = "lotusblight";

    /**
     * JourneyMap instantiates this class itself (reflection, no-arg constructor) via its own
     * plugin discovery — IClientPlugin has no tick/periodic callback, only initialize()/getModId().
     * Stashing the instance here lets a separate Forge ClientTickEvent handler (TODO: not yet
     * wired) call syncWaypoints() periodically without needing to locate the JourneyMap-owned
     * instance any other way.
     */
    private static LotusJourneyMapPlugin instance;

    private IClientAPI journeyMapClientApi;
    private final Set<UUID> shownWaypointIds = new HashSet<>();

    public static LotusJourneyMapPlugin instance() {
        return instance;
    }

    @Override
    public void initialize(IClientAPI jmClientApi) {
        this.journeyMapClientApi = jmClientApi;
        instance = this;
    }

    @Override
    public String getModId() {
        return MOD_ID;
    }

    /** Called periodically (e.g. from a client tick handler wired elsewhere) whenever ClientMapCache updates. */
    public void syncWaypoints() {
        if (journeyMapClientApi == null || Minecraft.getInstance().level == null) {
            return;
        }
        ResourceKey<Level> dimension = Minecraft.getInstance().level.dimension();
        Set<UUID> stillPresent = new HashSet<>();
        for (MapMarker marker : ClientMapCache.markers()) {
            stillPresent.add(marker.id());
            BlockPos pos = marker.pos();
            Waypoint waypoint = WaypointFactory.createWaypoint(MOD_ID, pos, dimension, true);
            waypoint.setColor(marker.heartAnchor() ? Color.MAGENTA.getRGB() : Color.GREEN.getRGB());
            journeyMapClientApi.addWaypoint(MOD_ID, waypoint);
            shownWaypointIds.add(marker.id());
        }
        shownWaypointIds.retainAll(stillPresent);
    }
}
