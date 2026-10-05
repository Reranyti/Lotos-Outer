package com.lotusblight.client;

import com.lotusblight.data.EndingsBook;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** The list of endings, opened from the title screen: what was seen is named, the rest stays "???". */
public final class EndingsScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_WIDTH = 220;

    private final Screen parent;

    public EndingsScreen(Screen parent) {
        super(Component.literal("Концовки"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(this.width / 2 - 100, this.height - 32, 200, 20).build());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.literal(EndingsBook.seenCount() + " / " + EndingsBook.ALL.size()), this.width / 2, 34, 0xA0A0A0);
        int left = (this.width - ROW_WIDTH) / 2;
        int y = 56;
        for (EndingsBook.Ending ending : EndingsBook.ALL) {
            boolean seen = EndingsBook.isSeen(ending.id());
            String text = seen && ending.name() != null ? ending.name() : "???";
            g.fill(left, y, left + ROW_WIDTH, y + ROW_HEIGHT - 4, seen ? 0x80402020 : 0x80101010);
            g.drawString(this.font, text, left + 8, y + 6, seen ? 0xFFE4E7D8 : 0xFF707070, false);
            y += ROW_HEIGHT;
        }
        super.render(g, mouseX, mouseY, partial);
    }
}
