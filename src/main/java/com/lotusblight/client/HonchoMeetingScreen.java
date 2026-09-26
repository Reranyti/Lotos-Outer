package com.lotusblight.client;

import com.lotusblight.map.HonchoMeetingChoicePacket;
import com.lotusblight.map.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The two-answer window of the trip-meeting (see HonchoMeetingCutscene). No backdrop - Honcho has
 * to stay visible behind it. "Принять руку?" first; a "Нет" there turns it into "Точно?", where "Нет"
 * again means he leaves and "Да" still takes his hand.
 */
public final class HonchoMeetingScreen extends Screen {
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 58;

    private final boolean confirming;

    public HonchoMeetingScreen(boolean confirming) {
        super(Component.literal("Хончо"));
        this.confirming = confirming;
    }

    private int panelTop() {
        return this.height - PANEL_HEIGHT - 40;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = panelTop() + 28;
        int buttonWidth = PANEL_WIDTH / 2 - 15;
        addRenderableWidget(Button.builder(Component.literal("Нет"), b -> answer(false))
                .bounds(left + 10, top, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Да"), b -> answer(true))
                .bounds(left + PANEL_WIDTH / 2 + 5, top, buttonWidth, 20).build());
    }

    private void answer(boolean accept) {
        NetworkHandler.CHANNEL.sendToServer(new HonchoMeetingChoicePacket(accept));
        if (this.minecraft == null) return;
        if (!accept && !confirming) {
            HonchoMeetingCutscene.openSceneScreen(new HonchoMeetingScreen(true));
            return;
        }
        this.minecraft.setScreen(null);
        HonchoMeetingCutscene.finish(accept);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = panelTop();
        g.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xB0100C18);
        g.fill(left, top, left + PANEL_WIDTH, top + 1, 0xFFB388FF);
        g.fill(left, top + PANEL_HEIGHT - 1, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFFB388FF);
        g.drawCenteredString(this.font, confirming ? "Точно?" : "Принять руку?", this.width / 2, top + 10, 0xFFEDEDED);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
