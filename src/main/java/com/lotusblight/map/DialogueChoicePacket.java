package com.lotusblight.map;

import com.lotusblight.data.LotusPlayerState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Client-to-server: the player has confirmed a dialogue branch with the
 * Lotus. {@link LotusPlayerState#setDialogueBranch} is a one-way write — it
 * silently no-ops if a branch is already locked in, so this packet can't be
 * replayed to flip sides later. On success we immediately push a
 * {@link PlayerStateSyncPacket} back so the dialogue screen (and anything
 * else gated on the flag) reflects the lock without waiting for the next
 * periodic sync.
 */
public class DialogueChoicePacket {
    private final int branch;

    public DialogueChoicePacket(int branch) {
        this.branch = branch;
    }

    public static void encode(DialogueChoicePacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.branch);
    }

    public static DialogueChoicePacket decode(FriendlyByteBuf buf) {
        return new DialogueChoicePacket(buf.readVarInt());
    }

    public static void handle(DialogueChoicePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            // Was only excluding UNDECIDED - any other garbage value (a modified client, or just a
            // stray packet) would still get stored verbatim by setDialogueBranch. Since that write
            // is one-way/permanent, a value that isn't actually RESISTANCE or ALLIANCE would lock
            // the player out of both branches' content forever with no way to recover.
            if (player == null
                    || (packet.branch != LotusPlayerState.BRANCH_RESISTANCE && packet.branch != LotusPlayerState.BRANCH_ALLIANCE)) {
                return;
            }
            boolean locked = LotusPlayerState.setDialogueBranch(player, packet.branch);
            // "репутация...плохие действия плохо хорошие хорошо" - committing to a side is the single
            // biggest one-time signal of where the player stands with the Lotus, only counted once
            // thanks to setDialogueBranch's own one-way lock (locked is false on a repeat attempt).
            if (locked) {
                LotusPlayerState.addReputation(player, com.lotusblight.data.Faction.LOTUS,
                        packet.branch == LotusPlayerState.BRANCH_ALLIANCE ? 10 : -10);
            }
            ChatOverhaulBranchColor.applyBranchColor(player, packet.branch);
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PlayerStateSyncPacket(
                    LotusPlayerState.getDialogueBranch(player), LotusPlayerState.hasFullMapVisibility(player),
                    LotusPlayerState.hasHeardInnerVoice(player), LotusPlayerState.hasSeenGuardian(player),
                    LotusPlayerState.getAllianceCleanseUses(player)));
        });
        ctx.setPacketHandled(true);
    }
}
