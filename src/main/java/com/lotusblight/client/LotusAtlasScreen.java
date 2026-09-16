package com.lotusblight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class LotusAtlasScreen extends Screen {
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
        graphics.fill(centerX - 4, centerY - 4, centerX + 5, centerY + 5, 0xFFFFFFFF);
        graphics.fill(centerX - 2, centerY - 9, centerX + 3, centerY + 10, 0xFFFFFFFF);
        graphics.fill(centerX - 9, centerY - 2, centerX + 10, centerY + 3, 0xFFFFFFFF);

        graphics.drawString(this.font, "АТЛАС ЛОТОСОВОЙ ПОРЧИ", left + 16, top + 8, 0xFFFFB3D7, false);
        graphics.drawString(this.font, "Карта строится из кэшированных данных; открытие не сканирует мир.", left + 16, bottom - 31, 0xFFB8C8BE, false);
        graphics.drawString(this.font, "Лотосы: " + LotusHudOverlay.getCachedFlowers() + "   Заражено: " + LotusHudOverlay.getCachedInfected() + "   Якоря: " + LotusHudOverlay.getCachedHearts(), left + 16, bottom - 18, 0xFF8CFF9F, false);
        graphics.drawString(this.font, "M — скрыть мини-карту   N — открыть Atlas   ESC — закрыть", left + 16, bottom - 6, 0xFFFFD66E, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
