package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The horn's bars, over the hotbar while it is in the hand: which of its three uses the slot it is held in gives, and
 * what is left of it - the blocks it can still cleanse today, or how long until Honcho's call or the blow is ready.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HornHud {
    private static int usesLeft = LotusPlayerState.HORN_DAILY_USES;
    private static int honchoTicks, blowTicks;
    private static long receivedAt = System.nanoTime();

    private HornHud() {}

    public static void update(int uses, int honcho, int blow) {
        usesLeft = uses;
        honchoTicks = honcho;
        blowTicks = blow;
        receivedAt = System.nanoTime();
    }

    @SubscribeEvent
    public static void render(RenderGuiOverlayEvent.Post event) {
        if (!LotusClientHooks.isOverlayPass(event)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !mc.player.getMainHandItem().is(ModItems.HORN.get())) return;
        int slot = mc.player.getInventory().selected;
        double passed = (System.nanoTime() - receivedAt) / 50_000_000.0;      // ticks since the numbers arrived
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        int bw = 120, bh = 6, x = (w - bw) / 2, y = h - 59 - 26;
        String title;
        double fill;
        String detail;
        int color;
        if (slot == 0) {
            title = "Рог: позвать Хончо";
            double left = Math.max(0, honchoTicks - passed);
            fill = 1 - left / 600.0;
            detail = left <= 0 ? "готов" : ((int) Math.ceil(left / 20.0)) + " с";
            color = 0xFFE8C66A;
        } else if (slot == 1) {
            title = "Рог: очищение";
            fill = usesLeft / (double) LotusPlayerState.HORN_DAILY_USES;
            detail = usesLeft + " / " + LotusPlayerState.HORN_DAILY_USES;
            color = 0xFF53C56E;
        } else if (slot == 2) {
            title = "Рог: протрубить";
            double left = Math.max(0, blowTicks - passed);
            fill = 1 - left / 48000.0;
            detail = left <= 0 ? "готов" : String.format("%.1f дня", left / 24000.0);
            color = 0xFFFFD34E;
        } else {
            g.drawCenteredString(mc.font, "Рог: положи в ячейку 1, 2 или 3 и нажми Shift", w / 2, y, 0xFFB8B8C8);
            return;
        }
        g.drawCenteredString(mc.font, title + "  " + detail, w / 2, y - 11, 0xFFFFFFFF);
        g.fill(x - 1, y - 1, x + bw + 1, y + bh + 1, 0xC0101618);
        g.fill(x, y, x + (int) (bw * Math.max(0, Math.min(1, fill))), y + bh, color);
    }
}
