package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * "Побег от лотоса" HUD — a plain countdown clock, centered near the top of the screen. Purely
 * cosmetic/informational: {@link com.lotusblight.escape.LotusChaseEvent} is the actual authority on
 * whether the player is caught, this just displays the ticking clock it told the client to start.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LotusChaseOverlay {
    private static boolean active = false;
    private static int totalDurationTicks;
    private static int ticksLeft;

    private LotusChaseOverlay() {}

    public static void start(int durationTicks) {
        active = true;
        totalDurationTicks = durationTicks;
        ticksLeft = durationTicks;
    }

    public static void stop() {
        active = false;
    }

    @SubscribeEvent
    public static void onRender(RenderGuiOverlayEvent.Post event) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (mc.level != null && !mc.isPaused()) {
            ticksLeft = Math.max(0, ticksLeft - 1);
        }

        GuiGraphics g = event.getGuiGraphics();
        int seconds = ticksLeft / 20;
        String clock = String.format("%d:%02d", seconds / 60, seconds % 60);
        float remainingFrac = totalDurationTicks > 0 ? ticksLeft / (float) totalDurationTicks : 0f;
        // Reads as increasingly urgent the closer the clock gets to zero, not just a flat color.
        int color = lerpColor(0xFFFF5555, 0xFFEDEDED, remainingFrac);

        int x = g.guiWidth() / 2;
        int y = 10;
        g.drawCenteredString(mc.font, Component.literal(clock), x, y, color);
        String label = "ПОБЕГ ОТ ЛОТОСА";
        g.drawCenteredString(mc.font, Component.literal(label), x, y + 12, 0x90FFFFFF);
    }

    private static int lerpColor(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int fa = (from >> 24) & 0xFF, fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int ta = (to >> 24) & 0xFF, tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int a = (int) (fa + (ta - fa) * t);
        int r = (int) (fr + (tr - fr) * t);
        int gg = (int) (fg + (tg - fg) * t);
        int b = (int) (fb + (tb - fb) * t);
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }
}
