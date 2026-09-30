package com.lotusblight.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-to-client: a window with one button the scene waits on (an empty button text closes it). */
public class HonchoPromptPacket {
    private final String title, button;

    public HonchoPromptPacket(String title, String button) {
        this.title = title;
        this.button = button;
    }

    public static void encode(HonchoPromptPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.title, 128);
        buf.writeUtf(p.button, 64);
    }

    public static HonchoPromptPacket decode(FriendlyByteBuf buf) {
        return new HonchoPromptPacket(buf.readUtf(128), buf.readUtf(64));
    }

    public static void handle(HonchoPromptPacket p, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lotusblight.client.HonchoPromptScreen.show(p.title, p.button)));
        ctx.setPacketHandled(true);
    }
}
