package com.lotusblight.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Whether this world has been switched to the Chromo difficulty. There is deliberately no way back:
 * nothing in the mod ever clears the flag, and there is no command for it.
 */
public class LotusChromoData extends SavedData {
    public static final String ID = "lotusblight_chromo";

    private boolean enabled;

    public static LotusChromoData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(LotusChromoData::load, LotusChromoData::new, ID);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void enable() {
        if (enabled) return;
        enabled = true;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Enabled", enabled);
        return tag;
    }

    public static LotusChromoData load(CompoundTag tag) {
        LotusChromoData data = new LotusChromoData();
        data.enabled = tag.getBoolean("Enabled");
        return data;
    }
}
