package com.lotusblight.overlay;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * What the first song does on the Chromo difficulty, on top of everything the ordinary one does:
 * 0:45 the screen (the circles and the bars) starts to turn; 1:00 the picture splits in two and an
 * enormous eye looks out from behind it; 1:20 three eyes open and the Glitcher spins among the field
 * of eyes; 1:43 great threads stretch across the screen and are walls for the cursor - they let it
 * through only if the left button is held while it crosses them, which cuts them - while the screen
 * turns first one way and then the other.
 *
 * Everything is a function of the song clock except the cut threads and where the cursor was, which
 * are kept here.
 */
final class ChromoSong1 {
    static final double SPIN_FROM = 45, DOUBLE_FROM = 60, EYE_UNTIL = 80, THREE_FROM = 80, THREADS_FROM = 103;
    private static final double END_S = 128;
    private static final double STEP = 0.01;

    private static final double[] SPIN = new double[(int) (END_S / STEP) + 2];
    private static final double[] GLITCHER = new double[SPIN.length];

    static {
        double a = 0, g = 0;
        for (int i = 1; i < SPIN.length; i++) {
            double s = i * STEP;
            a += omega(s) * STEP;
            g += glitcherOmega(s) * STEP;
            SPIN[i] = a;
            GLITCHER[i] = g;
        }
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** How fast the screen turns (rad/s): slowly one way from 0:45, then back and forth from 1:43. */
    private static double omega(double s) {
        double w = 0.24 * smooth((s - SPIN_FROM) / 5.0);
        if (s >= THREADS_FROM) {
            double u = s - THREADS_FROM;
            w += 0.5 * Math.sin(u * 0.85) * smooth(u / 2.0);
        }
        return w;
    }

    private static double glitcherOmega(double s) {
        return 2.4 * smooth((s - THREE_FROM) / 4.0) * (1 - smooth((s - 97) / 6.0));
    }

    private static double lookup(double[] table, double s) {
        if (s <= 0) return 0;
        double x = Math.min(table.length - 1.001, s / STEP);
        int i = (int) x;
        return table[i] + (table[i + 1] - table[i]) * (x - i);
    }

    /** The turn of the screen, in radians, at song time {@code ms}. */
    static double spin(double ms) {
        return lookup(SPIN, ms / 1000.0);
    }

    /** The turn of the Glitcher among the eyes: it speeds up from 1:20, slows down and comes to rest facing the front again by 1:43. */
    static double glitcherSpin(double ms) {
        double s = ms / 1000.0;
        double a = lookup(GLITCHER, s);
        double end = lookup(GLITCHER, 104.0);
        double target = 2 * Math.PI * Math.round(end / (2 * Math.PI));
        if (s >= 103.0) return 0;
        return a + (target - end) * smooth((s - 98.0) / 5.0);
    }

    // ------------------------------------------------------------ the threads

    /** One great thread across the screen. */
    private static final class Cord {
        double x1, y1, x2, y2;
        double spawnMs;
        double cutAt = -1, cutX, cutY;
        double pushAt = -1e9;
        long seed;

        boolean blocks(double ms) {
            return cutAt < 0 && ms >= spawnMs + GROW_MS && ms < spawnMs + LIFE_MS - 1000;
        }
    }

    private static final double GROW_MS = 600, LIFE_MS = 11_000, CUT_MS = 650;
    private static final int CORDS = 22;
    private static final double GAP_MS = 1_050;

    private final List<Cord> cords = new ArrayList<>();
    private int builtFor = -1;
    private int mx = -1, my = -1;                      // where the cursor last was (the last place it was allowed to be)
    private java.awt.image.BufferedImage buffer;

    private void build(int w, int h) {
        if (builtFor == w * 31 + h) return;
        builtFor = w * 31 + h;
        cords.clear();
        Random r = new Random(1043);
        double reach = Math.hypot(w, h);
        for (int i = 0; i < CORDS; i++) {
            Cord c = new Cord();
            double ang = r.nextDouble() * Math.PI, off = (r.nextDouble() - 0.5) * h * 0.6;
            double cx = w / 2.0 - Math.sin(ang) * off, cy = h / 2.0 + Math.cos(ang) * off;
            c.x1 = cx - Math.cos(ang) * reach;
            c.y1 = cy - Math.sin(ang) * reach;
            c.x2 = cx + Math.cos(ang) * reach;
            c.y2 = cy + Math.sin(ang) * reach;
            if (r.nextBoolean()) {
                double tx = c.x1, ty = c.y1;
                c.x1 = c.x2; c.y1 = c.y2; c.x2 = tx; c.y2 = ty;
            }
            c.spawnMs = (THREADS_FROM + 0.0) * 1000 + i * GAP_MS;
            c.seed = r.nextLong();
            cords.add(c);
        }
    }

    /**
     * The cursor moves from (px,py) to (x,y). Returns true if a thread stops it - then the caller puts
     * the cursor back. With the button held the thread is cut and the cursor goes through.
     */
    boolean blocked(int px, int py, int x, int y, boolean leftHeld, double ms, int w, int h) {
        build(w, h);
        mx = x;
        my = y;
        for (Cord c : cords) {
            if (!c.blocks(ms)) continue;
            double[] hit = crossing(px, py, x, y, c);
            if (hit == null) continue;
            if (leftHeld) {
                c.cutAt = ms;
                c.cutX = hit[0];
                c.cutY = hit[1];
            } else {
                c.pushAt = ms;
                mx = px;
                my = py;
                return true;
            }
        }
        return false;
    }

    /** Where the cursor's step crosses a thread, or null. */
    private static double[] crossing(double ax, double ay, double bx, double by, Cord c) {
        double rx = bx - ax, ry = by - ay, sx = c.x2 - c.x1, sy = c.y2 - c.y1;
        double den = rx * sy - ry * sx;
        if (Math.abs(den) < 1e-9) return null;
        double qx = c.x1 - ax, qy = c.y1 - ay;
        double t = (qx * sy - qy * sx) / den, u = (qx * ry - qy * rx) / den;
        if (t < 0 || t > 1 || u < 0 || u > 1) return null;
        return new double[]{ax + rx * t, ay + ry * t};
    }

    // ------------------------------------------------------------ drawing

    /** The doubling of the picture from 1:00, 0..1. */
    static double doubling(double ms) {
        double s = ms / 1000.0;
        double d = smooth((s - DOUBLE_FROM) / 6.0);
        return d * (s >= THREADS_FROM ? 0.75 : 1);
    }

    /**
     * The whole picture of the song: the back layer and the front are drawn once, as ever; from 1:00 the playfield
     * (circles and bars) is drawn a second time, a ghost slipping aside, so the screen seems doubled. The threads go
     * over it, undoubled, where they really are.
     */
    void render(Graphics2D g, int w, int h, double ms, java.util.function.Consumer<Graphics2D> back,
                java.util.function.Consumer<Graphics2D> playfield, java.util.function.Consumer<Graphics2D> front) {
        double s = ms / 1000.0;
        double d = doubling(ms);
        back.accept(g);
        if (d > 0.01) {
            if (buffer == null || buffer.getWidth() != w || buffer.getHeight() != h) {
                buffer = g.getDeviceConfiguration().createCompatibleImage(w, h, java.awt.Transparency.TRANSLUCENT);
            }
            Graphics2D b = buffer.createGraphics();
            b.setComposite(AlphaComposite.Clear);
            b.fillRect(0, 0, w, h);
            b.setComposite(AlphaComposite.SrcOver);
            playfield.accept(b);
            b.dispose();
            double dx = w * 0.04 * d * Math.cos(s * 0.9), dy = h * 0.022 * d * Math.sin(s * 1.3);
            java.awt.Composite old = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (0.45 * d)));
            g.drawImage(buffer, (int) dx, (int) dy, null);
            g.setComposite(old);
            g.drawImage(buffer, 0, 0, null);
        } else {
            playfield.accept(g);
        }
        front.accept(g);
        drawCords(g, w, h, ms);
    }

    /** The eyes between the back layer and the circles: the enormous one from 1:00, three from 1:20. */
    void renderEyes(Graphics2D g, int w, int h, double ms) {
        double s = ms / 1000.0;
        if (s < DOUBLE_FROM || s > THREADS_FROM + 2) return;
        Graphics2D e = (Graphics2D) g.create();
        e.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        e.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        double big = smooth((s - DOUBLE_FROM) / 3.0) * (1 - smooth((s - EYE_UNTIL) / 1.5));
        if (big > 0.01) {
            double ew = Math.min(w * 0.8, h * 1.5);
            drawEye(e, w, h, w / 2.0, h * 0.47, ew, s, big * 0.9, 0);
        }
        double gone = 1 - smooth((s - THREADS_FROM) / 1.5);
        double[][] spots = {{0.2, 0.27}, {0.8, 0.27}, {0.5, 0.82}};
        for (int i = 0; i < spots.length; i++) {
            double a = smooth((s - THREE_FROM - i * 0.9) / 1.2) * gone;
            if (a > 0.01) drawEye(e, w, h, w * spots[i][0], h * spots[i][1], w * 0.27, s, a * 0.95, i + 1);
        }
        e.dispose();
    }

    /** The shape of the almond for an eye of this width (open), around (cx, cy). */
    private static Path2D.Double almond(double cx, double cy, double rx, double ry) {
        Path2D.Double almond = new Path2D.Double();
        almond.moveTo(cx - rx, cy);
        almond.curveTo(cx - rx * 0.55, cy - ry * 1.55, cx + rx * 0.55, cy - ry * 1.55, cx + rx, cy);
        almond.curveTo(cx + rx * 0.55, cy + ry * 1.45, cx - rx * 0.55, cy + ry * 1.45, cx - rx, cy);
        almond.closePath();
        return almond;
    }

    private final java.util.Map<Integer, BufferedImage> eyeBodies = new java.util.HashMap<>();

    /** The shaded inside and the outlines of an open eye, drawn once per size: pink and olive ghosts, then white. */
    private BufferedImage eyeBody(int width) {
        return eyeBodies.computeIfAbsent(width, wd -> {
            double rx = wd / 2.0, ry = wd * 0.27;
            int pad = (int) (wd * 0.05) + 8;
            int iw = wd + pad * 2, ih = (int) (ry * 3.4) + pad * 2;
            BufferedImage img = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
            Graphics2D e = img.createGraphics();
            e.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double cx = iw / 2.0, cy = ih / 2.0;
            Path2D.Double almond = almond(cx, cy, rx, ry);
            e.setPaint(new java.awt.RadialGradientPaint((float) cx, (float) cy, (float) rx,
                    new float[]{0f, 0.6f, 1f}, new Color[]{new Color(14, 6, 22, 235), new Color(6, 2, 12, 245), new Color(0, 0, 0, 255)}));
            e.fill(almond);
            float lw = (float) Math.max(2.5, wd * 0.011);
            e.setStroke(new BasicStroke(lw * 2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            e.setColor(new Color(0xB0, 0x50, 0x90, 60));
            e.draw(almond);
            e.setStroke(new BasicStroke(lw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double off = lw * 0.9;
            e.translate(off, off * 0.6);
            e.setColor(new Color(0xB0, 0x50, 0x90, 190));
            e.draw(almond);
            e.translate(-2 * off, 0);
            e.setColor(new Color(0x90, 0x90, 0x30, 190));
            e.draw(almond);
            e.translate(off, -off * 0.6);
            e.setColor(new Color(255, 255, 255, 245));
            e.draw(almond);
            e.dispose();
            return img;
        });
    }

    /**
     * An eye as on the first song's circles: a dark almond with pink, olive and white outlines, a white pupil with an
     * olive dot, drawn at its own size so it stays sharp however large it is. The body is kept; the pupil, which looks at
     * the cursor (the big one) or at the middle of the screen, is drawn live, and a blink closes the lids by squeezing the body.
     */
    private void drawEye(Graphics2D g, int w, int h, double cx, double cy, double width, double s, double alpha, int who) {
        double blink = 1 - 0.95 * Math.max(0, 1 - Math.abs(((s + who * 1.7) % 6.1) - 3.05) * 6);
        int wd = (int) (Math.round(width / 16.0) * 16);
        BufferedImage body = eyeBody(wd);
        double sc = width / wd;
        if (blink >= 0.98 && Math.abs(sc - 1) < 0.02) {
            // The common case: the kept picture put down as it is, no scaling.
            java.awt.Composite old = g.getComposite();
            if (alpha < 0.99) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, alpha)));
            g.drawImage(body, (int) (cx - body.getWidth() / 2.0), (int) (cy - body.getHeight() / 2.0), null);
            g.setComposite(old);
        } else {
            Graphics2D e = (Graphics2D) g.create();
            e.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, alpha))));
            e.translate(cx, cy);
            e.scale(sc, sc * (blink < 0.98 ? blink : 1));
            e.drawImage(body, -body.getWidth() / 2, -body.getHeight() / 2, null);
            e.dispose();
        }

        double rx = width / 2, ry = width * 0.27 * blink;
        double tx = who == 0 ? (mx < 0 ? cx : mx) : w / 2.0, ty = who == 0 ? (mx < 0 ? cy : my) : h * 0.5;
        double dx = tx - cx, dy = ty - cy, len = Math.max(1, Math.hypot(dx, dy));
        double reach = Math.min(rx * 0.3, len * 0.25);
        double px = cx + dx / len * reach, py = cy + dy / len * reach * 0.45;
        double pr = width * 0.27 * 0.62 * Math.min(1, blink * 1.5);
        Graphics2D p = (Graphics2D) g.create();
        p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        p.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, alpha))));
        p.setColor(new Color(255, 255, 255, 60));
        p.fill(new Ellipse2D.Double(px - pr * 1.35, py - pr * 1.35, pr * 2.7, pr * 2.7));
        p.setColor(new Color(255, 255, 255, 248));
        p.fill(new Ellipse2D.Double(px - pr, py - pr, pr * 2, pr * 2));
        double dot = pr * 0.34;
        p.setColor(new Color(0x90, 0x90, 0x30));
        p.fill(new Ellipse2D.Double(px - dot + dx / len * pr * 0.2, py - dot + dy / len * pr * 0.2, dot * 2, dot * 2));
        p.dispose();
    }

    private void drawCords(Graphics2D g, int w, int h, double ms) {
        build(w, h);
        Graphics2D c = (Graphics2D) g.create();
        c.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double t = ms / 1000.0;
        for (Cord cord : cords) {
            double age = ms - cord.spawnMs;
            if (age < 0 || age > LIFE_MS) continue;
            double fade = 1 - smooth((age - (LIFE_MS - 1000)) / 1000.0);
            if (cord.cutAt < 0) {
                drawCord(c, cord, t, h, smooth(age / GROW_MS), 0, 1, fade, ms);
            } else {
                double u = (ms - cord.cutAt) / CUT_MS;
                if (u > 1) continue;
                // Both halves flick back to where they come from and fade, with a few sparks at the cut.
                double cutAlong = alongOf(cord, cord.cutX, cord.cutY);
                double back = 1 - smooth(u);
                drawCord(c, cord, t, h, 1, 0, cutAlong * back, fade * (1 - u), ms);
                drawCord(c, cord, t, h, 1, 1 - (1 - cutAlong) * back, 1, fade * (1 - u), ms);
                Random r = new Random(cord.seed);
                for (int i = 0; i < 14; i++) {
                    double a = r.nextDouble() * Math.PI * 2, d = (0.2 + r.nextDouble()) * h * 0.09 * u;
                    double sz = h * 0.007 * (1 - u);
                    c.setColor(new Color(255, i % 2 == 0 ? 255 : 120, 220, (int) (230 * (1 - u))));
                    c.fill(new Ellipse2D.Double(cord.cutX + Math.cos(a) * d - sz, cord.cutY + Math.sin(a) * d - sz, sz * 2, sz * 2));
                }
            }
        }
        c.dispose();
    }

    /** The stretch (u0, u1) of a thread, 0..1 along it, that lies on the screen, with a margin; null if none does. */
    private static double[] visible(Cord c, int w, int h) {
        double m = 60, u0 = 0, u1 = 1;
        double[] p = {-(c.x2 - c.x1), c.x2 - c.x1, -(c.y2 - c.y1), c.y2 - c.y1};
        double[] q = {c.x1 + m, w + m - c.x1, c.y1 + m, h + m - c.y1};
        for (int i = 0; i < 4; i++) {
            if (Math.abs(p[i]) < 1e-9) {
                if (q[i] < 0) return null;
            } else {
                double r = q[i] / p[i];
                if (p[i] < 0) u0 = Math.max(u0, r);
                else u1 = Math.min(u1, r);
            }
        }
        return u0 < u1 ? new double[]{u0, u1} : null;
    }

    private static double alongOf(Cord c, double x, double y) {
        double sx = c.x2 - c.x1, sy = c.y2 - c.y1;
        return Math.max(0, Math.min(1, ((x - c.x1) * sx + (y - c.y1) * sy) / (sx * sx + sy * sy)));
    }

    /**
     * Draws the part of a thread between {@code from} and {@code to} (0..1 along it), grown to {@code grow}: a wavering
     * cord with pink, olive and white outlines round a dark core, shivering when the cursor pushes at it.
     */
    private void drawCord(Graphics2D g, Cord c, double t, int h, double grow, double from, double to, double alpha, double ms) {
        double end = Math.min(to, from + (to - from) * grow);
        if (end <= from || alpha <= 0.01) return;
        // Only the part on the screen is worth stroking.
        double[] vis = visible(c, g.getDeviceConfiguration().getBounds().width, g.getDeviceConfiguration().getBounds().height);
        if (vis == null) return;
        from = Math.max(from, vis[0]);
        end = Math.min(end, vis[1]);
        if (end <= from) return;
        double sx = c.x2 - c.x1, sy = c.y2 - c.y1, len = Math.hypot(sx, sy);
        double nx = -sy / len, ny = sx / len;
        double shiver = Math.exp(-(ms - c.pushAt) / 260.0);
        double amp = h * (0.006 + 0.018 * shiver);
        int n = 36;
        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i <= n; i++) {
            double u = from + (end - from) * i / n;
            double wave = Math.sin(u * 38 + t * 5 + (c.seed % 7)) * amp * (0.5 + 0.5 * Math.sin(u * 9 - t * 2));
            double x = c.x1 + sx * u + nx * wave, y = c.y1 + sy * u + ny * wave;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        float wd = (float) (h * 0.02);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.min(1, alpha)));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        // One ghost, pink on one side and olive on the other (two strokes cost too much at this length).
        g.translate(-3, 1);
        g.setStroke(new BasicStroke(wd * 1.3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0xA8, 0x6A, 0x78, 175));
        g.draw(path);
        g.translate(3, -1);
        g.setStroke(new BasicStroke(wd, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(24, 6, 34));
        g.draw(path);
        g.setStroke(new BasicStroke(wd * 0.25f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 255, 255, 220));
        g.draw(path);
        g.setComposite(AlphaComposite.SrcOver);
    }
}
