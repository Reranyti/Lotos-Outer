package com.lotusblight.worldgen;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * "генерация всех биомов должна быть в пределах 1 млн на 1 млн блоков для сюжетности" - Minecraft
 * has no way to constrain WHICH biomes generate where beyond the world border itself, so this caps
 * the overworld's explorable area there instead: a 1,000,000-block world border centered on spawn,
 * set once (see {@link BorderSetupSavedData}) so an admin who deliberately resizes it later isn't
 * fought back to 1,000,000 on every restart.
 */
public final class WorldBorderSetup {
    public static final double STORY_BORDER_SIZE = 1_000_000.0;

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        BorderSetupSavedData data = BorderSetupSavedData.get(overworld);
        if (data.isConfigured()) return;

        var spawn = overworld.getSharedSpawnPos();
        var border = overworld.getWorldBorder();
        border.setCenter(spawn.getX(), spawn.getZ());
        border.setSize(STORY_BORDER_SIZE);
        // Vanilla's own border damage-per-second/warning visuals are replaced entirely by
        // QuarantineBarrier's bounce mechanic - zeroed out so they never fire alongside it.
        border.setDamagePerBlock(0);
        border.setDamageSafeZone(Double.MAX_VALUE);
        border.setWarningBlocks(0);
        border.setWarningTime(0);
        data.setConfigured(true);
    }

    public static class BorderSetupSavedData extends SavedData {
        private static final String ID = "lotusblight_border_setup";
        private boolean configured;

        public static BorderSetupSavedData get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(BorderSetupSavedData::load, BorderSetupSavedData::new, ID);
        }

        public static BorderSetupSavedData load(net.minecraft.nbt.CompoundTag tag) {
            BorderSetupSavedData data = new BorderSetupSavedData();
            data.configured = tag.getBoolean("Configured");
            return data;
        }

        @Override
        public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag) {
            tag.putBoolean("Configured", configured);
            return tag;
        }

        public boolean isConfigured() {
            return configured;
        }

        public void setConfigured(boolean value) {
            configured = value;
            setDirty();
        }
    }
}
