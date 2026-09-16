package com.lotusblight.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A vine barrier grown by a phase-4 outbreak: a wall of unbreakable
 * {@code LianaBarrierBlock} with a handful of breakable weak points
 * ({@code LianaWeakPointBlock}). Cutting every weak point clears the whole
 * structure — this is the "разрезать лианы в нескольких точках" mechanic
 * from the World Lotus reference, not a single destructible core.
 */
public final class BarrierRecord {

    private final UUID id;
    private final UUID outbreakId;
    private final List<BlockPos> solidPositions;
    private final List<BlockPos> weakPoints;
    private final Set<BlockPos> brokenWeakPoints;

    public BarrierRecord(UUID id, UUID outbreakId, List<BlockPos> solidPositions, List<BlockPos> weakPoints, Set<BlockPos> brokenWeakPoints) {
        this.id = id;
        this.outbreakId = outbreakId;
        this.solidPositions = solidPositions;
        this.weakPoints = weakPoints;
        this.brokenWeakPoints = brokenWeakPoints;
    }

    public static BarrierRecord create(UUID outbreakId, List<BlockPos> solidPositions, List<BlockPos> weakPoints) {
        return new BarrierRecord(UUID.randomUUID(), outbreakId, new ArrayList<>(solidPositions), new ArrayList<>(weakPoints), new LinkedHashSet<>());
    }

    public UUID id() {
        return id;
    }

    public UUID outbreakId() {
        return outbreakId;
    }

    public List<BlockPos> solidPositions() {
        return solidPositions;
    }

    public List<BlockPos> weakPoints() {
        return weakPoints;
    }

    public boolean breakWeakPoint(BlockPos pos) {
        return brokenWeakPoints.add(pos.immutable());
    }

    public boolean isFullyBroken() {
        return brokenWeakPoints.size() >= weakPoints.size();
    }

    public int remainingWeakPoints() {
        return Math.max(0, weakPoints.size() - brokenWeakPoints.size());
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putUUID("Id", id);
        tag.putUUID("OutbreakId", outbreakId);
        tag.put("Solid", posListTag(solidPositions));
        tag.put("WeakPoints", posListTag(weakPoints));
        tag.put("BrokenWeakPoints", posListTag(new ArrayList<>(brokenWeakPoints)));
        return tag;
    }

    public static BarrierRecord load(CompoundTag tag) {
        List<BlockPos> solid = readPosList(tag.getList("Solid", Tag.TAG_COMPOUND));
        List<BlockPos> weak = readPosList(tag.getList("WeakPoints", Tag.TAG_COMPOUND));
        Set<BlockPos> broken = new LinkedHashSet<>(readPosList(tag.getList("BrokenWeakPoints", Tag.TAG_COMPOUND)));
        return new BarrierRecord(tag.getUUID("Id"), tag.getUUID("OutbreakId"), solid, weak, broken);
    }

    private static ListTag posListTag(List<BlockPos> positions) {
        ListTag list = new ListTag();
        for (BlockPos pos : positions) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("X", pos.getX());
            entry.putInt("Y", pos.getY());
            entry.putInt("Z", pos.getZ());
            list.add(entry);
        }
        return list;
    }

    private static List<BlockPos> readPosList(ListTag list) {
        List<BlockPos> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            result.add(new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z")));
        }
        return result;
    }
}
