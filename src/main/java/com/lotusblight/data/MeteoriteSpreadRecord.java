package com.lotusblight.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Immutable snapshot of a single meteorite impact spread source (see MeteoriteSpreadEngine) -
 * "метеориты распространяют биом абсолютно на любой блок" - a growing footprint seeded at each
 * StarFall war-ending meteorite impact, mirroring MossyGlandRecord's own shape.
 */
public record MeteoriteSpreadRecord(
        UUID id,
        BlockPos pos,
        long createdGameTime,
        int convertedBlockCount
) {
    public MeteoriteSpreadRecord withConvertedBlockCount(int newCount) {
        return new MeteoriteSpreadRecord(id, pos, createdGameTime, Math.max(0, newCount));
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

    public static MeteoriteSpreadRecord load(CompoundTag tag) {
        return new MeteoriteSpreadRecord(
                tag.getUUID("Id"),
                new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")),
                tag.getLong("CreatedGameTime"),
                tag.getInt("ConvertedBlockCount")
        );
    }

    public static MeteoriteSpreadRecord newSeed(BlockPos pos, long gameTime) {
        return new MeteoriteSpreadRecord(UUID.randomUUID(), pos, gameTime, 0);
    }
}
