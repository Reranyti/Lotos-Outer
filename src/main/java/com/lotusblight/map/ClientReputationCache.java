package com.lotusblight.map;

import com.lotusblight.data.Faction;

/** Client-side mirror of the player's faction standings, kept fresh by {@link ReputationSyncPacket}. */
public final class ClientReputationCache {
    private static volatile boolean starFallSeen = false;
    private static volatile int lotus = 0;
    private static volatile int scientists = 0;
    private static volatile int starLight = 0;

    private ClientReputationCache() {
    }

    public static void update(boolean seen, int lotusRep, int scientistsRep, int starLightRep) {
        starFallSeen = seen;
        lotus = lotusRep;
        scientists = scientistsRep;
        starLight = starLightRep;
    }

    public static boolean starFallSeen() {
        return starFallSeen;
    }

    public static int reputation(Faction faction) {
        return switch (faction) {
            case LOTUS -> lotus;
            case SCIENTISTS -> scientists;
            case STAR_LIGHT -> starLight;
        };
    }

    public static void clear() {
        starFallSeen = false;
        lotus = 0;
        scientists = 0;
        starLight = 0;
    }
}
