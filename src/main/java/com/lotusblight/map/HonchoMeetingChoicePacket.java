package com.lotusblight.map;

import com.lotusblight.entity.HonchoMeetingManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client-to-server: an answer in the trip-meeting's "Принять руку?" / "Точно?" windows. HonchoMeetingManager decides what it means for the current stage. */
public class HonchoMeetingChoicePacket {
    private final boolean accept;

    public HonchoMeetingChoicePacket(boolean accept) {
        this.accept = accept;
    }

    public static void encode(HonchoMeetingChoicePacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.accept);
    }

    public static HonchoMeetingChoicePacket decode(FriendlyByteBuf buf) {
        return new HonchoMeetingChoicePacket(buf.readBoolean());
    }

    public static void handle(HonchoMeetingChoicePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            HonchoMeetingManager.handleChoice(player, packet.accept ? HonchoMeetingManager.CHOICE_ACCEPT : HonchoMeetingManager.CHOICE_DECLINE);
        });
        ctx.setPacketHandled(true);
    }
}
