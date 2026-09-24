package com.lotusblight.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-to-client: play Honcho's "let me be your assistant" scene (see HonchoAssistantOverlay). No payload - the text is static content already present in both jars. */
public class ShowHonchoAssistantPacket {
    public static void encode(ShowHonchoAssistantPacket packet, FriendlyByteBuf buf) {
    }

    public static ShowHonchoAssistantPacket decode(FriendlyByteBuf buf) {
        return new ShowHonchoAssistantPacket();
    }

    public static void handle(ShowHonchoAssistantPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lotusblight.client.HonchoAssistantOverlay.show()));
        ctx.setPacketHandled(true);
    }
}
