package com.lotusblight.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Per-player flags that survive death/relog via vanilla persistent NBT
 * (the tag block that already survives respawn, e.g. used for the "keep on
 * death" advancement/recipe data). Two flags live here:
 *
 * - joinedLotus: which ending branch the player has committed to. Gates the
 *   infection-spreading tools (item.spread package) — they only work once
 *   this is true, and it is meant to be a one-way choice.
 * - fullMapVisibility: whether the player has obtained the "full visibility"
 *   modifier that reveals every known outbreak on the map/minimap/atlas at
 *   once, instead of only outbreaks they've physically discovered.
 */
public final class LotusPlayerState {

    private static final String ROOT_TAG = "LotusBlightState";
    private static final String JOINED_LOTUS_KEY = "JoinedLotus";
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

    public static boolean hasJoinedLotus(Player player) {
        return root(player, false).getBoolean(JOINED_LOTUS_KEY);
    }

    public static void setJoinedLotus(Player player, boolean joined) {
        CompoundTag root = root(player, true);
        root.putBoolean(JOINED_LOTUS_KEY, joined);
        player.getPersistentData().put(ROOT_TAG, root);
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
