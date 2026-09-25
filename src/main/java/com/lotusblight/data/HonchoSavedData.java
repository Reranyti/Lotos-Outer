package com.lotusblight.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** "один на мир" - Honcho is spawned once per world, not once per player who lives through StarFall. Always read from the overworld. */
public class HonchoSavedData extends SavedData {
    public static final String ID = "lotusblight_honcho";

    private boolean spawned;

    public static HonchoSavedData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(HonchoSavedData::load, HonchoSavedData::new, ID);
    }

    public HonchoSavedData() {}

    public static HonchoSavedData load(CompoundTag tag) {
        HonchoSavedData data = new HonchoSavedData();
        data.spawned = tag.getBoolean("Spawned");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Spawned", spawned);
        return tag;
    }

    public boolean isSpawned() {
        return spawned;
    }

    public void markSpawned() {
        spawned = true;
        setDirty();
    }
}
