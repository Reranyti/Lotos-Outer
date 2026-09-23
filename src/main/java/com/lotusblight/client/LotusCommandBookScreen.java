package com.lotusblight.client;

import com.lotusblight.command.LotusCommandCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * "книга всех команд с описанием и их мгновенным запуском при нажатии" - every /lotus admin/testing
 * command (see LotusCommandCatalog) as one clickable row each; clicking sends the command exactly
 * as if typed, so permission checks still happen the normal way server-side. Opened via
 * {@code /lotus book} (see OpenCommandBookPacket).
 */
public final class LotusCommandBookScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int VISIBLE_ROWS = 12;
    private static final int PANEL_WIDTH = 420;

    private int scrollOffset;

    private LotusCommandBookScreen() {
        super(Component.literal("Команды Lotus Blight"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new LotusCommandBookScreen());
    }

    @Override
    protected void init() {
        rebuildButtons();
    }

    private void rebuildButtons() {
        this.clearWidgets();
        List<LotusCommandCatalog.Entry> entries = LotusCommandCatalog.ENTRIES;
        int maxScroll = Math.max(0, entries.size() - VISIBLE_ROWS);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        int left = (this.width - PANEL_WIDTH) / 2 + 10;
        int top = (this.height - VISIBLE_ROWS * ROW_HEIGHT) / 2;

        for (int i = 0; i < VISIBLE_ROWS && scrollOffset + i < entries.size(); i++) {
            LotusCommandCatalog.Entry entry = entries.get(scrollOffset + i);
            int rowTop = top + i * ROW_HEIGHT;
            this.addRenderableWidget(Button.builder(Component.literal(entry.command()), button -> runCommand(entry.command()))
                    .bounds(left, rowTop, PANEL_WIDTH - 20, 20).build());
        }

        if (scrollOffset > 0) {
            this.addRenderableWidget(Button.builder(Component.literal("^"), b -> { scrollOffset -= VISIBLE_ROWS; rebuildButtons(); })
                    .bounds(left + PANEL_WIDTH - 18, top - 24, 18, 18).build());
        }
        if (scrollOffset < maxScroll) {
            this.addRenderableWidget(Button.builder(Component.literal("v"), b -> { scrollOffset += VISIBLE_ROWS; rebuildButtons(); })
                    .bounds(left + PANEL_WIDTH - 18, top + VISIBLE_ROWS * ROW_HEIGHT + 6, 18, 18).build());
        }
        this.addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
                .bounds(left, top + VISIBLE_ROWS * ROW_HEIGHT + 6, 120, 20).build());
    }

    private void runCommand(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        String withoutSlash = command.startsWith("/") ? command.substring(1) : command;
        mc.getConnection().sendCommand(withoutSlash);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollOffset -= (int) Math.signum(delta);
        rebuildButtons();
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - VISIBLE_ROWS * ROW_HEIGHT) / 2 - 40;
        graphics.drawCenteredString(this.font, "Команды Lotus Blight", this.width / 2, top, 0xFFFFD54F);

        List<LotusCommandCatalog.Entry> entries = LotusCommandCatalog.ENTRIES;
        int rowsTop = (this.height - VISIBLE_ROWS * ROW_HEIGHT) / 2;
        for (int i = 0; i < VISIBLE_ROWS && scrollOffset + i < entries.size(); i++) {
            LotusCommandCatalog.Entry entry = entries.get(scrollOffset + i);
            int rowTop = rowsTop + i * ROW_HEIGHT;
            graphics.drawString(this.font, entry.description(), left + PANEL_WIDTH + 10, rowTop + 6, 0xFFAAAAAA, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
