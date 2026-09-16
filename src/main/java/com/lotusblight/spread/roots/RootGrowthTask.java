package com.lotusblight.spread.roots;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * One queued "grow the next root segment" unit of work for a single
 * outbreak, budgeted per-tick through {@link com.lotusblight.lib.LotusTaskQueue}
 * / {@link com.lotusblight.lib.LotusLib#process}. Carries the dimension
 * rather than a {@code ServerLevel} reference so a task can sit in the queue
 * safely across ticks without pinning a level object.
 */
record RootGrowthTask(UUID outbreakId, ResourceKey<Level> dimension) {
}
