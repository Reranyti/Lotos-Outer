package com.lotusblight.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Immutable snapshot of a single infection outbreak (anchor). Replaces the
 * non-persistent {@code static Map<GlobalPos,Integer>} that used to live in
 * LotusEvents — every field here survives a server restart because it is
 * only ever read from / written to {@link OutbreakSavedData}.
 */
public record OutbreakRecord(
        UUID id,
        BlockPos pos,
        int phase,
        float progress,
        boolean hidden,
        long createdGameTime,
        int infectedBlockCount
) {
    public static final int MIN_PHASE = 1;
    public static final int MAX_PHASE = 4;

    public OutbreakRecord withPhase(int newPhase) {
        return new OutbreakRecord(id, pos, Math.max(MIN_PHASE, Math.min(MAX_PHASE, newPhase)), progress, hidden, createdGameTime, infectedBlockCount);
    }

    public OutbreakRecord withProgress(float newProgress) {
        return new OutbreakRecord(id, pos, phase, Math.max(0f, Math.min(1f, newProgress)), hidden, createdGameTime, infectedBlockCount);
    }

    public OutbreakRecord withHidden(boolean newHidden) {
        return new OutbreakRecord(id, pos, phase, progress, newHidden, createdGameTime, infectedBlockCount);
    }

    public OutbreakRecord withInfectedBlockCount(int newCount) {
        return new OutbreakRecord(id, pos, phase, progress, hidden, createdGameTime, Math.max(0, newCount));
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putUUID("Id", id);
        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());
        tag.putInt("Phase", phase);
        tag.putFloat("Progress", progress);
        tag.putBoolean("Hidden", hidden);
        tag.putLong("CreatedGameTime", createdGameTime);
        tag.putInt("InfectedBlockCount", infectedBlockCount);
        return tag;
    }

    public static OutbreakRecord load(CompoundTag tag) {
        return new OutbreakRecord(
                tag.getUUID("Id"),
                new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")),
                tag.getInt("Phase"),
                tag.getFloat("Progress"),
                tag.getBoolean("Hidden"),
                tag.getLong("CreatedGameTime"),
                tag.getInt("InfectedBlockCount")
        );
    }

    public static OutbreakRecord newAnchor(BlockPos pos, long gameTime, boolean hidden) {
        return new OutbreakRecord(UUID.randomUUID(), pos, MIN_PHASE, 0f, hidden, gameTime, 0);
    }
}
