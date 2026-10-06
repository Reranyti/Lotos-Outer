package com.lotusblight.client;

/**
 * Client mirror of the one Нормальная_ветка fact the client needs: whether the main lotus has gone
 * quiet for this player. Set by NormalBranchSilencePacket; read where the lotus dialogue would open and
 * where the shoots would make sound.
 */
public final class NormalBranchClient {
    private static boolean silenced;

    private NormalBranchClient() {}

    public static void setSilenced(boolean value) {
        boolean was = silenced;
        silenced = value;
        // Three drownings in: the player is on the path, so the finale video starts coming in quietly; if the run is broken it goes at once.
        if (value && !was) FinaleVideoStore.prefetch(FinaleVideoStore.gameDir());
        else if (!value && was) FinaleVideoStore.remove(FinaleVideoStore.gameDir());
    }

    public static boolean isSilenced() {
        return silenced;
    }
}
