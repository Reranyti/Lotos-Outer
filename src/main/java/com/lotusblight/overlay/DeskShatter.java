package com.lotusblight.overlay;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The desktop breaking like a pane of glass. A blow lands in the middle: cracks run out from it along the edges
 * of irregular three-cornered shards, the picture flashes, and the shards let go in a wave from the middle
 * outwards - each lifts a moment, tilts, and falls, turning as it goes, shedding dust. What is under it (the
 * falling sky) shows through as it empties. Everything is a function of the time since the blow.
 */
final class DeskShatter {
    private static final int COLS = 16, ROWS = 9;
    /** When the cracks have spread right across, and when the last shards let go (ms). */
    static final double CRACK_MS = 1_000, LAST_RELEASE_MS = 2_000;

    private static final class Shard {
        BufferedImage sprite;
        double ox, oy;                      // where the sprite's corner lies on the desktop
        double cx, cy;                      // the centre it turns about
        double[] px = new double[3], py = new double[3];
        double dist;                        // 0 at the blow .. 1 at the far corner
        double release;                     // ms
        double vx, vy, spin, flip;
        int dust1, dust2;                   // colours of the dust it sheds
        double dustA, dustB;                // seeds
    }

    private final int w, h;
    private final double blowX, blowY;
    private final List<Shard> shards = new ArrayList<>();
    /** Edges shared by the shards, for the cracks: x1, y1, x2, y2, distance of its middle from the blow. */
    private final List<double[]> edges = new ArrayList<>();

    DeskShatter(BufferedImage desktop, int w, int h) {
        this.w = w;
        this.h = h;
        this.blowX = w * 0.5;
        this.blowY = h * 0.46;
        Random rnd = new Random(77);
        double[][][] pt = new double[COLS + 1][ROWS + 1][2];
        double tw = w / (double) COLS, th = h / (double) ROWS;
        for (int i = 0; i <= COLS; i++) {
            for (int j = 0; j <= ROWS; j++) {
                boolean edgeX = i == 0 || i == COLS, edgeY = j == 0 || j == ROWS;
                pt[i][j][0] = i * tw + (edgeX ? 0 : (rnd.nextDouble() - 0.5) * tw * 0.75);
                pt[i][j][1] = j * th + (edgeY ? 0 : (rnd.nextDouble() - 0.5) * th * 0.75);
            }
        }
        double maxDist = Math.hypot(Math.max(blowX, w - blowX), Math.max(blowY, h - blowY));
        for (int i = 0; i < COLS; i++) {
            for (int j = 0; j < ROWS; j++) {
                double[] a = pt[i][j], b = pt[i + 1][j], c = pt[i + 1][j + 1], d = pt[i][j + 1];
                boolean flip = (i + j) % 2 == 0;
                double[][][] tris = flip ? new double[][][]{{a, b, c}, {a, c, d}} : new double[][][]{{a, b, d}, {b, c, d}};
                for (double[][] t : tris) shards.add(makeShard(desktop, t, rnd, maxDist));
                double[][] cell = {a, b, c, d};
                for (int k = 0; k < 4; k++) {
                    double[] p = cell[k], q = cell[(k + 1) % 4];
                    edges.add(new double[]{p[0], p[1], q[0], q[1], Math.hypot((p[0] + q[0]) / 2 - blowX, (p[1] + q[1]) / 2 - blowY) / maxDist});
                }
                double[] m = flip ? new double[]{a[0], a[1], c[0], c[1]} : new double[]{b[0], b[1], d[0], d[1]};
                edges.add(new double[]{m[0], m[1], m[2], m[3], Math.hypot((m[0] + m[2]) / 2 - blowX, (m[1] + m[3]) / 2 - blowY) / maxDist});
            }
        }
    }

