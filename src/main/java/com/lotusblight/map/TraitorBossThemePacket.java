package com.lotusblight.map;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-client: tells the receiving player's client to start or stop looping
 * ModSounds.TRAITOR_BOSS_THEME (see TraitorBossMusicOverlay). Sent by TraitorBossFight
 * to bracket the traitor boss fight's music.
 */
public class TraitorBossThemePacket {
    private final boolean play;

    public TraitorBossThemePacket(boolean play) {
        this.play = play;
    }

    public static void encode(TraitorBossThemePacket packet, net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeBoolean(packet.play);
    }

    public static TraitorBossThemePacket decode(net.minecraft.network.FriendlyByteBuf buf) {
        return new TraitorBossThemePacket(buf.readBoolean());
    }

    public static void handle(TraitorBossThemePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> {
                    if (packet.play) {
                        com.lotusblight.client.TraitorBossMusicOverlay.start();
                    } else {
                        com.lotusblight.client.TraitorBossMusicOverlay.stop();
                    }
                }));
        ctx.setPacketHandled(true);
    }
}
