package com.lotusblight.map;

import com.lotusblight.world.ChromoDifficulty;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client-to-server: the player chose Chromo in the difficulty selector and accepted the warning. */
public class ChromoRequestPacket {
    public ChromoRequestPacket() {}

    public static void encode(ChromoRequestPacket p, FriendlyByteBuf buf) {}

    public static ChromoRequestPacket decode(FriendlyByteBuf buf) {
        return new ChromoRequestPacket();
    }

    public static void handle(ChromoRequestPacket p, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ServerPlayer sender = ctx.getSender();
        ctx.enqueueWork(() -> {
            if (sender == null) return;
            if (ChromoDifficulty.mayEnable(sender)) ChromoDifficulty.enable(sender.getServer());
            ChromoDifficulty.sync(sender);               // the answer either way: the selector shows what the world really is
        });
        ctx.setPacketHandled(true);
    }
}
