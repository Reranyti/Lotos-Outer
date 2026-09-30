package com.lotusblight.overlay;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * The portal. It opens as a vertical slit of light that is pulled apart into an oval; inside, two vortices of
 * spiral arms turn against each other above a dark hollow with a white core; the rim is torn and uneven,
 * split into colour fringes, with lightning crackling along it; sparks wind in, embers drift off, rings of
 * pressure spread out into the room, and its light falls on everything round it.
 */
final class PortalFx {
    private PortalFx() {}

    private static BufferedImage vortexA, vortexB;

    /** A disc of spiral arms: arms of soft glowing blobs along a logarithmic spiral, bright at the core, dark at the rim. */
    private static BufferedImage makeVortex(int arms, double tightness, long seed, int hueShift) {
        int s = 512;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(5, 2, 16));
        g.fill(new Ellipse2D.Double(0, 0, s, s));
        Random r = new Random(seed);
        for (int arm = 0; arm < arms; arm++) {
            double base = arm * Math.PI * 2 / arms;
            for (int i = 0; i < 260; i++) {
                double f = i / 260.0;                                    // 0 at the core .. 1 at the rim
                double rad = 6 + f * f * 244 + f * 14;
                double ang = base + Math.log(1 + rad / 10) * tightness + (r.nextDouble() - 0.5) * 0.06 * (0.3 + f);
                double x = 256 + Math.cos(ang) * rad, y = 256 + Math.sin(ang) * rad;
                double size = 5 + 22 * f * (0.5 + r.nextDouble());
                double alpha = (1 - f * 0.85) * (0.3 + 0.7 * r.nextDouble());
                int red = hueShift == 0 ? (int) (150 + 100 * (1 - f)) : (int) (90 + 80 * (1 - f));
                int green = hueShift == 0 ? (int) (90 + 140 * (1 - f) * (1 - f)) : (int) (170 + 70 * (1 - f));
                int blue = 255;
                g.setPaint(new RadialGradientPaint((float) x, (float) y, (float) size, new float[]{0f, 1f},
                        new Color[]{new Color(red, green, blue, (int) (200 * alpha)), new Color(red, green, blue, 0)}));
                g.fill(new Ellipse2D.Double(x - size, y - size, size * 2, size * 2));
            }
        }
        // The core: white, then violet, fading outward.
        g.setPaint(new RadialGradientPaint(256f, 256f, 120f, new float[]{0f, 0.18f, 0.6f, 1f},
                new Color[]{new Color(255, 255, 255, 255), new Color(230, 210, 255, 230), new Color(130, 80, 230, 90), new Color(130, 80, 230, 0)}));
        g.fill(new Ellipse2D.Double(136, 136, 240, 240));
        // Dark at the very edge, so the rim reads against it.
        g.setComposite(AlphaComposite.DstIn);
        g.setPaint(new RadialGradientPaint(256f, 256f, 256f, new float[]{0.72f, 1f},
                new Color[]{new Color(255, 255, 255, 255), new Color(255, 255, 255, 120)}));
        g.fillRect(0, 0, s, s);
        g.dispose();
        return img;
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /**
     * @param a  how open it is, 0..1 (0 draws nothing)
     * @param rx half the width at full size, ry half the height at full size
     * @param t  seconds, for the turning
     */
    static void draw(Graphics2D g, double px, double py, double rx, double ry, double a, double t, int h) {
        draw(g, px, py, rx, ry, a, t, h, 0);
    }

