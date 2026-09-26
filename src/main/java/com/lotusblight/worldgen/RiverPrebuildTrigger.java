package com.lotusblight.worldgen;

import com.lotusblight.LotusBlight;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Once per world, while it opens: builds Streams Reflowing's river regions for the area around spawn
 * ahead of time (see compat.StreamsRiverPrebuild), so walking out into new land reads finished rivers
 * from disk instead of computing them live. Opening a world for the first time takes longer; after
 * that nothing runs. Worlds from before this get it once, on their next load. Without Streams
 * Reflowing installed this does nothing.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RiverPrebuildTrigger {
    /**
     * Half of a 3000x3000 block square around spawn. 1000x1000 turned out to hold only regions the
     * spawn-area preparation builds anyway - the hitches came from the ring right past it.
     */
    private static final double PREBUILD_RADIUS = 1500.0;

    private RiverPrebuildTrigger() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!ModList.get().isLoaded("streamsreflowing")) return;
        ServerLevel overworld = event.getServer().overworld();
        Done done = Done.get(overworld);
        if (done.value) return;
        com.lotusblight.worldgen.compat.StreamsRiverPrebuild.prebuild(overworld, overworld.getSharedSpawnPos(), PREBUILD_RADIUS);
        done.value = true;
        done.setDirty();
    }

    /** Per-world "already done" flag, kept on the overworld. */
    private static final class Done extends SavedData {
        private static final String ID = "lotusblight_river_prebuild";
        private boolean value;

        static Done get(ServerLevel overworld) {
            return overworld.getDataStorage().computeIfAbsent(Done::load, Done::new, ID);
        }

        static Done load(CompoundTag tag) {
            Done done = new Done();
            done.value = tag.getBoolean("Done");
            return done;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean("Done", value);
            return tag;
        }
    }
}
