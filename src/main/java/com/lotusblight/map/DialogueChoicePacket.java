package com.lotusblight.map;

import com.lotusblight.data.LotusPlayerState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-to-server: the player has committed to a dialogue branch with the
 * Lotus. Only {@code joined == true} (the ALLIANCE branch) writes anything —
 * it's what actually flips {@link LotusPlayerState#setJoinedLotus}, the flag
 * that gates the infection-spreading tools. Before this packet existed, the
 * dialogue screen tracked the chosen branch only in its own local field, so
 * the choice was thrown away the moment the screen closed and the "join"
 * ending could never actually be reached through conversation.
 */
public class DialogueChoicePacket {
    private final boolean joined;

    public DialogueChoicePacket(boolean joined) {
        this.joined = joined;
    }

    public static void encode(DialogueChoicePacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.joined);
    }

    public static DialogueChoicePacket decode(FriendlyByteBuf buf) {
        return new DialogueChoicePacket(buf.readBoolean());
    }

    public static void handle(DialogueChoicePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null && packet.joined) {
                LotusPlayerState.setJoinedLotus(player, true);
            }
        });
        ctx.setPacketHandled(true);
    }
}
