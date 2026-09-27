package com.lotusblight.overlay;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * The "loops" scene: the Glitcher walks in profile, shown as a full row of himself that fills the width
 * edge to edge, each copy one walk-frame behind the next so the legs criss-cross in a wave. The copies
 * appear one at a time on the beat until the row is full, then it repeats. Purple letterbox bars sit
 * along the top and bottom, over a dark purple spiral backdrop.
 *
 * The walk cycle is rendered once at start into a strip of sprites; each frame just blits those, so it
 * stays smooth however wide the row.
 */
final class TrailScene {
    private static final int POSES = 4;              // look back, arms crossed, marionette, sitting
    private static final double BEAT_SEC = 0.60;     // 100 BPM - a frozen copy drops on each beat
    private static final double SWEEP_SEC = 2.2;     // the panel's trip across the screen
    private static final double SPACING = 0.34;      // column gap as a fraction of sprite width

    private final BufferedImage[] sprites = new BufferedImage[POSES];
    private final double[] lift = new double[POSES]; // how far each pose sits off the floor
    private final double scale;
    private final int spriteW;
    private final int spriteH;
    private final double originX;
    private final double originY;
    private BufferedImage spiral;

    TrailScene(int[] skin, int height) {
        // Sprites rendered at half scale and blitted 2x (pixelated anyway) to save memory.
        this.scale = height * 0.52 / 32.0;
        double rs = scale / 2;
        SkinModel model = new SkinModel();
        SoftRenderer r = new SoftRenderer((int) (26 * rs), (int) (42 * rs));
        this.spriteW = r.width * 2;
        this.spriteH = r.height * 2;
        this.originX = r.width;
        this.originY = (r.height - 2 * rs) * 2;
        for (int k = 0; k < POSES; k++) {
            lift[k] = k == 3 ? scale * 6 : 0;    // the sitting pose rests lower
            r.clear();
            r.draw(model, skin, pose(k), rs, r.width / 2.0, r.height - 2 * rs);
            sprites[k] = new BufferedImage(spriteW, spriteH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = sprites[k].createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(r.image, 0, 0, spriteW, spriteH, null);
            g.dispose();
        }
    }

    /** Draws the scene at time seconds into the canvas w x h. sceneStart is when the beat count begins. */
    void render(Graphics2D g, int w, int h, double time, double sceneStart) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        drawBackdrop(g, w, h);

        int barH = (int) (h * 0.07);
        double dx = spriteW * SPACING;
        // A row that fills the whole width, centred.
        int slots = (int) Math.ceil(w / dx) + 2;
        double total = (slots - 1) * dx;
        double startX = w / 2.0 - total / 2.0;
        double floorY = h - barH - h * 0.02;

        // Frozen copies drop in one per beat until the row is full. Each one keeps the walk phase it was
        // dropped at, so the row is a still trail whose legs criss-cross - it does not march.
        double since = Math.max(0, time - sceneStart);
        int shown = Math.min(slots, (int) (since / BEAT_SEC) + 1);

        for (int i = 0; i < shown; i++) {
            double x = startX + i * dx;
            int k = i % POSES;
            g.drawImage(sprites[k], (int) (x - originX), (int) (floorY + lift[k] - originY), null);
        }

        // Once the row is full, a purple band sweeps across with the one live figure inside it, which
        // plays through the four poses.
        double rowFull = sceneStart + slots * BEAT_SEC;
        if (time > rowFull) {
            double p = ((time - rowFull) / SWEEP_SEC) % 1.0;
            int k = ((int) (time / BEAT_SEC)) % POSES;
            drawPanel(g, w, h, k, floorY, p);
        }

        g.setColor(new Color(0x7A24FF));
        g.fillRect(0, 0, w, barH);
        g.fillRect(0, h - barH, w, barH);
    }

