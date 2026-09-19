package com.lotusblight.client;

import com.lotusblight.dialogue.ObjectZeroPages;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Reads one found {@link ObjectZeroPages.Variant} — a page-turning screen for a single
 * discoverable item, deliberately simpler than LotusWikiScreen's two-tab deck (no tab button,
 * no voice-overlay effect) since this is meant to read like an ordinary found paper, not the
 * player's own always-available reference book.
 */
public final class ScientistPageScreen extends Screen {
    private static final int WIDTH = 380;
    private static final int HEIGHT = 260;

    private final ObjectZeroPages.Variant variant;
    private int pageIndex;
    private Button prevButton;
    private Button nextButton;

    public ScientistPageScreen(int variantIndex) {
        super(Component.literal("Страница дневника Объекта Ноль"));
        int index = variantIndex >= 0 && variantIndex < ObjectZeroPages.VARIANTS.size() ? variantIndex : 0;
        this.variant = ObjectZeroPages.VARIANTS.get(index);
    }

    private List<String> pages() {
        return variant.pages();
    }

    @Override
    protected void init() {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;
        prevButton = addRenderableWidget(Button.builder(Component.literal("<"), b -> turnPage(-1))
                .bounds(left + 16, top + HEIGHT - 32, 40, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), b -> turnPage(1))
                .bounds(left + WIDTH - 56, top + HEIGHT - 32, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
                .bounds(left + WIDTH / 2 - 45, top + HEIGHT - 32, 90, 20).build());
        updateButtons();
    }

    private void turnPage(int delta) {
        int size = pages().size();
        pageIndex = Math.max(0, Math.min(size - 1, pageIndex + delta));
        updateButtons();
    }

    private void updateButtons() {
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
        g.fill(left + 10, top + 10, left + WIDTH - 10, top + 34, 0xFF263C2B);
        g.drawString(this.font, variant.title().toUpperCase(java.util.Locale.ROOT), left + 18, top + 18, 0xFFF1A9CF, false);

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
