package com.lotusblight.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-to-client: play the rare "trip and see a hand" first-meeting scene with Honcho (see HonchoMeetingManager/HonchoMeetingScreen). No payload. */
public class ShowHonchoMeetingPacket {
    public static void encode(ShowHonchoMeetingPacket packet, FriendlyByteBuf buf) {
    }

    public static ShowHonchoMeetingPacket decode(FriendlyByteBuf buf) {
        return new ShowHonchoMeetingPacket();
    }

    public static void handle(ShowHonchoMeetingPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lotusblight.client.HonchoMeetingScreen.show()));
        ctx.setPacketHandled(true);
    }
}
