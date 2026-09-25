package com.lotusblight.map;

import com.lotusblight.advancement.HonchoTripPleasedTrigger;
import com.lotusblight.advancement.HonchoTripRudeTrigger;
import com.lotusblight.data.LotusPlayerState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client-to-server: the player answered the rare trip-meeting's choice (see HonchoMeetingScreen). */
public class HonchoMeetingChoicePacket {
    private final boolean tookHand;

    public HonchoMeetingChoicePacket(boolean tookHand) {
        this.tookHand = tookHand;
    }

    public static void encode(HonchoMeetingChoicePacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.tookHand);
    }

    public static HonchoMeetingChoicePacket decode(FriendlyByteBuf buf) {
        return new HonchoMeetingChoicePacket(buf.readBoolean());
    }

    public static void handle(HonchoMeetingChoicePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || LotusPlayerState.hasMetHoncho(player) || !LotusPlayerState.isHonchoMeetingPending(player)) return;
            LotusPlayerState.markMetHoncho(player);
            if (packet.tookHand) {
                HonchoTripPleasedTrigger.INSTANCE.trigger(player);
                player.displayClientMessage(Component.literal("— Вот так. Осторожнее под ногами."), false);
            } else {
                HonchoTripRudeTrigger.INSTANCE.trigger(player);
                player.displayClientMessage(Component.literal("— ...Ладно. Сам, так сам."), false);
            }
        });
        ctx.setPacketHandled(true);
    }
}
