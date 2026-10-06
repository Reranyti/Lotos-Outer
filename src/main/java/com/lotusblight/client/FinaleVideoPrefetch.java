package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.lotusblight.branch.NormalBranchLock;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Starts fetching the finale video when the game starts, so it is on a roomy drive long before the fight asks for it. */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FinaleVideoPrefetch {
    private FinaleVideoPrefetch() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (NormalBranchLock.isLocked()) return;
        FinaleVideoStore.prefetch(Minecraft.getInstance().gameDirectory);
    }
}