    private Shard makeShard(BufferedImage desktop, double[][] t, Random rnd, double maxDist) {
        Shard s = new Shard();
        double minX = Math.min(t[0][0], Math.min(t[1][0], t[2][0])), maxX = Math.max(t[0][0], Math.max(t[1][0], t[2][0]));
        double minY = Math.min(t[0][1], Math.min(t[1][1], t[2][1])), maxY = Math.max(t[0][1], Math.max(t[1][1], t[2][1]));
        int x0 = (int) Math.floor(minX) - 1, y0 = (int) Math.floor(minY) - 1;
        int sw = (int) Math.ceil(maxX) - x0 + 2, sh = (int) Math.ceil(maxY) - y0 + 2;
        s.ox = x0;
        s.oy = y0;
        s.sprite = new BufferedImage(Math.max(2, sw), Math.max(2, sh), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = s.sprite.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Path2D.Double poly = new Path2D.Double();
        for (int i = 0; i < 3; i++) {
            s.px[i] = t[i][0];
            s.py[i] = t[i][1];
            if (i == 0) poly.moveTo(t[i][0] - x0, t[i][1] - y0);
            else poly.lineTo(t[i][0] - x0, t[i][1] - y0);
        }
        poly.closePath();
        g.setClip(poly);
        if (desktop != null) g.drawImage(desktop, -x0, -y0, w, h, null);
        else {
            g.setColor(new Color(40, 70, 130));
            g.fillRect(0, 0, sw, sh);
        }
        g.dispose();
        s.cx = (t[0][0] + t[1][0] + t[2][0]) / 3;
        s.cy = (t[0][1] + t[1][1] + t[2][1]) / 3;
        s.dist = Math.hypot(s.cx - blowX, s.cy - blowY) / maxDist;
        s.release = 700 + s.dist * 1150 + rnd.nextDouble() * 160;
        double ang = Math.atan2(s.cy - blowY, s.cx - blowX);
        double push = h * (0.03 + 0.12 * (1 - s.dist)) * (0.5 + rnd.nextDouble());
        s.vx = Math.cos(ang) * push + (rnd.nextDouble() - 0.5) * h * 0.06;
        s.vy = Math.sin(ang) * push * 0.4 - h * 0.05 * rnd.nextDouble();
        s.spin = (rnd.nextDouble() - 0.5) * 5;
        s.flip = 2 + rnd.nextDouble() * 4;
        s.dustA = rnd.nextDouble();
        s.dustB = rnd.nextDouble();
        if (desktop != null) {
            int sx = (int) Math.max(0, Math.min(w - 1, s.cx)), sy = (int) Math.max(0, Math.min(h - 1, s.cy));
            s.dust1 = desktop.getRGB(sx, sy);
            s.dust2 = desktop.getRGB((int) Math.max(0, Math.min(w - 1, s.cx + 8)), (int) Math.max(0, Math.min(h - 1, s.cy + 6)));
        } else {
            s.dust1 = 0xFF5078B0;
            s.dust2 = 0xFFB0C8F0;
        }
        return s;
    }

    /** Draws the desktop {@code k} ms after the blow; {@code b} is cleared to whatever lies beneath. */
    void render(Graphics2D b, double k) {
        Graphics2D g = (Graphics2D) b.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        // The blow shakes the whole pane.
        double shake = Math.max(0, 1 - k / 900.0) * h * 0.016;
        Random sh = new Random((long) (k / 28));
        g.translate((sh.nextDouble() - 0.5) * shake * 2, (sh.nextDouble() - 0.5) * shake * 2);
        AffineTransform base = g.getTransform();

        for (Shard s : shards) {
            double age = (k - s.release) / 1000.0;
            if (age < 0) {
                g.drawImage(s.sprite, (int) s.ox, (int) s.oy, null);
                continue;
            }
            // Lifts and tilts for a moment, then falls.
            double lift = Math.min(1, age / 0.14);
            double fall = Math.max(0, age - 0.10);
            double dx = s.vx * age, dy = s.vy * age - h * 0.012 * Math.sin(Math.PI * Math.min(1, age / 0.25)) + 0.5 * h * 3.0 * fall * fall;
            if (s.cy + dy > h * 1.35) continue;
            double rot = s.spin * age * (0.3 + lift);
            double squash = Math.cos(s.flip * fall);
            g.setTransform(base);
            g.translate(s.cx + dx, s.cy + dy);
            g.rotate(rot);
            g.scale(1, Math.abs(squash) < 0.12 ? 0.12 : squash);
            g.translate(-s.cx, -s.cy);
            g.drawImage(s.sprite, (int) s.ox, (int) s.oy, null);
            if (age < 0.35) {                                  // the raw edge, glowing as it breaks off
                double e = 1 - age / 0.35;
                Path2D.Double poly = new Path2D.Double();
                poly.moveTo(s.px[0], s.py[0]);
                poly.lineTo(s.px[1], s.py[1]);
                poly.lineTo(s.px[2], s.py[2]);
                poly.closePath();
                g.setStroke(new BasicStroke(2.2f));
                g.setColor(new Color(255, 255, 255, (int) (230 * e)));
                g.draw(poly);
            }
        }
        g.setTransform(base);

        // Dust and splinters shed by the shards that let go.
        for (Shard s : shards) {
            double age = (k - s.release) / 1000.0;
            if (age < 0 || age > 0.9) continue;
            for (int i = 0; i < 2; i++) {
                double seed = i == 0 ? s.dustA : s.dustB;
                double ang = seed * Math.PI * 2, sp = h * (0.05 + 0.2 * seed);
                double x = s.cx + Math.cos(ang) * sp * age + s.vx * age * 0.6;
                double y = s.cy + Math.sin(ang) * sp * age + 0.5 * h * 1.6 * age * age;
                double size = 2 + 6 * (1 - age) * (0.4 + seed);
                int rgb = i == 0 ? s.dust1 : s.dust2;
                g.setColor(new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, (int) (220 * (1 - age / 0.9))));
                g.fillRect((int) x, (int) y, (int) size, (int) size);
            }
        }

        // The cracks, running out from the blow along the edges, first bright, then dying as the pieces go.
        if (k < LAST_RELEASE_MS + 300) {
            double reach = Math.min(1.2, k / CRACK_MS * 1.2);
            double fade = 1 - smooth((k - 900) / 1100.0);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (double[] e : edges) {
                if (e[4] > reach) continue;
                double front = Math.min(1, (reach - e[4]) / 0.12);            // the crack has a moment of arriving
                double alpha = fade * front;
                if (alpha <= 0.02) continue;
                g.setColor(new Color(120, 210, 255, (int) (70 * alpha)));
                g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(e[0], e[1], e[2], e[3]));
                g.setColor(new Color(255, 255, 255, (int) (235 * alpha)));
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(e[0], e[1], e[2], e[3]));
            }
        }
        // The flash of the blow at the middle.
        if (k < 420) {
            double f = 1 - k / 420.0;
            g.setPaint(new java.awt.RadialGradientPaint((float) blowX, (float) blowY, (float) (h * (0.15 + 0.6 * (1 - f))), new float[]{0f, 1f},
                    new Color[]{new Color(255, 255, 255, (int) (230 * f)), new Color(255, 255, 255, 0)}));
            g.fillRect(0, 0, w, h);
        }
        g.dispose();
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }
}
