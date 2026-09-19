package com.lotusblight.map;

import com.lotusblight.data.LotusPlayerState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-to-server, no payload: sent once when {@link com.lotusblight.client.LotusDialogueScreen}
 * is actually opened. The only server-observable signal that a player has spoken to the Lotus at
 * all before locking in a branch — see {@link LotusPlayerState#hasTalkedToLotus}, which
 * GlowingBerryItem now gates the inner-voice scene behind.
 */
public class DialogueOpenedPacket {
    public static void encode(DialogueOpenedPacket packet, FriendlyByteBuf buf) {
    }

    public static DialogueOpenedPacket decode(FriendlyByteBuf buf) {
        return new DialogueOpenedPacket();
    }

    public static void handle(DialogueOpenedPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            LotusPlayerState.setHasTalkedToLotus(player);
        });
        ctx.setPacketHandled(true);
    }
}
