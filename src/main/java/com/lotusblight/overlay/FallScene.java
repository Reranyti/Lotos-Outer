package com.lotusblight.overlay;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

/**
 * Everyone falling: a dark sky with the wind rushing past as lines, and bits of the ruined desktop - error
 * windows - tumbling upwards past the eye. It is the backdrop of the third song's fight.
 */
final class FallScene {
    private static final int STREAKS = 130, DEBRIS = 15;

    private final double[][] streaks = new double[STREAKS][5];   // x (0..1), length, speed, width, alpha
    private final double[][] debris = new double[DEBRIS][6];     // sprite, x (0..1), size, speed, spin, phase
    private final List<BufferedImage> sprites = shardSprites();
    private BufferedImage backdrop;      // sky, vignette and the darkening under the fight, painted once

    FallScene() {
        Random rnd = new Random(88);
        for (double[] s : streaks) {
            s[0] = rnd.nextDouble();
            s[1] = 0.06 + 0.22 * rnd.nextDouble();
            s[2] = 0.9 + 1.8 * rnd.nextDouble();
            s[3] = 1 + 2.4 * rnd.nextDouble();
            s[4] = 40 + 120 * rnd.nextDouble();
        }
        for (double[] d : debris) {
            d[0] = rnd.nextInt(sprites.size());
            d[1] = rnd.nextDouble();
            d[2] = 0.05 + 0.13 * rnd.nextDouble();
            d[3] = 0.25 + 0.5 * rnd.nextDouble();
            d[4] = (rnd.nextDouble() - 0.5) * 1.6;
            d[5] = rnd.nextDouble();
        }
    }

    /** Splinters of the broken screen: irregular pieces of glass, pale and blue, with a bright edge. */
    private static List<BufferedImage> shardSprites() {
        Random rnd = new Random(12);
        java.util.List<BufferedImage> out = new java.util.ArrayList<>();
        for (int k = 0; k < 7; k++) {
            int size = 220;
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int n = 5 + rnd.nextInt(3);
            double[] ang = new double[n];
            for (int i = 0; i < n; i++) ang[i] = (i + rnd.nextDouble() * 0.6) * Math.PI * 2 / n;
            java.awt.geom.Path2D.Double poly = new java.awt.geom.Path2D.Double();
            for (int i = 0; i < n; i++) {
                double r = size * (0.22 + 0.26 * rnd.nextDouble());
                double x = size / 2.0 + Math.cos(ang[i]) * r, y = size / 2.0 + Math.sin(ang[i]) * r * (0.6 + 0.4 * rnd.nextDouble());
                if (i == 0) poly.moveTo(x, y); else poly.lineTo(x, y);
            }
            poly.closePath();
            g.setPaint(new GradientPaint(0, 0, new Color(200, 225, 255, 190), size, size, new Color(70, 110, 190, 120)));
            g.fill(poly);
            g.setColor(new Color(255, 255, 255, 220));
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(poly);
            g.setColor(new Color(255, 255, 255, 110));
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new java.awt.geom.Line2D.Double(size * 0.35, size * 0.4, size * 0.65, size * 0.62));
            g.dispose();
            out.add(img);
        }
        return out;
    }

    /** {@code ms} is any steadily growing clock; {@code speed} 1 is the usual fall. */
    void render(Graphics2D g, int w, int h, double ms, double speed) {
        render(g, w, h, ms, speed, g);
    }

    /** {@code sky} paints the plain dark backdrop (it may be a differently turned graphics than {@code g}). */
    void render(Graphics2D g, int w, int h, double ms, double speed, Graphics2D sky) {
        if (backdrop == null || backdrop.getWidth() != w || backdrop.getHeight() != h) backdrop = makeBackdrop(w, h);
        sky.drawImage(backdrop, 0, 0, null);
        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double t = ms / 1000.0 * speed;

        // Far debris first, then the wind, so the windows near the eye pass in front of the lines.
        for (int pass = 0; pass < 2; pass++) {
            for (double[] d : debris) {
                boolean near = d[2] > 0.1;
                if (near == (pass == 0)) continue;
                BufferedImage sp = sprites.get((int) d[0]);
                double size = h * d[2];
                double travel = h + size * 2.2;
                double y = h + size - ((t * d[3] * h * 0.55 + d[5] * travel) % travel);
                double x = d[1] * w + Math.sin(t * 0.7 + d[5] * 9) * w * 0.02;
                double sc = size / Math.max(sp.getWidth(), sp.getHeight());
                AffineTransform old = b.getTransform();
                b.translate(x, y);
                b.rotate(d[4] * t + d[5] * 6);
                b.scale(sc, sc);
                b.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, near ? 0.9f : 0.45f));
                b.drawImage(sp, -sp.getWidth() / 2, -sp.getHeight() / 2, null);
                b.setComposite(AlphaComposite.SrcOver);
                b.setTransform(old);
            }
            if (pass == 0) drawWind(b, w, h, t);
        }
        b.dispose();
    }

    private static BufferedImage makeBackdrop(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D b = img.createGraphics();
        b.setPaint(new GradientPaint(0, 0, new Color(10, 14, 42), 0, h, new Color(3, 2, 12)));
        b.fillRect(0, 0, w, h);
        b.setPaint(new RadialGradientPaint((float) (w / 2.0), (float) (h / 2.0), (float) (Math.hypot(w, h) / 2),
                new float[]{0.45f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 170)}));
        b.fillRect(0, 0, w, h);
        b.setColor(new Color(0, 0, 0, 90));                     // the fight is played a little darker than the bare sky
        b.fillRect(0, 0, w, h);
        b.dispose();
        return img;
    }

    private void drawWind(Graphics2D b, int w, int h, double t) {
        for (double[] s : streaks) {
            double len = s[1] * h;
            double travel = h + len * 2;
            double y = h + len - ((t * s[2] * h * 1.4 + s[0] * travel * 3) % travel);
            double x = s[0] * w;
            b.setStroke(new BasicStroke((float) s[3], BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            b.setColor(new Color(190, 215, 255, (int) s[4]));
            b.draw(new java.awt.geom.Line2D.Double(x, y, x, y + len));
        }
    }
}
