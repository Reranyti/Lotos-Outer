package com.lotusblight.client.gecko;

/**
 * SKELETON — not wired up yet. Plan: GeckoLib-driven idle "breathing"
 * animation for the infected_lotus crown (LotusMainBlock, PART=3). Playback
 * speed should scale with the outbreak's current phase (see
 * com.lotusblight.spread.InfectionPhases) — slow and calm at phase 1, faster
 * by phase 4.
 *
 * Still needed before this does anything:
 * - A GeoBlockEntity for the crown part (LotusMainBlock currently has no
 *   BlockEntity at all).
 * - assets/lotusblight/geo/infected_lotus_crown.geo.json — bone geometry;
 *   can reuse the coordinates already sitting unused in
 *   models/block/infected_lotus.json (petals/center/stem elements).
 * - assets/lotusblight/animations/infected_lotus_crown.animation.json — the
 *   actual idle keyframes.
 * - A GeoBlockRenderer registered client-side.
 */
public final class LotusCrownAnimation {
    private LotusCrownAnimation() {
    }
}
