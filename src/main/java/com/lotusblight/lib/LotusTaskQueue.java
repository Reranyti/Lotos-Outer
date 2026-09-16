package com.lotusblight.lib;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Consumer;

/**
 * Small server-side work queue used to keep expensive world scans out of
 * chunk generation and to process a bounded amount of work per tick.
 */
public final class LotusTaskQueue<T> {
    private final Queue<T> tasks = new ArrayDeque<>();

    public void offer(T task) {
        if (task != null) tasks.offer(task);
    }

    public int size() {
        return tasks.size();
    }

    public int drain(int maximum, Consumer<T> worker) {
        int processed = 0;
        while (processed < maximum) {
            T task = tasks.poll();
            if (task == null) break;
            worker.accept(task);
            processed++;
        }
        return processed;
    }
}
