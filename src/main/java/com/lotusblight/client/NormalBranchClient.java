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
        silenced = value;
    }

    public static boolean isSilenced() {
        return silenced;
    }
}
