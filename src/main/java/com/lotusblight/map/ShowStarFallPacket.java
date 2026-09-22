package com.lotusblight.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-client: tells the receiving player's client to play the "StarFall" scene (see
 * StarFallOverlay). Carries only which branch's lines to show - the actual text
 * (StarFallLibrary) is static content already present in both jars.
 */
public class ShowStarFallPacket {
    private final boolean allianceBranch;

    public ShowStarFallPacket(boolean allianceBranch) {
        this.allianceBranch = allianceBranch;
    }

    public static void encode(ShowStarFallPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.allianceBranch);
    }

    public static ShowStarFallPacket decode(FriendlyByteBuf buf) {
        return new ShowStarFallPacket(buf.readBoolean());
    }

    public static void handle(ShowStarFallPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lotusblight.client.StarFallOverlay.show(packet.allianceBranch)));
        ctx.setPacketHandled(true);
    }
}