    /** As above with {@code burst} (0..1), a surge of energy: a ring of pressure goes out and the lightning flares. */
    static void draw(Graphics2D g, double px, double py, double rx, double ry, double a, double t, int h, double burst) {
        if (a <= 0.01) return;
        if (vortexA == null) {
            vortexA = makeVortex(4, 3.1, 11, 0);
            vortexB = makeVortex(3, -2.4, 29, 1);
        }
        // First a slit, then pulled apart.
        double tall = smooth(a / 0.4), wide = smooth((a - 0.22) / 0.78);
        double w2 = Math.max(1.5, rx * wide), h2 = ry * tall;
        Graphics2D d = (Graphics2D) g.create();
        d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double pulse = 1 + 0.03 * Math.sin(t * 7) + 0.05 * burst;

        // Its light on the room.
        int glow = (int) (h2 * 3.2);
        d.setPaint(new RadialGradientPaint((float) px, (float) py, Math.max(2, glow), new float[]{0f, 0.35f, 1f},
                new Color[]{new Color(170, 130, 255, (int) (95 * a + 60 * burst)), new Color(120, 90, 230, (int) (45 * a)), new Color(120, 90, 230, 0)}));
        d.fill(new Ellipse2D.Double(px - glow, py - glow, glow * 2, glow * 2));

        // Rings of pressure spreading out.
        for (int i = 0; i < 3; i++) {
            double ph = ((t * 0.55 + i / 3.0) % 1.0);
            double k = 1 + ph * 1.1;
            d.setStroke(new BasicStroke((float) (h * 0.004 * (1 - ph) + 0.6)));
            d.setColor(new Color(170, 140, 255, (int) (70 * a * (1 - ph) + 90 * burst * (1 - ph))));
            d.draw(new Ellipse2D.Double(px - w2 * k * 1.1, py - h2 * k * 1.06, w2 * 2 * k * 1.1, h2 * 2 * k * 1.06));
        }

        // The rim: torn, uneven, breathing; drawn three times, shifted, for the colour fringe.
        Path2D.Double rim = rimPath(px, py, w2 * pulse, h2 * pulse, t, 1.0);
        Path2D.Double rimIn = rimPath(px, py, w2 * pulse * 0.93, h2 * pulse * 0.95, t + 3, 0.6);
        AffineTransform keep = d.getTransform();
        d.translate(-2.5, 0);
        d.setColor(new Color(255, 70, 190, (int) (150 * a)));
        d.setStroke(new BasicStroke((float) (h * 0.006)));
        d.draw(rim);
        d.setTransform(keep);
        d.translate(2.5, 0);
        d.setColor(new Color(60, 230, 255, (int) (160 * a)));
        d.draw(rim);
        d.setTransform(keep);

        // The inside: a dark hollow, and two vortices turning against each other, clipped to the oval.
        d.setColor(new Color(6, 2, 18, (int) (250 * a)));
        d.fill(rim);
        java.awt.Shape oldClip = d.getClip();
        d.clip(rimIn);
        AffineTransform at = new AffineTransform(keep);
        at.translate(px, py);
        at.scale(w2 / 256.0, h2 / 256.0);
        d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.min(1, a)));
        AffineTransform a1 = new AffineTransform(at);
        a1.rotate(t * 0.85);
        d.setTransform(a1);
        d.drawImage(vortexA, -256, -256, null);
        AffineTransform a2 = new AffineTransform(at);
        a2.rotate(-t * 1.35);
        a2.scale(0.62, 0.62);
        d.setTransform(a2);
        d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (0.75 * Math.min(1, a))));
        d.drawImage(vortexB, -256, -256, null);
        d.setTransform(keep);
        d.setComposite(AlphaComposite.SrcOver);
        // A bright core that swells with the pulse.
        double core = Math.min(w2, h2) * (0.35 + 0.08 * Math.sin(t * 5) + 0.25 * burst);
        d.setPaint(new RadialGradientPaint((float) px, (float) py, (float) Math.max(2, core), new float[]{0f, 1f},
                new Color[]{new Color(255, 255, 255, (int) (200 * a)), new Color(200, 170, 255, 0)}));
        d.fill(new Ellipse2D.Double(px - core, py - core, core * 2, core * 2));
        d.setClip(oldClip);

        // The bright edge itself, in two weights.
        d.setStroke(new BasicStroke((float) (h * 0.0085)));
        d.setColor(new Color(150, 110, 255, (int) (200 * a)));
        d.draw(rim);
        d.setStroke(new BasicStroke((float) (h * 0.0025 + 0.8)));
        d.setColor(new Color(235, 225, 255, (int) (240 * a)));
        d.draw(rim);

        // Lightning along the rim, flaring with the burst.
        Random lr = new Random((long) (t * 13) * 7919L + 3);
        int bolts = 4 + (int) (6 * burst);
        for (int i = 0; i < bolts; i++) {
            double ang = lr.nextDouble() * Math.PI * 2;
            double len = h2 * (0.12 + 0.3 * lr.nextDouble()) * (0.7 + burst);
            double x = px + Math.cos(ang) * w2 * pulse, y = py + Math.sin(ang) * h2 * pulse;
            Path2D.Double bolt = new Path2D.Double();
            bolt.moveTo(x, y);
            double dirx = Math.cos(ang), diry = Math.sin(ang);
            for (int s = 1; s <= 6; s++) {
                double f = s / 6.0;
                x = px + dirx * (w2 * pulse + len * f) * (1) + (lr.nextDouble() - 0.5) * len * 0.35;
                y = py + diry * (h2 * pulse + len * f) + (lr.nextDouble() - 0.5) * len * 0.35;
                bolt.lineTo(x, y);
            }
            d.setStroke(new BasicStroke((float) (h * 0.006)));
            d.setColor(new Color(150, 130, 255, (int) (70 * a)));
            d.draw(bolt);
            d.setStroke(new BasicStroke(1.4f));
            d.setColor(new Color(240, 235, 255, (int) (230 * a * (0.4 + 0.6 * lr.nextDouble()))));
            d.draw(bolt);
        }

        // Sparks winding in, and embers going off the other way.
        Random sr = new Random(5);
        for (int i = 0; i < 64; i++) {
            double ang = sr.nextDouble() * Math.PI * 2 + t * (0.6 + sr.nextDouble() * 1.2);
            double ph = (sr.nextDouble() + t * (0.25 + 0.2 * sr.nextDouble())) % 1.0;
            boolean in = i % 3 != 0;
            double f = in ? 1.9 - 0.9 * ph : 1.05 + 1.3 * ph;                 // in: from far to the rim; out: away from it
            double sz = h * 0.0042 * (1 + sr.nextDouble()) * (in ? 1 - 0.4 * ph : 1 - ph);
            double alpha = in ? ph : 1 - ph;
            double sx = px + Math.cos(ang) * w2 * f, sy = py + Math.sin(ang) * h2 * f - (in ? 0 : ph * h * 0.05);
            double tx = px + Math.cos(ang - 0.3 * (in ? 1 : -1)) * w2 * (f + (in ? 0.12 : -0.08)), ty = py + Math.sin(ang - 0.3 * (in ? 1 : -1)) * h2 * (f + (in ? 0.12 : -0.08));
            d.setStroke(new BasicStroke((float) Math.max(0.8, sz * 0.7)));
            d.setColor(in ? new Color(200, 180, 255, (int) (110 * a * alpha)) : new Color(255, 200, 150, (int) (110 * a * alpha)));
            d.draw(new Line2D.Double(sx, sy, tx, ty));
            d.setColor(new Color(255, 255, 255, (int) (230 * a * alpha)));
            d.fill(new Ellipse2D.Double(sx - sz, sy - sz, sz * 2, sz * 2));
        }
        d.dispose();
    }

    /** The edge of the portal: an oval pushed in and out by noise that shifts with time, sharper at the top and bottom. */
    private static Path2D.Double rimPath(double px, double py, double rx, double ry, double t, double roughness) {
        Path2D.Double p = new Path2D.Double();
        int n = 72;
        for (int i = 0; i <= n; i++) {
            double ang = i * Math.PI * 2 / n;
            double noise = 0.045 * Math.sin(ang * 5 + t * 2.3) + 0.03 * Math.sin(ang * 11 - t * 3.1) + 0.02 * Math.sin(ang * 23 + t * 5.7);
            double spike = Math.pow(Math.abs(Math.sin(ang)), 6) * 0.06 * Math.sin(t * 4 + ang * 3);   // the tips pull out a little
            double k = 1 + (noise + spike) * roughness;
            double x = px + Math.cos(ang) * rx * k, y = py + Math.sin(ang) * ry * k;
            if (i == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        p.closePath();
        return p;
    }
}
