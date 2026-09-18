package com.lotusblight.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Per-dimension registry of infection outbreaks and their infected-block
 * counts, backed by vanilla's {@link SavedData} persistence.
 *
 * This is the single source of truth for the spread engine, the roots
 * system, the map/minimap/atlas and the boss bar. Nothing in the mod should
 * scan the world to answer "where are the outbreaks" or "how big is this
 * outbreak" — those answers live here and are updated incrementally
 * (O(1) per block conversion) instead of recomputed by scanning a volume.
 */
public class OutbreakSavedData extends SavedData {

    public static final String ID = "lotusblight_outbreaks";

    private final Map<UUID, OutbreakRecord> outbreaks = new LinkedHashMap<>();
    private final Map<Long, Integer> chunkInfectionCounts = new HashMap<>();
    private final Map<UUID, BarrierRecord> barriers = new LinkedHashMap<>();
    /** True once EpicenterManager has placed (or given up trying to place) the guaranteed distant mini-biome epicenter for this dimension. */
    private boolean epicenterResolved = false;
    /**
     * True once ANY outbreak in this dimension has grown a lotus heart. Only one heart is meant
     * to ever exist - a singular, deliberately rare landmark like the End portal - not a reward
     * every sufficiently-grown outbreak gets. Whichever outbreak reaches phase 4 first claims it;
     * every other outbreak just stays at phase 4 without ever growing its own heart block.
     */
    private boolean heartClaimed = false;

