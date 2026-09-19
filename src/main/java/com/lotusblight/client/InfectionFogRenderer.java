package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.lotusblight.map.ClientMapCache;
import com.lotusblight.map.MapMarker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Thickens and tints the fog toward a sickly green the closer the player is
 * to a mature outbreak, instead of the biome having no felt presence beyond
 * MiniBiomeAtmosphere's particles once you're already standing in one. The
 * effect ramps in gradually starting at phase 2 (not just the mini-biome at
 * phase 4) and by phase 4 should read as genuinely oppressive up close -
 * "заросли, а не декорация" applies to how it feels to walk toward one, not
 * just what it looks like once you're inside it.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class InfectionFogRenderer {

    /** Clean-air fog color to blend away from - vanilla's own default is close to this. */
    private static final float CLEAR_R = 0.75f, CLEAR_G = 0.85f, CLEAR_B = 0.80f;
    private static final float SICK_R = 0.20f, SICK_G = 0.42f, SICK_B = 0.24f;

    private InfectionFogRenderer() {}

    /** How far out (blocks) each phase's fog starts fading in, and how strong it gets at zero distance. */
    private static double rangeForPhase(int phase) {
        return switch (phase) {
            case 4 -> 56.0;
            case 3 -> 40.0;
            default -> 24.0; // phase 2
        };
    }

    private static float intensityCapForPhase(int phase) {
        return switch (phase) {
            case 4 -> 1.0f;
            case 3 -> 0.6f;
            default -> 0.35f;
        };
    }

    /** 0 (clean air) to 1 (thick as this system gets) - the strongest nearby phase 2+ outbreak wins. */
    private static float currentDensity() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return 0f;
        var playerPos = player.blockPosition();

        float best = 0f;
        for (MapMarker marker : ClientMapCache.markers()) {
            if (marker.phase() < 2) continue;
            double range = rangeForPhase(marker.phase());
            double dist = Math.sqrt(marker.pos().distSqr(playerPos));
            if (dist >= range) continue;
            float density = intensityCapForPhase(marker.phase()) * (float) (1.0 - dist / range);
            if (density > best) best = density;
        }
        return Math.min(1f, best);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float density = currentDensity();
        if (density <= 0f) return;
        event.setRed(lerp(event.getRed(), SICK_R, density));
        event.setGreen(lerp(event.getGreen(), SICK_G, density));
        event.setBlue(lerp(event.getBlue(), SICK_B, density));
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        float density = currentDensity();
        if (density <= 0f) return;
        // At full density, the far plane closes down to roughly a third of what it would otherwise
        // be - close enough to feel oppressive without dropping to a disorienting wall of green.
        float farMultiplier = 1.0f - 0.65f * density;
        event.setNearPlaneDistance(event.getNearPlaneDistance() * farMultiplier);
        event.setFarPlaneDistance(event.getFarPlaneDistance() * farMultiplier);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}
