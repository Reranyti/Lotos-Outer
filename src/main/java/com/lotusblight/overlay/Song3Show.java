package com.lotusblight.overlay;

import java.awt.AlphaComposite;
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
import java.util.function.BiConsumer;

/**
 * What happens around the third song's fight, by the clock: the world spins (0:41 - 0:58), the Glitcher's
 * eyes watch from the sides (1:08), a grinning thing holds the screen (1:34), Honcho sees a tunnel of eyes
 * through his own eyes, shaking with fear, and falls into one of them (1:47), the fight goes on, and from
 * 2:25 the Glitcher tears the picture up.
 */
final class Song3Show {
    static final double SPIN_FROM = 41, SPIN_BACK = 49, SPIN_END = 58;
    static final double EYES_FROM = 68, EYES_TO = 94;
    static final double FACE_FROM = 94, FACE_TO = 107;
    static final double TUNNEL_FROM = 107, TUNNEL_ZOOM = 119, TUNNEL_TO = 122.6, WORLD_BACK = 122.6;
    static final double GLITCH_FROM = 145;

    private BufferedImage faceCache;
    private int cacheW, cacheH;
    private static final double FIG_RES = 0.6;
    private Figure figureRig;
    private SoftRenderer figure;
    private double figureUnit;
    private int[] glitcherSkin;

