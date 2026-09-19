package com.lotusblight.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per-dimension registry of Mossy Gland seeds - see MossyGlandRecord for why this isn't OutbreakSavedData. */
public class MossyGlandSavedData extends SavedData {
    public static final String ID = "lotusblight_mossy_glands";

    private final Map<UUID, MossyGlandRecord> glands = new LinkedHashMap<>();

    public static MossyGlandSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(MossyGlandSavedData::load, MossyGlandSavedData::new, ID);
    }

    public MossyGlandSavedData() {}

    public static MossyGlandSavedData load(CompoundTag tag) {
        MossyGlandSavedData data = new MossyGlandSavedData();
        ListTag list = tag.getList("Glands", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            MossyGlandRecord record = MossyGlandRecord.load(list.getCompound(i));
            data.glands.put(record.id(), record);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (MossyGlandRecord record : glands.values()) {
            list.add(record.save(new CompoundTag()));
        }
        tag.put("Glands", list);
        return tag;
    }

    public MossyGlandRecord registerGland(BlockPos pos, long gameTime) {
        MossyGlandRecord record = MossyGlandRecord.newSeed(pos, gameTime);
        glands.put(record.id(), record);
        setDirty();
        return record;
    }

    public void updateGland(MossyGlandRecord updated) {
        glands.put(updated.id(), updated);
        setDirty();
    }

    public Collection<MossyGlandRecord> allGlands() {
        return glands.values();
    }

    public MossyGlandRecord nearestGland(BlockPos from, double maxDistance) {
        MossyGlandRecord nearest = null;
        double nearestDistSq = maxDistance * maxDistance;
        for (MossyGlandRecord record : glands.values()) {
            double distSq = record.pos().distSqr(from);
            if (distSq <= nearestDistSq) {
                nearest = record;
                nearestDistSq = distSq;
            }
        }
        return nearest;
    }

    public List<MossyGlandRecord> asList() {
        return new ArrayList<>(glands.values());
    }
}
