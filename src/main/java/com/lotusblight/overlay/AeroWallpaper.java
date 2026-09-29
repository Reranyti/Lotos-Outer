package com.lotusblight.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/** A bright glossy "Aero" wallpaper - sky, rainbow, sea, dolphins, bubbles - drawn from scratch. */
final class AeroWallpaper {
    private AeroWallpaper() {}

    /** The picture shipped in the jar, scaled to cover the screen; if it isn't there, the drawn one. */
    static BufferedImage paint(int w, int h, int variant) {
        for (String name : variant == 0 ? new String[]{"aero.jpg"} : new String[]{"aero2.jpg", "aero.jpg"}) {
            try (java.io.InputStream in = AeroWallpaper.class.getResourceAsStream("/assets/lotusblight/overlay/" + name)) {
                if (in != null) {
                    BufferedImage src = javax.imageio.ImageIO.read(in);
                    if (src != null) return cover(src, w, h);
                }
            } catch (java.io.IOException | RuntimeException ignored) { }
        }
        return draw(w, h);
    }

    private static BufferedImage cover(BufferedImage src, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        double s = Math.max(w / (double) src.getWidth(), h / (double) src.getHeight());
        int dw = (int) Math.ceil(src.getWidth() * s), dh = (int) Math.ceil(src.getHeight() * s);
        g.drawImage(src, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
        g.dispose();
        return img;
    }

    private static BufferedImage draw(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        Random rnd = new Random(2007);
        double horizon = h * 0.56;

        g.setPaint(new GradientPaint(0, 0, new Color(0x0F6FD6), 0, (float) horizon, new Color(0xA6E3FA)));
        g.fillRect(0, 0, w, (int) horizon + 1);

        // Sun and its rays.
        double sx = w * 0.56, sy = h * 0.2;
        g.setPaint(new RadialGradientPaint((float) sx, (float) sy, (float) (h * 0.45), new float[]{0f, 0.25f, 1f},
                new Color[]{new Color(255, 255, 255, 255), new Color(255, 255, 255, 110), new Color(255, 255, 255, 0)}));
        g.fill(new Ellipse2D.Double(sx - h * 0.45, sy - h * 0.45, h * 0.9, h * 0.9));
        g.setColor(new Color(255, 255, 255, 60));
        for (int i = 0; i < 14; i++) {
            double ang = i * Math.PI * 2 / 14;
            Path2D.Double ray = new Path2D.Double();
            ray.moveTo(sx, sy);
            ray.lineTo(sx + Math.cos(ang - 0.04) * h * 0.7, sy + Math.sin(ang - 0.04) * h * 0.7);
            ray.lineTo(sx + Math.cos(ang + 0.04) * h * 0.7, sy + Math.sin(ang + 0.04) * h * 0.7);
            ray.closePath();
            g.fill(ray);
        }

        // Clouds: piles of soft white puffs.
        for (int c = 0; c < 9; c++) {
            double cx = w * (0.05 + 0.9 * rnd.nextDouble());
            double cy = h * (0.08 + 0.36 * rnd.nextDouble());
            double size = h * (0.06 + 0.07 * rnd.nextDouble());
            for (int p = 0; p < 7; p++) {
                double px = cx + (rnd.nextDouble() - 0.5) * size * 3.2;
                double py = cy + (rnd.nextDouble() - 0.5) * size * 0.8;
                double r = size * (0.6 + rnd.nextDouble() * 0.7);
                g.setPaint(new RadialGradientPaint((float) px, (float) py, (float) r, new float[]{0f, 0.6f, 1f},
                        new Color[]{new Color(255, 255, 255, 235), new Color(255, 255, 255, 170), new Color(255, 255, 255, 0)}));
                g.fill(new Ellipse2D.Double(px - r, py - r, r * 2, r * 2));
            }
        }

        // The rainbow, over the sea.
        double rx = w * 0.4, ry = horizon + h * 0.04, rr = h * 0.5;
        Color[] band = {new Color(0xE8, 0x30, 0x30), new Color(0xF2, 0x8C, 0x28), new Color(0xF5, 0xE0, 0x3A),
                new Color(0x4C, 0xC2, 0x50), new Color(0x33, 0x99, 0xE8), new Color(0x7A, 0x4C, 0xC8)};
        for (int i = 0; i < band.length; i++) {
            double r = rr - i * h * 0.014;
            g.setStroke(new BasicStroke((float) (h * 0.0165)));
            g.setColor(new Color(band[i].getRed(), band[i].getGreen(), band[i].getBlue(), 190));
            g.draw(new java.awt.geom.Arc2D.Double(rx - r, ry - r, r * 2, r * 2, 8, 164, java.awt.geom.Arc2D.OPEN));
        }

        // The sea: bright near the horizon, deeper below, with the waterline in glass.
        g.setPaint(new GradientPaint(0, (float) horizon, new Color(0x53D2E8), 0, h, new Color(0x03579A)));
        g.fillRect(0, (int) horizon, w, h - (int) horizon);
        double waterline = h * 0.76;
        g.setPaint(new GradientPaint(0, (float) waterline, new Color(255, 255, 255, 90), 0, (float) (waterline + h * 0.03), new Color(255, 255, 255, 0)));
        g.fillRect(0, (int) waterline, w, (int) (h * 0.03));
        g.setColor(new Color(255, 255, 255, 120));
        for (int i = 0; i < 90; i++) {
            double x = rnd.nextDouble() * w, y = horizon + rnd.nextDouble() * (waterline - horizon);
            double len = w * (0.01 + 0.03 * rnd.nextDouble());
            g.setStroke(new BasicStroke(1.6f));
            g.draw(new java.awt.geom.Line2D.Double(x, y, x + len, y));
        }
        // Light shafts under the water.
        g.setColor(new Color(255, 255, 255, 22));
        for (int i = 0; i < 9; i++) {
            double x = w * (0.1 + 0.1 * i);
            Path2D.Double shaft = new Path2D.Double();
            shaft.moveTo(x - w * 0.02, waterline);
            shaft.lineTo(x + w * 0.02, waterline);
            shaft.lineTo(x + w * 0.09, h);
            shaft.lineTo(x - w * 0.05, h);
            shaft.closePath();
            g.fill(shaft);
        }

        // Dolphins, leaping over the sea and swimming below.
        dolphin(g, w * 0.16, h * 0.46, h * 0.34, -0.85, false);
        dolphin(g, w * 0.7, h * 0.66, h * 0.22, 0.25, true);
        dolphin(g, w * 0.5, h * 0.3, h * 0.24, -0.35, false);
        dolphin(g, w * 0.42, h * 0.86, h * 0.3, 0.12, true);

        // Fish and reef in the deep.
        for (int i = 0; i < 26; i++) {
            double x = rnd.nextDouble() * w, y = waterline + h * 0.05 + rnd.nextDouble() * h * 0.19;
            double s = h * (0.014 + 0.018 * rnd.nextDouble());
            Color c = new Color[]{new Color(0xFFC63A), new Color(0xFF7A3A), new Color(0xFFFFFF), new Color(0x5CE0D8)}[rnd.nextInt(4)];
            fish(g, x, y, s, c, rnd.nextBoolean());
        }
        g.setColor(new Color(0x0A, 0x50, 0x70, 210));
        for (int i = 0; i < 22; i++) {
            double x = w * i / 21.0 + rnd.nextDouble() * 40, hh = h * (0.03 + 0.05 * rnd.nextDouble());
            g.fill(new Ellipse2D.Double(x - hh, h - hh * 1.2, hh * 2, hh * 2.4));
        }

        // A glassy sheen across the top, the Aero look.
        g.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 70), 0, (float) (h * 0.35), new Color(255, 255, 255, 0)));
        g.fillRect(0, 0, w, (int) (h * 0.35));
        g.dispose();
        return img;
    }

    private static void fish(Graphics2D g, double x, double y, double s, Color c, boolean flip) {
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        if (flip) g.scale(-1, 1);
        g.setColor(c);
        g.fill(new Ellipse2D.Double(-s * 1.4, -s * 0.7, s * 2.8, s * 1.4));
        Path2D.Double tail = new Path2D.Double();
        tail.moveTo(s * 1.2, 0);
        tail.lineTo(s * 2.3, -s * 0.8);
        tail.lineTo(s * 2.3, s * 0.8);
        tail.closePath();
        g.fill(tail);
        g.setColor(new Color(255, 255, 255, 190));
        g.fill(new Ellipse2D.Double(-s * 0.9, -s * 0.28, s * 0.4, s * 0.4));
        g.setTransform(old);
    }

    /** A dolphin: blue back, pale belly, fin and flukes. {@code size} is its length; {@code tilt} in radians. */
    private static void dolphin(Graphics2D g, double x, double y, double size, double tilt, boolean flip) {
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.rotate(tilt);
        g.scale(flip ? -size : size, size);
        Path2D.Double body = new Path2D.Double();
        body.moveTo(-0.5, 0.015);                                 // tip of the beak
        body.curveTo(-0.47, -0.01, -0.44, -0.03, -0.41, -0.06);  // beak up to the round forehead
        body.curveTo(-0.36, -0.16, -0.16, -0.21, 0.06, -0.16);   // melon and back
        body.curveTo(0.26, -0.12, 0.38, -0.06, 0.48, -0.02);     // to the tail stock
        body.lineTo(0.62, -0.13);                                 // upper fluke
        body.lineTo(0.56, 0.0);
        body.lineTo(0.62, 0.13);                                  // lower fluke
        body.lineTo(0.47, 0.02);
        body.curveTo(0.3, 0.1, 0.0, 0.16, -0.22, 0.1);            // belly
        body.curveTo(-0.34, 0.06, -0.42, 0.04, -0.46, 0.035);
        body.lineTo(-0.5, 0.015);
        body.closePath();
        g.setPaint(new GradientPaint(0, -0.16f, new Color(0x1E5FA8), 0, 0.12f, new Color(0xE8F4FF)));
        g.fill(body);
        g.setPaint(new Color(0x19, 0x4F, 0x8C));
        Path2D.Double fin = new Path2D.Double();
        fin.moveTo(-0.04, -0.17);
        fin.curveTo(0.0, -0.29, 0.1, -0.31, 0.16, -0.29);
        fin.curveTo(0.12, -0.24, 0.12, -0.18, 0.18, -0.14);
        fin.closePath();
        g.fill(fin);
        Path2D.Double flipper = new Path2D.Double();
        flipper.moveTo(-0.18, 0.06);
        flipper.curveTo(-0.16, 0.16, -0.08, 0.2, -0.02, 0.2);
        flipper.curveTo(-0.06, 0.14, -0.06, 0.1, -0.05, 0.08);
        flipper.closePath();
        g.fill(flipper);
        g.setColor(new Color(255, 255, 255, 180));
        g.fill(new Ellipse2D.Double(-0.36, -0.06, 0.03, 0.03));
        // Wet gleam along the back.
        g.setStroke(new BasicStroke(0.008f));
        g.setColor(new Color(255, 255, 255, 120));
        g.draw(new java.awt.geom.QuadCurve2D.Double(-0.4, -0.1, -0.15, -0.18, 0.1, -0.1));
        g.setTransform(old);
    }

    /** Glossy soap-bubble spheres drifting up. Cheap enough to redraw every frame. */
    static void bubbles(Graphics2D g, int w, int h, double t, double amount) {
        Random rnd = new Random(7);
        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int i = 0; i < 46; i++) {
            double baseX = rnd.nextDouble(), speed = 0.02 + 0.05 * rnd.nextDouble(), size = h * (0.01 + 0.035 * rnd.nextDouble());
            double phase = rnd.nextDouble();
            double rise = (phase + t * speed) % 1.0;
            double y = h * (1.05 - rise * 1.15);
            double x = w * baseX + Math.sin(t * (0.6 + speed * 6) + i) * size * 1.4;
            b.setPaint(new RadialGradientPaint((float) (x - size * 0.3), (float) (y - size * 0.35), (float) (size * 1.25),
                    new float[]{0f, 0.35f, 0.8f, 1f},
                    new Color[]{new Color(255, 255, 255, 230), new Color(190, 235, 255, 90), new Color(120, 200, 255, 60), new Color(255, 255, 255, 150)}));
            b.fill(new Ellipse2D.Double(x - size, y - size, size * 2, size * 2));
        }
        b.dispose();
    }
}