    /** The first song's eye with its pupil painted in, so an eye is a single stamp. */
    private static BufferedImage withPupil(BufferedImage src) {
        BufferedImage img = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, null);
        int cx = src.getWidth() / 2, cy = src.getHeight() / 2;
        g.setColor(new Color(255, 255, 255, 235));
        g.fillOval(cx - 11, cy + 6 - 11, 22, 22);
        g.setColor(new Color(0x9A, 0x90, 0x30, 200));
        g.fillOval(cx - 13, cy + 4 - 4, 10, 10);
        g.dispose();
        return img;
    }

    /** All the rows of the tunnel of eyes, packed tight and slightly different from each other, on one picture. */
    private void buildTunnel(int w, int h) {
        tunnelW = w;
        tunnelH = h;
        int tw = (int) (w * 1.15), th = (int) (h * 1.15);
        tunnel = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        Graphics2D d = tunnel.createGraphics();
        d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double vx = tw / 2.0, vy = th / 2.0;
        d.setColor(Color.BLACK);
        d.fillRect(0, 0, tw, th);
        for (int i = 0; i < RINGS; i++) {
            double r = h * 0.03 * Math.pow(RING_GROW, i);
            double fade = Math.max(0, Math.min(1, i * 0.5));
            double perimeter = Math.PI * 2 * r * 1.55;
            double sc = perimeter / PER_RING * 1.4 / eyeSprite.getWidth();
            d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) fade));
            int par = i % 2;                                                    // the look of a row repeats every two rows
            for (int k = 0; k < PER_RING; k++) {
                double ang = (k + par * 0.5) * Math.PI * 2 / PER_RING + Math.sin(par * 7.3 + k * 3.1) * 0.05;
                double ex = vx + Math.cos(ang) * r * 1.8, ey = vy + Math.sin(ang) * r * 1.3;
                double jit = 0.9 + 0.25 * Math.abs(Math.sin(par * 5.1 + k * 2.3));
                AffineTransform old = d.getTransform();
                d.translate(ex, ey);
                d.rotate(Math.atan2(1.3 * Math.cos(ang), -1.8 * Math.sin(ang)) + Math.sin(k * 1.9 + par) * 0.1);
                d.scale(sc * jit, sc * jit);
                d.drawImage(eyeSprite, -eyeSprite.getWidth() / 2, -eyeSprite.getHeight() / 2, null);
                d.setTransform(old);
            }
        }
        d.setComposite(AlphaComposite.SrcOver);
        d.setPaint(new RadialGradientPaint((float) vx, (float) vy, (float) (Math.hypot(w, h) * 0.6), new float[]{0.5f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 200)}));
        d.fillRect(0, 0, tw, th);                                                // the vignette of a stare that will not blink
        d.dispose();
    }

    void setGlitcher(int[] skin) { this.glitcherSkin = skin; }
    private static final int RINGS = 27, PER_RING = 30;
    private static final double RING_GROW = 1.13;
    private final BufferedImage eyeSprite = withPupil(Hazards.makeEyeSprite());
    private BufferedImage doorway, tunnel;
    private int tunnelW, tunnelH;
    private int doorwayH;
    // Eyes as in the first song: position round the middle (angle, radius), size, phase; sprites are made once.
    private final double[][] watchers = new double[16][6];
    private BufferedImage[] watcherOpen, watcherBlink;
    private int watcherH;

    Song3Show() {
        Random r = new Random(1977);
        for (int i = 0; i < watchers.length; i++) {
            double[] e = watchers[i];
            e[0] = r.nextDouble() * Math.PI * 2;
            e[1] = 0.3 + 0.2 * r.nextDouble();
            e[2] = 0.045 + 0.07 * r.nextDouble();
            e[3] = r.nextDouble() * 7;
            e[4] = r.nextDouble() < 0.5 ? 0 : 1;
        }
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** The turn of the world, in radians: once round to 0:49, and back again by 0:58. */
    static double spin(double s) {
        if (s <= SPIN_FROM || s >= SPIN_END) return 0;
        if (s < SPIN_BACK) return Math.PI * 2 * smooth((s - SPIN_FROM) / (SPIN_BACK - SPIN_FROM));
        return Math.PI * 2 * (1 - smooth((s - SPIN_BACK) / (SPIN_END - SPIN_BACK)));
    }

    /**
     * While the thing holds the screen (1:34 - 1:47) the game shrinks into a small screen in its hands:
     * {x, y, width, height}, or null when the game has the whole display.
     */
    static double[] screenRect(double ms, int w, int h) {
        double s = ms / 1000.0;
        if (s < FACE_FROM || s >= FACE_TO) return null;
        double k = smooth((s - FACE_FROM) / 2.6);
        double mw = w * 0.44, mh = h * 0.44, mx = (w - mw) / 2, my = h * 0.68 - mh / 2;
        return new double[]{mx * k, my * k, w + (mw - w) * k, h + (mh - h) * k};
    }

    /** Draws the whole background of the fight; {@code world} is the falling sky with the two fighters. */
    void render(Graphics2D g, int w, int h, double ms, BiConsumer<Graphics2D, Graphics2D> world) {
        double s = ms / 1000.0;
        double glitch = smooth((s - GLITCH_FROM) / 25.0);
        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // How much of the falling world is showing (it stays, in the little screen, while it is held).
        double wa = 1;
        if (s >= FACE_TO && s < WORLD_BACK) wa = 0;
        else if (s >= WORLD_BACK && s < WORLD_BACK + 2) wa = smooth((s - WORLD_BACK) / 2);
        if (tunnel == null && s > TUNNEL_FROM - 5 && s < TUNNEL_TO) buildTunnel(w, h);
        double[] rect = screenRect(ms, w, h);
        double faceA = 0;
        if (s >= FACE_FROM && s < FACE_TO) {
            faceA = smooth((s - FACE_FROM) / 2.6);
            drawFace(b, w, h, s, faceA);                                          // behind the game
        }
        if (wa > 0.003) {
            Graphics2D wg = (Graphics2D) b.create();
            double a = spin(s);
            if (a != 0) {
                double ratio = Math.max(w / (double) h, h / (double) w);
                double cover = Math.abs(Math.cos(a)) + ratio * Math.abs(Math.sin(a));
                wg.translate(w / 2.0, h / 2.0);
                wg.rotate(a);
                wg.scale(cover, cover);
                wg.translate(-w / 2.0, -h / 2.0);
            }
            if (rect != null) {                                                   // the screen draws away, into the hands
                wg.translate(rect[0], rect[1]);
                wg.scale(rect[2] / w, rect[3] / h);
                wg.clipRect(0, 0, w, h);
            }
            wg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) wa));
            // The dark sky is the same turned any way, so it is not turned (a turned full-screen picture is the costly part).
            Graphics2D plain = a != 0 && rect == null ? (Graphics2D) b.create() : null;
            if (plain != null) plain.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) wa));
            world.accept(wg, plain != null ? plain : wg);
            if (plain != null) plain.dispose();
            wg.dispose();
            if (s >= EYES_FROM && s < EYES_TO + 1) drawWatchers(b, w, h, s, wa);
        }
        if (rect != null) drawHands(b, w, h, s, faceA, rect);
        if (s >= TUNNEL_FROM && s < TUNNEL_TO) drawTunnel(b, w, h, s);

        b.dispose();
        if (glitch > 0) drawTorn(g, w, h, ms, glitch);
    }

    // ------------------------------------------------------------ eyes at the sides

    private void drawWatchers(Graphics2D g, int w, int h, double s, double worldAlpha) {
        if (watcherOpen == null || watcherH != h) buildWatchers(w, h);
        for (int i = 0; i < watchers.length; i++) {
            double[] e = watchers[i];
            double a = smooth((s - (EYES_FROM + i * 0.75)) / 0.6) * (1 - smooth((s - (EYES_TO - 1.2)) / 1.2));
            if (a <= 0.01) continue;
            double ex = w / 2.0 + Math.cos(e[0]) * w * e[1], ey = h / 2.0 + Math.sin(e[0]) * h * e[1] * 1.05;
            boolean blinking = (s * 0.4 + e[3]) % 1.0 < 0.05;
            BufferedImage img = blinking ? watcherBlink[i] : watcherOpen[i];
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) a));
            g.drawImage(img, (int) (ex - img.getWidth() / 2.0), (int) (ey - img.getHeight() / 2.0), null);
        }
        g.setComposite(AlphaComposite.SrcOver);
    }

    private void buildWatchers(int w, int h) {
        watcherH = h;
        watcherOpen = new BufferedImage[watchers.length];
        watcherBlink = new BufferedImage[watchers.length];
        for (int i = 0; i < watchers.length; i++) {
            double[] e = watchers[i];
            double size = h * e[2];
            double ex = w / 2.0 + Math.cos(e[0]) * w * e[1], ey = h / 2.0 + Math.sin(e[0]) * h * e[1] * 1.05;
            Color iris = new Color(20, 12, 34);
            watcherOpen[i] = eyeImage(size, false, w * 0.82 - ex, h * 0.72 - ey, iris);
            watcherBlink[i] = eyeImage(size, true, 0, 0, iris);
        }
    }

    /** An eye drawn just as in the first song - pale almond, dark iris, pink and cyan ghosts - in the colour of the iris given. */
    private static BufferedImage eyeImage(double size, boolean blinking, double lookX, double lookY, Color iris) {
        double hw = size * 1.1, hh = size * (blinking ? 0.08 : 0.5);
        int pad = (int) (size * 0.35) + 4;
        int iw = (int) (hw * 2) + pad * 2, ih = (int) (size * 1.0 * 1.7) + pad * 2;
        BufferedImage img = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
        Graphics2D b = img.createGraphics();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double ex = iw / 2.0, ey = ih / 2.0;
        Path2D.Double almond = new Path2D.Double();
        almond.moveTo(ex - hw, ey);
        almond.quadTo(ex, ey - hh * 1.7, ex + hw, ey);
        almond.quadTo(ex, ey + hh * 1.7, ex - hw, ey);
        almond.closePath();
        b.setColor(new Color(246, 240, 246, 232));
        b.fill(almond);
        if (!blinking) {
            double len = Math.max(1, Math.hypot(lookX, lookY));
            double ix = ex + lookX / len * hw * 0.28, iy = ey + lookY / len * hh * 0.25, ir = Math.min(hh * 0.95, hw * 0.4);
            java.awt.Shape old = b.getClip();
            b.clip(almond);
            b.setColor(iris);
            b.fill(new Ellipse2D.Double(ix - ir, iy - ir, ir * 2, ir * 2));
            b.setColor(new Color(20, 8, 30));
            b.fill(new Ellipse2D.Double(ix - ir * 0.55, iy - ir * 0.55, ir * 1.1, ir * 1.1));
            b.setColor(new Color(255, 255, 255, 230));
            b.fill(new Ellipse2D.Double(ix - ir * 0.5, iy - ir * 0.55, ir * 0.35, ir * 0.35));
            b.setClip(old);
        }
        b.setStroke(new BasicStroke((float) (size * 0.09)));
        b.translate(size * 0.04, size * 0.02);
        b.setColor(new Color(255, 70, 190, 140));
        b.draw(almond);
        b.translate(-size * 0.08, -size * 0.04);
        b.setColor(new Color(60, 230, 255, 140));
        b.draw(almond);
        b.translate(size * 0.04, size * 0.02);
        b.setColor(new Color(12, 6, 24, 240));
        b.draw(almond);
        b.dispose();
        return img;
    }

    // ------------------------------------------------------------ the thing that holds the screen

    private void drawFace(Graphics2D g, int w, int h, double s, double a) {
        if (faceCache == null || cacheW != w || cacheH != h) {
            cacheW = w;
            cacheH = h;
            faceCache = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D c = faceCache.createGraphics();
            c.setColor(Color.BLACK);
            c.fillRect(0, 0, w, h);
            c.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintFace(c, w, h);
            c.dispose();
        }
        float al = (float) Math.max(0, Math.min(1, a));
        Graphics2D d = (Graphics2D) g.create();
        if (al < 0.999f) d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, al));
        d.drawImage(faceCache, 0, 0, null);
        // The pupils drift about, never quite still.
        double S = h * 0.86, cx = w / 2.0, cy = h * 0.42;
        for (int side = -1; side <= 1; side += 2) {
            double ex = cx + side * 0.21 * S, ey = cy - 0.17 * S, er = 0.17 * S;
            double px = ex + Math.sin(s * 0.7 + side) * er * 0.06, py = ey + 0.05 * er + Math.cos(s * 0.5) * er * 0.05;
            double pr = Math.max(2.5, S * 0.006);
            d.setColor(new Color(255, 250, 235, 70));
            d.fill(new Ellipse2D.Double(px - pr * 3, py - pr * 3, pr * 6, pr * 6));
            d.setColor(new Color(255, 252, 240));
            d.fill(new Ellipse2D.Double(px - pr, py - pr, pr * 2, pr * 2));
        }
        d.dispose();

    }

    /** The frame of the little screen and the hands that hold it, twitching. */
    private void drawHands(Graphics2D g, int w, int h, double s, double a, double[] r) {
        Graphics2D f = (Graphics2D) g.create();
        f.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        f.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, a))));
        double rx = r[0], ry = r[1], rw = r[2], rh = r[3];
        f.setStroke(new BasicStroke((float) (h * 0.016), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        f.setColor(new Color(10, 8, 8));
        f.draw(new java.awt.geom.RoundRectangle2D.Double(rx - h * 0.008, ry - h * 0.008, rw + h * 0.016, rh + h * 0.016, h * 0.02, h * 0.02));
        f.setStroke(new BasicStroke(1.6f));
        f.setColor(new Color(90, 74, 68, 190));
        f.draw(new java.awt.geom.RoundRectangle2D.Double(rx - h * 0.016, ry - h * 0.016, rw + h * 0.032, rh + h * 0.032, h * 0.024, h * 0.024));
        for (int side = -1; side <= 1; side += 2) {
            double palmX = side < 0 ? rx - w * 0.115 : rx + rw + w * 0.115, palmY = ry + rh * 0.62;
            double pw = w * 0.085, ph = h * 0.34;
            f.setColor(new Color(6, 5, 5));
            f.fill(new Ellipse2D.Double(palmX - pw / 2, palmY - ph / 2, pw, ph));
            f.setColor(new Color(75, 60, 55, 170));
            f.setStroke(new BasicStroke(1.6f));
            f.draw(new Ellipse2D.Double(palmX - pw / 2, palmY - ph / 2, pw, ph));
            for (int k = 0; k < 4; k++) {
                double fy = ry + rh * (0.16 + 0.24 * k);
                double curl = h * 0.02 * Math.sin(s * 1.7 + k * 1.3 + side);
                double tipX = side < 0 ? rx + w * 0.022 : rx + rw - w * 0.022;
                fingerAt(f, palmX, palmY - ph * 0.3 + k * ph * 0.2, tipX, fy + curl, h);
            }
            fingerAt(f, palmX, palmY + ph * 0.42, side < 0 ? rx + w * 0.03 : rx + rw - w * 0.03, ry + rh + h * 0.012, h);   // the thumb below
        }
        f.dispose();
    }

    /** The face without its pupils, painted once. */
    private static void paintFace(Graphics2D d, int w, int h) {
        double S = h * 0.86, cx = w / 2.0, cy = h * 0.42;
        Random rnd = new Random(99);
        for (int side = -1; side <= 1; side += 2) {
            double ex = cx + side * 0.21 * S, ey = cy - 0.17 * S, er = 0.17 * S;
            d.setPaint(new RadialGradientPaint((float) ex, (float) ey, (float) (er * 1.5), new float[]{0f, 0.7f, 1f},
                    new Color[]{new Color(0, 0, 0, 255), new Color(14, 10, 9, 255), new Color(0, 0, 0, 0)}));
            d.fill(new Ellipse2D.Double(ex - er * 1.5, ey - er * 1.5, er * 3, er * 3));
            for (int k = 0; k < 120; k++) {
                double ang = rnd.nextDouble() * Math.PI * 2;
                double r0 = er * (0.86 + 0.5 * rnd.nextDouble()), len = er * (0.08 + 0.2 * rnd.nextDouble());
                d.setColor(new Color(70 + rnd.nextInt(40), 55 + rnd.nextInt(30), 48 + rnd.nextInt(25), 40 + rnd.nextInt(60)));
                d.setStroke(new BasicStroke(1f + rnd.nextInt(2)));
                d.draw(new java.awt.geom.Line2D.Double(ex + Math.cos(ang) * r0, ey + Math.sin(ang) * r0 * 0.95,
                        ex + Math.cos(ang) * (r0 + len), ey + Math.sin(ang) * (r0 + len) * 0.95));
            }
        }
        for (int i = 0; i < 520; i++) {
            double u = -1 + 2 * (i + rnd.nextDouble()) / 520.0;
            double edge = 1 - u * u;
            double top = cy - 0.02 * S + 0.2 * S * Math.pow(edge, 0.9);
            double bottom = cy + 0.05 * S + 0.4 * S * Math.pow(edge, 0.7) + rnd.nextDouble() * 0.03 * S;
            double x = cx + u * 0.48 * S;
            double light = 0.35 + 0.65 * Math.pow(edge, 0.6);
            int c = (int) ((45 + 70 * rnd.nextDouble()) * light);
            d.setStroke(new BasicStroke((float) (1 + 2.4 * rnd.nextDouble())));
            d.setPaint(new GradientPaint((float) x, (float) top, new Color(c + 8, c + 4, c, (int) (55 + 110 * light)),
                    (float) x, (float) bottom, new Color(c / 3, c / 3, c / 3, 0)));
            d.draw(new java.awt.geom.Line2D.Double(x + (rnd.nextDouble() - 0.5) * 3, top, x + (rnd.nextDouble() - 0.5) * 6, bottom));
        }
        d.setPaint(new RadialGradientPaint((float) cx, (float) cy, (float) (S * 0.75), new float[]{0.5f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 230)}));
        d.fillRect(0, 0, w, h);
    }

    /** One long dark finger from (x0,y0) at the edge, curling to a tip at (x1,y1). */
    private static void fingerAt(Graphics2D g, double x0, double y0, double x1, double y1, int h) {
        int seg = 12;
        double thick0 = h * 0.028, thick1 = h * 0.012;
        Path2D.Double body = new Path2D.Double();
        double[] px = new double[seg + 1], py = new double[seg + 1], pr = new double[seg + 1];
        for (int i = 0; i <= seg; i++) {
            double f = i / (double) seg;
            px[i] = x0 + (x1 - x0) * f;
            py[i] = y0 + (y1 - y0) * f + Math.sin(f * Math.PI) * -h * 0.02;
            pr[i] = thick0 + (thick1 - thick0) * f;
        }
        body.moveTo(px[0], py[0] - pr[0]);
        for (int i = 1; i <= seg; i++) body.lineTo(px[i], py[i] - pr[i]);
        for (int i = seg; i >= 0; i--) body.lineTo(px[i], py[i] + pr[i]);
        body.closePath();
        g.setColor(new Color(6, 5, 5));
        g.fill(body);
        g.setColor(new Color(75, 60, 55, 170));
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(body);
        for (int i = 3; i < seg; i += 4) {                                   // knuckles
            g.setColor(new Color(60, 48, 44, 140));
            g.draw(new java.awt.geom.Line2D.Double(px[i], py[i] - pr[i], px[i], py[i] + pr[i]));
        }
    }

    // ------------------------------------------------------------ Honcho's eyes: the tunnel

    private void drawTunnel(Graphics2D g, int w, int h, double s) {
        double u = s - TUNNEL_FROM;
        Graphics2D d = (Graphics2D) g.create();
        double alphaIn = smooth(u / 0.35);
        if (alphaIn < 0.999) {                                        // only the first moments blend into what was there
            d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alphaIn));
            d.setColor(Color.BLACK);
            d.fillRect(0, 0, w, h);
        }
        Random sr = new Random((long) (s * 24));
        double fear = smooth(u / 9.0);
        double heart = Math.pow(Math.max(0, Math.sin(s * 5.2)), 6);
        double sx = (sr.nextDouble() - 0.5) * h * 0.02 * fear, sy = (sr.nextDouble() - 0.5) * h * 0.02 * fear;
        double vx = w / 2.0, vy = h * 0.5;
        double zoomK = s < TUNNEL_ZOOM ? 0 : (s - TUNNEL_ZOOM) / (TUNNEL_TO - TUNNEL_ZOOM);
        double zoom = Math.exp(Math.log(90) * zoomK * zoomK);
        d.translate(vx + sx, vy + sy);
        d.rotate((sr.nextDouble() - 0.5) * 0.02 * fear + Math.sin(s * 0.8) * 0.02);
        double pulse = 1 + 0.018 * heart * (1 + 2 * fear);
        d.scale(zoom * pulse, zoom * pulse);
        d.translate(-vx, -vy);

        // The tunnel from the reference: arches of eyes packed tight, each row a little bigger than the one
        // beyond it, all looking out of the wall at us, and the light of a doorway at the far end.
        // The tunnel is painted once, all its rows; moving forward is just the picture growing, and after
        // two rows it is the same picture again.
        if (tunnel == null || tunnelW != w || tunnelH != h) buildTunnel(w, h);
        double q = (u * 0.7) % 2.0;
        double grow = Math.pow(RING_GROW, q);
        AffineTransform keep = d.getTransform();
        d.translate(vx, vy);
        d.scale(grow, grow);
        if (alphaIn < 0.999) d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alphaIn));
        d.drawImage(tunnel, -tunnel.getWidth() / 2, -tunnel.getHeight() / 2, null);
        d.setTransform(keep);
        // The doorway at the end.
        if (doorway == null || doorwayH != h) {
            doorwayH = h;
            doorway = new BufferedImage((int) (h * 0.7), (int) (h * 0.7), BufferedImage.TYPE_INT_ARGB);
            Graphics2D c = doorway.createGraphics();
            c.setPaint(new RadialGradientPaint(doorway.getWidth() / 2f, doorway.getHeight() / 2f, doorway.getWidth() / 2f, new float[]{0f, 0.5f, 1f},
                    new Color[]{new Color(255, 255, 255, 235), new Color(230, 230, 240, 120), new Color(200, 200, 220, 0)}));
            c.fillRect(0, 0, doorway.getWidth(), doorway.getHeight());
            c.dispose();
        }
        d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (alphaIn * 0.85)));
        d.drawImage(doorway, (int) (vx - doorway.getWidth() * 0.5 * 0.55), (int) (vy - doorway.getHeight() * 0.5 * 0.55),
                (int) (doorway.getWidth() * 0.55), (int) (doorway.getHeight() * 0.55), null);
        d.dispose();

        // At the far end of the tunnel stands the Glitcher, facing us, still.
        if (glitcherSkin != null) {
            double unit = h * 0.36 / 32.0 * FIG_RES;
            if (figure == null || figureUnit != unit) {
                figureUnit = unit;
                figure = new SoftRenderer((int) (30 * unit), (int) (36 * unit));
            }
            SoftRenderer.Pose pose = new SoftRenderer.Pose();
            pose.reset();
            pose.partPitch[SkinModel.Part.RIGHT_ARM.ordinal()] = 0.05 * Math.sin(s * 1.1);
            pose.partPitch[SkinModel.Part.LEFT_ARM.ordinal()] = -0.05 * Math.sin(s * 1.1);
            figure.clear();
            if (figureRig == null) figureRig = new Figure(glitcherSkin, false);
            figureRig.draw(figure, pose, unit, figure.width / 2.0, figure.height - 2 * unit);
            Graphics2D f = (Graphics2D) g.create();
            f.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            Random fr = new Random((long) (s * 12));
            float flicker = fr.nextDouble() < 0.06 ? 0.4f : 1f;
            f.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (alphaIn * (1 - smooth(zoomK * 3))) * flicker));
            double sc = 1 / FIG_RES;
            f.translate(vx + sx * 0.4, vy + h * 0.22 + sy * 0.4);
            f.scale(sc, sc);
            f.drawImage(figure.image, -figure.width / 2, -figure.height + (int) (2 * unit), null);
            f.dispose();
        }

        // The world dims to black just before it comes back.
        double dark = smooth((s - (TUNNEL_TO - 1.3)) / 1.3);
        if (dark > 0.003) {
            g.setColor(new Color(0, 0, 0, (int) (255 * dark)));
            g.fillRect(0, 0, w, h);
        }
    }

    // ------------------------------------------------------------ the Glitcher tears the picture

    private void drawTorn(Graphics2D g, int w, int h, double ms, double amt) {
        Random r = new Random((long) (ms / 70) * 31 + 7);
        boolean burst = amt > 0.75 || (int) (ms / 380) % 3 != 0;
        if (!burst) return;
        int n = (int) (4 + 12 * amt);
        for (int i = 0; i < n; i++) {                                              // strips of the picture slide sideways
            int y = r.nextInt(Math.max(1, h - 4));
            int hh = Math.max(2, Math.min(h - y, (int) (h * (0.008 + 0.05 * r.nextDouble()))));
            int dx = (int) ((r.nextDouble() - 0.5) * w * (0.03 + 0.13 * amt));
            g.copyArea(0, y, w, hh, dx, 0);
        }
        for (int j = 0; j < 2 + (int) (4 * amt); j++) {
            int y = r.nextInt(h), hh = 3 + r.nextInt((int) (10 + 24 * amt));
            g.setColor(r.nextBoolean() ? new Color(0, 255, 240, 40 + (int) (60 * amt)) : new Color(255, 0, 200, 40 + (int) (60 * amt)));
            g.fillRect(0, y, w, hh);
        }
        for (int j = 0; j < (int) (10 * amt); j++) {                                  // blocks of static
            int bw = 20 + r.nextInt(120), bh = 6 + r.nextInt(30);
            int gv = r.nextInt(255);
            g.setColor(new Color(gv, gv, gv, 90));
            g.fillRect(r.nextInt(w), r.nextInt(h), bw, bh);
        }
        if (r.nextDouble() < 0.05 * amt) {                                            // now and then it all cuts out for a frame
            g.setColor(new Color(0, 0, 0, 200));
            g.fillRect(0, 0, w, h);
        }
    }
}
