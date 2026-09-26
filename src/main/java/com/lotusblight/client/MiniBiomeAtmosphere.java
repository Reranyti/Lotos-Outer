package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.lotusblight.map.ClientMapCache;
import com.lotusblight.map.MapMarker;
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
 *
 * Used to be a hard on/off switch at phase 4 only - a player walking toward
 * one got nothing at all until they crossed the phase-4 line, then the full
 * effect all at once. Now every phase 2+ outbreak has its own (smaller,
 * sparser) haze, so approaching one reads as a gradual buildup instead of a
 * binary state.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MiniBiomeAtmosphere {
    private static final DustParticleOptions GREEN_HAZE = new DustParticleOptions(new Vector3f(0.3f, 0.8f, 0.4f), 1.3f);

    private MiniBiomeAtmosphere() {}

    private static double rangeForPhase(int phase) {
        return switch (phase) {
            case 5 -> 56.0;
            case 4 -> 40.0;
            case 3 -> 28.0;
            default -> 16.0; // phase 2
        };
    }

    /** Particle spawn attempts per tick at zero distance from the outbreak. */
    private static int maxAttemptsForPhase(int phase) {
        return phase >= 5 ? 3 : (phase >= 4 ? 2 : 1);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return;

        var playerPos = player.blockPosition();
        int phase = 0;
        double closestFraction = 1.0; // 0 = right on top of the anchor, 1 = at the edge of its range
        for (MapMarker marker : ClientMapCache.markers()) {
            if (marker.phase() < 2) continue;
            double range = rangeForPhase(marker.phase());
            double dist = Math.sqrt(marker.pos().distSqr(playerPos));
            if (dist >= range) continue;
            double fraction = dist / range;
            // Prefer whichever nearby outbreak gives the strongest (highest-phase, closest) haze.
            if (marker.phase() > phase || (marker.phase() == phase && fraction < closestFraction)) {
                phase = marker.phase();
                closestFraction = fraction;
            }
        }
        if (phase == 0) return;

        int maxAttempts = maxAttemptsForPhase(phase);
        // Odds of each attempt actually spawning a particle taper off toward the range's edge -
        // dense right at the anchor, thinning out rather than stopping abruptly.
        int oddsDenominator = phase >= 4 ? 3 : (phase == 3 ? 5 : 8);
        for (int i = 0; i < maxAttempts; i++) {
            if (level.random.nextDouble() > (1.0 - closestFraction) || level.random.nextInt(oddsDenominator) != 0) continue;
            double x = player.getX() + (level.random.nextDouble() - 0.5) * 20;
            double y = player.getY() + level.random.nextDouble() * 5 - 1;
            double z = player.getZ() + (level.random.nextDouble() - 0.5) * 20;
            level.addParticle(GREEN_HAZE, x, y, z, 0.0, 0.008, 0.0);
        }
    }
}
