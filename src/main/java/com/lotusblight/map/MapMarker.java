package com.lotusblight.map;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * Lightweight, network-friendly snapshot of a single outbreak marker as shown
 * on the minimap/atlas. Deliberately separate from {@link com.lotusblight.data.OutbreakRecord}
 * (the server's persisted source of truth) so the map system can carry a
 * client-relevant field — {@link #heartAnchor()}, whether the outbreak's
 * anchor block is currently a {@code lotusblight:lotus_heart} — without
 * touching the read-only data-layer classes.
 *
 * @param hidden true if this marker is only visible because the viewing
 *               player has the full-visibility modifier and has NOT
 *               physically discovered it yet (rendered dimmed/"?" on the map).
 *               Ordinary discovered markers always have hidden == false.
 */
public record MapMarker(
        UUID id,
        BlockPos pos,
        int phase,
        float progress,
        int infectedBlockCount,
        boolean heartAnchor,
        boolean hidden
) {
}
