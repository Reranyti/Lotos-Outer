package com.lotusblight.lib;

/** Shared optimisation and extension entry point for Lotus Blight systems. */
public final class LotusLib {
    public static final int MAX_WORLDGEN_TASKS_PER_TICK = 1;
    public static final int MAX_SPREAD_TASKS_PER_TICK = 8;

    private LotusLib() {
    }

    public static <T> int process(LotusTaskQueue<T> queue, int maximum, java.util.function.Consumer<T> worker) {
        return queue.drain(Math.max(0, maximum), worker);
    }
}
