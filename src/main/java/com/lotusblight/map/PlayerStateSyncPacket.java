package com.lotusblight.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-client: pushes the receiving player's own {@link com.lotusblight.data.LotusPlayerState}
 * flags, since that data lives in server-only persistent NBT. Sent periodically
 * alongside {@link MapSyncPacket} by {@link MapSyncManager}, and once
 * immediately after a {@link DialogueChoicePacket} locks a branch in, so the
 * dialogue screen doesn't have to wait for the next periodic sync to reflect it.
 */
public class PlayerStateSyncPacket {
    private final int dialogueBranch;
    private final boolean fullMapVisibility;
    private final boolean heardInnerVoice;
    private final boolean hasSeenGuardian;
    private final int allianceCleanseUses;

    public PlayerStateSyncPacket(int dialogueBranch, boolean fullMapVisibility, boolean heardInnerVoice, boolean hasSeenGuardian, int allianceCleanseUses) {
        this.dialogueBranch = dialogueBranch;
        this.fullMapVisibility = fullMapVisibility;
        this.heardInnerVoice = heardInnerVoice;
        this.hasSeenGuardian = hasSeenGuardian;
        this.allianceCleanseUses = allianceCleanseUses;
    }

    public static void encode(PlayerStateSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.dialogueBranch);
        buf.writeBoolean(packet.fullMapVisibility);
        buf.writeBoolean(packet.heardInnerVoice);
        buf.writeBoolean(packet.hasSeenGuardian);
        buf.writeVarInt(packet.allianceCleanseUses);
    }

    public static PlayerStateSyncPacket decode(FriendlyByteBuf buf) {
        return new PlayerStateSyncPacket(buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt());
    }

    public static void handle(PlayerStateSyncPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> ClientPlayerStateCache.update(packet.dialogueBranch, packet.fullMapVisibility, packet.heardInnerVoice, packet.hasSeenGuardian, packet.allianceCleanseUses));
        ctx.setPacketHandled(true);
    }
}
