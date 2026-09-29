package com.lotusblight.branch;

import com.lotusblight.data.LotusPlayerState;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Runs once the death-run is complete: it marks the player as having entered the Нормальная_ветка and,
 * from here, drives the entry sequence (the sky reddening, the pull to the water, the lift, the world
 * coming apart on screen, and the move to the empty place). The sequence itself is built up in steps;
 * for now this locks the branch in so it can never be re-triggered or lost.
 */
public final class NormalBranchEntry {
    private static final Logger LOG = LogUtils.getLogger();

    private NormalBranchEntry() {}

    /** Where a player arrives in the empty place. */
    private static final Vec3 ARRIVAL = new Vec3(0.5, 96.0, 0.5);

    public static void begin(ServerPlayer player) {
        if (LotusPlayerState.isNormalBranchEntered(player)) return;
        LotusPlayerState.markNormalBranchEntered(player);
        // Run the entry scene; it calls finish() when the collapse is done.
        NormalBranchScene.start(player);
    }

    /** Called at the end of the entry scene: drop the player into the empty place. */
    static void finish(ServerPlayer player) {
        if (NormalBranchDimension.send(player, ARRIVAL)) {
            LotusPlayerState.markNormalBranchArrived(player);
        } else {
            LOG.warn("Нормальная_ветка: the hollow dimension is missing, {} stays where they are", player.getScoreboardName());
        }
    }
}
