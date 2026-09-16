package com.lotusblight.lib;

/** Optional integration point for another Forge mod. */
public interface LotusIntegration {
    String modId();

    default boolean enabled() {
        return true;
    }

    default void onServerTick() {
    }
}
