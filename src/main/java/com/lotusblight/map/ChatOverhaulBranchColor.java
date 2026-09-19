package com.lotusblight.map;

import com.example.chatoverhaul.data.NickColorManager;
import com.example.chatoverhaul.network.NetworkHandler;
import com.example.chatoverhaul.network.SyncColorPacket;
import com.lotusblight.data.LotusPlayerState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.PacketDistributor;

/**
 * Soft dependency on the "Chat Overhaul" mod (Lololoshka-style centered chat
 * with per-player nickname colors): the moment a player locks in a dialogue
 * branch (see {@link DialogueChoicePacket}), their chat nickname is tinted to
 * match it — green for ALLIANCE, red for RESISTANCE — so the choice is
 * visible everywhere they type, not just on the dialogue screen and boss bar.
 * Guarded by {@link #LOADED} exactly like InfectionSpreadEngine's Streams
 * Reflowing check, so this class is only ever touched when the mod is
 * actually present; without it, dialogue branches work exactly as before.
 *
 * Safe in a way LotusSeerLensItem's reverted Curios attempt was not: that
 * case failed because `implements ICurioItem` puts the optional type in the
 * class's own hierarchy, which the JVM resolves eagerly at class-load time
 * regardless of any runtime check. Here chatoverhaul's types only appear as
 * method-body call targets (static calls inside applyColor), which the
 * JVM resolves lazily per bytecode instruction - the `if (!LOADED) return`
 * above runs and exits before those instructions ever execute when the mod
 * is absent, so this class loads and links fine either way.
 *
 * The same trick also marks {@link com.lotusblight.effect.TrueLightEffect}:
 * gold while it's active (see GlowingBerryItem), reverting to the player's
 * branch color - or clearing back to default if still UNDECIDED - the moment
 * it expires (see the {@code TrueLightExpiryListener} companion below).
 */
public final class ChatOverhaulBranchColor {
    public static final boolean LOADED = ModList.get().isLoaded("chatoverhaul");

    private ChatOverhaulBranchColor() {}

    public static void applyBranchColor(ServerPlayer player, int branch) {
        String colorName = switch (branch) {
            case LotusPlayerState.BRANCH_ALLIANCE -> "green";
            case LotusPlayerState.BRANCH_RESISTANCE -> "red";
            default -> null;
        };
        if (colorName == null) {
            clearColor(player);
        } else {
            applyColor(player, colorName);
        }
    }

    public static void applyTrueLightColor(ServerPlayer player) {
        applyColor(player, "gold");
    }

    /** Called when TRUE_LIGHT expires - falls back to whatever the player's branch (if any) says. */
    public static void revertToBranchColor(ServerPlayer player) {
        applyBranchColor(player, LotusPlayerState.getDialogueBranch(player));
    }

    private static void applyColor(ServerPlayer player, String colorName) {
        if (!LOADED) return;
        String playerName = player.getGameProfile().getName();
        NickColorManager.setColor(playerName, colorName);
        NickColorManager.save();
        NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), new SyncColorPacket(playerName, colorName));
    }

    private static void clearColor(ServerPlayer player) {
        if (!LOADED) return;
        String playerName = player.getGameProfile().getName();
        NickColorManager.removeColor(playerName);
        NickColorManager.save();
        // "reset" isn't a real registered color name, but Chat Overhaul's own sync path only
        // cares that clients re-fetch/clear this player's entry - reusing SyncColorPacket with the
        // default marker keeps this to the one packet type instead of adding a second.
        NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), new SyncColorPacket(playerName, "white"));
    }
}
