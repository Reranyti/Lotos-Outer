package com.lotusblight.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-client: whether the main lotus has gone quiet for this player - the single outward sign of
 * being partway into the Нормальная_ветка (after the three drownings). While it holds, the client won't
 * open the lotus dialogue and the shoots make no sound.
 */
public class NormalBranchSilencePacket {
    private final boolean silenced;

    public NormalBranchSilencePacket(boolean silenced) {
        this.silenced = silenced;
    }

    public static void encode(NormalBranchSilencePacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.silenced);
    }

    public static NormalBranchSilencePacket decode(FriendlyByteBuf buf) {
        return new NormalBranchSilencePacket(buf.readBoolean());
    }

    public static void handle(NormalBranchSilencePacket p, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lotusblight.client.NormalBranchClient.setSilenced(p.silenced)));
        ctx.setPacketHandled(true);
    }
}
