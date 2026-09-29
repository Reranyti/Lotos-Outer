package com.lotusblight.overlay;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.DoubleConsumer;

/**
 * Song two, from 1:17: the whole screen tears and asks whether you're going to die, then an eye opens
 * at the side and the Glitcher tries to cut the contact off - the eye comes apart in two and the halves
 * rush back together on the beat; the space bar has to be pressed just as they meet, or the health bar pays. Everything runs off the song
 * clock, so it can be joined mid-way.
 */
final class ContactBreak {
    static final double GLITCH_FROM = 77_000;      // 1:17 - the screen tears
    static final double START = 79_000;            // 1:19 - the eye opens, the bars unfold
    static final double END = 134_500;             // and it lets go just before the finale film
    static final double WALL_FROM = 115_000;       // 1:55 - the mess turns into a wall of "error" and a plea
    static final double LABELS_AT = 81_000;        // the bars get their names when they've unfolded

    private static final double WINDOW = 140;      // ms either side of the beat that count as on time
    private static final double APPROACH = 900;    // how long the ring takes to close
    private static final double MISS_DAMAGE = 12;
    private static final double SPAM_DAMAGE = 3;   // pressing when nothing is due

    private static final String JAPANESE = "お前は死ぬのか？　そうなのか？";
    private static final String ENGLISH = "You're going to die?  Yes?";

    private final double[] beats;
    private final DoubleConsumer hurt;
    private final Runnable onHit;
    private final BufferedImage face;
    private int next;
    private boolean started;
    private double resultAt = -1e9;
    private boolean resultHit;

