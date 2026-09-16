package com.lotusblight.map.journeymap;

/**
 * SKELETON — not wired up yet. Plan: replace the mod's own
 * LotusHudOverlay/LotusAtlasScreen with waypoints/overlays registered
 * through the official journeymap-api (@JourneyMapPlugin, API v2.0.0 —
 * NOT the deprecated @ClientPlugin). The existing server-side pipeline
 * (OutbreakSavedData -> MapSyncManager -> ClientMapCache) stays as-is; only
 * the client rendering target changes.
 *
 * Still needed before this does anything:
 * - Exact journeymap-api Maven coordinates/repository for Forge 1.20.1 in
 *   build.gradle (unconfirmed — API 2.0.0 restructured around a
 *   MultiLoader Template, need to double check the Forge artifact name).
 * - journeymap dependency block in mods.toml (mandatory=true — this becomes
 *   a required external mod, same tier as GeckoLib, not soft like Streams
 *   Reflowing).
 * - The actual plugin implementation: read ClientMapCache.markers() and
 *   push them as JourneyMap waypoints/overlays.
 * - Decide whether LotusHudOverlay/LotusAtlasScreen get deleted outright or
 *   just stop being registered (user decided: replace, not keep both).
 */
public final class LotusJourneyMapPlugin {
    private LotusJourneyMapPlugin() {
    }
}
