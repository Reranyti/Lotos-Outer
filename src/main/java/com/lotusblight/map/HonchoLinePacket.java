package com.lotusblight.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-client: one line Honcho says (see HonchoSpeech). With Chat Overhaul it becomes his own
 * chat message with his icon; without it, it's the same plain chat line displayClientMessage gave.
 */
public class HonchoLinePacket {
    private static final int MAX_LENGTH = 1024;

    private final String text;

    public HonchoLinePacket(String text) {
        this.text = text;
    }

    public static void encode(HonchoLinePacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.text, MAX_LENGTH);
    }

    public static HonchoLinePacket decode(FriendlyByteBuf buf) {
        return new HonchoLinePacket(buf.readUtf(MAX_LENGTH));
    }

    public static void handle(HonchoLinePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lotusblight.client.LotusClientHooks.showHonchoLine(packet.text)));
        ctx.setPacketHandled(true);
    }
}
