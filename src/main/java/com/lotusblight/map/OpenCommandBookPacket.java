package com.lotusblight.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-to-client: opens the admin command-reference book screen (see com.lotusblight.command.LotusCommands#openCommandBook and LotusCommandBookScreen). No payload - the client already knows the full command catalog. */
public class OpenCommandBookPacket {
    public static void encode(OpenCommandBookPacket packet, FriendlyByteBuf buf) {
    }

    public static OpenCommandBookPacket decode(FriendlyByteBuf buf) {
        return new OpenCommandBookPacket();
    }

    public static void handle(OpenCommandBookPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lotusblight.client.LotusCommandBookScreen.open()));
        ctx.setPacketHandled(true);
    }
}
