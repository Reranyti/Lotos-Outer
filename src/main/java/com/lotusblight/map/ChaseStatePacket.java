package com.lotusblight.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-client: tells the receiving player's client to start or stop the "Побег от лотоса"
 * countdown HUD (see LotusChaseOverlay). {@code active=false} covers both a clean escape and being
 * caught - the client doesn't need to know which, the server already resolved it.
 */
public class ChaseStatePacket {
    private final boolean active;
    private final int durationTicks;

    public ChaseStatePacket(boolean active, int durationTicks) {
        this.active = active;
        this.durationTicks = durationTicks;
    }

    public static void encode(ChaseStatePacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.active);
        buf.writeVarInt(packet.durationTicks);
    }

    public static ChaseStatePacket decode(FriendlyByteBuf buf) {
        return new ChaseStatePacket(buf.readBoolean(), buf.readVarInt());
    }

    public static void handle(ChaseStatePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (packet.active) {
                com.lotusblight.client.LotusChaseOverlay.start(packet.durationTicks);
            } else {
                com.lotusblight.client.LotusChaseOverlay.stop();
            }
        }));
        ctx.setPacketHandled(true);
    }
}
