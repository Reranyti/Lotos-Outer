package com.lotusblight.branch;

import com.lotusblight.data.LotusPlayerState;
import net.minecraft.server.level.ServerPlayer;

/**
 * Runs once the death-run is complete: it marks the player as having entered the Нормальная_ветка and,
 * from here, drives the entry sequence (the sky reddening, the pull to the water, the lift, the world
 * coming apart on screen, and the move to the empty place). The sequence itself is built up in steps;
 * for now this locks the branch in so it can never be re-triggered or lost.
 */
public final class NormalBranchEntry {
    private NormalBranchEntry() {}

    public static void begin(ServerPlayer player) {
        if (LotusPlayerState.isNormalBranchEntered(player)) return;
        LotusPlayerState.markNormalBranchEntered(player);
        // The staged entry scene (red sky -> water -> lift -> collapse -> teleport) is wired in next.
    }
}
