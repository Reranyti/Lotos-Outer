package com.lotusblight.client;

import com.lotusblight.data.EndingsBook;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The endings: a ring of circles, one per ending, each with a line going out from it (the path from the start to the
 * last event and the ending itself). Hovering a circle lights its whole line and names it; clicking opens the map of the way to that ending (EndingMapScreen).
 */
public final class EndingsScreen extends Screen {
    /** How many endings there are. */
    private static final int SLOTS = 13;
    private static final int NODE_RADIUS = 13;
    private static final int RAY_SHORT = 26;

    private final Screen parent;
    private int hovered = -1;

    public EndingsScreen(Screen parent) {
        super(Component.literal("Концовки"));
        this.parent = parent;
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

    private int ringRadius() {
        return Math.min(this.width, this.height) * 28 / 100;
    }

    private int centerY() {
        return this.height / 2 + 4;
    }

    /** Angle of the node: the first one at the top, then clockwise. */
    private static double angle(int i) {
        return -Math.PI / 2 + i * 2 * Math.PI / SLOTS;
    }

    private int nodeX(int i) {
        return this.width / 2 + (int) Math.round(Math.cos(angle(i)) * ringRadius());
    }

    private int nodeY(int i) {
        return centerY() + (int) Math.round(Math.sin(angle(i)) * ringRadius());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < SLOTS; i++) {
            double dx = mx - nodeX(i);
            double dy = my - nodeY(i);
            if (dx * dx + dy * dy <= NODE_RADIUS * NODE_RADIUS) {
                EndingsBook.Ending e = ending(i);
                this.minecraft.setScreen(new EndingMapScreen(this, e == null ? null : e.id(), seen(i) && e.name() != null ? e.name() : "???"));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private EndingsBook.Ending ending(int i) {
        return i < EndingsBook.ALL.size() ? EndingsBook.ALL.get(i) : null;
    }

    private boolean seen(int i) {
        EndingsBook.Ending e = ending(i);
        return e != null && EndingsBook.isSeen(e.id());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        // the title in its frame
        int tw = this.font.width(this.title) + 40;
        int tx = this.width / 2 - tw / 2;
        frame(g, tx, 10, tx + tw, 32, 0xFFB0B0B0);
        g.drawCenteredString(this.font, this.title, this.width / 2, 17, 0xFFFFFF);

        hovered = -1;
        for (int i = 0; i < SLOTS; i++) {
            int dx = mouseX - nodeX(i);
            int dy = mouseY - nodeY(i);
            if (dx * dx + dy * dy <= NODE_RADIUS * NODE_RADIUS) hovered = i;
        }
        for (int i = 0; i < SLOTS; i++) {
            int x = nodeX(i);
            int y = nodeY(i);
            double a = angle(i);
            boolean on = i == hovered;
            int len = on ? ringRadius() * 6 / 10 : RAY_SHORT;
            int color = on ? 0xFFE4E7D8 : seen(i) ? 0xFF907070 : 0xFF505050;
            line(g, x + (int) (Math.cos(a) * NODE_RADIUS), y + (int) (Math.sin(a) * NODE_RADIUS),
                    x + (int) (Math.cos(a) * (NODE_RADIUS + len)), y + (int) (Math.sin(a) * (NODE_RADIUS + len)), color);
            disc(g, x, y, NODE_RADIUS, 0xFF000000 | (seen(i) ? 0x402020 : 0x181818));
            ring(g, x, y, NODE_RADIUS, on ? 0xFFFFFFFF : color);
            EndingsBook.Ending e = ending(i);
            if (e != null && e.icon() != null) {
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                float shade = seen(i) || on ? 1f : 0.45f;
                g.setColor(shade, shade, shade, 1f);
                g.blit(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("lotusblight", "textures/gui/endings/" + e.icon() + ".png"),
                        x - 12, y - 12, 24, 24, 0, 0, 32, 32, 32, 32);
                if (e.icon().equals("alliance") || e.icon().equals("neutral")) {
                    // the player's own face goes into the icon's empty square (see tools/ending_icons.py, FACE)
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g,
                            mc.getSkinManager().getInsecureSkinLocation(mc.getUser().getGameProfile()), x - 12 + 8, y - 12 + 1, 9);
                }
                g.setColor(1f, 1f, 1f, 1f);
            }
        }

        g.drawCenteredString(this.font, Component.literal(EndingsBook.seenCount() + " / " + SLOTS), this.width / 2, centerY() - 4, 0xFFA0A0A0);
        if (hovered >= 0) {
            EndingsBook.Ending e = ending(hovered);
            String name = seen(hovered) && e.name() != null ? e.name() : "???";
            g.drawCenteredString(this.font, Component.literal(name), this.width / 2, centerY() + 10, 0xFFE4E7D8);
        }
        super.render(g, mouseX, mouseY, partial);
    }

    // ---- small drawing helpers (the GUI only knows rectangles)

    private static void frame(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }

    private static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.floor(Math.sqrt(r * r - dy * dy));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int color) {
        int steps = r * 8;
        for (int s = 0; s < steps; s++) {
            double a = s * 2 * Math.PI / steps;
            int x = cx + (int) Math.round(Math.cos(a) * r);
            int y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int s = 0; s <= steps; s++) {
            int x = x0 + (steps == 0 ? 0 : (x1 - x0) * s / steps);
            int y = y0 + (steps == 0 ? 0 : (y1 - y0) * s / steps);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
}
