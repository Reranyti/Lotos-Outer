package com.lotusblight.client;

import com.lotusblight.map.HonchoMeetingChoicePacket;
import com.lotusblight.map.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A window with a single answer, for scenes with Honcho that only wait for the player to go along (give a
 * hand, agree). No backdrop - Honcho has to stay in view behind it.
 */
public final class HonchoPromptScreen extends Screen {
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 58;

    private final String title, button;

    private HonchoPromptScreen(String title, String button) {
        super(Component.literal("Хончо"));
        this.title = title;
        this.button = button;
    }

    /** Opens the window; an empty button text closes whatever prompt is up. */
    public static void show(String title, String button) {
        Minecraft mc = Minecraft.getInstance();
        if (button.isEmpty()) {
            if (mc.screen instanceof HonchoPromptScreen) mc.setScreen(null);
            return;
        }
        mc.setScreen(new HonchoPromptScreen(title, button));
    }

    private int panelTop() {
        return this.height - PANEL_HEIGHT - 40;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        addRenderableWidget(Button.builder(Component.literal(button), b -> {
            NetworkHandler.CHANNEL.sendToServer(new HonchoMeetingChoicePacket(true));
            if (this.minecraft != null) this.minecraft.setScreen(null);
        }).bounds(left + 20, panelTop() + 28, PANEL_WIDTH - 40, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = panelTop();
        g.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xB0100C18);
        g.fill(left, top, left + PANEL_WIDTH, top + 1, 0xFFB388FF);
        g.fill(left, top + PANEL_HEIGHT - 1, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFFB388FF);
        g.drawCenteredString(this.font, title, this.width / 2, top + 10, 0xFFEDEDED);
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
