package com.lotusblight.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-to-client: play the trip-meeting cutscene (see HonchoMeetingManager/HonchoMeetingCutscene). Carries Honcho's entity id so the camera can look up at him. */
public class ShowHonchoMeetingPacket {
    private final int honchoEntityId;

    public ShowHonchoMeetingPacket(int honchoEntityId) {
        this.honchoEntityId = honchoEntityId;
    }

    public static void encode(ShowHonchoMeetingPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.honchoEntityId);
    }

    public static ShowHonchoMeetingPacket decode(FriendlyByteBuf buf) {
        return new ShowHonchoMeetingPacket(buf.readVarInt());
    }

    public static void handle(ShowHonchoMeetingPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lotusblight.client.HonchoMeetingCutscene.start(packet.honchoEntityId)));
        ctx.setPacketHandled(true);
    }
}
