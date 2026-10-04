package com.lotusblight.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-to-client: whether this world is on the Chromo difficulty (the difficulty selector shows it, and the desktop fight plays its Chromo maps). */
public class ChromoStatePacket {
    private final boolean active;

    public ChromoStatePacket(boolean active) {
        this.active = active;
    }

    public static void encode(ChromoStatePacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.active);
    }

    public static ChromoStatePacket decode(FriendlyByteBuf buf) {
        return new ChromoStatePacket(buf.readBoolean());
    }

    public static void handle(ChromoStatePacket p, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lotusblight.client.ChromoClient.setActive(p.active)));
        ctx.setPacketHandled(true);
    }
}
