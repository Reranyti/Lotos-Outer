package com.lotusblight.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The way to one ending, like the advancements window: a chain of squares from the spawn, in a big window that can be dragged
 * and scrolled forward. The steps come from {@link EndingPaths}; a choice shows its answers under its square, a step that is
 * decided but not built yet is dim and dashed.
 */
public final class EndingMapScreen extends Screen {
    private static final int BOX_W = 132;
    private static final int BOX_H = 62;
    private static final int GAP = 26;

    private final Screen parent;
    private final List<EndingPaths.Step> steps;
    private double scroll;

    public EndingMapScreen(Screen parent, String endingId, String endingName) {
        super(Component.literal(endingName));
        this.parent = parent;
        this.steps = EndingPaths.of(endingId);
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
        return Math.max(0, steps.size() * (BOX_W + GAP) + 40 - (this.width - 60));
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - dx));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - delta * 40));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        int top = 34;
        int bottom = this.height - 36;
        g.fill(20, top, this.width - 20, bottom, 0xA0101010);
        g.enableScissor(20, top, this.width - 20, bottom);
        int y = top + 30;
        for (int i = 0; i < steps.size(); i++) {
            EndingPaths.Step step = steps.get(i);
            int x = 36 + i * (BOX_W + GAP) - (int) scroll;
            if (x > this.width || x + BOX_W < 0) continue;
            int edge = step.planned() ? 0xFF707070 : step.kind().color;
            if (i > 0) g.fill(x - GAP, y + BOX_H / 2, x, y + BOX_H / 2 + 1, 0xFF707070);
            g.fill(x, y, x + BOX_W, y + BOX_H, step.planned() ? 0xFF1A1A1A : 0xFF2A1818);
            frame(g, x, y, x + BOX_W, y + BOX_H, edge, step.planned());
            g.drawString(this.font, step.kind().label + (step.planned() ? " (план)" : ""), x + 4, y - 11, edge, false);
            int ty = y + 5;
            for (FormattedCharSequence line : this.font.split(Component.literal(step.title()), BOX_W - 8)) {
                if (ty > y + BOX_H - 9) break;
                g.drawString(this.font, line, x + 4, ty, step.planned() ? 0xFF9A9A9A : 0xFFE4E7D8, false);
                ty += 10;
            }
            int oy = y + BOX_H + 8;
            for (String option : step.options()) {
                g.fill(x + BOX_W / 2, oy - 6, x + BOX_W / 2 + 1, oy, 0xFF505050);
                for (FormattedCharSequence line : this.font.split(Component.literal(option), BOX_W - 10)) {
                    g.drawString(this.font, line, x + 6, oy, 0xFF9AD8E8, false);
                    oy += 10;
                }
                oy += 4;
            }
        }
        g.disableScissor();
        super.render(g, mouseX, mouseY, partial);
        EndingsScreen.vhs(g, this.font, this.width, this.height);
    }

    private static void frame(GuiGraphics g, int x0, int y0, int x1, int y1, int color, boolean dashed) {
        if (!dashed) {
            g.fill(x0, y0, x1, y0 + 1, color);
            g.fill(x0, y1 - 1, x1, y1, color);
            g.fill(x0, y0, x0 + 1, y1, color);
            g.fill(x1 - 1, y0, x1, y1, color);
            return;
        }
        for (int x = x0; x < x1; x += 6) {
            int xe = Math.min(x + 3, x1);
            g.fill(x, y0, xe, y0 + 1, color);
            g.fill(x, y1 - 1, xe, y1, color);
        }
        for (int y = y0; y < y1; y += 6) {
            int ye = Math.min(y + 3, y1);
            g.fill(x0, y, x0 + 1, ye, color);
            g.fill(x1 - 1, y, x1, ye, color);
        }
    }
}
