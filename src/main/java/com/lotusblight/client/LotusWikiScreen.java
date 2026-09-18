package com.lotusblight.client;

import com.lotusblight.dialogue.LotusWikiLibrary;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Replaces the vanilla BookViewScreen path entirely — LotusWikiItem used to
 * extend WrittenBookItem and rely on Player#openItemGui, but vanilla's own
 * client-side implementation (LocalPlayer#openItemGui) hardcodes a check
 * against `Items.WRITTEN_BOOK` by exact reference, not `instanceof
 * WrittenBookItem`. A custom item subclass never passes that check, so the
 * screen silently never opened — no crash, no error, nothing. This is a
 * fully custom screen instead, with two decks (see LotusWikiLibrary): the
 * practical wiki, and the scientist's lore journal (meant to grow with
 * story-triggered entries later).
 */
public final class LotusWikiScreen extends Screen {
    private static final int WIDTH = 380;
    private static final int HEIGHT = 260;

    private boolean journalTab;
    private int pageIndex;
    private Button prevButton;
    private Button nextButton;
    private Button tabButton;

    public LotusWikiScreen() {
        super(Component.literal("Вики Lotus Blight"));
    }

    private List<String> pages() {
        return journalTab ? LotusWikiLibrary.JOURNAL_PAGES : LotusWikiLibrary.WIKI_PAGES;
    }

    @Override
    protected void init() {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;

        tabButton = addRenderableWidget(Button.builder(Component.literal(""), b -> switchTab())
                .bounds(left + WIDTH - 140, top + 8, 130, 20).build());
        prevButton = addRenderableWidget(Button.builder(Component.literal("<"), b -> turnPage(-1))
                .bounds(left + 16, top + HEIGHT - 32, 40, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), b -> turnPage(1))
                .bounds(left + WIDTH - 56, top + HEIGHT - 32, 40, 20).build());
        updateButtons();
    }

    private void switchTab() {
        journalTab = !journalTab;
        pageIndex = 0;
        updateButtons();
    }

    private void turnPage(int delta) {
        int size = pages().size();
        pageIndex = Math.max(0, Math.min(size - 1, pageIndex + delta));
        updateButtons();
    }

    private void updateButtons() {
        tabButton.setMessage(Component.literal(journalTab ? "-> Вики" : "-> Дневник"));
        prevButton.active = pageIndex > 0;
        nextButton.active = pageIndex < pages().size() - 1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;

        g.fill(left - 3, top - 3, left + WIDTH + 3, top + HEIGHT + 3, 0xFF17251B);
        g.fill(left, top, left + WIDTH, top + HEIGHT, 0xF20B100D);
        g.fill(left + 10, top + 10, left + WIDTH - 150, top + 34, 0xFF263C2B);

        String heading = journalTab ? "ДНЕВНИК" : "ВИКИ LOTUS BLIGHT";
        g.drawString(this.font, heading, left + 18, top + 18, 0xFFF1A9CF, false);

        List<String> pages = pages();
        String pageText = pages.isEmpty() ? "" : pages.get(pageIndex);
        var lines = this.font.split(Component.literal(pageText), WIDTH - 36);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(this.font, lines.get(i), left + 18, top + 46 + i * 11, 0xFFE4E7D8, false);
        }

        String counter = (pageIndex + 1) + " / " + pages.size();
        g.drawString(this.font, counter, left + WIDTH / 2 - this.font.width(counter) / 2, top + HEIGHT - 26, 0xFF9AD47D, false);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
