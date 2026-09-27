package com.lotusblight.overlay;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;

/**
 * The "walking loop" scene: the Glitcher walks in profile and drags a long trail of his own earlier
 * frames behind him, onion-skin style, over a dark purple spiral backdrop. Every so often a purple
 * panel slides across and singles him out large, without the trail. Purely a picture - it draws itself
 * for a given moment in time and touches nothing.
 */
final class TrailScene {
    // The trail's copies, front to back, and how far apart and how much darker each one is.
    private static final int TRAIL = 16;
    private static final double PHASE_STEP = 0.42;

    private final int[] skin;
    private final SkinModel model = new SkinModel();
    private final SoftRenderer figure;
    private final double scale;
    private final double stride;          // horizontal gap between trail copies
    private BufferedImage spiral;

    TrailScene(int[] skin, int height) {
        this.skin = skin;
        // The lead figure is about half the screen tall, leaving headroom and floor.
        this.scale = height * 0.50 / 32.0;
        this.figure = new SoftRenderer((int) (26 * scale), (int) (42 * scale));
        this.stride = scale * 3.2;
    }

    /**
     * Draws the scene at time seconds into the canvas w x h. panel is 0 before the singling-out panel,
     * rising to 1 as it covers the screen.
     */
    void render(Graphics2D g, int w, int h, double time, double panel) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        // The floor the feet walk along.
        double floorY = h * 0.98;
        double leadX = w * 0.30;
        double phase = time * 7.0;

        drawBackdrop(g, w, h, time);

        // Trail from the back forward, so nearer copies cover farther ones.
        for (int i = TRAIL; i >= 1; i--) {
            double t = i / (double) TRAIL;                 // 1 at the back
            double bright = 0.30 + 0.70 * (1 - t);         // fades into the dark behind
            float alpha = (float) (0.35 + 0.65 * (1 - t));
            drawFigure(g, leadX + i * stride, floorY, phase - i * PHASE_STEP, bright, alpha, false);
        }
        // The lead figure, full brightness.
        drawFigure(g, leadX, floorY, phase, 1.0, 1f, false);

        if (panel > 0) drawPanel(g, w, h, time, panel);
    }

    /** The purple vertical band that slides in and shows one clean, large Glitcher. */
    private void drawPanel(Graphics2D g, int w, int h, double time, double panel) {
        double bandW = w * 0.34;
        double cx = w * (1.15 - 1.05 * panel);            // slides in from the right
        Graphics2D p = (Graphics2D) g.create();
        p.setPaint(new GradientPaint((float) (cx - bandW / 2), 0, new Color(0x2A0B4A),
                (float) (cx + bandW / 2), 0, new Color(0x5A1B8C)));
        p.fillRect((int) (cx - bandW / 2), 0, (int) bandW, h);
        // A crisp figure walking inside the band, no trail.
        drawFigure(p, cx, h * 0.98, time * 7.0, 1.15, 1f, true);
        p.dispose();
    }

    private void drawFigure(Graphics2D g, double x, double floorY, double phase,
                            double bright, float alpha, boolean lit) {
        figure.clear();
        double bob = Math.abs(Math.cos(phase)) * scale * 0.7;    // rises on each step
        double originX = figure.width / 2.0;
        double originY = figure.height - 2 * scale;
        figure.draw(model, skin, walkPose(phase), scale, originX, originY);

        BufferedImage img = figure.image;
        if (bright != 1.0 || alpha != 1f) {
            float b = (float) bright;
            img = new RescaleOp(new float[]{b, b, b, alpha}, new float[]{0, 0, 0, 0}, null)
                    .filter(figure.image, null);
        }
        int drawX = (int) (x - originX);
        int drawY = (int) (floorY - bob - originY);
        if (lit) {
            // A soft radial glow behind the singled-out figure, fading to nothing at the edge.
            Graphics2D gl = (Graphics2D) g.create();
            float gw = figure.width * 1.1f, gh = figure.height * 1.0f;
            float gx = (float) x, gy = (float) (floorY - figure.height * 0.45);
            gl.setPaint(new java.awt.RadialGradientPaint(gx, gy, Math.max(gw, gh) / 2,
                    new float[]{0f, 1f},
                    new Color[]{new Color(0xB0, 0x60, 0xFF, 150), new Color(0xB0, 0x60, 0xFF, 0)}));
            gl.fillRect((int) (gx - gw / 2), (int) (gy - gh / 2), (int) gw, (int) gh);
            gl.dispose();
        }
        g.drawImage(img, drawX, drawY, null);
    }

    /** A big, slow stride in profile: legs swing wide, arms counter-swing, the body leans forward. */
    private SoftRenderer.Pose walkPose(double phase) {
        SoftRenderer.Pose pose = new SoftRenderer.Pose();
        pose.yaw = -Math.PI / 2;                 // side profile, facing the way it walks
        pose.pitch = 0.12;                        // slight forward lean
        double s = Math.sin(phase);
        pose.partPitch[SkinModel.Part.RIGHT_LEG.ordinal()] = s * 1.05;
        pose.partPitch[SkinModel.Part.LEFT_LEG.ordinal()] = -s * 1.05;
        pose.partPitch[SkinModel.Part.RIGHT_ARM.ordinal()] = -s * 0.75;
        pose.partPitch[SkinModel.Part.LEFT_ARM.ordinal()] = s * 0.75;
        pose.partPitch[SkinModel.Part.HEAD.ordinal()] = 0.15 + Math.cos(phase * 2) * 0.05;
        return pose;
    }

    private void drawBackdrop(Graphics2D g, int w, int h, double time) {
        g.setColor(new Color(0x120820));
        g.fillRect(0, 0, w, h);
        if (spiral == null || spiral.getWidth() != w || spiral.getHeight() != h) spiral = buildSpiral(w, h);
        // Slowly turning spiral, dim.
        Graphics2D s = (Graphics2D) g.create();
        s.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        s.rotate(time * 0.15, w / 2.0, h / 2.0);
        double over = 1.6;   // large enough that the corners stay covered while it turns
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
                double a = Math.atan2(dy, dx);
                // Bands that wind outward form the spiral arms.
                double v = Math.sin(a * 2 + r * 0.03);
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
