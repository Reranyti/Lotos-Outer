package com.lotusblight.event;

import com.lotusblight.data.LotusPlayerState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Events of the line "Хончо". This is its own skeleton: add each real event here as a method of its own, next to {@link #onLine}.
 * The placeholder {@link #skeleton} does nothing but say so, so the line and its test command can be tried before any event is written.
 */
public final class HonchoEvents {
    private HonchoEvents() {}

    /** Whether this player is on the line "Хончо". */
    public static boolean onLine(ServerPlayer player) {
        return LotusPlayerState.hasMetHoncho(player);
    }

    /** A placeholder event: only a message. */
    public static EventResult skeleton(ServerPlayer player, boolean force) {
        if (!force && !onLine(player)) return EventResult.WRONG_LINE;
        player.displayClientMessage(Component.literal("[заготовка] События линии «Хончо» пока нет."), false);
        return EventResult.DONE;
    }
}
