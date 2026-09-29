package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Paints the Нормальная_ветка entry scene over everything: the sky/screen reddening as the player is
 * drawn to the water, then a deep bordeaux flood with a shake as the world comes apart. Driven entirely
 * by NormalBranchScenePacket - the client just renders the stage and intensity the server sends.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NormalBranchSceneOverlay {
    // Stages, matching NormalBranchScene on the server.
    public static final int OFF = 0;
    public static final int RED = 1;        // sky reddens while the player is pulled to the water
    public static final int DROWN = 2;      // held under, the red deepens
    public static final int LIFT = 3;       // lifted out, the red pulses
    public static final int COLLAPSE = 4;   // bordeaux flood + shake before the move

    private static int stage = OFF;
    private static float intensity;
    private static long lastUpdateMs;

    private NormalBranchSceneOverlay() {}

    public static void set(int newStage, float newIntensity) {
        stage = newStage;
        intensity = newIntensity;
        lastUpdateMs = System.currentTimeMillis();
    }

    public static boolean active() {
        // Fades itself out if packets stop arriving (e.g. the teleport happens), so it can't stick.
        return stage != OFF && System.currentTimeMillis() - lastUpdateMs < 1500;
    }

    @SubscribeEvent
    public static void onRender(RenderGuiOverlayEvent.Post event) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        int alpha = (int) (Math.min(1f, intensity) * 255);
        // A bright blood red early, sinking to a deep bordeaux as it collapses.
        int rgb = stage >= COLLAPSE ? 0x3A0008 : 0xB00010;
        g.fill(0, 0, w, h, (alpha << 24) | rgb);

        // A dark vignette closing in as it deepens.
        if (stage >= DROWN) {
            int vig = (int) (Math.min(1f, intensity) * 200);
            int band = (int) (Math.min(w, h) * 0.28);
            for (int i = 0; i < band; i++) {
                int a = (int) (vig * (1 - i / (double) band));
                int col = (a << 24);
                g.fill(0, i, w, i + 1, col);
                g.fill(0, h - i - 1, w, h - i, col);
                g.fill(i, 0, i + 1, h, col);
                g.fill(w - i - 1, 0, w - i, h, col);
            }
        }
    }

    /** A screen shake offset for the collapse, in scaled pixels (used by the render mixin if wired). */
    public static float shake() {
        if (stage < COLLAPSE || !active()) return 0;
        return (float) (Math.sin(System.currentTimeMillis() * 0.05) * 6 * intensity);
    }
}
