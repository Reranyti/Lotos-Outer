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
    private static final int FRAMES = 48;            // sprites in one walk cycle
    private static final double CYCLES_PER_SEC = 1.1;
    private static final int STEP_FRAMES = 4;        // walk-frame gap between neighbours (leg criss-cross)
    private static final double BEAT_SEC = 0.30;     // a copy appears each beat
    private static final double SWEEP_SEC = 2.2;     // the panel's trip across the screen
    private static final double SPACING = 0.42;      // column gap as a fraction of sprite width

    private final BufferedImage[] sprites = new BufferedImage[FRAMES];
    private final double[] bob = new double[FRAMES];
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
        for (int k = 0; k < FRAMES; k++) {
            double phase = 2 * Math.PI * k / FRAMES;
            bob[k] = Math.abs(Math.cos(phase)) * scale * 0.7;
            r.clear();
            r.draw(model, skin, walkPose(phase), rs, r.width / 2.0, r.height - 2 * rs);
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
        double leadFrame = time * CYCLES_PER_SEC * FRAMES;

        // Copies come in one per beat until the row is full.
        double since = Math.max(0, time - sceneStart);
        int shown = Math.min(slots, (int) (since / BEAT_SEC) + 1);

        for (int i = 0; i < shown; i++) {
            double x = startX + i * dx;
            int k = ((int) Math.round(leadFrame - i * STEP_FRAMES) % FRAMES + FRAMES) % FRAMES;
            g.drawImage(sprites[k], (int) (x - originX), (int) (floorY - bob[k] - originY), null);
        }

        // Once the row is full, a purple band sweeps across showing one clean walker inside it.
        double rowFull = sceneStart + slots * BEAT_SEC;
        if (time > rowFull) {
            double p = ((time - rowFull) / SWEEP_SEC) % 1.0;
            drawPanel(g, w, h, leadFrame, floorY, p);
        }

        g.setColor(new Color(0x7A24FF));
        g.fillRect(0, 0, w, barH);
        g.fillRect(0, h - barH, w, barH);
    }

    /** The purple vertical band that slides across the screen with a single clean Glitcher walking. */
    private void drawPanel(Graphics2D g, int w, int h, double leadFrame, double floorY, double p) {
        double bandW = w * 0.30;
        double cx = -bandW / 2 + (w + bandW) * p;    // sweeps left to right
        Graphics2D b = (Graphics2D) g.create();
        b.setPaint(new java.awt.GradientPaint((float) (cx - bandW / 2), 0, new Color(0x2A0B4A),
                (float) (cx + bandW / 2), 0, new Color(0x5A1B8C)));
        b.fillRect((int) (cx - bandW / 2), 0, (int) bandW, h);
        int k = ((int) Math.round(leadFrame) % FRAMES + FRAMES) % FRAMES;
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        b.drawImage(sprites[k], (int) (cx - originX), (int) (floorY - bob[k] - originY), null);
        b.dispose();
    }

    /** A profile walk: legs swing wide, arms counter-swing, the body leans forward. */
    private static SoftRenderer.Pose walkPose(double phase) {
        SoftRenderer.Pose pose = new SoftRenderer.Pose();
        pose.yaw = -Math.PI / 2;                 // side profile, facing the walk
        pose.pitch = 0.12;                        // slight forward lean
        double s = Math.sin(phase);
        pose.partPitch[SkinModel.Part.RIGHT_LEG.ordinal()] = s * 1.05;
        pose.partPitch[SkinModel.Part.LEFT_LEG.ordinal()] = -s * 1.05;
        pose.partPitch[SkinModel.Part.RIGHT_ARM.ordinal()] = -s * 0.75;
        pose.partPitch[SkinModel.Part.LEFT_ARM.ordinal()] = s * 0.75;
        pose.partPitch[SkinModel.Part.HEAD.ordinal()] = 0.15 + Math.cos(phase * 2) * 0.05;
        return pose;
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
