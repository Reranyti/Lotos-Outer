package com.lotusblight.overlay;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Between the second song and the third. The Glitcher asks what he is; a rift opens and Honcho is thrown out of
 * it, and the Glitcher takes him by the throat - the player has twelve seconds to push him off, or Honcho
 * dies and so does the player. Furious, the Glitcher says they will play by his rules, the desktop shatters
 * and everyone falls; the third song is the fight on the way down.
 *
 * <p>The scene runs on its own wall clock from the moment it is made.
 */
final class Interlude {
    static final double MONO_END = 14_000;          // the Glitcher's questions end
    static final double RIFT_END = 17_800;          // the rift, the throw and the grab: the choke begins
    static final double CHOKE_MS = 12_000;          // to push him off
    private static final double PUSH = 0.03;        // one press
    private static final double DECAY = 0.04;       // his weight, per second
    private static final double ANGRY_AFTER = 400, ANGRY_LEN = 3_400, COLLAPSE_LEN = 3_600, FAIL_LEN = 3_000;

    /** {from, to, Japanese, English} - what the Glitcher says while the first part runs. */
    private static final Object[][] MONO = {
            {1_600.0, 4_900.0, "私の物語を知ろうとしているのか？", "Trying to learn my story?"},
            {5_500.0, 7_600.0, "私は消されたのか？", "Have I been erased?"},
            {8_000.0, 9_600.0, "わからない", "I don't know"},
            {10_300.0, 12_300.0, "私は生きているのか？", "Am I alive?"},
            {12_700.0, 14_000.0, "わからない", "I don't know"},
    };
    private static final String ANGRY_JP = "お前たちは、私のルールで遊ぶんだ";
    private static final String ANGRY_EN = "You will play by my rules";

    private final BufferedImage face;
    private final BufferedImage desktop;
    private final FallScene fall;
    private final GlitchBackdrop backdrop = new GlitchBackdrop();
    private BufferedImage fakeDesktop;

    private long startNano = System.nanoTime();
    private double meter;
    private double lastFrame, lastPress = -1e9, winAt = -1, failAt = -1;

    private final int[] glitcherSkin, honchoSkin;

