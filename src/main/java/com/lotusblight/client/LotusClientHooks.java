package com.lotusblight.client;

import com.lotusblight.map.ClientMapCache;
import com.lotusblight.map.MapMarker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;

public final class LotusClientHooks {
    private LotusClientHooks() {
    }

    public static void openDialogue(BlockHitResult hit) {
        Minecraft.getInstance().setScreen(new LotusDialogueScreen(nearestKnownPhase(hit.getBlockPos()), hit));
    }

    /**
     * The dialogue screen used to always open at phase 0 regardless of the
     * outbreak's real state — the client never had any outbreak data to read.
     * It does now: {@link ClientMapCache} already carries the phase of every
     * marker the server has synced (see MapSyncManager), so we just look up
     * whichever marker is closest to the block the player clicked.
     */
    private static int nearestKnownPhase(BlockPos clicked) {
        MapMarker nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (MapMarker marker : ClientMapCache.markers()) {
            double distSq = marker.pos().distSqr(clicked);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = marker;
            }
        }
        return nearest == null ? 0 : nearest.phase();
    }
}
