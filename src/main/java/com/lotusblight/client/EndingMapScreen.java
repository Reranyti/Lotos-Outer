package com.lotusblight.client;

import com.lotusblight.data.EndingsBook;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The way to one ending, on the same TV: a chain of squares from the spawn along the middle of the glass, which can be dragged and
 * scrolled forward. A choice forks: its answers stand in a column, the answer this line took carries the chain on, the others end
 * with an arrow to the line they lead to. A step that is decided but not built yet is dim and dashed.
 */
public final class EndingMapScreen extends Screen {
    private static final int BOX_W = 124;
    private static final int BOX_H = 58;
    private static final int GAP = 28;
    private static final int OPT_H = 34;

    private final Screen parent;
    private final VhsTv tv = new VhsTv(false);
    private final List<EndingPaths.Step> steps;
    private double scroll;
    private int[] columnX;
    private int totalWidth;

    public EndingMapScreen(Screen parent, String endingId, String endingName) {
        super(Component.literal(endingName));
        this.parent = parent;
        this.steps = EndingPaths.of(endingId);
        layoutColumns();
    }

    /** Each step takes a column; a choice takes another one for its answers. */
    private void layoutColumns() {
        columnX = new int[steps.size()];
        int x = 24;
        for (int i = 0; i < steps.size(); i++) {
            columnX[i] = x;
            x += BOX_W + GAP;
            if (!steps.get(i).options().isEmpty()) x += BOX_W + GAP;
        }
        totalWidth = x;
    }

    @Override
    protected void init() {
        tv.layout(this.width, this.height);
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(tv.vcrX() + tv.vcrW() - 120, tv.vcrY() + tv.vcrH() / 2 - 10, 70, 20).build());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private double maxScroll() {
        return Math.max(0, totalWidth - tv.sw + 24);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - dx));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - delta * 50));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        tv.body(g, this.width, this.height);
        tv.beginScreen(g);
        int midY = tv.sy + tv.sh / 2;
        g.drawCenteredString(this.font, this.title, tv.sx + tv.sw / 2, tv.sy + 12, 0xFFFFFF);
        for (int i = 0; i < steps.size(); i++) {
            EndingPaths.Step step = steps.get(i);
            int x = tv.sx + columnX[i] - (int) scroll;
            int y = midY - BOX_H / 2;
            boolean last = i == steps.size() - 1;
            if (x > tv.sx + tv.sw || x + 2 * BOX_W + GAP < tv.sx) {
                continue;
            }
            // the chain's line in, and out
            if (i > 0) {
                int prevEnd = tv.sx + columnX[i - 1] - (int) scroll + BOX_W + (steps.get(i - 1).options().isEmpty() ? 0 : GAP + BOX_W);
                g.fill(prevEnd, midY, x, midY + 1, 0xFF8A8A8A);
            }
            box(g, x, y, step);
            if (!step.options().isEmpty()) forks(g, step, x + BOX_W, midY);
        }
        tv.endScreen(g, this.font);
        super.render(g, mouseX, mouseY, partial);
    }

    private void forks(GuiGraphics g, EndingPaths.Step step, int fromX, int midY) {
        int n = step.options().size();
        int colX = fromX + GAP;
        int top = midY - (n * OPT_H + (n - 1) * 8) / 2;
        for (int k = 0; k < n; k++) {
            EndingPaths.Opt opt = step.options().get(k);
            int oy = top + k * (OPT_H + 8);
            boolean main = k == step.main();
            int edge = main ? 0xFFE6C36A : 0xFF6A6A6A;
            // from the choice to this answer: out, a vertical, in
            int cy = oy + OPT_H / 2;
            g.fill(fromX, midY, fromX + GAP / 2, midY + 1, edge);
            g.fill(fromX + GAP / 2, Math.min(midY, cy), fromX + GAP / 2 + 1, Math.max(midY, cy) + 1, edge);
            g.fill(fromX + GAP / 2, cy, colX, cy + 1, edge);
            g.fill(colX, oy, colX + BOX_W, oy + OPT_H, 0xFF1C1C22);
            frame(g, colX, oy, colX + BOX_W, oy + OPT_H, edge, false);
            int ty = oy + 4;
            for (FormattedCharSequence line : this.font.split(Component.literal(opt.text()), BOX_W - 8)) {
                if (ty > oy + OPT_H - 17) break;
                g.drawString(this.font, line, colX + 4, ty, 0xFF9AD8E8, false);
                ty += 9;
            }
            if (opt.to() != null && !main) {
                String name = lineName(opt.to());
                g.drawString(this.font, "→ " + name, colX + 4, oy + OPT_H - 11, 0xFFB98AE6, false);
            }
            if (main) {
                // the chain carries on through this answer
                int outX = colX + BOX_W;
                g.fill(outX, cy, outX + GAP / 2, cy + 1, edge);
                g.fill(outX + GAP / 2, Math.min(midY, cy), outX + GAP / 2 + 1, Math.max(midY, cy) + 1, edge);
                g.fill(outX + GAP / 2, midY, outX + GAP, midY + 1, edge);
            }
        }
    }

    private static String lineName(String id) {
        for (EndingsBook.Ending e : EndingsBook.ALL) {
            if (e.id().equals(id) && e.name() != null) return e.name();
        }
        return "???";
    }

    private void box(GuiGraphics g, int x, int y, EndingPaths.Step step) {
        int edge = step.planned() ? 0xFF707070 : step.kind().color;
        g.fill(x, y, x + BOX_W, y + BOX_H, step.planned() ? 0xFF1A1A1A : 0xFF2A1818);
        frame(g, x, y, x + BOX_W, y + BOX_H, edge, step.planned());
        g.drawString(this.font, step.kind().label + (step.planned() ? " (план)" : ""), x + 4, y - 11, edge, false);
        int ty = y + 5;
        for (FormattedCharSequence line : this.font.split(Component.literal(step.title()), BOX_W - 8)) {
            if (ty > y + BOX_H - 9) break;
            g.drawString(this.font, line, x + 4, ty, step.planned() ? 0xFF9A9A9A : 0xFFE4E7D8, false);
            ty += 10;
        }
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
