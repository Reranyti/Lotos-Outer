package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.lotusblight.map.ClientMapCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * "Заросли... воздух станет зелёным" — the mini-biome (phase 4) is supposed
 * to feel like a thick, hazardous thicket, not scattered decoration. Drifting
 * green spore-dust fills the air whenever the player is near a mature
 * outbreak, purely atmospheric (LotusLeavesBlock handles the actual hazard).
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MiniBiomeAtmosphere {
    private static final DustParticleOptions GREEN_HAZE = new DustParticleOptions(new Vector3f(0.3f, 0.8f, 0.4f), 1.3f);
    private static final double RANGE = 40.0;

    private MiniBiomeAtmosphere() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return;

        double rangeSq = RANGE * RANGE;
        boolean inMiniBiome = false;
        var playerPos = player.blockPosition();
        for (var marker : ClientMapCache.markers()) {
            if (marker.phase() >= 4 && marker.pos().distSqr(playerPos) <= rangeSq) {
                inMiniBiome = true;
                break;
            }
        }
        if (!inMiniBiome) return;

        for (int i = 0; i < 2; i++) {
            if (level.random.nextInt(3) != 0) continue;
            double x = player.getX() + (level.random.nextDouble() - 0.5) * 20;
            double y = player.getY() + level.random.nextDouble() * 5 - 1;
            double z = player.getZ() + (level.random.nextDouble() - 0.5) * 20;
            level.addParticle(GREEN_HAZE, x, y, z, 0.0, 0.008, 0.0);
        }
    }
}
