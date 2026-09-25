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

    /** Silently drops the task instead of queuing it once {@code maxSize} pending tasks are already waiting. */
    public void offer(T task, int maxSize) {
        tryOffer(task, maxSize);
    }

    /** Same as {@link #offer(Object, int)}, but reports whether the task was actually queued. */
    public boolean tryOffer(T task, int maxSize) {
        if (task == null || tasks.size() >= maxSize) return false;
        tasks.offer(task);
        return true;
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
