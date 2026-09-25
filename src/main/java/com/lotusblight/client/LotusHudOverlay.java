package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.lotusblight.map.ClientMapCache;
import com.lotusblight.map.MapMarker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Minimap in the top-right corner. Renders purely from {@link ClientMapCache}
 * — the list synced periodically by the server (see
 * {@link com.lotusblight.map.MapSyncManager}) — so there is no per-frame or
 * per-tick block scanning of any kind here, unlike the old implementation.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LotusHudOverlay {
    private static final int SIZE = 92;
    /** World-space radius (blocks) the minimap viewport covers. */
    private static final int VIEW_RADIUS = 72;

    private static final int COLOR_BACKDROP = 0xB0101618;
    private static final int COLOR_PANEL = 0xD01C2825;
    private static final int COLOR_PLAYER = 0xFFFFFFFF;
    private static final int COLOR_HEART = 0xFFFFC1EC;
    private static final int COLOR_HEART_HIDDEN = 0x80FFC1EC;
    private static final int COLOR_OUTBREAK_LOW = 0xFF53C56E;
    private static final int COLOR_OUTBREAK_HIGH = 0xFFFF4F9A;
    private static final int COLOR_OUTBREAK_HIDDEN = 0x80B8C8BE;
    private static final int COLOR_METEORITE = 0xFFB388FF;

    private static boolean hidden;

    public static void toggleHidden() {
        hidden = !hidden;
    }

    public static void setHidden(boolean value) {
        hidden = value;
    }

    public static boolean isHidden() {
        return hidden;
    }

    @SubscribeEvent
    public static void render(RenderGuiOverlayEvent.Post event) {
        // JourneyMap (if installed) replaces this HUD with real waypoints — see
        // com.lotusblight.map.journeymap.LotusJourneyMapPlugin. Avoid drawing both.
        if (net.minecraftforge.fml.ModList.get().isLoaded("journeymap")) return;
        Minecraft mc = Minecraft.getInstance();
        if (hidden || mc.player == null || mc.level == null || mc.screen instanceof ChatScreen) return;

        List<MapMarker> markers = ClientMapCache.markers();
        double px = mc.player.getX();
        double pz = mc.player.getZ();

        GuiGraphics g = event.getGuiGraphics();
        int left = g.guiWidth() - SIZE - 8;
        int top = 8;
        g.fill(left - 2, top - 2, left + SIZE + 2, top + SIZE + 40, COLOR_BACKDROP);
        g.fill(left, top, left + SIZE, top + SIZE, COLOR_PANEL);
        int mapCenterX = left + SIZE / 2;
        int mapCenterY = top + SIZE / 2;
        g.enableScissor(left, top, left + SIZE, top + SIZE);

        int heartCount = 0;
        int outbreakCount = 0;
        double nearestDistSq = Double.MAX_VALUE;
        float scale = (SIZE / 2f) / VIEW_RADIUS;

        for (MapMarker marker : markers) {
            double dx = marker.pos().getX() + 0.5 - px;
            double dz = marker.pos().getZ() + 0.5 - pz;
            if (marker.heartAnchor()) heartCount++; else outbreakCount++;
            double distSq = dx * dx + dz * dz;
            if (distSq < nearestDistSq) nearestDistSq = distSq;

            int mx = mapCenterX + Math.round((float) (dx * scale));
            int mz = mapCenterY + Math.round((float) (dz * scale));
            int color = markerColor(marker);
            int half = marker.heartAnchor() ? 2 : 1;
            g.fill(mx - half, mz - half, mx + half + 1, mz + half + 1, color);
        }

        for (net.minecraft.core.BlockPos pos : ClientMapCache.meteoriteMarkers()) {
            double dx = pos.getX() + 0.5 - px;
            double dz = pos.getZ() + 0.5 - pz;
            int mx = mapCenterX + Math.round((float) (dx * scale));
            int mz = mapCenterY + Math.round((float) (dz * scale));
            g.fill(mx - 1, mz - 1, mx + 2, mz + 2, COLOR_METEORITE);
        }

        g.disableScissor();
        g.fill(mapCenterX - 1, mapCenterY - 1, mapCenterX + 2, mapCenterY + 2, COLOR_PLAYER);

        g.drawString(mc.font, "Лотосы: " + outbreakCount + "  Якоря: " + heartCount, left, top + SIZE + 3, 0xFFFFB3D7, false);
        if (nearestDistSq < Double.MAX_VALUE) {
            g.drawString(mc.font, "Ближайший: " + Math.round(Math.sqrt(nearestDistSq)) + " м", left, top + SIZE + 14, 0xFFFFD66E, false);
        } else {
            g.drawString(mc.font, "Очаги не обнаружены", left, top + SIZE + 14, 0xFF8CFF9F, false);
        }

        // "календарик с 13 майнкрафтовскими днями" - a self-contained world calendar, not tied to
        // any real-world date, purely derived from elapsed game days (see LotusCalendar). Gated on
        // actually owning the crafted calendar item, same "have the tool to see the info" logic as
        // a vanilla clock/compass, rather than being free HUD info from the start.
        if (mc.player.getInventory().contains(new net.minecraft.world.item.ItemStack(com.lotusblight.registry.ModItems.CALENDAR.get()))) {
            var date = com.lotusblight.data.LotusCalendar.dateFor(mc.level.getDayTime() / 24000L);
            String dateLine = date.monthName() + " " + date.dayOfMonth() + ", год " + date.year();
            String weekLine = "Неделя " + date.weekOfMonth() + ", день " + date.dayOfWeek();
            g.drawString(mc.font, dateLine, left, top + SIZE + 25, 0xFFCFE8FF, false);
            g.drawString(mc.font, weekLine, left, top + SIZE + 36, 0xFF9FB8D0, false);
        }
    }

    private static int markerColor(MapMarker marker) {
        if (marker.heartAnchor()) {
            return marker.hidden() ? COLOR_HEART_HIDDEN : COLOR_HEART;
        }
        if (marker.hidden()) {
            return COLOR_OUTBREAK_HIDDEN;
        }
        float t = Math.max(0f, Math.min(1f, (marker.phase() - 1) / 3f));
        return lerpColor(COLOR_OUTBREAK_LOW, COLOR_OUTBREAK_HIGH, t);
    }

    private static int lerpColor(int from, int to, float t) {
        int fa = (from >> 24) & 0xFF, fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int ta = (to >> 24) & 0xFF, tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int a = (int) (fa + (ta - fa) * t);
        int r = (int) (fr + (tr - fr) * t);
        int gg = (int) (fg + (tg - fg) * t);
        int b = (int) (fb + (tb - fb) * t);
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    private LotusHudOverlay() {}
}
