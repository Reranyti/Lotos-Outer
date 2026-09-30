package com.lotusblight.overlay;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * The closing cutscene, after the results of the third song: a portal opens into Minecraft, Honcho throws
 * the Glitcher into it, two last lines are said, and the signal is lost. When it has played out the process
 * ends with {@link #EXIT_CODE}, which tells the game to put the horn in the player's hands; the game's own
 * window is brought back if it was hidden.
 *
 * <p>It runs on the wall clock, like the cutscene between the songs, and the scene is a pure function of that
 * clock, so any moment of it can be previewed with {@code --finale SECONDS}.
 */
final class Finale3 {
    /** The process ends with this when the scene has played out (the game checks for it: NormalBranchReward). */
    static final int EXIT_CODE = 77;

    /** {from ms, to ms, Japanese, English, speaker (0 nobody, 1 Honcho, 2 the Glitcher)} - the captions. */
    private static final Object[][] LINES = {
            {5_600.0, 8_300.0, "お前はまだ十分に強くない", "You're still not strong enough", 2},
            {9_000.0, 12_400.0, "最初の一つはお前のものだ。失せろ", "You have the first part. Get lost", 2},
    };

    /** {from ms, to ms, kind} - what happens besides the words; drawBeat paints each kind. */
    private static final Object[][] BEATS = {
            {2_000.0, 12_000.0, "portal"},
            {13_200.0, 16_200.0, "signal"},
    };

    private static final double PORTAL_FROM_MS = 2_000;
    private static final double PORTAL_OPEN_MS = 2_600, PORTAL_CLOSE_FROM = 10_500, PORTAL_CLOSE_MS = 1_500;
    private static final double THROW_FROM = 4_800, THROW_TO = 7_000;
    private static final double FADE_IN_MS = 1_800, FADE_OUT_MS = 500, LEAD_OUT_MS = 300;
    private static final double SIGNAL_FROM = 13_200, SIGNAL_TO = 16_200;

    private final BufferedImage desktop;
    private final int[] glitcherSkin, honchoSkin;
    private final Actor[] rigs;
    private final SoftRenderer[] figures = new SoftRenderer[2];
    private final double unit;
    private final long startNano = System.nanoTime();
    private double seekMs;

    Finale3(int[] glitcherSkin, int[] honchoSkin, BufferedImage desktop, int screenH) {
        this.glitcherSkin = glitcherSkin;
        this.honchoSkin = honchoSkin;
        this.desktop = desktop;
        this.unit = screenH * 0.36 / 32.0 * 0.6;
        this.rigs = new Actor[]{new Actor(honchoSkin, true), new Actor(glitcherSkin, false)};
        for (int i = 0; i < 2; i++) figures[i] = new SoftRenderer((int) (30 * unit), (int) (36 * unit));
    }

    /** Starts the scene at a given second (for previewing). */
    void seek(double seconds) {
        seekMs = seconds * 1000 - (System.nanoTime() - startNano) / 1e6;
    }

    private double now() {
        return (System.nanoTime() - startNano) / 1e6 + seekMs;
    }

    private static double length() {
        double end = 0;
        for (Object[] l : LINES) end = Math.max(end, (Double) l[1]);
        for (Object[] b : BEATS) end = Math.max(end, (Double) b[1]);
        return end + LEAD_OUT_MS;
    }

    /** True once the last of it has faded to black. */
    boolean done() {
        return now() >= length() + FADE_OUT_MS;
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    // The portal stands to the right of the two of them.
    private static double portalX(int w) { return w * 0.80; }

    private static double portalY(int h) { return h * 0.46; }

    void render(Graphics2D g, int w, int h) {
        double c = now();
        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        b.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // The stage: the real desktop, dimmed, with a soft light on the floor where they stand.
        b.setColor(new Color(4, 3, 10));
        b.fillRect(0, 0, w, h);
        if (desktop != null) {
            b.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
            b.drawImage(desktop, 0, 0, w, h, null);
            b.setComposite(AlphaComposite.SrcOver);
        }
        b.setPaint(new GradientPaint(0, 0, new Color(4, 3, 12, 150), 0, h, new Color(10, 6, 24, 200)));
        b.fillRect(0, 0, w, h);
        b.setPaint(new RadialGradientPaint((float) (w * 0.5), (float) (h * 0.78), (float) (w * 0.42), new float[]{0f, 1f},
                new Color[]{new Color(150, 120, 220, 70), new Color(150, 120, 220, 0)}));
        b.fillRect(0, 0, w, h);

        boolean signal = c >= SIGNAL_FROM;
        if (!signal) {
            for (Object[] beat : BEATS) {
                if ("portal".equals(beat[2]) && c >= (Double) beat[0] && c < (Double) beat[1]) drawPortal(b, w, h, c);
            }
            drawFigure(b, w, h, c, 0);
            drawFigure(b, w, h, c, 1);
            for (Object[] line : LINES) {
                if (c >= (Double) line[0] && c < (Double) line[1]) caption(b, w, h, c, line);
            }
        }
        for (Object[] beat : BEATS) {
            if ("signal".equals(beat[2]) && c >= (Double) beat[0] && c < (Double) beat[1]) drawSignal(b, w, h, c);
        }

        // In from black, out to black.
        double in = Math.min(1, c / FADE_IN_MS);
        double out = Math.max(0, Math.min(1, (c - length()) / FADE_OUT_MS));
        double dark = Math.max(1 - in, out);
        if (dark > 0) {
            b.setColor(new Color(0, 0, 0, (int) (255 * dark)));
            b.fillRect(0, 0, w, h);
        }
        b.dispose();
    }

    private void drawFigure(Graphics2D g, int w, int h, double c, int who) {
        double feetY = h * 0.86;
        double x = w * (who == 0 ? 0.40 : 0.60);
        double t = c / 1000.0;
        double sway = Math.sin(t * 1.1 + who * 1.7), breath = Math.sin(t * 1.6 + who);
        double tumble = 0, scale = 1;
        Actor a = rigs[who];
        Pose15 p = a.begin();
        Rig15.Joint torso = Rig15.Joint.UPPER_TORSO, head = Rig15.Joint.HEAD;
        if (who == 0) {
            // Honcho: he crouches and draws his hands back, sweeps them forward as the Glitcher goes, follows
            // through with open hands, then lets his arms fall and watches the portal.
            double rel = c - THROW_FROM;                                 // ms from the moment of the throw
            double wind = smooth((rel + 900) / 600.0) * (1 - smooth((rel + 300) / 300.0));
            double sweep = smooth((rel + 300) / 300.0) * (1 - smooth((rel - 200) / 500.0));
            double hold = smooth((rel - 100) / 300.0) * (1 - smooth((rel - 700) / 700.0));
            double act = Math.max(wind, Math.max(sweep, hold));
            double lunge = smooth((c - (THROW_FROM - 600)) / 500.0) * (1 - smooth((c - (THROW_FROM + 900)) / 900.0));
            x += w * 0.06 * lunge;
            a.viewYaw = 0.55 - 0.2 * lunge;
            // Feet planted, the hips dropping into the crouch and coming up with the throw.
            p.reach(Rig15.Limb.R_LEG, -2.6, 3, -1.5, 1).reach(Rig15.Limb.L_LEG, 2.6, 3, 2.0, 1);
            p.rootPos[1] = -0.8 - 1.4 * wind + 0.4 * sweep;
            p.rootPos[2] = 0.8 * sweep;
            double[] back = {2.8, 16.5, -2.5}, fwd = {2.5, 23, 8.8}, through = {2.4, 25, 9.0}, rest = {5.4, 14, 1.0};
            double gx = rest[0] + (back[0] - rest[0]) * wind + (fwd[0] - rest[0]) * sweep + (through[0] - rest[0]) * hold;
            double gy = rest[1] + (back[1] - rest[1]) * wind + (fwd[1] - rest[1]) * sweep + (through[1] - rest[1]) * hold;
            double gz = rest[2] + (back[2] - rest[2]) * wind + (fwd[2] - rest[2]) * sweep + (through[2] - rest[2]) * hold;
            double weight = Math.min(1, act * 1.2);
            p.reach(Rig15.Limb.R_ARM, -gx, gy, gz, weight).reach(Rig15.Limb.L_ARM, gx, gy, gz, weight);
            p.turn(torso, 3 + 1.2 * breath - 10 * wind + 20 * sweep - 4 * hold, 10 * wind - 14 * sweep, 0);
            p.turn(head, -2 + 6 * wind - 12 * sweep, 0, 0);
            p.turn(Rig15.Joint.R_UPPER_ARM, 3 * sway, 0, -6).turn(Rig15.Joint.L_UPPER_ARM, -3 * sway, 0, 6);
            p.turn(Rig15.Joint.R_LOWER_ARM, -8, 0, 0).turn(Rig15.Joint.L_LOWER_ARM, -8, 0, 0);
            a.fistR = a.fistL = 0.85 * wind + 0.6 * sweep;               // the fists close as he draws back, open on the release
            a.spread = 0.3 + 0.7 * hold;
        } else {
            double e = (c - THROW_FROM) / (THROW_TO - THROW_FROM);
            if (e >= 1) return;                                          // gone through
            a.viewYaw = -0.55;
            if (e <= 0) {
                // Waiting: standing, arms loose, the head down a little.
                p.reach(Rig15.Limb.R_LEG, -2.4, 3, 0, 1).reach(Rig15.Limb.L_LEG, 2.4, 3, 0, 1);
                p.rootPos[1] = -0.6;
                p.turn(torso, 2 + breath, 0, 0).turn(head, 6, 0, 0);
                p.turn(Rig15.Joint.R_UPPER_ARM, 4 * sway, 0, -5).turn(Rig15.Joint.L_UPPER_ARM, -4 * sway, 0, 5);
                p.turn(Rig15.Joint.R_LOWER_ARM, -6, 0, 0).turn(Rig15.Joint.L_LOWER_ARM, -6, 0, 0);
            } else {
                // Thrown: over in an arc, turning, growing small as it goes into the portal; the body goes limp, the limbs trailing.
                double ee = e * e * (3 - 2 * e);
                x = w * 0.60 + (portalX(w) - w * 0.60) * ee;
                feetY = h * 0.86 + (portalY(h) + h * 0.12 - h * 0.86) * ee - Math.sin(Math.PI * ee) * h * 0.16;
                tumble = ee * Math.PI * 3.5;
                scale = 1 - 0.85 * ee * ee;
                double fl = Math.sin(e * 23), fl2 = Math.sin(e * 19 + 1.3), fl3 = Math.sin(e * 27 + 2.1);
                p.turn(torso, -8 + 6 * fl2, 8 * fl3, 5 * fl);
                p.turn(head, -25 + 14 * fl, 12 * fl2, 8 * fl3);
                p.turn(Rig15.Joint.R_UPPER_ARM, -150 + 40 * fl, 0, -40 + 25 * fl2).turn(Rig15.Joint.L_UPPER_ARM, -130 + 40 * fl2, 0, 40 + 25 * fl3);
                p.turn(Rig15.Joint.R_LOWER_ARM, -40 - 25 * fl3, 0, 0).turn(Rig15.Joint.L_LOWER_ARM, -55 - 25 * fl, 0, 0);
                p.turn(Rig15.Joint.R_UPPER_LEG, -45 + 35 * fl2, 0, 6).turn(Rig15.Joint.L_UPPER_LEG, 25 + 35 * fl3, 0, -6);
                p.turn(Rig15.Joint.R_LOWER_LEG, 55 + 25 * fl, 0, 0).turn(Rig15.Joint.L_LOWER_LEG, 35 + 25 * fl2, 0, 0);
                p.turn(Rig15.Joint.R_FOOT, 25, 0, 0).turn(Rig15.Joint.L_FOOT, 35, 0, 0);
                a.fistR = a.fistL = 0.1;
                a.spread = 1;
            }
        }
        SoftRenderer r = figures[who];
        r.clear();
        a.draw(r, unit, r.width / 2.0, r.height - 2 * unit);
        Graphics2D d = (Graphics2D) g.create();
        double sc = scale / 0.6;
        d.translate(x, feetY);
        d.rotate(tumble, 0, -h * 0.16 * scale);
        d.scale(sc, sc);
        d.drawImage(r.image, -r.width / 2, -r.height + (int) (2 * unit), null);
        d.dispose();
    }

    /** The portal, to the right of the two of them. */
    private void drawPortal(Graphics2D g, int w, int h, double c) {
        double a = smooth((c - PORTAL_FROM_MS) / PORTAL_OPEN_MS) * (1 - smooth((c - PORTAL_CLOSE_FROM) / PORTAL_CLOSE_MS));
        PortalFx.draw(g, portalX(w), portalY(h), h * 0.13, h * 0.27, a, c / 1000.0, h);
    }

    /** "SIGNAL LOST": static, scanlines, the words split in colour and flickering, then black. */
    private void drawSignal(Graphics2D g, int w, int h, double c) {
        double k = (c - SIGNAL_FROM) / (SIGNAL_TO - SIGNAL_FROM);
        Random r = new Random((long) (c / 55));
        Graphics2D d = (Graphics2D) g.create();
        double cover = Math.min(1, k / 0.12);
        d.setColor(new Color(0, 0, 0, (int) (255 * cover)));
        d.fillRect(0, 0, w, h);
        double noise = k < 0.88 ? 1 : Math.max(0, 1 - (k - 0.88) / 0.12);
        for (int i = 0; i < (int) (260 * noise); i++) {
            int gv = 40 + r.nextInt(200);
            d.setColor(new Color(gv, gv, gv, 40 + r.nextInt(90)));
            d.fillRect(r.nextInt(w), r.nextInt(h), 6 + r.nextInt(w / 8), 1 + r.nextInt(3));
        }
        d.setColor(new Color(0, 0, 0, 70));
        for (int y = 0; y < h; y += 4) d.fillRect(0, y, w, 1);
        if (k < 0.9 && r.nextDouble() > 0.12) {
            d.setFont(new Font(Font.MONOSPACED, Font.BOLD, (int) (h * 0.13)));
            FontMetrics fm = d.getFontMetrics();
            String text = "SIGNAL LOST";
            int tw = fm.stringWidth(text);
            int jx = (int) ((r.nextDouble() - 0.5) * h * 0.02 * (1 + 3 * Math.max(0, k - 0.6)));
            int x = (w - tw) / 2 + jx, y = (int) (h * 0.54);
            d.setColor(new Color(255, 40, 80, 170));
            d.drawString(text, x - 5, y);
            d.setColor(new Color(40, 230, 255, 170));
            d.drawString(text, x + 5, y);
            d.setColor(new Color(240, 240, 245));
            d.drawString(text, x, y);
        }
        d.dispose();
    }

    /** A caption at the bottom: the English line under the Japanese one, tinted by who speaks. */
    private void caption(Graphics2D g, int w, int h, double c, Object[] line) {
        double from = (Double) line[0], to = (Double) line[1];
        double a = Math.min(1, Math.min((c - from) / 250.0, (to - c) / 300.0));
        if (a <= 0) return;
        String jp = (String) line[2], en = (String) line[3];
        int speaker = line.length > 4 ? (Integer) line[4] : 0;
        Color tint = speaker == 1 ? new Color(255, 236, 190) : speaker == 2 ? new Color(230, 170, 255) : new Color(240, 240, 250);
        Graphics2D d = (Graphics2D) g.create();
        d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) a));
        int size = (int) (h * 0.034);
        d.setFont(new Font(Font.DIALOG, Font.BOLD, size));
        FontMetrics fm = d.getFontMetrics();
        int jw = fm.stringWidth(jp);
        d.setFont(new Font(Font.DIALOG, Font.PLAIN, (int) (size * 0.8)));
        FontMetrics fe = d.getFontMetrics();
        int ew = fe.stringWidth(en);
        int boxW = Math.max(jw, ew) + (int) (h * 0.08), boxH = (int) (h * 0.11);
        int bx = (w - boxW) / 2, by = (int) (h * 0.86);
        d.setColor(new Color(0, 0, 0, 150));
        d.fillRoundRect(bx, by, boxW, boxH, 18, 18);
        d.setFont(new Font(Font.DIALOG, Font.BOLD, size));
        d.setColor(tint);
        d.drawString(jp, (w - jw) / 2, by + (int) (boxH * 0.44));
        d.setFont(new Font(Font.DIALOG, Font.PLAIN, (int) (size * 0.8)));
        d.setColor(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 210));
        d.drawString(en, (w - ew) / 2, by + (int) (boxH * 0.85));
        d.dispose();
    }
}
