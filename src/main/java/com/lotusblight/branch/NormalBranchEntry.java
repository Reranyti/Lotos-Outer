package com.lotusblight.branch;

import com.lotusblight.data.LotusPlayerState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Runs once the death-run is complete: it marks the player as having entered the Нормальная_ветка and,
 * from here, drives the entry sequence (the sky reddening, the pull to the water, the lift, the world
 * coming apart on screen, and the move to the empty place). The sequence itself is built up in steps;
 * for now this locks the branch in so it can never be re-triggered or lost.
 */
public final class NormalBranchEntry {
    private NormalBranchEntry() {}

    /** Where a player arrives in the empty place. */
    private static final Vec3 ARRIVAL = new Vec3(0.5, 96.0, 0.5);

    public static void begin(ServerPlayer player) {
        if (LotusPlayerState.isNormalBranchEntered(player)) return;
        LotusPlayerState.markNormalBranchEntered(player);
        // For now the run ends by dropping the player straight into the empty place. The staged entry
        // scene (red sky -> water -> lift -> collapse) is layered in front of this next.
        NormalBranchDimension.send(player, ARRIVAL);
    }
}
