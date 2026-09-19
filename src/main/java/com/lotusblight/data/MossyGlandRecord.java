package com.lotusblight.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Immutable snapshot of a single Mossy Gland (see MossyGlandSpreadEngine) - the World Lotus arc's
 * second infection type. Deliberately its own record/SavedData instead of reusing
 * OutbreakRecord/OutbreakSavedData (see the "separate engine" decision) - it doesn't have phases,
 * a heart, or vine barriers, just a growing footprint of converted vanilla lush-cave blocks.
 */
public record MossyGlandRecord(
        UUID id,
        BlockPos pos,
        long createdGameTime,
        int convertedBlockCount
) {
    public MossyGlandRecord withConvertedBlockCount(int newCount) {
        return new MossyGlandRecord(id, pos, createdGameTime, Math.max(0, newCount));
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putUUID("Id", id);
        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());
        tag.putLong("CreatedGameTime", createdGameTime);
        tag.putInt("ConvertedBlockCount", convertedBlockCount);
        return tag;
    }

    public static MossyGlandRecord load(CompoundTag tag) {
        return new MossyGlandRecord(
                tag.getUUID("Id"),
                new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")),
                tag.getLong("CreatedGameTime"),
                tag.getInt("ConvertedBlockCount")
        );
    }

    public static MossyGlandRecord newSeed(BlockPos pos, long gameTime) {
        return new MossyGlandRecord(UUID.randomUUID(), pos, gameTime, 0);
    }
}
