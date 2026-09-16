package com.lotusblight.spread.roots;

import net.minecraft.core.BlockPos;

/**
 * Non-persistent per-outbreak growth cursor for the root chain, mirroring
 * how {@code InfectionSpreadEngine} keeps its frontier in memory rather than
 * in {@code OutbreakSavedData}: if the server restarts the chain simply
 * re-seeds from the outbreak anchor and resumes growing (capped the same as
 * before), which is harmless since roots are a cosmetic/obstruction layer,
 * not the authoritative infection state.
 */
final class RootChainState {
    BlockPos tip;
    int chainLength;
    int rootsGrown;
    boolean exhausted;

    RootChainState(BlockPos anchor) {
        this.tip = anchor;
    }
}
