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

/** Per-dimension registry of meteorite spread sources - see MeteoriteSpreadRecord for why this isn't OutbreakSavedData/MossyGlandSavedData. */
public class MeteoriteSpreadSavedData extends SavedData {
    public static final String ID = "lotusblight_meteorite_spread";

    private final Map<UUID, MeteoriteSpreadRecord> sources = new LinkedHashMap<>();

    public static MeteoriteSpreadSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(MeteoriteSpreadSavedData::load, MeteoriteSpreadSavedData::new, ID);
    }

    public MeteoriteSpreadSavedData() {}

    public static MeteoriteSpreadSavedData load(CompoundTag tag) {
        MeteoriteSpreadSavedData data = new MeteoriteSpreadSavedData();
        ListTag list = tag.getList("Sources", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            MeteoriteSpreadRecord record = MeteoriteSpreadRecord.load(list.getCompound(i));
            data.sources.put(record.id(), record);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (MeteoriteSpreadRecord record : sources.values()) {
            list.add(record.save(new CompoundTag()));
        }
        tag.put("Sources", list);
        return tag;
    }

    public MeteoriteSpreadRecord registerSource(BlockPos pos, long gameTime) {
        MeteoriteSpreadRecord record = MeteoriteSpreadRecord.newSeed(pos, gameTime);
        sources.put(record.id(), record);
        setDirty();
        return record;
    }

    public void updateSource(MeteoriteSpreadRecord updated) {
        sources.put(updated.id(), updated);
        setDirty();
    }

    public Collection<MeteoriteSpreadRecord> allSources() {
        return sources.values();
    }

    public List<MeteoriteSpreadRecord> asList() {
        return new ArrayList<>(sources.values());
    }
}
