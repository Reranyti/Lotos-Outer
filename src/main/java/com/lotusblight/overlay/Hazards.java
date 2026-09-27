package com.lotusblight.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * What gets in the player's way during the rhythm game: the Glitcher himself wandering across and, now
 * and then, a purple wall sliding over the playfield to hide the circles behind it. Drawn on top of the
 * circles so it really does block them. Purely visual - it never changes the notes, only how well you
 * can see and reach them.
 */
final class Hazards {
    private static final double FAKE_START_MS = 81_000;   // 1:21 - the Glitcher starts spewing decoys

    private final BufferedImage glitcher;
    private final int gW;
    private final int gH;


    Hazards(int[] skin, int height) {
        double scale = height * 0.40 / 32.0;
        double rs = scale / 2;
        SkinModel model = new SkinModel();
        SoftRenderer r = new SoftRenderer((int) (26 * rs), (int) (42 * rs));
        r.clear();
        SoftRenderer.Pose p = new SoftRenderer.Pose();
        p.yaw = -0.4;
        p.partPitch[SkinModel.Part.HEAD.ordinal()] = 0.1;
        r.draw(model, skin, p, rs, r.width / 2.0, r.height - 2 * rs);
        this.gW = r.width * 2;
        this.gH = r.height * 2;
        this.glitcher = new BufferedImage(gW, gH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = glitcher.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(r.image, 0, 0, gW, gH, null);
        g.dispose();
    }

    void render(Graphics2D g, int w, int h, double timeMs) {
        double t = timeMs / 1000.0;

        drawFakes(g, w, h, timeMs);

        // The Glitcher paces across the lower playfield, swaying, blocking whatever he passes.
        double gx = w * (0.5 + 0.42 * Math.sin(t * 0.55));
        double gy = h * 0.72 + Math.abs(Math.sin(t * 1.6)) * h * 0.03;
        double lean = Math.sin(t * 0.55) * 0.12;
        java.awt.geom.AffineTransform old = g.getTransform();
        g.rotate(lean, gx, gy);
        g.drawImage(glitcher, (int) (gx - gW / 2.0), (int) (gy - gH), null);
        g.setTransform(old);

        // Every so often a purple wall slides across and hides the circles behind it.
        double period = 9.0;
        double phase = (t % period) / period;
        if (phase < 0.5) {
            double p = phase / 0.5;                    // 0..1 sweep
            double bandW = w * 0.26;
            double cx = -bandW + (w + 2 * bandW) * p;
            Graphics2D b = (Graphics2D) g.create();
            b.setPaint(new GradientPaint((float) (cx - bandW / 2), 0, new Color(0x2A0B4A),
                    (float) (cx + bandW / 2), 0, new Color(0x6A24C0)));
            b.fillRect((int) (cx - bandW / 2), 0, (int) bandW, h);
            // A soft glitch edge.
            b.setColor(new Color(0xB0, 0x60, 0xFF, 120));
            b.fillRect((int) (cx + bandW / 2 - 4), 0, 4, h);
            b.fillRect((int) (cx - bandW / 2), 0, 4, h);
            b.dispose();
        }
    }

    /**
     * From 1:21, decoy circles fill the whole screen as two counter-rotating spiral arms - one winding
     * left, one right - all looking like real notes, to confuse the eye.
     */
    private void drawFakes(Graphics2D g, int w, int h, double timeMs) {
        if (timeMs < FAKE_START_MS) return;
        double cx = w * 0.5, cy = h * 0.5;
        double note = h * 0.06;
        double maxR = Math.hypot(w, h) / 2 * 1.05;
        int count = 46;
        double turns = 3.0;                            // how many times the arm winds to the centre
        double t = timeMs / 1000.0;
        double travel = t * 0.16;                      // circles fly inward
        double spin = t * 0.35;                        // and the whole tunnel turns

        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int i = 0; i < count; i++) {
            double depth = ((i / (double) count) - travel) % 1.0;   // 0 at the centre, 1 at the rim
            if (depth < 0) depth += 1;
            double r = maxR * depth;
            double ang = depth * turns * Math.PI * 2 + spin;
            double x = cx + Math.cos(ang) * r;
            double y = cy + Math.sin(ang) * r;
            double size = note * (0.22 + 0.9 * depth);              // small deep in, big at the rim
            float alpha = (float) Math.min(1, depth * 2.5);         // fade out of the centre
            b.setColor(new Color(0x2A, 0x0B, 0x4A, (int) (alpha * 200)));
            b.fillOval((int) (x - size), (int) (y - size), (int) (size * 2), (int) (size * 2));
            b.setStroke(new BasicStroke((float) (size * 0.18)));
            b.setColor(new Color(0xB0, 0x60, 0xFF, (int) (alpha * 235)));
            b.drawOval((int) (x - size), (int) (y - size), (int) (size * 2), (int) (size * 2));
        }
        b.dispose();
    }
}
