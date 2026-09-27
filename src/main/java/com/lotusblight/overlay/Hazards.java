package com.lotusblight.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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

    /** A decoy circle: born at the centre, orbiting out, made to look like a real note. */
    private static final class Fake {
        double angle, spin, radius, radialV, born;
    }

    private final List<Fake> fakes = new ArrayList<>();
    private final Random rnd = new Random(99);
    private double lastSpawn = -1;

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

    /** From 1:21, decoy circles pour out of the centre, orbiting left and right to confuse the eye. */
    private void drawFakes(Graphics2D g, int w, int h, double timeMs) {
        if (timeMs < FAKE_START_MS) { fakes.clear(); return; }
        double cx = w * 0.5, cy = h * 0.5;
        double note = h * 0.05;                        // roughly the real circle size

        // Spawn a couple every so often, alternating spin direction.
        if (lastSpawn < 0 || timeMs - lastSpawn > 260) {
            lastSpawn = timeMs;
            for (int n = 0; n < 2; n++) {
                Fake f = new Fake();
                f.angle = rnd.nextDouble() * Math.PI * 2;
                f.spin = (rnd.nextBoolean() ? 1 : -1) * (1.2 + rnd.nextDouble() * 1.5);
                f.radius = note;
                f.radialV = h * (0.10 + rnd.nextDouble() * 0.10);
                f.born = timeMs;
                fakes.add(f);
            }
        }

        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        fakes.removeIf(f -> timeMs - f.born > 2200);
        for (Fake f : fakes) {
            double age = (timeMs - f.born) / 1000.0;
            double rad = f.radius + f.radialV * age;
            double ang = f.angle + f.spin * age;
            double x = cx + Math.cos(ang) * rad;
            double y = cy + Math.sin(ang) * rad;
            float alpha = (float) Math.max(0, 1 - age / 2.2);
            // Same look as a real note, so it blends in.
            b.setColor(new Color(0x2A, 0x0B, 0x4A, (int) (alpha * 200)));
            b.fillOval((int) (x - note), (int) (y - note), (int) (note * 2), (int) (note * 2));
            b.setStroke(new BasicStroke((float) (note * 0.18)));
            b.setColor(new Color(0xB0, 0x60, 0xFF, (int) (alpha * 240)));
            b.drawOval((int) (x - note), (int) (y - note), (int) (note * 2), (int) (note * 2));
        }
        b.dispose();
    }
}
