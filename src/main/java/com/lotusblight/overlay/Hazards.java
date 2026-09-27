package com.lotusblight.overlay;

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
}