    ContactBreak(double[] beats, DoubleConsumer hurt, Runnable onHit, int[] skin) {
        this.beats = beats;
        this.hurt = hurt;
        this.onHit = onHit;
        BufferedImage f = null;
        if (skin != null && skin.length >= 64 * 64) {
            // The front of the head, with the hat layer over it.
            f = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int base = skin[(8 + y) * 64 + 8 + x];
                    int hat = skin[(8 + y) * 64 + 40 + x];
                    f.setRGB(x, y, (hat >>> 24) > 128 ? hat : base | 0xFF000000);
                }
            }
        }
        this.face = f;
    }

    /** Every so often one of the map's own circle times, so the eye keeps the song's beat. */
    static double[] pickBeats(OsuMap map) {
        List<Double> picked = new ArrayList<>();
        double last = -1e9;
        for (OsuMap.Circle c : map.circles) {
            double t = c.timeMs();
            if (t < START + 1800 || t > END - 400) continue;
            if (t - last >= 1700) {
                picked.add(t);
                last = t;
            }
        }
        double[] out = new double[picked.size()];
        for (int i = 0; i < out.length; i++) out[i] = picked.get(i);
        return out;
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** 0..1: how hard the screen is tearing. Quick in, quick out, over the two seconds before the eye. */
    static double glitchAmount(double ms) {
        if (ms < GLITCH_FROM || ms >= START) return 0;
        double f = (ms - GLITCH_FROM) / (START - GLITCH_FROM);
        return Math.min(1, Math.min(f * 5, (1 - f) * 8));
    }

    /** How much the backdrop has turned into the wall of "error" (0..1). */
    static double wallAmount(double ms) {
        return smooth((ms - WALL_FROM) / 2500.0) * (1 - smooth((ms - END) / 1500.0));
    }

    /** How far the health bars have unfolded, 0..1, and how visible their names are. */
    static double unfold(double ms) {
        return smooth((ms - START) / 2000.0);
    }

    static double labelAlpha(double ms) {
        return smooth((ms - LABELS_AT) / 700.0);
    }

    /** Judges the beats that have gone by. Call each frame. */
    void update(double ms) {
        if (!started) {
            started = true;
            while (next < beats.length && beats[next] < ms - WINDOW) next++;     // joined late: skip, don't punish
        }
        while (next < beats.length && ms > beats[next] + WINDOW) {
            hurt.accept(MISS_DAMAGE);
            resultAt = ms;
            resultHit = false;
            next++;
        }
    }

    void press(double ms) {
        if (ms < START || ms > END) return;
        if (next < beats.length && Math.abs(ms - beats[next]) <= WINDOW) {
            next++;
            resultAt = ms;
            resultHit = true;
            onHit.run();
        } else {
            hurt.accept(SPAM_DAMAGE);
            resultAt = ms;
            resultHit = false;
        }
    }

    /** The question over the torn screen. Drawn into the picture that then gets torn. */
    void renderText(Graphics2D g, int w, int h, double ms) {
        if (ms < GLITCH_FROM || ms >= START + 300) return;
        double f = (ms - GLITCH_FROM) / (START + 300 - GLITCH_FROM);
        double alpha = Math.min(1, Math.min(f * 6, (1 - f) * 5));
        Random flick = new Random((long) (ms / 70));
        if (flick.nextInt(6) == 0) alpha *= 0.3;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        drawGhosted(g, JAPANESE, w, h * 0.44, h * 0.09, alpha);
        drawGhosted(g, ENGLISH, w, h * 0.55, h * 0.05, alpha);
    }

    private static void drawGhosted(Graphics2D g, String text, int w, double y, double size, double alpha) {
        g.setFont(new Font(Font.DIALOG, Font.BOLD, (int) size));
        int tw = g.getFontMetrics().stringWidth(text);
        int x = (w - tw) / 2;
        int a = (int) (255 * Math.max(0, Math.min(1, alpha)));
        g.setColor(new Color(255, 40, 80, a / 2));
        g.drawString(text, x - 6, (int) y + 2);
        g.setColor(new Color(40, 220, 255, a / 2));
        g.drawString(text, x + 6, (int) y - 2);
        g.setColor(new Color(255, 255, 255, a));
        g.drawString(text, x, (int) y);
    }

    /** Draws a picture torn into strips, its colours split, black bars cutting across it. */
    static void blit(Graphics2D g, BufferedImage buf, int w, int h, double gl, double ms) {
        Random rnd = new Random((long) (ms / 45));
        Graphics2D b = (Graphics2D) g.create();
        int shift = (int) (22 * gl);
        b.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        b.drawImage(buf, new RescaleOp(new float[]{1f, 0.1f, 0.2f, 1f}, new float[4], null), -shift, 0);
        b.drawImage(buf, new RescaleOp(new float[]{0.1f, 1f, 1f, 1f}, new float[4], null), shift, 0);
        b.setComposite(AlphaComposite.SrcOver);
        int strips = 22;
        int sh = h / strips + 1;
        for (int i = 0; i < strips; i++) {
            int y = i * sh;
            int dx = rnd.nextInt(3) == 0 ? (int) ((rnd.nextDouble() - 0.5) * 2 * w * 0.08 * gl) : 0;
            b.drawImage(buf, dx, y, dx + w, Math.min(h, y + sh), 0, y, w, Math.min(h, y + sh), null);
        }
        b.setColor(new Color(0, 0, 0, 210));
        for (int i = 0; i < 4; i++) {
            if (rnd.nextDouble() < gl) b.fillRect(0, rnd.nextInt(h), w, 3 + rnd.nextInt(16));
        }
        b.dispose();
    }

    // The torn-up screen behind everything, from 1:19
    private final GlitchBackdrop backdrop = new GlitchBackdrop();

    void renderBackdrop(Graphics2D g, int w, int h, double ms) {
        double a = smooth((ms - START) / 1500.0) * (1 - smooth((ms - END) / 1500.0));
        backdrop.render(g, w, h, ms, a);
    }

    // ------------------------------------------------------------------------------------------------
    // The Glitcher's face in the dark, and the two eyes that part and have to be brought together
    // ------------------------------------------------------------------------------------------------

    private static BufferedImage scleraSprite, irisSprite;

    private static synchronized void buildSprites() {
        if (scleraSprite != null) return;
        Random rnd = new Random(666);

        // The white of the eye, dull grey-white, going dark at the corners, with a few faint veins and grain.
        int sw = 480, sh = 300;
        BufferedImage sc = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sc.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new RadialGradientPaint(sw / 2f, sh / 2f, sw * 0.56f, new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(214, 210, 202), new Color(168, 160, 156), new Color(70, 22, 28)}));
        g.fillRect(0, 0, sw, sh);
        for (int i = 0; i < 34; i++) {
            boolean left = i % 2 == 0;
            double x = left ? 6 : sw - 6, y = sh * (0.12 + 0.76 * rnd.nextDouble());
            double ang = (left ? 0 : Math.PI) + (rnd.nextDouble() - 0.5) * 1.1;
            vein(g, rnd, x, y, ang, 60 + rnd.nextInt(100), 2.2f, 50 + rnd.nextInt(70), 2);
        }
        for (int i = 0; i < 5200; i++) {
            int v = rnd.nextInt(256);
            g.setColor(new Color(v, v, v, 22 + rnd.nextInt(28)));
            g.fillRect(rnd.nextInt(sw), rnd.nextInt(sh), 1 + rnd.nextInt(2), 1);
        }
        g.dispose();
        scleraSprite = sc;

        // The iris: dark, with a pale ring and fine fibres; the pupil is drawn big over it.
        int is = 256, c = is / 2;
        BufferedImage ir = new BufferedImage(is, is, BufferedImage.TYPE_INT_ARGB);
        g = ir.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new RadialGradientPaint(c, c, c - 1, new float[]{0f, 0.35f, 0.75f, 1f},
                new Color[]{new Color(96, 96, 102), new Color(56, 56, 62), new Color(124, 128, 136), new Color(14, 14, 18)}));
        g.fillOval(0, 0, is, is);
        for (int i = 0; i < 340; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r0 = c * (0.24 + 0.25 * rnd.nextDouble()), r1 = c * (0.6 + 0.38 * rnd.nextDouble());
            boolean dark = rnd.nextInt(3) == 0;
            g.setStroke(new BasicStroke(0.7f + rnd.nextFloat() * 1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(dark ? new Color(0, 0, 0, 70 + rnd.nextInt(80)) : new Color(255, 255, 255, 22 + rnd.nextInt(55)));
            double bend = (rnd.nextDouble() - 0.5) * 0.1;
            g.draw(new java.awt.geom.Line2D.Double(c + Math.cos(a) * r0, c + Math.sin(a) * r0,
                    c + Math.cos(a + bend) * r1, c + Math.sin(a + bend) * r1));
        }
        g.setColor(new Color(2, 2, 6, 240));
        g.setStroke(new BasicStroke(c * 0.12f));
        g.drawOval(2, 2, is - 4, is - 4);
        g.dispose();
        irisSprite = ir;
    }

    private static void vein(Graphics2D g, Random rnd, double x, double y, double ang, double len, float wd, int alpha, int depth) {
        double px = x, py = y;
        int steps = Math.max(2, (int) (len / 9));
        for (int i = 0; i < steps; i++) {
            ang += (rnd.nextDouble() - 0.5) * 0.7;
            double nx = px + Math.cos(ang) * 9, ny = py + Math.sin(ang) * 9;
            g.setStroke(new BasicStroke(Math.max(0.6f, wd * (1 - i / (float) steps)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(140, 50, 56, Math.max(0, Math.min(255, alpha))));
            g.draw(new java.awt.geom.Line2D.Double(px, py, nx, ny));
            if (depth > 0 && rnd.nextInt(6) == 0) {
                vein(g, rnd, nx, ny, ang + (rnd.nextBoolean() ? 0.75 : -0.75), len * 0.5, wd * 0.6f, alpha * 3 / 4, depth - 1);
            }
            px = nx;
            py = ny;
        }
    }

    /** How far apart the eyes are, 0 (together) .. 1: they part fast, hang, then rush together on the beat. */
    private double apart(double ms) {
        if (next >= beats.length) return 0;
        double d = beats[next] - ms;
        if (d > APPROACH || d <= 0) return 0;
        double p = 1 - d / APPROACH;
        double out = smooth(p / 0.22);
        double back = p < 0.35 ? 0 : Math.pow((p - 0.35) / 0.65, 2.2);
        return out * (1 - back);
    }

    /** 0..1 progress towards the coming beat, 1 while it can still be hit. */
    private double progress(double ms) {
        if (next >= beats.length) return 0;
        double d = beats[next] - ms;
        if (d > APPROACH) return 0;
        if (d <= 0) return d > -WINDOW ? 1 : 0;
        return 1 - d / APPROACH;
    }

    /** The Glitcher's face in the dark, the eyes on it, the ring round it. */
    void renderEye(Graphics2D g, int w, int h, double ms) {
        double open = smooth((ms - START) / 1600.0) * (1 - smooth((ms - END) / 800.0));
        if (open <= 0) return;
        buildSprites();
        double r = h * 0.105;
        double cx = w * 0.5, cy = h * 0.5;
        double t = ms / 1000.0;

        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double since = ms - resultAt;
        boolean fresh = since >= 0 && since < 420;
        Color tint = fresh ? (resultHit ? new Color(140, 255, 210) : new Color(255, 60, 80)) : new Color(226, 228, 232);
        double p = progress(ms);
        double gapFrac = apart(ms);
        double rest = r * 1.17, ex = rest + gapFrac * r * 3.4;
        double shake = fresh && !resultHit ? (1 - since / 420.0) * r * 0.09 : gapFrac * r * 0.025;
        double blink = fresh && resultHit ? Math.max(0, 1 - since / 130.0) * 0.85 : 0;
        double eyeOpen = open * (1 - blink);
        Random jitter = new Random((long) (ms / 40));

        // Darkness round the face, so the eyes are all there is.
        b.setPaint(new RadialGradientPaint((float) cx, (float) cy, (float) (h * 0.7), new float[]{0.15f, 0.7f, 1f},
                new Color[]{new Color(0, 0, 0, (int) (215 * open)), new Color(0, 0, 0, (int) (150 * open)), new Color(0, 0, 0, 0)}));
        b.fillRect(0, 0, w, h);
        drawFace(b, cx, cy, h * 0.72, open, jitter);

        // A glow between and round the eyes, stronger as the moment nears and after a judgement.
        float ga = (float) (open * (fresh ? 0.6 : 0.16 + 0.24 * p));
        b.setPaint(new RadialGradientPaint((float) cx, (float) cy, (float) (r * 4.2), new float[]{0.2f, 1f},
                new Color[]{new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), (int) (255 * Math.min(1, ga))),
                        new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 0)}));
        b.fillOval((int) (cx - r * 4.2), (int) (cy - r * 4.2), (int) (r * 8.4), (int) (r * 8.4));

        drawTimerRing(b, cx, cy, r, r * 4.3, t, p, open, tint);
        if (gapFrac > 0.02) drawTension(b, cx, cy, r, ex, gapFrac, ms);

        // The two eyes; each looks at the cursor.
        double px = cx, py = cy;
        try {
            java.awt.PointerInfo pi = java.awt.MouseInfo.getPointerInfo();
            if (pi != null) { px = pi.getLocation().x; py = pi.getLocation().y; }
        } catch (RuntimeException ignored) { }
        for (int side = -1; side <= 1; side += 2) {
            Graphics2D eb = (Graphics2D) b.create();
            double dx = side * ex + (jitter.nextDouble() - 0.5) * shake, dy = (jitter.nextDouble() - 0.5) * shake;
            eb.translate(dx, dy);
            drawEye(eb, cx, cy, r, eyeOpen, tint, px - (cx + side * ex), py - cy, p, t);
            eb.dispose();
        }

        if (fresh) {
            if (resultHit) drawShards(b, cx, cy, r, since, tint);
            else drawCracks(b, cx, cy, r, since);
        }

        if (ms < START + 9000) {
            b.setFont(new Font(Font.DIALOG, Font.BOLD, (int) (h * 0.028)));
            String hint = "ПРОБЕЛ — когда сомкнутся";
            int tw = b.getFontMetrics().stringWidth(hint);
            b.setColor(new Color(0, 0, 0, (int) (170 * open)));
            b.drawString(hint, (int) (cx - tw / 2.0) + 2, (int) (h * 0.9) + 2);
            b.setColor(new Color(255, 255, 255, (int) (225 * open)));
            b.drawString(hint, (int) (cx - tw / 2.0), (int) (h * 0.9));
        }
        b.dispose();
    }

    /** His face - the front of the skin's head - huge, pixelated, barely lit, sliding a little now and then. */
    private void drawFace(Graphics2D b, double cx, double cy, double size, double open, Random rnd) {
        if (face == null) return;
        Graphics2D f = (Graphics2D) b.create();
        f.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        double x = cx - size / 2, y = cy - size * 0.5625;
        f.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (0.5 * open)));
        int strips = 16, sh = (int) Math.ceil(size / strips);
        for (int i = 0; i < strips; i++) {
            int dx = rnd.nextInt(24) == 0 ? (int) ((rnd.nextDouble() - 0.5) * size * 0.06) : 0;
            f.drawImage(face, (int) x + dx, (int) y + i * sh, (int) (x + size) + dx, (int) y + (i + 1) * sh,
                    0, i * face.getHeight() / strips, face.getWidth(), (i + 1) * face.getHeight() / strips, null);
        }
        f.setComposite(AlphaComposite.SrcOver);
        // Fade the edges of the face into the dark.
        f.setPaint(new RadialGradientPaint((float) cx, (float) cy, (float) (size * 0.62), new float[]{0.35f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, (int) (235 * open))}));
        f.fillRect((int) (cx - size), (int) (cy - size), (int) (size * 2), (int) (size * 2));
        f.dispose();
    }

    /** A ring of ticks turning round the pair, with an arc filling up that closes exactly on the beat. */
    private static void drawTimerRing(Graphics2D b, double cx, double cy, double r, double rr, double t, double p, double open, Color tint) {
        b.setStroke(new BasicStroke((float) (r * 0.018)));
        b.setColor(new Color(255, 255, 255, (int) (70 * open)));
        b.draw(new java.awt.geom.Ellipse2D.Double(cx - rr, cy - rr, rr * 2, rr * 2));
        b.draw(new java.awt.geom.Ellipse2D.Double(cx - rr * 0.94, cy - rr * 0.94, rr * 1.88, rr * 1.88));
        double spin = t * 0.3;
        for (int i = 0; i < 96; i++) {
            double a = spin + i * Math.PI * 2 / 96;
            double len = r * (i % 8 == 0 ? 0.16 : 0.07);
            b.setColor(new Color(255, 255, 255, (int) ((i % 8 == 0 ? 190 : 100) * open)));
            b.setStroke(new BasicStroke((float) (r * (i % 8 == 0 ? 0.025 : 0.014))));
            b.draw(new java.awt.geom.Line2D.Double(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr,
                    cx + Math.cos(a) * (rr + len), cy + Math.sin(a) * (rr + len)));
        }
        if (p > 0) {
            double ar = rr * 0.94;
            for (int pass = 0; pass < 2; pass++) {
                b.setStroke(new BasicStroke((float) (r * (pass == 0 ? 0.16 : 0.06)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                b.setColor(pass == 0 ? new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), (int) (60 * open))
                        : new Color(255, 255, 255, (int) (235 * open)));
                b.draw(new java.awt.geom.Arc2D.Double(cx - ar, cy - ar, ar * 2, ar * 2, 90, -360 * p, java.awt.geom.Arc2D.OPEN));
            }
        }
        b.setColor(new Color(255, 255, 255, (int) (230 * open)));
        java.awt.geom.Path2D.Double tri = new java.awt.geom.Path2D.Double();
        tri.moveTo(cx, cy - rr * 0.94 - r * 0.02);
        tri.lineTo(cx - r * 0.09, cy - rr * 0.94 - r * 0.2);
        tri.lineTo(cx + r * 0.09, cy - rr * 0.94 - r * 0.2);
        tri.closePath();
        b.fill(tri);
        for (int i = 0; i < 4; i++) {
            double a = -t * (0.9 + p) + i * Math.PI / 2;
            double nx = cx + Math.cos(a) * rr * 1.06, ny = cy + Math.sin(a) * rr * 1.06;
            b.setColor(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), (int) (200 * open)));
            b.fill(new java.awt.geom.Rectangle2D.Double(nx - r * 0.035, ny - r * 0.035, r * 0.07, r * 0.07));
        }
    }

    /** Bolts arcing between the two eyes while they're held apart. */
    private static void drawTension(Graphics2D b, double cx, double cy, double r, double ex, double gapFrac, double ms) {
        Random rnd = new Random((long) (ms / 35));
        double xl = cx - ex + r * 0.95, xr = cx + ex - r * 0.95;
        if (xr - xl < r * 0.3) return;
        int bolts = 4 + (int) (gapFrac * 5);
        for (int k = 0; k < bolts; k++) {
            double y0 = cy + (rnd.nextDouble() - 0.5) * r * 0.9;
            java.awt.geom.Path2D.Double bolt = new java.awt.geom.Path2D.Double();
            bolt.moveTo(xl, y0);
            int segs = 12;
            for (int i = 1; i < segs; i++) {
                bolt.lineTo(xl + (xr - xl) * i / segs, y0 + (rnd.nextDouble() - 0.5) * r * 0.5 * Math.sin(Math.PI * i / segs));
            }
            bolt.lineTo(xr, y0 + (rnd.nextDouble() - 0.5) * r * 0.1);
            b.setStroke(new BasicStroke((float) (r * 0.08), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            b.setColor(new Color(80, 200, 255, (int) (80 * gapFrac)));
            b.draw(bolt);
            b.setStroke(new BasicStroke((float) (r * 0.022), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            b.setColor(new Color(240, 250, 255, (int) (235 * gapFrac)));
            b.draw(bolt);
        }
    }

    /** One eye: socket, grey white with faint veins, dark iris with a pale ring, a big pupil, shine, lids. */
    private static void drawEye(Graphics2D b, double cx, double cy, double r, double open, Color tint,
                                double lx, double ly, double p, double t) {
        b.setColor(new Color(0, 0, 0, (int) (235 * Math.min(1, open * 1.5))));
        b.fillOval((int) (cx - r * 1.14), (int) (cy - r * 1.14), (int) (r * 2.28), (int) (r * 2.28));
        double ew = r * 1.08, eh = r * 0.5 * open;
        if (eh < 1) return;
        java.awt.geom.Path2D.Double eye = new java.awt.geom.Path2D.Double();
        eye.moveTo(cx - ew, cy);
        eye.quadTo(cx, cy - eh * 2, cx + ew, cy);
        eye.quadTo(cx, cy + eh * 2, cx - ew, cy);
        eye.closePath();

        java.awt.Shape old = b.getClip();
        b.clip(eye);
        b.drawImage(scleraSprite, (int) (cx - ew), (int) (cy - eh), (int) (ew * 2), (int) (eh * 2), null);

        double len = Math.hypot(lx, ly);
        double ir = r * 0.52;
        double ix = cx + (len > 0 ? lx / len * Math.min(r * 0.4, len * 0.2) : 0);
        double iy = cy + (len > 0 ? ly / len * Math.min(r * 0.16, len * 0.08) : 0);
        b.drawImage(irisSprite, (int) (ix - ir), (int) (iy - ir), (int) (ir * 2), (int) (ir * 2), null);
        // The pupil is wide and shrinks as they come together, so they seem to fix on you.
        double pr = ir * (0.72 - 0.22 * p);
        b.setColor(Color.BLACK);
        b.fill(new java.awt.geom.Ellipse2D.Double(ix - pr, iy - pr, pr * 2, pr * 2));
        b.setColor(new Color(255, 255, 255, 240));
        b.fill(new java.awt.geom.Ellipse2D.Double(ix - ir * 0.4, iy - ir * 0.46, ir * 0.24, ir * 0.2));
        b.setColor(new Color(255, 255, 255, 150));
        b.fill(new java.awt.geom.Ellipse2D.Double(ix + ir * 0.16, iy + ir * 0.22, ir * 0.11, ir * 0.09));

        b.setPaint(new java.awt.GradientPaint(0, (float) (cy - eh), new Color(0, 0, 0, 250), 0, (float) (cy + eh * 0.05), new Color(0, 0, 0, 0)));
        b.fillRect((int) (cx - ew), (int) (cy - eh), (int) (ew * 2), (int) (eh * 1.1));
        b.setPaint(new java.awt.GradientPaint(0, (float) (cy + eh), new Color(0, 0, 0, 170), 0, (float) (cy + eh * 0.3), new Color(0, 0, 0, 0)));
        b.fillRect((int) (cx - ew), (int) (cy + eh * 0.3), (int) (ew * 2), (int) (eh * 0.75));
        b.setClip(old);

        b.setStroke(new BasicStroke((float) (r * 0.05)));
        b.translate(r * 0.02, r * 0.01);
        b.setColor(new Color(255, 70, 190, 110));
        b.draw(eye);
        b.translate(-r * 0.04, -r * 0.02);
        b.setColor(new Color(60, 230, 255, 110));
        b.draw(eye);
        b.translate(r * 0.02, r * 0.01);
        b.setColor(tint);
        b.draw(eye);

    }

    /** Bright shards flying out from where the eyes met. */
    private static void drawShards(Graphics2D b, double cx, double cy, double r, double since, Color tint) {
        Random rnd = new Random(77);
        double f = since / 420.0;
        for (int i = 0; i < 40; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, sp = r * (1.4 + 3.0 * rnd.nextDouble()), sz = r * (0.04 + 0.09 * rnd.nextDouble());
            double x = cx + Math.cos(a) * sp * f, y = cy + Math.sin(a) * sp * f + f * f * r * 0.6;
            java.awt.geom.AffineTransform old = b.getTransform();
            b.translate(x, y);
            b.rotate(a + f * 6 * (rnd.nextBoolean() ? 1 : -1));
            b.setColor(new Color(i % 3 == 0 ? 255 : tint.getRed(), i % 3 == 1 ? 255 : tint.getGreen(), tint.getBlue(),
                    (int) (255 * (1 - f))));
            b.fill(new java.awt.geom.Rectangle2D.Double(-sz, -sz * 0.4, sz * 2, sz * 0.8));
            b.setTransform(old);
        }
    }

    /** Red cracks across both eyes after a miss. */
    private static void drawCracks(Graphics2D b, double cx, double cy, double r, double since) {
        Random rnd = new Random(5);
        double f = Math.min(1, since / 120.0);
        b.setStroke(new BasicStroke((float) (r * 0.03), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        b.setColor(new Color(255, 70, 90, (int) (255 * (1 - since / 420.0))));
        for (int i = 0; i < 14; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, px = cx + (i % 2 == 0 ? -1 : 1) * r * 1.17, py = cy;
            double reach = r * (0.7 + 1.0 * rnd.nextDouble()) * f, walked = 0;
            while (walked < reach) {
                a += (rnd.nextDouble() - 0.5) * 0.9;
                double seg = r * (0.1 + 0.12 * rnd.nextDouble());
                double nx = px + Math.cos(a) * seg, ny = py + Math.sin(a) * seg;
                b.draw(new java.awt.geom.Line2D.Double(px, py, nx, ny));
                px = nx;
                py = ny;
                walked += seg;
            }
        }
    }
}