    public static OutbreakSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(OutbreakSavedData::load, OutbreakSavedData::new, ID);
    }

    public OutbreakSavedData() {
    }

    public static OutbreakSavedData load(CompoundTag tag) {
        OutbreakSavedData data = new OutbreakSavedData();
        ListTag list = tag.getList("Outbreaks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            OutbreakRecord record = OutbreakRecord.load(list.getCompound(i));
            data.outbreaks.put(record.id(), record);
        }
        CompoundTag counts = tag.getCompound("ChunkCounts");
        for (String key : counts.getAllKeys()) {
            try {
                data.chunkInfectionCounts.put(Long.parseLong(key), counts.getInt(key));
            } catch (NumberFormatException ignored) {
                // Corrupt entry from a future/foreign save format — skip rather than fail the whole load.
            }
        }
        ListTag barrierList = tag.getList("Barriers", Tag.TAG_COMPOUND);
        for (int i = 0; i < barrierList.size(); i++) {
            BarrierRecord barrier = BarrierRecord.load(barrierList.getCompound(i));
            data.barriers.put(barrier.id(), barrier);
        }
        data.epicenterResolved = tag.getBoolean("EpicenterResolved");
        data.heartClaimed = tag.getBoolean("HeartClaimed");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (OutbreakRecord record : outbreaks.values()) {
            list.add(record.save(new CompoundTag()));
        }
        tag.put("Outbreaks", list);

        CompoundTag counts = new CompoundTag();
        for (Map.Entry<Long, Integer> entry : chunkInfectionCounts.entrySet()) {
            counts.putInt(Long.toString(entry.getKey()), entry.getValue());
        }
        tag.put("ChunkCounts", counts);

        ListTag barrierList = new ListTag();
        for (BarrierRecord barrier : barriers.values()) {
            barrierList.add(barrier.save(new CompoundTag()));
        }
        tag.put("Barriers", barrierList);
        tag.putBoolean("EpicenterResolved", epicenterResolved);
        tag.putBoolean("HeartClaimed", heartClaimed);
        return tag;
    }

    public boolean isHeartClaimed() {
        return heartClaimed;
    }

    /** Returns false (and claims nothing) if a heart was already claimed by another outbreak first. */
    public boolean claimHeart() {
        if (heartClaimed) return false;
        heartClaimed = true;
        setDirty();
        return true;
    }

    public boolean isEpicenterResolved() {
        return epicenterResolved;
    }

    public void markEpicenterResolved() {
        epicenterResolved = true;
        setDirty();
    }

    // ---- Vine barriers ------------------------------------------------------

    public BarrierRecord registerBarrier(BarrierRecord barrier) {
        barriers.put(barrier.id(), barrier);
        setDirty();
        return barrier;
    }

    public BarrierRecord getBarrier(UUID id) {
        return barriers.get(id);
    }

    /** Barrier covering the given position, if any — used when a weak-point block is broken. */
    public BarrierRecord barrierAt(BlockPos pos) {
        for (BarrierRecord barrier : barriers.values()) {
            if (barrier.weakPoints().contains(pos) || barrier.solidPositions().contains(pos)) {
                return barrier;
            }
        }
        return null;
    }

    public void removeBarrier(UUID id) {
        if (barriers.remove(id) != null) {
            setDirty();
        }
    }

    public Collection<BarrierRecord> allBarriers() {
        return barriers.values();
    }

    public void markBarrierDirty() {
        setDirty();
    }

    // ---- Outbreak registry -------------------------------------------------

    public OutbreakRecord registerOutbreak(BlockPos pos, long gameTime, boolean hidden) {
        OutbreakRecord record = OutbreakRecord.newAnchor(pos, gameTime, hidden);
        outbreaks.put(record.id(), record);
        setDirty();
        return record;
    }

    public void removeOutbreak(UUID id) {
        if (outbreaks.remove(id) != null) {
            setDirty();
        }
    }

    public OutbreakRecord getOutbreak(UUID id) {
        return outbreaks.get(id);
    }

    public void updateOutbreak(OutbreakRecord updated) {
        outbreaks.put(updated.id(), updated);
        setDirty();
    }

    public Collection<OutbreakRecord> allOutbreaks() {
        return outbreaks.values();
    }

    public List<OutbreakRecord> visibleOutbreaks() {
        return outbreaks.values().stream().filter(o -> !o.hidden()).collect(Collectors.toList());
    }

    /** Reveals every currently known outbreak — the "full visibility modifier" effect. */
    public void revealAll() {
        boolean changed = false;
        for (Map.Entry<UUID, OutbreakRecord> entry : outbreaks.entrySet()) {
            if (entry.getValue().hidden()) {
                entry.setValue(entry.getValue().withHidden(false));
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    public OutbreakRecord nearestOutbreak(BlockPos from, double maxDistance, boolean onlyVisible) {
        OutbreakRecord nearest = null;
        double nearestDistSq = maxDistance * maxDistance;
        for (OutbreakRecord record : outbreaks.values()) {
            if (onlyVisible && record.hidden()) {
                continue;
            }
            double distSq = record.pos().distSqr(from);
            if (distSq <= nearestDistSq) {
                nearest = record;
                nearestDistSq = distSq;
            }
        }
        return nearest;
    }

    public List<OutbreakRecord> outbreaksWithin(BlockPos from, double radius, boolean onlyVisible) {
        double radiusSq = radius * radius;
        List<OutbreakRecord> result = new ArrayList<>();
        for (OutbreakRecord record : outbreaks.values()) {
            if (onlyVisible && record.hidden()) {
                continue;
            }
            if (record.pos().distSqr(from) <= radiusSq) {
                result.add(record);
            }
        }
        return result;
    }

    // ---- Incremental per-chunk infection counters --------------------------

    public void incrementChunkCount(ChunkPos chunkPos, int delta) {
        if (delta == 0) {
            return;
        }
        long key = chunkPos.toLong();
        int updated = chunkInfectionCounts.merge(key, delta, Integer::sum);
        if (updated <= 0) {
            chunkInfectionCounts.remove(key);
        }
        setDirty();
    }

    public int getChunkInfectionCount(ChunkPos chunkPos) {
        return chunkInfectionCounts.getOrDefault(chunkPos.toLong(), 0);
    }

    public int totalInfectedBlocks() {
        return chunkInfectionCounts.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Every chunk that currently has at least one converted block — the raw footprint the map's infection-area overlay is built from. */
    public java.util.Set<ChunkPos> infectedChunks() {
        java.util.Set<ChunkPos> result = new java.util.HashSet<>(chunkInfectionCounts.size());
        for (long key : chunkInfectionCounts.keySet()) {
            result.add(new ChunkPos(key));
        }
        return result;
    }
}
