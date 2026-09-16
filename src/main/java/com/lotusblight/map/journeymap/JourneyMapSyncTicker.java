package com.lotusblight.map.journeymap;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * IClientPlugin has no periodic callback of its own (only initialize()/
 * getModId()), so this drives LotusJourneyMapPlugin.syncWaypoints() from a
 * normal Forge client tick instead. Guarded by ModList.isLoaded("journeymap")
 * before ever touching LotusJourneyMapPlugin, so the class (which imports
 * journeymap.api types directly) is never resolved when JourneyMap isn't
 * actually installed.
 */
public final class JourneyMapSyncTicker {

    private static final int SYNC_INTERVAL_TICKS = 40;
    private int ticks;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!ModList.get().isLoaded("journeymap")) return;
        if (++ticks % SYNC_INTERVAL_TICKS != 0) return;

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            LotusJourneyMapPlugin plugin = LotusJourneyMapPlugin.instance();
            if (plugin != null) {
                plugin.syncWaypoints();
            }
        });
    }
}
