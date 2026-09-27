package com.lotusblight.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-client: drives the Нормальная_ветка entry scene on screen - a stage (0 = clear/off) and how
 * far along that stage is (0..1), which the overlay turns into the reddening and, at the end, the
 * bordeaux flood and shake. The server owns the timing; the client only paints what it's told.
 */
public class NormalBranchScenePacket {
    private final int stage;
    private final float intensity;

    public NormalBranchScenePacket(int stage, float intensity) {
        this.stage = stage;
        this.intensity = intensity;
    }

    public static void encode(NormalBranchScenePacket p, FriendlyByteBuf buf) {
        buf.writeByte(p.stage);
        buf.writeFloat(p.intensity);
    }

    public static NormalBranchScenePacket decode(FriendlyByteBuf buf) {
        return new NormalBranchScenePacket(buf.readByte(), buf.readFloat());
    }

    public static void handle(NormalBranchScenePacket p, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lotusblight.client.NormalBranchSceneOverlay.set(p.stage, p.intensity)));
        ctx.setPacketHandled(true);
    }
}
