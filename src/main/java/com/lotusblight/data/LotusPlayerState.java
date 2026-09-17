package com.lotusblight.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Per-player flags that survive death/relog via vanilla persistent NBT
 * (the tag block that already survives respawn, e.g. used for the "keep on
 * death" advancement/recipe data).
 *
 * - dialogueBranch: which ending branch the player has committed to in
 *   conversation with the Lotus (see com.lotusblight.dialogue.LotusDialogueLibrary.Branch).
 *   Stored as an ordinal so this class doesn't need to depend on the client
 *   package. Once set away from UNDECIDED it is meant to be permanent — the
 *   dialogue screen locks the choice in and never re-offers it.
 * - fullMapVisibility: whether the player has obtained the "full visibility"
 *   modifier that reveals every known outbreak on the map/minimap/atlas at
 *   once, instead of only outbreaks they've physically discovered.
 */
public final class LotusPlayerState {

    public static final int BRANCH_UNDECIDED = 0;
    public static final int BRANCH_RESISTANCE = 1;
    public static final int BRANCH_ALLIANCE = 2;

    private static final String ROOT_TAG = "LotusBlightState";
    private static final String DIALOGUE_BRANCH_KEY = "DialogueBranch";
    private static final String FULL_MAP_VISIBILITY_KEY = "FullMapVisibility";

    private LotusPlayerState() {
    }

    private static CompoundTag root(Player player, boolean createIfMissing) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(ROOT_TAG)) {
            if (!createIfMissing) {
                return new CompoundTag();
            }
            persistent.put(ROOT_TAG, new CompoundTag());
        }
        return persistent.getCompound(ROOT_TAG);
    }

    public static int getDialogueBranch(Player player) {
        return root(player, false).getInt(DIALOGUE_BRANCH_KEY);
    }

    /** No-op if a branch is already locked in — the choice is meant to be one-way. */
    public static boolean setDialogueBranch(Player player, int branch) {
        CompoundTag root = root(player, true);
        if (root.getInt(DIALOGUE_BRANCH_KEY) != BRANCH_UNDECIDED) {
            return false;
        }
        root.putInt(DIALOGUE_BRANCH_KEY, branch);
        player.getPersistentData().put(ROOT_TAG, root);
        return true;
    }

    /** Admin-only override for testing (see com.lotusblight.command.LotusCommands) — bypasses the one-way lock that {@link #setDialogueBranch} enforces for real dialogue choices. */
    public static void forceDialogueBranch(Player player, int branch) {
        CompoundTag root = root(player, true);
        root.putInt(DIALOGUE_BRANCH_KEY, branch);
        player.getPersistentData().put(ROOT_TAG, root);
    }

    public static boolean hasJoinedLotus(Player player) {
        return getDialogueBranch(player) == BRANCH_ALLIANCE;
    }

    public static boolean hasFullMapVisibility(Player player) {
        return root(player, false).getBoolean(FULL_MAP_VISIBILITY_KEY);
    }

    public static void setFullMapVisibility(Player player, boolean visible) {
        CompoundTag root = root(player, true);
        root.putBoolean(FULL_MAP_VISIBILITY_KEY, visible);
        player.getPersistentData().put(ROOT_TAG, root);
    }
}
