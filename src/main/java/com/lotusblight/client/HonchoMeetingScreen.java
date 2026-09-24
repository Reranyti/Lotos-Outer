package com.lotusblight.client;

import com.lotusblight.map.HonchoMeetingChoicePacket;
import com.lotusblight.map.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "весь худ резко пропадает игрок пытается встать, игрок видит чью то руку на его голове и
 * смотрит вверх" - the rare trip-meeting scene (see HonchoMeetingManager). A real Screen rather
 * than an overlay: opening it already stops movement/mouse-look input the same way the vanilla
 * inventory does, and its own opaque background is what makes the HUD disappear
 * underneath it - no separate hideGui hack needed. Pitch is forced upward once on open, and stays
 * there because a Screen intercepts further mouse look while it's up.
 */
public final class HonchoMeetingScreen extends Screen {
    private static final int WIDTH = 320;

    public HonchoMeetingScreen() {
        super(Component.literal("Хончо"));
    }

    public static void show() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.setXRot(-75.0f);
        }
        mc.setScreen(new HonchoMeetingScreen());
    }

    @Override
    protected void init() {
        int left = (this.width - WIDTH) / 2;
        int top = this.height / 2 - 20;

        addRenderableWidget(Button.builder(Component.literal("Встать самому"), b -> answer(false))
                .bounds(left, top + 50, WIDTH / 2 - 5, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Взять руку Хончо"), b -> answer(true))
                .bounds(left + WIDTH / 2 + 5, top + 50, WIDTH / 2 - 5, 20).build());
    }

    private void answer(boolean tookHand) {
        NetworkHandler.CHANNEL.sendToServer(new HonchoMeetingChoicePacket(tookHand));
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xF0000000);

        int left = (this.width - WIDTH) / 2;
        int top = this.height / 2 - 20;
        String line1 = "Ты запинаешься о что-то в темноте и падаешь...";
        String line2 = "Чья-то рука ложится тебе на голову. Ты смотришь вверх.";
        g.drawCenteredString(this.font, line1, left + WIDTH / 2, top, 0xFFE4E7D8);
        g.drawCenteredString(this.font, line2, left + WIDTH / 2, top + 14, 0xFFB388FF);

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
