package com.lotusblight.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-to-client: what the horn's bars show - cleansing uses left today and the ticks until Honcho's call and the blow are ready. */
public class HornStatePacket {
    private final int usesLeft, honchoTicks, blowTicks;

    public HornStatePacket(int usesLeft, int honchoTicks, int blowTicks) {
        this.usesLeft = usesLeft;
        this.honchoTicks = honchoTicks;
        this.blowTicks = blowTicks;
    }

    public static void encode(HornStatePacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.usesLeft);
        buf.writeVarInt(p.honchoTicks);
        buf.writeVarInt(p.blowTicks);
    }

    public static HornStatePacket decode(FriendlyByteBuf buf) {
        return new HornStatePacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(HornStatePacket p, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lotusblight.client.HornHud.update(p.usesLeft, p.honchoTicks, p.blowTicks)));
        ctx.setPacketHandled(true);
    }
}
