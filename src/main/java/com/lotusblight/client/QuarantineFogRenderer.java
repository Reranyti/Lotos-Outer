package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * "шейдер неба там кровавокрасный за границей" - the sky/horizon (there's no separate sky-color
 * hook in Forge for the Overworld's plain fog-dominated sky, so fog color is what actually reads
 * as "the sky" at ground level, same approach InfectionFogRenderer already uses for outbreaks)
 * blends toward a deep blood red as the player approaches the quarantine wall, independent of and
 * stacking with the ordinary infection fog.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QuarantineFogRenderer {
    private static final float BLOOD_R = 0.55f, BLOOD_G = 0.02f, BLOOD_B = 0.03f;
    /** Starts blending in this far from the edge, reaching full blood red right at the wall. */
    private static final double FADE_DISTANCE = 400.0;

    private QuarantineFogRenderer() {}

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.level() == null) return;

        WorldBorder border = player.level().getWorldBorder();
        double x = player.getX();
        double z = player.getZ();
        double distance = Math.min(
                Math.min(x - border.getMinX(), border.getMaxX() - x),
                Math.min(z - border.getMinZ(), border.getMaxZ() - z));
        if (distance >= FADE_DISTANCE) return;

        float density = (float) Math.max(0.0, Math.min(1.0, 1.0 - distance / FADE_DISTANCE));
        event.setRed(lerp(event.getRed(), BLOOD_R, density));
        event.setGreen(lerp(event.getGreen(), BLOOD_G, density));
        event.setBlue(lerp(event.getBlue(), BLOOD_B, density));
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}
