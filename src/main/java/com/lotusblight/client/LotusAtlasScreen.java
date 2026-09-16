package com.lotusblight.client;

import com.lotusblight.map.ClientMapCache;
import com.lotusblight.map.MapMarker;
import com.lotusblight.spread.InfectionPhases;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Full-screen atlas. Draws a marker for every outbreak in
 * {@link ClientMapCache} projected onto a player-centered top-down view.
 * Never touches the world/block data directly — opening this screen does not
 * scan anything, it only reads whatever the server last synced.
 */
public final class LotusAtlasScreen extends Screen {
    /** World-space radius (blocks) the atlas viewport covers — larger than the HUD minimap's. */
    private static final int VIEW_RADIUS = 260;

    private static final int COLOR_HEART = 0xFFFFC1EC;
    private static final int COLOR_HEART_HIDDEN = 0x80FFC1EC;
    private static final int COLOR_OUTBREAK_LOW = 0xFF53C56E;
    private static final int COLOR_OUTBREAK_HIGH = 0xFFFF4F9A;
    private static final int COLOR_OUTBREAK_HIDDEN = 0x80B8C8BE;

    public LotusAtlasScreen() {
        super(Component.translatable("screen.lotusblight.atlas"));
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int left = 34;
        int top = 24;
        int right = this.width - 34;
        int bottom = this.height - 28;
        graphics.fill(left, top, right, bottom, 0xF0182020);
        graphics.fill(left + 8, top + 8, right - 8, bottom - 38, 0xE024302B);

        int mapLeft = left + 18;
        int mapTop = top + 18;
        int mapRight = right - 18;
        int mapBottom = bottom - 52;
        for (int x = mapLeft; x < mapRight; x += 16) graphics.fill(x, mapTop, x + 1, mapBottom, 0x553D5D51);
        for (int y = mapTop; y < mapBottom; y += 16) graphics.fill(mapLeft, y, mapRight, y + 1, 0x553D5D51);

        int centerX = (mapLeft + mapRight) / 2;
        int centerY = (mapTop + mapBottom) / 2;
        float mapHalfWidth = (mapRight - mapLeft) / 2f;
        float mapHalfHeight = (mapBottom - mapTop) / 2f;
        float scale = Math.min(mapHalfWidth, mapHalfHeight) / VIEW_RADIUS;

        List<MapMarker> markers = ClientMapCache.markers();
        double playerX = 0, playerZ = 0;
        int discovered = 0;
        int revealedOnly = 0;
        MapMarker nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        if (this.minecraft != null && this.minecraft.player != null) {
            playerX = this.minecraft.player.getX();
            playerZ = this.minecraft.player.getZ();
        }

        graphics.enableScissor(mapLeft, mapTop, mapRight, mapBottom);
        for (MapMarker marker : markers) {
            double dx = marker.pos().getX() + 0.5 - playerX;
            double dz = marker.pos().getZ() + 0.5 - playerZ;
            double distSq = dx * dx + dz * dz;
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = marker;
            }
            if (marker.hidden()) revealedOnly++; else discovered++;

            int mx = centerX + Math.round((float) (dx * scale));
            int mz = centerY + Math.round((float) (dz * scale));
            int color = markerColor(marker);
            int half = marker.heartAnchor() ? 4 : 3;
            graphics.fill(mx - half, mz - half, mx + half, mz + half, color);
            if (marker.hidden()) {
                graphics.drawCenteredString(this.font, "?", mx, mz - 4, 0xFFFFFFFF);
            }
        }
        graphics.disableScissor();

        // Player marker.
        graphics.fill(centerX - 4, centerY - 4, centerX + 5, centerY + 5, 0xFFFFFFFF);
        graphics.fill(centerX - 2, centerY - 9, centerX + 3, centerY + 10, 0xFFFFFFFF);
        graphics.fill(centerX - 9, centerY - 2, centerX + 10, centerY + 3, 0xFFFFFFFF);

        graphics.drawString(this.font, "АТЛАС ЛОТОСОВОЙ ПОРЧИ", left + 16, top + 8, 0xFFFFB3D7, false);

        // Legend.
        int legendX = mapRight - 118;
        int legendY = mapTop + 6;
        graphics.fill(legendX - 6, legendY - 6, mapRight - 4, legendY + 62, 0xC0101618);
        drawLegendSwatch(graphics, legendX, legendY, COLOR_HEART, "Якорь");
        drawLegendSwatch(graphics, legendX, legendY + 12, COLOR_OUTBREAK_LOW, "Очаг (ранняя фаза)");
        drawLegendSwatch(graphics, legendX, legendY + 24, COLOR_OUTBREAK_HIGH, "Очаг (поздняя фаза)");
        drawLegendSwatch(graphics, legendX, legendY + 36, COLOR_OUTBREAK_HIDDEN, "Не обнаружено");

        graphics.drawString(this.font, "Карта строится из кэшированных данных; открытие не сканирует мир.", left + 16, bottom - 31, 0xFFB8C8BE, false);
        graphics.drawString(this.font, "Обнаружено: " + discovered + "   Раскрыто (не найдено): " + revealedOnly, left + 16, bottom - 18, 0xFF8CFF9F, false);

        String nearestLine;
        if (nearest != null) {
            double distance = Math.sqrt(nearestDistSq);
            nearestLine = "Ближайший: " + InfectionPhases.phaseName(nearest.phase()) + " — " + Math.round(distance) + " м";
        } else {
            nearestLine = "Ближайший: неизвестно";
        }
        graphics.drawString(this.font, nearestLine, left + 16, bottom - 6, 0xFFFFD66E, false);
        graphics.drawString(this.font, "M — скрыть мини-карту   N — открыть Atlas   ESC — закрыть", mapRight - 220, bottom - 6, 0xFFFFD66E, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawLegendSwatch(GuiGraphics graphics, int x, int y, int color, String label) {
        graphics.fill(x, y, x + 7, y + 7, color);
        graphics.drawString(this.font, label, x + 11, y - 1, 0xFFE8F0EA, false);
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
}
