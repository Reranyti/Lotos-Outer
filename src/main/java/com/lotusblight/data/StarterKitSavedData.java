package com.lotusblight.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks which players have already received the starter kit (3 lotus seeds + the wiki), as a
 * world-level SavedData instead of the player entity's own persistent NBT (see LotusEvents -
 * bug #14, the kit re-appearing on relog/version updates on an EXISTING world). SavedData is
 * guaranteed loaded before any player login event can fire, unlike per-entity persistent data,
 * whose load-vs-event-fire ordering isn't something this codebase should have been betting on for
 * a one-time grant.
 */
public class StarterKitSavedData extends SavedData {
    public static final String ID = "lotusblight_starter_kit";

    private final Set<UUID> given = new HashSet<>();

    public static StarterKitSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(StarterKitSavedData::load, StarterKitSavedData::new, ID);
    }

    public StarterKitSavedData() {}

    public static StarterKitSavedData load(CompoundTag tag) {
        StarterKitSavedData data = new StarterKitSavedData();
        ListTag list = tag.getList("Given", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < list.size(); i++) {
            data.given.add(NbtUtils.loadUUID(list.get(i)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (UUID id : given) {
            list.add(NbtUtils.createUUID(id));
        }
        tag.put("Given", list);
        return tag;
    }

    public boolean hasReceived(UUID playerId) {
        return given.contains(playerId);
    }

    public void markReceived(UUID playerId) {
        if (given.add(playerId)) {
            setDirty();
        }
    }
}
