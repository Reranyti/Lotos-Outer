package com.lotusblight.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The way to one ending, like the advancements window: a chain of squares from the spawn, in a big window that can be
 * dragged and scrolled forward. What the player has not opened yet is drawn as "???".
 *
 * The steps themselves (dialogue lines, events) come from the story and are not written yet: until then the map holds
 * the spawn and one unknown step after it.
 */
public final class EndingMapScreen extends Screen {
    private static final int BOX = 26;
    private static final int GAP = 22;

    /** One square of the chain; a null label is an unopened step. */
    private record Step(String label) {}

    private final Screen parent;
    private final String endingName;
    private final List<Step> steps;
    private double scroll;

    public EndingMapScreen(Screen parent, String endingId, String endingName) {
        super(Component.literal(endingName));
        this.parent = parent;
        this.endingName = endingName;
        this.steps = List.of(new Step("Спавн"), new Step(null));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private double maxScroll() {
        return Math.max(0, steps.size() * (BOX + GAP) - (this.width - 80));
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - dx));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - delta * 24));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        int top = 40;
        int bottom = this.height - 40;
        g.fill(30, top, this.width - 30, bottom, 0xA0101010);
        g.enableScissor(30, top, this.width - 30, bottom);
        int y = (top + bottom) / 2 - BOX / 2;
        for (int i = 0; i < steps.size(); i++) {
            int x = 50 + i * (BOX + GAP) - (int) scroll;
            if (i > 0) g.fill(x - GAP, y + BOX / 2, x, y + BOX / 2 + 1, 0xFF707070);
            boolean known = steps.get(i).label() != null;
            g.fill(x, y, x + BOX, y + BOX, known ? 0xFF402020 : 0xFF202020);
            g.fill(x, y, x + BOX, y + 1, 0xFFB0B0B0);
            g.fill(x, y + BOX - 1, x + BOX, y + BOX, 0xFFB0B0B0);
            g.fill(x, y, x + 1, y + BOX, 0xFFB0B0B0);
            g.fill(x + BOX - 1, y, x + BOX, y + BOX, 0xFFB0B0B0);
            String text = known ? steps.get(i).label() : "???";
            g.drawCenteredString(this.font, text, x + BOX / 2, y + BOX + 6, known ? 0xFFE4E7D8 : 0xFF707070);
        }
        g.disableScissor();
        super.render(g, mouseX, mouseY, partial);
        EndingsScreen.vhs(g, this.font, this.width, this.height);
    }
}
