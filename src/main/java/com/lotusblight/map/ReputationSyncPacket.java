package com.lotusblight.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-client: the receiving player's own faction standings (see LotusPlayerState's
 * Reputation_* keys) plus whether they've lived through StarFall yet - the diary's reputation
 * tab (see LotusWikiScreen) only shows up once that's true, per "репутация...после старфолла".
 * A separate packet rather than folding into PlayerStateSyncPacket so this doesn't have to touch
 * that constructor's many existing call sites.
 */
public class ReputationSyncPacket {
    private final boolean starFallSeen;
    private final int lotus;
    private final int scientists;
    private final int starLight;

    public ReputationSyncPacket(boolean starFallSeen, int lotus, int scientists, int starLight) {
        this.starFallSeen = starFallSeen;
        this.lotus = lotus;
        this.scientists = scientists;
        this.starLight = starLight;
    }

    public static void encode(ReputationSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.starFallSeen);
        buf.writeVarInt(packet.lotus);
        buf.writeVarInt(packet.scientists);
        buf.writeVarInt(packet.starLight);
    }

    public static ReputationSyncPacket decode(FriendlyByteBuf buf) {
        return new ReputationSyncPacket(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(ReputationSyncPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientReputationCache.update(packet.starFallSeen, packet.lotus, packet.scientists, packet.starLight)));
        ctx.setPacketHandled(true);
    }
}
