package com.lotusblight.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

/**
 * "один на мир" - Honcho is spawned once per world, not once per player who lives through StarFall.
 * Also remembers which entity is the real one (the meeting scene may have to bring in a fresh copy
 * while the old one sits in an unloaded chunk) and whether he's gone for good after being turned
 * away twice. Always read from the overworld.
 */
public class HonchoSavedData extends SavedData {
    public static final String ID = "lotusblight_honcho";

    private boolean spawned;
    private boolean gone;
    private UUID honchoId;

    public static HonchoSavedData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(HonchoSavedData::load, HonchoSavedData::new, ID);
    }

    public HonchoSavedData() {}

    public static HonchoSavedData load(CompoundTag tag) {
        HonchoSavedData data = new HonchoSavedData();
        data.spawned = tag.getBoolean("Spawned");
        data.gone = tag.getBoolean("Gone");
        if (tag.hasUUID("HonchoId")) data.honchoId = tag.getUUID("HonchoId");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Spawned", spawned);
        tag.putBoolean("Gone", gone);
        if (honchoId != null) tag.putUUID("HonchoId", honchoId);
        return tag;
    }

    public boolean isSpawned() {
        return spawned;
    }

    public void markSpawned() {
        spawned = true;
        setDirty();
    }

    /** Turned away twice in the meeting scene - he left and never comes back in this world. */
    public boolean isGone() {
        return gone;
    }

    public void markGone() {
        gone = true;
        honchoId = null;
        setDirty();
    }

    public UUID honchoId() {
        return honchoId;
    }

    public void setHonchoId(UUID id) {
        honchoId = id;
        spawned = true;
        setDirty();
    }

    /** /lotus honcho reset world - as if he had never appeared: StarFall may spawn him again and a vanished one may come back. */
    public void reset() {
        spawned = false;
        gone = false;
        honchoId = null;
        setDirty();
    }
}
