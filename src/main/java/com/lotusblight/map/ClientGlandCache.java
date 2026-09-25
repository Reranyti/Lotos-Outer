package com.lotusblight.map;

import net.minecraft.core.BlockPos;

import java.util.List;

/** Client-side mirror of MossyGlandSavedData - see GlandSyncPacket. */
public final class ClientGlandCache {
    private static volatile List<BlockPos> positions = List.of();

    private ClientGlandCache() {}

    public static void update(List<BlockPos> newPositions) {
        positions = newPositions;
    }

    public static List<BlockPos> positions() {
        return positions;
    }

    public static void clear() {
        positions = List.of();
    }
}
