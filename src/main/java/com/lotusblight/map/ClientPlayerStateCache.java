package com.lotusblight.map;

import com.lotusblight.data.LotusPlayerState;

/**
 * Client-side mirror of {@link LotusPlayerState}, kept fresh by
 * {@link PlayerStateSyncPacket}. Needed because {@code Player#getPersistentData()}
 * is server-authoritative NBT that is never synced to the client on its own —
 * without this cache the dialogue screen has no way to know a branch was
 * already locked in on a previous visit and would keep re-offering the choice.
 */
public final class ClientPlayerStateCache {
    private static volatile int dialogueBranch = LotusPlayerState.BRANCH_UNDECIDED;
    private static volatile boolean fullMapVisibility = false;
    private static volatile boolean heardInnerVoice = false;
    private static volatile boolean hasSeenGuardian = false;
    private static volatile int allianceCleanseUses = 0;

    private ClientPlayerStateCache() {
    }

    public static void update(int branch, boolean fullVisibility, boolean heardVoice, boolean seenGuardian, int cleanseUses) {
        dialogueBranch = branch;
        fullMapVisibility = fullVisibility;
        heardInnerVoice = heardVoice;
        hasSeenGuardian = seenGuardian;
        allianceCleanseUses = cleanseUses;
    }

    public static int dialogueBranch() {
        return dialogueBranch;
    }

    public static boolean fullMapVisibility() {
        return fullMapVisibility;
    }

    public static boolean heardInnerVoice() {
        return heardInnerVoice;
    }

    public static boolean hasSeenGuardian() {
        return hasSeenGuardian;
    }

    public static boolean hasCleansedAsAlly() {
        return allianceCleanseUses > 0;
    }

    public static int allianceCleanseUses() {
        return allianceCleanseUses;
    }

    /** Clears the cache, e.g. on disconnect, so a stale server's state doesn't linger. */
    public static void clear() {
        dialogueBranch = LotusPlayerState.BRANCH_UNDECIDED;
        fullMapVisibility = false;
        heardInnerVoice = false;
        hasSeenGuardian = false;
        allianceCleanseUses = 0;
    }
}
