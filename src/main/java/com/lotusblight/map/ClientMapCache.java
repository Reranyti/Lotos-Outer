package com.lotusblight.map;

import java.util.List;

/**
 * Client-side cache of the outbreak markers most recently pushed by
 * {@link MapSyncManager} via {@link MapSyncPacket}. This is the ONLY source
 * of outbreak data the map item, HUD minimap and atlas screen are allowed to
 * read from — none of them may scan blocks. Purely data (no Minecraft client
 * classes), so it's safe to reference from common code too.
 */
public final class ClientMapCache {
    private static volatile List<MapMarker> markers = List.of();
    private static volatile long lastUpdateMs = 0L;

    private ClientMapCache() {
    }

    public static void update(List<MapMarker> newMarkers) {
        markers = List.copyOf(newMarkers);
        lastUpdateMs = System.currentTimeMillis();
    }

    public static List<MapMarker> markers() {
        return markers;
    }

    public static long lastUpdateMs() {
        return lastUpdateMs;
    }

    /** Clears the cache, e.g. on disconnect, so a stale server's markers don't linger. */
    public static void clear() {
        markers = List.of();
        lastUpdateMs = 0L;
    }
}
