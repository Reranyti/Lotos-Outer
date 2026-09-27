package com.lotusblight.overlay;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * The "echo" scene: the Glitcher holds one menacing pose (leaning forward, arms up near his head) and
 * that pose repeats into a growing arc of copies sweeping up and to the left, onion-skin style, over a
 * dark purple backdrop. The real figure is on the right, brightest; copies fade into the dark behind.
 * The pose steps every few copies and the arc grows across the scene, then it loops.
 *
 * The pose loop is rendered once at start into a handful of sprites; each frame just blits those.
 */
final class TrailScene {
    private static final int POSES = 8;             // key poses in the held-pose loop
    private static final double POSE_PER_SEC = 6.0; // how fast the loop plays at the front
    private static final int COPIES_PER_POSE = 4;   // the pose changes every 4 copies
    private static final int MAX_COPIES = 40;
    private static final double GROW_PER_SEC = 9.0;  // copies added per second
    private static final double LOOP_SEC = 5.2;      // grow, then start over

    private final BufferedImage[] sprites = new BufferedImage[POSES];
    private final double scale;
    private final int spriteW;
    private final int spriteH;
    private final double originX;
    private final double originY;
    private BufferedImage spiral;

    TrailScene(int[] skin, int height) {
        // Big figure; sprites rendered at half and blitted 2x (pixelated anyway) to save memory.
        this.scale = height * 0.52 / 32.0;
        double rs = scale / 2;
        SkinModel model = new SkinModel();
        SoftRenderer r = new SoftRenderer((int) (30 * rs), (int) (40 * rs));
        this.spriteW = r.width * 2;
        this.spriteH = r.height * 2;
        this.originX = r.width;
        this.originY = (r.height - 2 * rs) * 2;
        for (int k = 0; k < POSES; k++) {
            r.clear();
            r.draw(model, skin, menacePose(k / (double) POSES), rs, r.width / 2.0, r.height - 2 * rs);
            sprites[k] = new BufferedImage(spriteW, spriteH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = sprites[k].createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(r.image, 0, 0, spriteW, spriteH, null);
            g.dispose();
        }
    }

    /**
     * Draws the scene at time seconds into the canvas w x h. panel rises 0..1 as the singling-out band
     * covers the screen (kept for the second half of the beat).
     */
    void render(Graphics2D g, int w, int h, double time, double panel) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        drawBackdrop(g, w, h, time);

        double frontX = w * 0.60;
        double frontY = h * 0.98;
        double dx = spriteW * 0.34;        // horizontal gap between copies
        double poseFront = time * POSE_PER_SEC;

        // The arc grows over the scene, then resets - it "repeats" every LOOP_SEC.
        double localT = time % LOOP_SEC;
        int copies = (int) Math.min(MAX_COPIES, 3 + localT * GROW_PER_SEC);

        // Back to front, so nearer copies cover farther ones.
        for (int i = copies; i >= 0; i--) {
            double t = i / (double) MAX_COPIES;
            // A curve up and to the left: x steps left, y climbs and eases off.
            double x = frontX - i * dx;
            double y = frontY - Math.pow(i, 0.85) * (spriteH * 0.06);
            int pose = ((int) Math.floor(poseFront - i / (double) COPIES_PER_POSE) % POSES + POSES) % POSES;
            float alpha = i == 0 ? 1f : (float) Math.max(0.12, 0.85 * (1 - t));
            draw(g, sprites[pose], x, y, alpha);
        }
    }

    private void draw(Graphics2D g, BufferedImage sprite, double x, double y, float alpha) {
        int drawX = (int) (x - originX);
        int drawY = (int) (y - originY);
        if (alpha >= 1f) {
            g.drawImage(sprite, drawX, drawY, null);
        } else {
            Graphics2D c = (Graphics2D) g.create();
            c.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            c.drawImage(sprite, drawX, drawY, null);
            c.dispose();
        }
    }

    /**
     * The held pose: leaning hard forward, turned three-quarters to camera, both arms raised up toward
     * the head. phase (0..1) makes it breathe - arms and head sway a little through the loop.
     */
    private static SoftRenderer.Pose menacePose(double phase) {
        SoftRenderer.Pose pose = new SoftRenderer.Pose();
        double a = 2 * Math.PI * phase;
        pose.yaw = -0.55;                          // three-quarter view
        pose.pitch = 0.5;                          // hunched forward
        double sway = Math.sin(a);
        // Arms up near the head, swaying through the loop.
        pose.partPitch[SkinModel.Part.RIGHT_ARM.ordinal()] = -2.5 + sway * 0.2;
        pose.partPitch[SkinModel.Part.LEFT_ARM.ordinal()] = -2.5 - sway * 0.2;
        pose.partRoll[SkinModel.Part.RIGHT_ARM.ordinal()] = -0.4;
        pose.partRoll[SkinModel.Part.LEFT_ARM.ordinal()] = 0.4;
        // Legs planted, a slight stagger.
        pose.partPitch[SkinModel.Part.RIGHT_LEG.ordinal()] = 0.35;
        pose.partPitch[SkinModel.Part.LEFT_LEG.ordinal()] = -0.25;
        pose.partPitch[SkinModel.Part.HEAD.ordinal()] = 0.25 + Math.cos(a) * 0.08;
        return pose;
    }

    private void drawBackdrop(Graphics2D g, int w, int h, double time) {
        g.setColor(new Color(0x120820));
        g.fillRect(0, 0, w, h);
        if (spiral == null || spiral.getWidth() != w || spiral.getHeight() != h) spiral = buildSpiral(w, h);
        Graphics2D s = (Graphics2D) g.create();
        s.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        s.rotate(time * 0.15, w / 2.0, h / 2.0);
        double over = 1.6;
        s.drawImage(spiral, (int) (-w * (over - 1) / 2), (int) (-h * (over - 1) / 2),
                (int) (w * over), (int) (h * over), null);
        s.dispose();
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