    /** The purple vertical band that slides across the screen with the one live figure inside it. */
    private void drawPanel(Graphics2D g, int w, int h, int k, double floorY, double p) {
        double bandW = w * 0.30;
        double cx = -bandW / 2 + (w + bandW) * p;    // sweeps left to right
        Graphics2D b = (Graphics2D) g.create();
        b.setPaint(new java.awt.GradientPaint((float) (cx - bandW / 2), 0, new Color(0x2A0B4A),
                (float) (cx + bandW / 2), 0, new Color(0x5A1B8C)));
        b.fillRect((int) (cx - bandW / 2), 0, (int) bandW, h);
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        if (k == 2) drawStrings(b, cx, floorY);
        b.drawImage(sprites[k], (int) (cx - originX), (int) (floorY + lift[k] - originY), null);
        b.dispose();
    }

    /** Puppet strings up to the top for the marionette pose. */
    private void drawStrings(Graphics2D g, double cx, double floorY) {
        g.setColor(new Color(0xC0, 0xA0, 0xFF, 120));
        double topY = floorY - spriteH;
        for (double off : new double[]{-spriteW * 0.32, -spriteW * 0.12, spriteW * 0.12, spriteW * 0.32}) {
            g.drawLine((int) (cx + off * 0.6), 0, (int) (cx + off), (int) (topY + spriteH * 0.35));
        }
    }

    /** One of the four held poses: look back, arms crossed, marionette, sitting cross-legged. */
    private static SoftRenderer.Pose pose(int which) {
        SoftRenderer.Pose p = new SoftRenderer.Pose();
        int ra = SkinModel.Part.RIGHT_ARM.ordinal(), la = SkinModel.Part.LEFT_ARM.ordinal();
        int rl = SkinModel.Part.RIGHT_LEG.ordinal(), ll = SkinModel.Part.LEFT_LEG.ordinal();
        int hd = SkinModel.Part.HEAD.ordinal();
        switch (which) {
            case 0 -> {                              // looking back over the shoulder
                p.yaw = -0.4;
                p.partYaw[hd] = 2.4;
                p.partPitch[hd] = 0.1;
                p.partRoll[ra] = -0.15;
                p.partRoll[la] = 0.15;
            }
            case 1 -> {                              // arms crossed, head slightly down
                p.yaw = 0.15;
                p.partPitch[ra] = -1.35;
                p.partPitch[la] = -1.35;
                p.partYaw[ra] = 0.9;                 // swing each forearm across the chest
                p.partYaw[la] = -0.9;
                p.partPitch[hd] = 0.35;
            }
            case 2 -> {                              // marionette, hung by the limbs
                p.yaw = 0.1;
                p.partPitch[ra] = -2.6;
                p.partPitch[la] = -2.6;
                p.partRoll[ra] = -0.7;               // arms up and out
                p.partRoll[la] = 0.7;
                p.partRoll[rl] = -0.5;               // legs splayed out
                p.partRoll[ll] = 0.5;
                p.partPitch[hd] = -0.4;              // head lolling back
            }
            default -> {                             // sitting cross-legged
                p.yaw = 0.1;
                p.partPitch[rl] = 1.6;               // knees up and folded in
                p.partPitch[ll] = 1.6;
                p.partRoll[rl] = 0.7;
                p.partRoll[ll] = -0.7;
                p.partPitch[ra] = -0.5;              // hands resting toward the knees
                p.partPitch[la] = -0.5;
                p.partPitch[hd] = 0.2;
            }
        }
        return p;
    }

    private void drawBackdrop(Graphics2D g, int w, int h) {
        g.setColor(new Color(0x120820));
        g.fillRect(0, 0, w, h);
        if (spiral == null || spiral.getWidth() != w || spiral.getHeight() != h) spiral = buildSpiral(w, h);
        g.drawImage(spiral, 0, 0, null);
    }

    /** A dark purple spiral on transparent, drawn once. */
    private static BufferedImage buildSpiral(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        double cx = w / 2.0, cy = h / 2.0;
        double maxR = Math.hypot(cx, cy);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = x - cx, dy = y - cy;
                double r = Math.hypot(dx, dy);
                double an = Math.atan2(dy, dx);
                double v = Math.sin(an * 2 + r * 0.03);
                if (v > 0.3) {
                    double fade = 0.10 + 0.14 * (1 - r / maxR);
                    int alpha = (int) (Math.min(1, (v - 0.3) / 0.7) * fade * 255);
                    img.setRGB(x, y, (alpha << 24) | 0x6A2FA0);
                }
            }
        }
        return img;
    }
}
