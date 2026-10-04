package com.lotusblight.event;

import com.lotusblight.data.LotusPlayerState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Events of the line "Путеводная". This is its own skeleton: add each real event here as a method of its own, next to {@link #onLine}.
 * The placeholder {@link #skeleton} does nothing but say so, so the line and its test command can be tried before any event is written.
 */
public final class GuidingEvents {
    private GuidingEvents() {}

    /** Whether this player is on the line "Путеводная". */
    public static boolean onLine(ServerPlayer player) {
        return false;          // the author named this line but has not defined it yet, so nobody is on it; the test command can force it
    }

    /** A placeholder event: only a message. */
    public static EventResult skeleton(ServerPlayer player, boolean force) {
        if (!force && !onLine(player)) return EventResult.WRONG_LINE;
        player.displayClientMessage(Component.literal("[заготовка] События линии «Путеводная» пока нет."), false);
        return EventResult.DONE;
    }
}
