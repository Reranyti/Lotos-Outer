package com.lotusblight.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * "он не может попасть туда даже если найдёт его до 15 процентов" — the lab is a real, fixed
 * structure a player has to find, not something spawned around them. Built once (lazily, the first
 * time {@link com.lotusblight.escape.LotusChaseEvent} sees no lab recorded for this dimension) and
 * remembered forever after, so it survives restarts at the exact same spot.
 */
public class LotusLabSavedData extends SavedData {
    public static final String ID = "lotusblight_lab";

    private boolean built = false;
    private BlockPos entrance;
    private Direction facing;
    private boolean correctIsLeft;
    private boolean unlocked = false;

    public static LotusLabSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(LotusLabSavedData::load, LotusLabSavedData::new, ID);
    }

    public LotusLabSavedData() {
    }

    public boolean isBuilt() {
        return built;
    }

    public BlockPos entrance() {
        return entrance;
    }

    public Direction facing() {
        return facing;
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public boolean correctIsLeft() {
        return correctIsLeft;
    }

    public void markBuilt(BlockPos entrance, Direction facing, boolean correctIsLeft) {
        this.built = true;
        this.entrance = entrance;
        this.facing = facing;
        this.correctIsLeft = correctIsLeft;
        setDirty();
    }

    public void setUnlocked(boolean unlocked) {
        this.unlocked = unlocked;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Built", built);
        tag.putBoolean("Unlocked", unlocked);
        tag.putBoolean("CorrectIsLeft", correctIsLeft);
        if (entrance != null) {
            tag.putInt("X", entrance.getX());
            tag.putInt("Y", entrance.getY());
            tag.putInt("Z", entrance.getZ());
        }
        if (facing != null) {
            tag.putString("Facing", facing.getSerializedName());
        }
        return tag;
    }

    public static LotusLabSavedData load(CompoundTag tag) {
        LotusLabSavedData data = new LotusLabSavedData();
        data.built = tag.getBoolean("Built");
        data.unlocked = tag.getBoolean("Unlocked");
        data.correctIsLeft = tag.getBoolean("CorrectIsLeft");
        if (data.built && tag.contains("X")) {
            data.entrance = new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
            data.facing = Direction.byName(tag.getString("Facing"));
            if (data.facing == null) data.facing = Direction.NORTH;
        }
        return data;
    }
}