    Interlude(int[] glitcherSkin, int[] honchoSkin, BufferedImage desktop, FallScene fall) {
        this.glitcherSkin = glitcherSkin;
        this.honchoSkin = honchoSkin;
        this.desktop = desktop;
        this.fall = fall;
        backdrop.setNoWall(true);
        backdrop.setNoWindows(true);
        BufferedImage f = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int base = glitcherSkin[(8 + y) * 64 + 8 + x];
                int hat = glitcherSkin[(8 + y) * 64 + 40 + x];
                f.setRGB(x, y, (hat >>> 24) > 128 ? hat : base | 0xFF000000);
            }
        }
        this.face = f;
    }

    /** Starts the scene as if it had been running for the given number of seconds (to try a later part). */
    void seek(double seconds) {
        startNano = System.nanoTime() - (long) (seconds * 1e9);
    }

    private double now() {
        return (System.nanoTime() - startNano) / 1e6;
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    private boolean choking(double c) {
        return c >= RIFT_END && winAt < 0 && failAt < 0;
    }

    /** A press of space (or Enter, Z, X): one push against the Glitcher, if he is at Honcho's throat. */
    void press() {
        push(PUSH);
    }

    /** A click: it counts if it lands on the Glitcher. */
    void click(double x, double y) {
        if (hitBox.contains(x, y)) push(PUSH * 1.3);
    }

    private void push(double amount) {
        double c = now();
        if (!choking(c)) return;
        meter = Math.min(1, meter + amount);
        lastPress = c;
        if (meter >= 1) winAt = c;
    }

    /** Milliseconds until the scene is over (won or lost); a very large number while it is still undecided. */
    double doneIn() {
        double c = now();
        if (winAt >= 0) return winAt + ANGRY_AFTER + ANGRY_LEN + COLLAPSE_LEN - c;
        if (failAt >= 0) return failAt + FAIL_LEN - c;
        return 1e9;
    }

    /** True once Honcho has been strangled and the black has closed in. */
    boolean failed() {
        return failAt >= 0 && now() >= failAt + FAIL_LEN;
    }

    /** True once the desktop has come down and everyone is falling. */
    boolean done() {
        return winAt >= 0 && now() >= winAt + ANGRY_AFTER + ANGRY_LEN + COLLAPSE_LEN;
    }

    void render(Graphics2D g, int w, int h) {
        double c = now();
        double dt = Math.max(0, Math.min(100, c - lastFrame));
        lastFrame = c;
        if (choking(c)) {
            meter = Math.max(0, meter - DECAY * dt / 1000.0);
            if (c >= RIFT_END + CHOKE_MS) failAt = c;
        }
        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        b.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (winAt >= 0) {
            drawAfterWin(b, w, h, c - winAt);
        } else if (failAt >= 0) {
            double f = smooth((c - failAt) / FAIL_LEN);
            drawStrangle(b, w, h, c, meter, true, 0);
            b.setColor(new Color(0, 0, 0, (int) (255 * f)));                      // and it all goes dark
            b.fillRect(0, 0, w, h);
        } else if (c < MONO_END) {
            drawMonologue(b, w, h, c);
        } else if (c < RIFT_END) {
            drawRift(b, w, h, c);
        } else {
            drawStrangle(b, w, h, c, meter, false, 0);
            drawChokeHud(b, w, h, c);
        }
        b.dispose();
    }

    // ---------------------------------------------------------------- the questions

    private void drawMonologue(Graphics2D b, int w, int h, double c) {
        b.setColor(new Color(4, 3, 10, 200));           // dark, but the real desktop still shows through
        b.fillRect(0, 0, w, h);
        backdrop.render(b, w, h, c + 50_000, 0.35 * smooth(c / 1400.0));
        boolean talking = false;
        Object[] line = null;
        for (Object[] m : MONO) {
            if (c >= (Double) m[0] && c <= (Double) m[1] + 200) { line = m; talking = c < (Double) m[1]; }
        }
        drawFace(b, w * 0.5, h * 0.4, h * 0.62, 0.6 * smooth(c / 1800.0), c, talking ? 1 : 0.25, null);
        if (line != null) {
            double from = (Double) line[0], to = (Double) line[1];
            double a = Math.min(1, (c - from) / 300.0) * (1 - smooth((c - to) / 200.0));
            if (new Random((long) (c / 80)).nextInt(9) == 0) a *= 0.45;
            ghosted(b, (String) line[2], w / 2.0, h * 0.8, h * 0.065, a, false);
            ghosted(b, (String) line[3], w / 2.0, h * 0.88, h * 0.04, a, false);
        }
        if (c < 1200) {                                              // out of the dark
            b.setColor(new Color(0, 0, 0, (int) (255 * (1 - c / 1200.0))));
            b.fillRect(0, 0, w, h);
        }
    }

    // ---------------------------------------------------------------- the rift, the throw, the grab

    private void drawRift(Graphics2D b, int w, int h, double c) {
        b.setColor(new Color(4, 3, 10, c < 15_200 ? 190 : 255));
        b.fillRect(0, 0, w, h);
        if (c < 15_200) {
            backdrop.render(b, w, h, c + 50_000, 0.3);
            drawFace(b, w * 0.5, h * 0.4, h * 0.62, 0.45, c, 0.3, null);
            // The rift tears open, top to bottom.
            double p = smooth((c - 14_000) / 1_200.0);
            drawTear(b, w, h, p, c);
            b.setColor(new Color(255, 255, 255, (int) (200 * p * p * p)));
            b.fillRect(0, 0, w, h);
            return;
        }
        // Thrown out of it: the whole picture tumbles, lines stream past, and something comes at us.
        double cc = c - 15_200;
        AffineTransform old = b.getTransform();
        b.translate(w / 2.0, h / 2.0);
        b.rotate(Math.sin(cc * 0.009) * 0.9 + cc * 0.0011);
        double zoom = 1 + 0.25 * Math.sin(cc * 0.013);
        b.scale(zoom, zoom);
        b.translate(-w / 2.0, -h / 2.0);
        fall.render(b, w, h, cc * 2.2, 3.5);
        drawStreaks(b, w, h, cc);
        b.setTransform(old);
        double rush = smooth((c - 16_600) / 1_200.0);
        if (rush > 0) {
            drawFace(b, w * 0.5, h * 0.42, h * (0.15 + 0.85 * rush), 0.5 + 0.5 * rush, c, 0.6, null);
            drawHands(b, w, h, smooth((c - 16_900) / 900.0), c);
            vignette(b, w, h, h * (1.6 - 1.0 * rush), 235);
        }
        // The arrival: white at the start of the throw.
        double flash = Math.max(0, 1 - cc / 500.0);
        b.setColor(new Color(255, 255, 255, (int) (230 * flash)));
        b.fillRect(0, 0, w, h);
    }

    private void drawTear(Graphics2D b, int w, int h, double p, double c) {
        if (p <= 0) return;
        Random rnd = new Random(14);
        int segs = 16;
        double px = w * 0.47, py = -h * 0.02;
        java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();
        path.moveTo(px, py);
        for (int i = 1; i <= segs && i <= Math.ceil(segs * p); i++) {
            px = w * (0.47 + 0.06 * i / segs) + (rnd.nextDouble() - 0.5) * w * 0.05;
            py = h * 1.04 * i / segs;
            path.lineTo(px, py);
        }
        b.setStroke(new BasicStroke((float) (h * 0.06 * p), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        b.setColor(new Color(120, 200, 255, (int) (60 * p)));
        b.draw(path);
        b.setStroke(new BasicStroke((float) (h * 0.012 + 3), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        b.setColor(new Color(255, 255, 255, 230));
        b.draw(path);
    }

    private void drawStreaks(Graphics2D b, int w, int h, double cc) {
        Random rnd = new Random(5);
        double cx = w / 2.0, cy = h / 2.0;
        b.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 70; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, base = 0.05 + rnd.nextDouble() * 0.5, sp = 0.6 + rnd.nextDouble();
            double d0 = ((cc / 1000.0 * sp * 0.9 + base) % 1.0), d1 = d0 + 0.08 + 0.2 * d0;
            double r0 = d0 * Math.hypot(w, h) * 0.6, r1 = d1 * Math.hypot(w, h) * 0.6;
            b.setColor(new Color(210, 225, 255, (int) (200 * d0)));
            b.draw(new java.awt.geom.Line2D.Double(cx + Math.cos(a) * r0, cy + Math.sin(a) * r0, cx + Math.cos(a) * r1, cy + Math.sin(a) * r1));
        }
    }

    // ---------------------------------------------------------------- the choke, seen from the side

    private static final int RIGHT_ARM = SkinModel.Part.RIGHT_ARM.ordinal();
    private static final int LEFT_ARM = SkinModel.Part.LEFT_ARM.ordinal();
    private static final int RIGHT_LEG = SkinModel.Part.RIGHT_LEG.ordinal();
    private static final int LEFT_LEG = SkinModel.Part.LEFT_LEG.ordinal();
    private static final int HEAD = SkinModel.Part.HEAD.ordinal();

    private final SkinModel glitcherModel = new SkinModel();
    private final SkinModel honchoModel = SkinModel.honcho();
    private final SoftRenderer.Pose pose = new SoftRenderer.Pose();
    private SoftRenderer glitcherRender, honchoRender;
    private double figureUnit;
    private java.awt.geom.Rectangle2D.Double hitBox = new java.awt.geom.Rectangle2D.Double();

    /**
     * The Glitcher holds Honcho up by the throat; the player has to knock him off by clicking on him.
     *
     * @param m     how far he has been pushed back, 0..1
     * @param limp  Honcho has gone slack
     * @param freed 0 while he is held, growing to 1 as he is let go and drops
     */
    private void drawStrangle(Graphics2D b, int w, int h, double c, double m, boolean limp, double freed) {
        double unit = h * 0.62 / 32.0;
        if (glitcherRender == null || figureUnit != unit) {
            figureUnit = unit;
            glitcherRender = new SoftRenderer((int) (60 * unit), (int) (50 * unit));
            honchoRender = new SoftRenderer((int) (60 * unit), (int) (50 * unit));
        }
        b.setColor(new Color(6, 3, 10, 120));           // the real desktop is the room they are in
        b.fillRect(0, 0, w, h);
        backdrop.render(b, w, h, c + 50_000, 0.14);
        // A dim light on the two of them.
        b.setPaint(new RadialGradientPaint((float) (w * 0.5), (float) (h * 0.55), (float) (h * 0.8), new float[]{0f, 1f},
                new Color[]{new Color(120, 90, 200, 40), new Color(0, 0, 0, 140)}));
        b.fillRect(0, 0, w, h);

        double t = c / 1000.0;
        double ground = h * 0.92;
        double gx = w * (0.63 + 0.14 * m + 0.13 * freed);
        // The Glitcher: facing left, his right arm out at Honcho's throat.
        pose.reset();
        pose.yaw = -1.15;
        pose.partPitch[RIGHT_ARM] = -2.6 * (1 - freed) + 0.4 * freed;
        pose.partPitch[LEFT_ARM] = -0.3 + 0.2 * Math.sin(t * 5);
        pose.partPitch[RIGHT_LEG] = 0.15 + 0.1 * Math.sin(t * 3);
        pose.partPitch[LEFT_LEG] = -0.15;
        pose.pitch = -0.32 * freed + 0.05 * Math.sin(t * 9) * (1 - freed);
        pose.partPitch[HEAD] = 0.15;
        glitcherRender.clear();
        glitcherRender.draw(glitcherModel, glitcherSkin, pose, unit, glitcherRender.width / 2.0, glitcherRender.height - 6 * unit);
        double gDrawX = gx - glitcherRender.width / 2.0;
        double gDrawY = ground - glitcherRender.height + 6 * unit;
        b.drawImage(glitcherRender.image, (int) gDrawX, (int) gDrawY, null);
        hitBox.setRect(gx - 10 * unit, ground - 36 * unit, 20 * unit, 38 * unit);

        // Honcho: hanging from his hand until he is let go, then falling.
        double hx = gx - 7.7 * unit, hy = ground - 32.3 * unit;            // where his raised hand is
        double fatigue = limp ? 1 : Math.min(1, Math.max(0, (c - RIFT_END) / CHOKE_MS) * (1 - 0.6 * m));
        double flail = (1 - 0.9 * fatigue) * (1 - freed);
        pose.reset();
        pose.yaw = 0.8;
        pose.partPitch[RIGHT_ARM] = Math.sin(t * 17) * 0.9 * flail - 0.4;
        pose.partPitch[LEFT_ARM] = Math.cos(t * 19) * 0.9 * flail - 0.2;
        pose.partRoll[RIGHT_ARM] = -0.5 - 0.4 * flail;
        pose.partRoll[LEFT_ARM] = 0.5 + 0.4 * flail;
        pose.partPitch[RIGHT_LEG] = Math.sin(t * 15) * 0.8 * flail;
        pose.partPitch[LEFT_LEG] = -Math.sin(t * 15) * 0.8 * flail;
        pose.partPitch[HEAD] = -0.25 - 0.5 * fatigue;
        honchoRender.clear();
        honchoRender.draw(honchoModel, honchoSkin, pose, unit, honchoRender.width / 2.0, honchoRender.height - 6 * unit);
        double drop = freed * freed * h * 0.9;
        AffineTransform old = b.getTransform();
        b.translate(hx, hy + 24 * unit + drop);
        b.rotate(0.3 * freed + Math.sin(t * 8) * 0.03 * flail);
        b.drawImage(honchoRender.image, -honchoRender.width / 2, -honchoRender.height + (int) (6 * unit), null);
        b.setTransform(old);

        // The dark closing in as the time runs out, red at its edge.
        double left = Math.max(0, RIFT_END + CHOKE_MS - c);
        if (winAt < 0) {
            double danger = 1 - Math.min(1, left / CHOKE_MS);
            b.setPaint(new RadialGradientPaint((float) (w / 2.0), (float) (h / 2.0), (float) (Math.hypot(w, h) / 2), new float[]{0.55f, 1f},
                    new Color[]{new Color(120, 0, 10, 0), new Color(120, 0, 10, (int) (140 * danger * (0.6 + 0.4 * Math.sin(c * 0.012))))}));
            b.fillRect(0, 0, w, h);
        }
        // The cut from Honcho's eyes to here.
        double cut = Math.max(0, 1 - (c - RIFT_END) / 300.0);
        if (cut > 0) {
            b.setColor(new Color(255, 255, 255, (int) (255 * cut)));
            b.fillRect(0, 0, w, h);
        }
        // Every press knocks the picture a moment.
        double since = c - lastPress;
        if (since >= 0 && since < 140) {
            b.setColor(new Color(255, 255, 255, (int) (60 * (1 - since / 140.0))));
            b.fillRect(0, 0, w, h);
        }
    }

    private void drawChokeHud(Graphics2D b, int w, int h, double c) {
        double left = Math.max(0, RIFT_END + CHOKE_MS - c);
        b.setFont(new Font(Font.DIALOG, Font.BOLD, (int) (h * 0.032)));
        String title = "СПАСИ ХОНЧО: КЛИКАЙ ПО ГЛИТЧЕРУ";
        int tw = b.getFontMetrics().stringWidth(title);
        b.setColor(new Color(0, 0, 0, 200));
        b.drawString(title, (w - tw) / 2 + 2, (int) (h * 0.07) + 2);
        b.setColor(new Color(255, 90, 100));
        b.drawString(title, (w - tw) / 2, (int) (h * 0.07));
        b.setFont(new Font(Font.DIALOG, Font.BOLD, (int) (h * 0.11)));
        String secs = String.format(java.util.Locale.ROOT, "%.1f", left / 1000.0);
        int sw = b.getFontMetrics().stringWidth(secs);
        b.setColor(new Color(0, 0, 0, 200));
        b.drawString(secs, (w - sw) / 2 + 3, (int) (h * 0.18) + 3);
        b.setColor(left < 4000 ? new Color(255, 70, 80) : Color.WHITE);
        b.drawString(secs, (w - sw) / 2, (int) (h * 0.18));

        double bw = w * 0.4, bh = h * 0.035, bx = (w - bw) / 2, by = h * 0.94;
        b.setColor(new Color(0, 0, 0, 190));
        b.fillRoundRect((int) bx - 6, (int) by - 6, (int) bw + 12, (int) bh + 12, 14, 14);
        b.setColor(new Color((int) (255 * (1 - meter)), (int) (90 + 165 * meter), 90));
        b.fillRoundRect((int) bx, (int) by, (int) (bw * meter), (int) bh, 10, 10);
        b.setColor(new Color(255, 255, 255, 220));
        b.setStroke(new BasicStroke(2f));
        b.drawRoundRect((int) bx, (int) by, (int) bw, (int) bh, 10, 10);
        b.setFont(new Font(Font.DIALOG, Font.BOLD, (int) (h * 0.024)));
        String hint = c < RIFT_END + 4_000 ? "жми на него мышью (или пробел, Enter, Z, X)" : "ЖМИ!";
        int hw = b.getFontMetrics().stringWidth(hint);
        b.setColor(new Color(0, 0, 0, 200));
        b.drawString(hint, (w - hw) / 2 + 2, (int) (by - h * 0.012) + 2);
        b.setColor(Color.WHITE);
        b.drawString(hint, (w - hw) / 2, (int) (by - h * 0.012));
        // A ring round where to press, at first.
        if (c < RIFT_END + 3_500) {
            double pulse = 0.5 + 0.5 * Math.sin(c * 0.012);
            b.setColor(new Color(255, 255, 255, (int) (200 * pulse)));
            b.setStroke(new BasicStroke(3f));
            b.draw(new java.awt.geom.RoundRectangle2D.Double(hitBox.x - 8, hitBox.y - 8, hitBox.width + 16, hitBox.height + 16, 24, 24));
        }
    }

    // ---------------------------------------------------------------- pushed off; the rules; the fall

    private void drawAfterWin(Graphics2D b, int w, int h, double since) {
        double c = now();
        if (since < ANGRY_AFTER) {
            // Knocked back: the face flies off and the hands let go.
            drawStrangle(b, w, h, c, 1, false, smooth(since / ANGRY_AFTER));
            return;
        }
        double s = since - ANGRY_AFTER;
        if (s < ANGRY_LEN) {
            // Furious.
            b.setColor(new Color(60, 0, 10, 165));
            b.fillRect(0, 0, w, h);
            backdrop.render(b, w, h, c + 50_000, 0.3);
            Random sh = new Random((long) (c / 35));
            AffineTransform old = b.getTransform();
            double amp = h * 0.012;
            b.translate((sh.nextDouble() - 0.5) * amp, (sh.nextDouble() - 0.5) * amp);
            drawFace(b, w * 0.5, h * 0.4, h * (0.55 + 0.05 * Math.sin(c * 0.02)), 0.95, c, 1, new Color(200, 0, 30, 110));
            b.setTransform(old);
            double a = smooth(s / 300.0) * (1 - smooth((s - (ANGRY_LEN - 300)) / 300.0));
            ghosted(b, ANGRY_JP, w / 2.0, h * 0.8, h * 0.066, a, true);
            ghosted(b, ANGRY_EN, w / 2.0, h * 0.88, h * 0.04, a, true);
            return;
        }
        // The desktop cracks and comes down; the fall goes on beneath it.
        double k = s - ANGRY_LEN;
        fall.render(b, w, h, k, 1.6 + smooth(k / 1500.0));
        drawShattering(b, w, h, k);
    }

    private BufferedImage desktopPicture(int w, int h) {
        if (desktop != null) return desktop;
        if (fakeDesktop == null) {
            fakeDesktop = AeroWallpaper.paint(w, h, 1);
            Graphics2D g = fakeDesktop.createGraphics();
            g.setColor(new Color(20, 30, 60, 235));
            g.fillRect(0, h - (int) (h * 0.045), w, (int) (h * 0.045));
            g.dispose();
        }
        return fakeDesktop;
    }

    private void drawShattering(Graphics2D b, int w, int h, double k) {
        BufferedImage img = desktopPicture(w, h);
        int cols = 12, rows = 7;
        double tw = w / (double) cols, th = h / (double) rows;
        double shake = Math.max(0, 1 - k / 700.0) * h * 0.012;
        Random sh = new Random((long) (k / 30));
        AffineTransform base = b.getTransform();
        b.translate((sh.nextDouble() - 0.5) * shake * 2, (sh.nextDouble() - 0.5) * shake * 2);
        Random rnd = new Random(31);
        for (int r = 0; r < rows; r++) {
            for (int q = 0; q < cols; q++) {
                double delay = 500 + rnd.nextDouble() * 900 + (rows - r) * 45;
                double vx = (rnd.nextDouble() - 0.5) * w * 0.12, spin = (rnd.nextDouble() - 0.5) * 4;
                double age = Math.max(0, k - delay) / 1000.0;
                double x0 = q * tw, y0 = r * th;
                double dx = vx * age, dy = 0.5 * h * 2.4 * age * age;
                if (y0 + dy > h * 1.3) continue;
                AffineTransform old = b.getTransform();
                b.translate(x0 + tw / 2 + dx, y0 + th / 2 + dy);
                b.rotate(spin * age);
                b.drawImage(img, (int) (-tw / 2), (int) (-th / 2), (int) (tw / 2) + 1, (int) (th / 2) + 1,
                        (int) x0, (int) y0, (int) (x0 + tw) + 1, (int) (y0 + th) + 1, null);
                b.setTransform(old);
            }
        }
        // Cracks running over it before it goes.
        if (k < 1400) {
            double f = Math.min(1, k / 450.0);
            b.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            b.setColor(new Color(255, 255, 255, (int) (240 * (1 - smooth((k - 500) / 900.0)))));
            Random cr = new Random(6);
            for (int i = 0; i < 18; i++) {
                double a = cr.nextDouble() * Math.PI * 2, x = w * (0.35 + 0.3 * cr.nextDouble()), y = h * (0.3 + 0.4 * cr.nextDouble());
                double reach = Math.hypot(w, h) * (0.15 + 0.4 * cr.nextDouble()) * f, walked = 0;
                while (walked < reach) {
                    a += (cr.nextDouble() - 0.5) * 0.9;
                    double seg = 30 + cr.nextDouble() * 50;
                    double nx = x + Math.cos(a) * seg, ny = y + Math.sin(a) * seg;
                    b.draw(new java.awt.geom.Line2D.Double(x, y, nx, ny));
                    x = nx;
                    y = ny;
                    walked += seg;
                }
            }
        }
        b.setTransform(base);
    }

    // ---------------------------------------------------------------- pieces

    private BufferedImage faceBuf;

    /**
     * The Glitcher's face - the front of his skin's head - large, pixelated, torn into strips as he speaks, its
     * edges melting into the dark (no box round it).
     */
    private void drawFace(Graphics2D b, double cx, double cy, double size, double alpha, double ms, double agitation, Color tint) {
        int S = (int) Math.ceil(size);
        if (alpha <= 0 || S < 4) return;
        if (faceBuf == null || faceBuf.getWidth() < S) faceBuf = new BufferedImage(Math.max(S, 64), Math.max(S, 64), BufferedImage.TYPE_INT_ARGB);
        Graphics2D f = faceBuf.createGraphics();
        f.setComposite(AlphaComposite.Clear);
        f.fillRect(0, 0, faceBuf.getWidth(), faceBuf.getHeight());
        f.setComposite(AlphaComposite.SrcOver);
        f.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        Random rnd = new Random((long) (ms / 70));
        int strips = 18, sh = (int) Math.ceil(S / (double) strips);
        for (int i = 0; i < strips; i++) {
            int dx = rnd.nextDouble() < 0.14 * agitation ? (int) ((rnd.nextDouble() - 0.5) * S * 0.1 * (0.4 + agitation)) : 0;
            f.drawImage(face, dx, i * sh, S + dx, (i + 1) * sh, 0, i * 8 / strips, 8, Math.max(i * 8 / strips + 1, (i + 1) * 8 / strips), null);
        }
        // Melt the edge into nothing, then colour it if asked.
        f.setComposite(AlphaComposite.DstIn);
        f.setPaint(new RadialGradientPaint(S / 2f, S / 2f, S / 2f, new float[]{0.5f, 1f},
                new Color[]{new Color(255, 255, 255, 255), new Color(255, 255, 255, 0)}));
        f.fillRect(0, 0, S, S);
        if (tint != null) {
            f.setComposite(AlphaComposite.SrcAtop);
            f.setColor(tint);
            f.fillRect(0, 0, S, S);
        }
        f.dispose();
        Graphics2D o = (Graphics2D) b.create();
        o.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.min(1, alpha)));
        o.drawImage(faceBuf, (int) (cx - S / 2.0), (int) (cy - S / 2.0), (int) (cx + S / 2.0), (int) (cy + S / 2.0), 0, 0, S, S, null);
        o.dispose();
    }

    /** Big dark hands coming up from the bottom corners at the throat, fingers working. */
    private void drawHands(Graphics2D b, int w, int h, double p, double c) {
        if (p <= 0.01) return;
        for (int side = -1; side <= 1; side += 2) {
            double bx = w * 0.5 + side * w * (0.6 - 0.28 * p);
            double by = h * (1.25 - 0.45 * p);
            double dirX = -side, dirY = -0.9;
            double norm = Math.hypot(dirX, dirY);
            dirX /= norm;
            dirY /= norm;
            b.setColor(new Color(6, 4, 12));
            b.fill(new java.awt.geom.Ellipse2D.Double(bx - h * 0.13, by - h * 0.11, h * 0.26, h * 0.22));
            b.setStroke(new BasicStroke((float) (h * 0.05), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < 4; i++) {
                double spread = (i - 1.5) * 0.28;
                double wig = Math.sin(c * 0.02 + i * 1.3) * 0.12 * p;
                double ang = Math.atan2(dirY, dirX) + spread + wig;
                double len = h * (0.2 + 0.03 * (i == 1 || i == 2 ? 1 : 0));
                b.draw(new java.awt.geom.Line2D.Double(bx, by, bx + Math.cos(ang) * len, by + Math.sin(ang) * len));
            }
            b.setColor(new Color(170, 90, 240, 190));                        // a violet rim, as if lit by the glitch
            b.setStroke(new BasicStroke(3f));
            b.draw(new java.awt.geom.Ellipse2D.Double(bx - h * 0.13, by - h * 0.11, h * 0.26, h * 0.22));
            for (int i = 0; i < 4; i++) {
                double spread = (i - 1.5) * 0.28;
                double wig = Math.sin(c * 0.02 + i * 1.3) * 0.12 * p;
                double ang = Math.atan2(dirY, dirX) + spread + wig;
                double len = h * (0.2 + 0.03 * (i == 1 || i == 2 ? 1 : 0));
                b.setStroke(new BasicStroke((float) (h * 0.05 + 6), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                b.setColor(new Color(170, 90, 240, 110));
                b.draw(new java.awt.geom.Line2D.Double(bx, by, bx + Math.cos(ang) * len, by + Math.sin(ang) * len));
                b.setStroke(new BasicStroke((float) (h * 0.05), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                b.setColor(new Color(6, 4, 12));
                b.draw(new java.awt.geom.Line2D.Double(bx, by, bx + Math.cos(ang) * len, by + Math.sin(ang) * len));
            }
            b.setColor(new Color(6, 4, 12));
            b.fill(new java.awt.geom.Ellipse2D.Double(bx - h * 0.125, by - h * 0.105, h * 0.25, h * 0.21));
        }
    }

    private static void vignette(Graphics2D b, int w, int h, double radius, int darkness) {
        radius = Math.max(4, radius);
        b.setPaint(new RadialGradientPaint((float) (w / 2.0), (float) (h * 0.45), (float) radius, new float[]{0.35f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, darkness)}));
        b.fillRect(0, 0, w, h);
        // Outside the circle it is black.
        b.setColor(new Color(0, 0, 0, darkness));
        java.awt.geom.Area outside = new java.awt.geom.Area(new java.awt.geom.Rectangle2D.Double(0, 0, w, h));
        outside.subtract(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(w / 2.0 - radius, h * 0.45 - radius, radius * 2, radius * 2)));
        b.fill(outside);
    }

    private static void ghosted(Graphics2D g, String text, double cx, double y, double size, double alpha, boolean angry) {
        g.setFont(new Font(Font.DIALOG, Font.BOLD, (int) size));
        int tw = g.getFontMetrics().stringWidth(text);
        int x = (int) (cx - tw / 2.0);
        int a = (int) (255 * Math.max(0, Math.min(1, alpha)));
        g.setColor(new Color(255, 40, 80, a / 2));
        g.drawString(text, x - 5, (int) y + 2);
        g.setColor(new Color(40, 220, 255, a / 2));
        g.drawString(text, x + 5, (int) y - 2);
        g.setColor(angry ? new Color(255, 120, 130, a) : new Color(255, 255, 255, a));
        g.drawString(text, x, (int) y);
    }
}
