package com.lotusblight.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server-to-client: tells the client to display an inner-voice scene (see
 * InnerVoiceOverlay). The server decides whether a scene fires at all
 * (LotusPlayerState#canTriggerInnerVoiceFreely) — the client no longer runs
 * its own independent copy of that decision, since it only has an eventually
 * -synced mirror of the player's real state, not the authoritative count.
 */
public class ShowInnerVoicePacket {
    private final List<String> lines;

    public ShowInnerVoicePacket(List<String> lines) {
        this.lines = lines;
    }

    public static void encode(ShowInnerVoicePacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.lines.size());
        for (String line : packet.lines) {
            buf.writeUtf(line, 512);
        }
    }

    public static ShowInnerVoicePacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<String> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lines.add(buf.readUtf(512));
        }
        return new ShowInnerVoicePacket(lines);
    }

    public static void handle(ShowInnerVoicePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lotusblight.client.InnerVoiceOverlay.show(packet.lines)));
        ctx.setPacketHandled(true);
    }
}
