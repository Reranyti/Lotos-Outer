package com.lotusblight.event;

import com.lotusblight.data.LotusPlayerState;
import net.minecraft.server.level.ServerPlayer;

/**
 * Events of the neutral line - a player who has chosen neither the alliance nor the war. Each real event is a method of its own;
 * the first is {@link LeafilesEvent} (the whole inventory turns into a folder).
 */
public final class NeutralEvents {
    private NeutralEvents() {}

    /** Whether this player is on the neutral line. */
    public static boolean onLine(ServerPlayer player) {
        // the hidden branch is entered from the undecided state, but its players are not on the neutral line any more
        return LotusPlayerState.getDialogueBranch(player) == LotusPlayerState.BRANCH_UNDECIDED && !LotusPlayerState.isNormalBranchEntered(player);
    }

    /** "Лейфайлс": the whole inventory becomes a folder. */
    public static EventResult leafiles(ServerPlayer player, boolean force) {
        if (!force && !onLine(player)) return EventResult.WRONG_LINE;
        return LeafilesEvent.run(player);
    }
}
