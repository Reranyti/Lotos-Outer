package com.lotusblight.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-client: start the Нормальная_ветка exit on this client. Carries nothing - the client decides
 * and does everything from here with its own installation.
 */
public class NormalBranchExitPacket {
    public NormalBranchExitPacket() {}

    public static void encode(NormalBranchExitPacket p, FriendlyByteBuf buf) {}

    public static NormalBranchExitPacket decode(FriendlyByteBuf buf) {
        return new NormalBranchExitPacket();
    }

    public static void handle(NormalBranchExitPacket p, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lotusblight.client.NormalBranchFreeze.begin()));
        ctx.setPacketHandled(true);
    }
}
