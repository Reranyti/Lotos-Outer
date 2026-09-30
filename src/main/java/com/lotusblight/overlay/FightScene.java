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
import java.util.Random;

/**
 * Honcho and the Glitcher fighting as they fall. It goes on by itself - every few seconds they trade a blow
 * whatever the player does - and the circles steer it: a circle hit is Honcho's blow, one missed is the
 * Glitcher's, a long run of hits is a special. The fight changes with the song: at the edges first, then
 * drifting in waves, then the Glitcher starts to tear, and at the end they close in on the middle.
 */
final class FightScene {
    private enum Move { PUNCH, KICK, UPPER, DASH, SLAM, GRAB, BLAST }

    private static final Move[] HONCHO_MOVES = {Move.PUNCH, Move.KICK, Move.UPPER, Move.DASH, Move.SLAM};
    private static final Move[] GLITCHER_MOVES = {Move.PUNCH, Move.GRAB, Move.KICK, Move.BLAST, Move.DASH};

    private static final int RIGHT_ARM = SkinModel.Part.RIGHT_ARM.ordinal();
    private static final int LEFT_ARM = SkinModel.Part.LEFT_ARM.ordinal();
    private static final int RIGHT_LEG = SkinModel.Part.RIGHT_LEG.ordinal();
    private static final int LEFT_LEG = SkinModel.Part.LEFT_LEG.ordinal();
    private static final int HEAD = SkinModel.Part.HEAD.ordinal();
    private static final int BODY = SkinModel.Part.BODY.ordinal();

    private static final double AMBIENT_EVERY = 7_000, AMBIENT_FIRST = 3_500, AMBIENT_LEN = 1_600;
    private static final double MOVE_LEN = 420;

    // The figures are drawn small by software and stretched, which is plenty for blocky skins and much lighter.
    private static final double RES = 0.5;

    private final Figure[] figures;
    private final int[] honchoSkin, glitcherSkin;
    private final double unit;
    private final SoftRenderer[] renderers = new SoftRenderer[2];
    private final SoftRenderer.Pose pose = new SoftRenderer.Pose();

    private double seenHit = -1e9, seenMiss = -1e9, hMoveAt = -1e9, gMoveAt = -1e9, specialAt = -1e9;
    private int hits, misses, lastMile;
    private Move hMove = Move.PUNCH, gMove = Move.PUNCH;
    // Where each figure is this frame, for the effects drawn between them.
    private double hx, hy, gx, gy;

    FightScene(int[] honchoSkin, int[] glitcherSkin, int screenH) {
        this.honchoSkin = honchoSkin;
        this.glitcherSkin = glitcherSkin;
        this.unit = screenH * 0.42 / 32.0;
        this.figures = new Figure[]{new Figure(honchoSkin, true), new Figure(glitcherSkin, false)};
        for (int i = 0; i < 2; i++) renderers[i] = new SoftRenderer((int) (36 * unit * RES), (int) (40 * unit * RES));
    }

    /**
     * @param lastHit  the clock time of the player's last hit circle
     * @param lastMiss the clock time of the last circle missed
     * @param combo    the current run of hits
     */
    void render(Graphics2D g, int w, int h, double ms, double lastHit, double lastMiss, int combo) {
        // What just happened chooses the next move, each time a different one.
        if (lastHit > seenHit) { seenHit = lastHit; hMove = HONCHO_MOVES[hits++ % HONCHO_MOVES.length]; hMoveAt = lastHit; }
        if (lastMiss > seenMiss) { seenMiss = lastMiss; gMove = GLITCHER_MOVES[misses++ % GLITCHER_MOVES.length]; gMoveAt = lastMiss; }
        int mile = combo / 25;
        if (mile > lastMile) specialAt = ms;
        lastMile = mile;

        // Their own exchange, whatever the player does.
        double amb = ms - AMBIENT_FIRST;
        int ambKind = amb < 0 ? -1 : (int) (amb / AMBIENT_EVERY) % 3;
        double ambU = amb < 0 ? -1 : (amb % AMBIENT_EVERY);
        boolean ambient = ambKind >= 0 && ambU < AMBIENT_LEN;
        Move ambG = ambKind == 0 ? Move.BLAST : ambKind == 1 ? Move.DASH : Move.PUNCH;
        double ambGBell = ambient ? bell(ambU, 0, AMBIENT_LEN) : 0;

        double specialU = ms - specialAt;
        boolean special = specialU >= 0 && specialU < 1000;
        double hBell = bell(ms - hMoveAt, 0, MOVE_LEN);
        double gBell = Math.max(bell(ms - gMoveAt, 0, MOVE_LEN), ambient && ambKind != 2 ? ambGBell : 0);
        Move gNow = ms - gMoveAt < MOVE_LEN && gBell >= bell(ms - gMoveAt, 0, MOVE_LEN) - 1e-9 ? gMove : ambG;
        if (ms - gMoveAt >= MOVE_LEN) gNow = ambG;
        // A clash in the middle on the third kind of exchange: both go for it.
        boolean clash = ambient && ambKind == 2;
        double clashBell = clash ? ambGBell : 0;

        // Song stages: the pull towards the middle at the end, waves in the middle of the song.
        double t = ms / 1000.0;
        double closeness = smooth((ms - 165_000) / 30_000.0);
        double waves = smooth((ms - 55_000) / 15_000.0);
        double tearing = smooth((ms - 145_000) / 8_000.0);

        // Screen shake for what lands.
        double shake = Math.max(Math.max(impact(ms - hMoveAt), impact(ms - gMoveAt)), special && specialU > 350 ? 1 : 0);
        Random sr = new Random((long) (ms / 40));
        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        b.translate((sr.nextDouble() - 0.5) * shake * h * 0.012, (sr.nextDouble() - 0.5) * shake * h * 0.012);

        double baseH = w * (0.135 + 0.13 * closeness), baseG = w * (0.865 - 0.13 * closeness);
        double waveH = Math.sin(t * 0.8) * h * 0.06 * waves, waveG = Math.sin(t * 0.8 + 2.2) * h * 0.06 * waves;

        // ---- Honcho ----
        double hDx = 0, hDy = 0;
        Move hPlay = hMove;
        double hB = hBell;
        if (special) hDx = 0;                                         // he stays put and charges
        if (hB > 0) {
            if (hPlay == Move.DASH) hDx = w * 0.13 * hB;
            if (hPlay == Move.UPPER) hDy = -h * 0.05 * hB;
            if (hPlay == Move.SLAM) hDy = -h * 0.07 * Math.sin(Math.PI * Math.min(1, (ms - hMoveAt) / MOVE_LEN));
        }
        double dodge = ambient && ambKind == 0 ? Math.sin(Math.PI * Math.min(1, ambU / 800.0)) * h * 0.11 : 0;   // he sidesteps the beam
        double block = ambient && ambKind == 1 ? bell(ambU, 300, 700) : 0;
        double clashX = clash ? w * 0.19 * clashBell : 0;
        hx = baseH + hDx + clashX;
        hy = h * 0.72 + Math.sin(t * 1.3) * h * 0.02 + waveH + hDy - dodge;
        // ---- the Glitcher ----
        double gDx = 0, gDy = 0;
        if (gBell > 0) {
            if (gNow == Move.DASH) gDx = -w * 0.14 * gBell;
            if (gNow == Move.GRAB) gDx = -w * 0.04 * gBell;
        }
        double gBack = special ? Math.sin(Math.PI * Math.min(1, specialU / 900.0)) * w * 0.07 : 0;
        double tearPhase = tearing > 0 && sr.nextDouble() < 0.06 * tearing ? (sr.nextDouble() - 0.5) * w * 0.05 : 0;
        gx = baseG + gDx + gBack + tearPhase - (clash ? w * 0.19 * clashBell : 0);
        gy = h * 0.72 + Math.sin(t * 1.3 + 1) * h * 0.02 + waveG + gDy;

        // Things between them, behind the figures.
        if (special) drawSpecial(b, w, h, specialU);
        if (ambient && ambKind == 0 && ambU > 350 && ambU < 1100) drawBeam(b, gx - 2 * unit, gy - 18 * unit, hx + w * 0.02, hy - 18 * unit - dodge * 0.0, new Color(200, 90, 255), h * 0.03, ambU);
        if (gNow == Move.BLAST && gBell > 0.3 && ms - gMoveAt < MOVE_LEN) drawBeam(b, gx - 2 * unit, gy - 18 * unit, hx, hy - 18 * unit, new Color(255, 70, 110), h * 0.025, ms);
        if (hPlay == Move.DASH && hB > 0.05) drawStreaks(b, hx - w * 0.13 * hB, hx, hy - 16 * unit, new Color(255, 245, 200), h);
        if (gNow == Move.DASH && gBell > 0.05) drawStreaks(b, gx, gx + w * 0.14 * gBell, gy - 16 * unit, new Color(255, 70, 110), h);

        drawFighter(b, w, h, ms, 0, hx, hy, Move.values()[hPlay.ordinal()], hB, special, gBell, ambient && ambKind == 1, block, dodge > 0, clash ? clashBell : 0, 0);
        drawFighter(b, w, h, ms, 1, gx, gy, gNow, gBell, false, hB, false, 0, false, clash ? clashBell : 0, tearing);

        // Flashes where blows land.
        if (hB > 0.5 && hPlay != Move.SLAM) flash(b, gx - 5 * unit, gy - 16 * unit, h * 0.1 * hB, new Color(255, 245, 200));
        if (hPlay == Move.SLAM && hB > 0.3) shockRing(b, gx, gy + 8 * unit, hB, h);
        if (gBell > 0.5 && gNow != Move.BLAST) flash(b, hx + 5 * unit, hy - 16 * unit, h * 0.1 * gBell, new Color(255, 70, 110));
        if (clash && clashBell > 0.8) flash(b, w / 2.0, h * 0.5, h * 0.2 * clashBell, new Color(255, 255, 255));
        b.dispose();
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** 0..1..0 over {@code len} ms starting at {@code start} (0 outside). */
    private static double bell(double t, double start, double len) {
        double f = (t - start) / len;
        return f <= 0 || f >= 1 ? 0 : Math.sin(Math.PI * f);
    }

    /** The jolt of a blow: sharp at first, gone in a fifth of a second. */
    private static double impact(double since) {
        return since < 0 || since > 250 ? 0 : 1 - since / 250.0;
    }

    private void drawFighter(Graphics2D g, int w, int h, double ms, int who, double cx, double cy, Move move, double bellV,
                             boolean charging, double otherBell, boolean blocking, double blockV, boolean dodging, double clashV, double tearing) {
        double t = ms / 1000.0;
        double face = who == 0 ? 1.0 : -1.0;
        double sway = Math.sin(t * 1.7 + who * 2.1);
        pose.reset();
        pose.yaw = face * 1.15;
        // Falling: arms up and back, legs trailing.
        pose.partPitch[RIGHT_ARM] = 0.5 + 0.3 * sway;
        pose.partPitch[LEFT_ARM] = 0.7 - 0.3 * sway;
        pose.partRoll[RIGHT_ARM] = -0.5;
        pose.partRoll[LEFT_ARM] = 0.5;
        pose.partPitch[RIGHT_LEG] = -0.35 + 0.25 * sway;
        pose.partPitch[LEFT_LEG] = 0.35 - 0.25 * sway;
        pose.pitch = 0.25 * sway;
        double tilt = face * 0.18 + 0.1 * sway * face;
        double b = bellV;
        if (b > 0 || clashV > 0) {
            double k = Math.max(b, clashV);
            switch (move) {
                case PUNCH -> { pose.partPitch[RIGHT_ARM] = -1.5 * k + 0.5 * (1 - k); pose.partRoll[RIGHT_ARM] = 0; pose.partPitch[BODY] = -0.25 * k; }
                case KICK -> { pose.partPitch[RIGHT_LEG] = -1.5 * k; pose.partPitch[LEFT_LEG] = 0.5 * k; pose.pitch = 0.35 * k; tilt += face * 0.3 * k; }
                case UPPER -> { pose.partPitch[RIGHT_ARM] = -2.4 * k + 0.5 * (1 - k); pose.partRoll[RIGHT_ARM] = 0; pose.pitch = -0.35 * k; tilt -= face * 0.25 * k; }
                case DASH -> { pose.partPitch[RIGHT_ARM] = -1.5 * k; pose.partPitch[LEFT_ARM] = -1.5 * k; pose.partRoll[RIGHT_ARM] = 0; pose.partRoll[LEFT_ARM] = 0; pose.pitch = -0.4 * k; tilt = face * 0.5 * k; }
                case SLAM -> { pose.partPitch[RIGHT_ARM] = -2.8 * (1 - k * 0.2) + 3.0 * k * 0.6; pose.partPitch[LEFT_ARM] = pose.partPitch[RIGHT_ARM]; pose.partRoll[RIGHT_ARM] = 0; pose.partRoll[LEFT_ARM] = 0; pose.pitch = 0.4 * k; }
                case GRAB -> { pose.partPitch[RIGHT_ARM] = -1.4 * k; pose.partPitch[LEFT_ARM] = -1.4 * k; pose.partRoll[RIGHT_ARM] = 0.2; pose.partRoll[LEFT_ARM] = -0.2; }
                case BLAST -> { pose.partPitch[RIGHT_ARM] = -1.55 * k; pose.partPitch[LEFT_ARM] = -1.55 * k; pose.partRoll[RIGHT_ARM] = 0.1; pose.partRoll[LEFT_ARM] = -0.1; }
            }
        }
        if (charging) {                                                       // Honcho gathering himself for a special
            pose.partPitch[RIGHT_ARM] = -2.2;
            pose.partPitch[LEFT_ARM] = -2.2;
            pose.partRoll[RIGHT_ARM] = -0.3;
            pose.partRoll[LEFT_ARM] = 0.3;
        }
        if (blocking && blockV > 0) {                                         // arms crossed in front to take the blow
            pose.partPitch[RIGHT_ARM] = -1.3;
            pose.partPitch[LEFT_ARM] = -1.3;
            pose.partRoll[RIGHT_ARM] = 0.8;
            pose.partRoll[LEFT_ARM] = -0.8;
        }
        if (dodging) { pose.pitch = 0.5; tilt -= face * 0.3; }
        if (otherBell > 0.3 && b <= 0) pose.partPitch[HEAD] = 0.5 * otherBell;   // rocked by the other's blow

        SoftRenderer r = renderers[who];
        r.clear();
        figures[who].draw(r, pose, unit * RES, r.width / 2.0, r.height - 6 * unit * RES);
        Graphics2D d = (Graphics2D) g.create();
        d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        AffineTransform at = new AffineTransform();
        at.translate(cx, cy);
        at.rotate(tilt);
        d.transform(at);
        d.scale(1 / RES, 1 / RES);
        // The Glitcher, torn in the middle of the song: doubled in cyan and magenta, flickering.
        if (who == 1 && tearing > 0) {
            Random fr = new Random((long) (ms / 70));
            d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (0.35 * tearing)));
            d.drawImage(r.image, -r.width / 2 - (int) (h * 0.02 * tearing * RES), -r.height + (int) (6 * unit * RES), null);
            d.drawImage(r.image, -r.width / 2 + (int) (h * 0.02 * tearing * RES), -r.height + (int) (6 * unit * RES), null);
            d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fr.nextDouble() < 0.07 * tearing ? 0.35f : 1f));
        } else if (who == 0 && charging) {
            d.setPaint(new RadialGradientPaint(0f, (float) (-16 * unit * RES), (float) (14 * unit * RES), new float[]{0f, 1f},
                    new Color[]{new Color(255, 240, 180, 150), new Color(255, 240, 180, 0)}));
            d.fill(new java.awt.geom.Ellipse2D.Double(-14 * unit, -30 * unit, 28 * unit, 28 * unit));
        }
        d.drawImage(r.image, -r.width / 2, -r.height + (int) (6 * unit * RES), null);
        d.dispose();
    }

    /** Honcho's special: he charges, then a beam of light crosses the whole screen into the Glitcher. */
    private void drawSpecial(Graphics2D b, int w, int h, double u) {
        if (u < 350) {
            double f = u / 350.0;
            flash(b, hx + 6 * unit, hy - 18 * unit, h * 0.08 * (0.3 + f), new Color(255, 240, 180));
        } else if (u < 800) {
            double f = (u - 350) / 450.0;
            drawBeam(b, hx + 6 * unit, hy - 18 * unit, gx - 4 * unit, gy - 16 * unit, new Color(255, 240, 180), h * 0.06 * (1 - 0.6 * f), u);
            flash(b, gx - 4 * unit, gy - 16 * unit, h * 0.22 * f, new Color(255, 250, 220));
        } else {
            double f = (u - 800) / 200.0;
            flash(b, gx - 4 * unit, gy - 16 * unit, h * 0.22 * (1 - f), new Color(255, 250, 220));
        }
    }

    private void drawBeam(Graphics2D b, double x1, double y1, double x2, double y2, Color c, double width, double ms) {
        Random r = new Random((long) (ms / 30));
        java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();
        path.moveTo(x1, y1);
        int segs = 14;
        for (int i = 1; i < segs; i++) {
            double f = i / (double) segs;
            path.lineTo(x1 + (x2 - x1) * f, y1 + (y2 - y1) * f + (r.nextDouble() - 0.5) * width * 0.6);
        }
        path.lineTo(x2, y2);
        b.setStroke(new BasicStroke((float) width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        b.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 70));
        b.draw(path);
        b.setStroke(new BasicStroke((float) (width * 0.4), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        b.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 200));
        b.draw(path);
        b.setStroke(new BasicStroke((float) (width * 0.14), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        b.setColor(Color.WHITE);
        b.draw(path);
    }

    private void drawStreaks(Graphics2D b, double x1, double x2, double y, Color c, int h) {
        Random r = new Random(7);
        for (int i = 0; i < 6; i++) {
            double yy = y + (r.nextDouble() - 0.5) * h * 0.18;
            b.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 90));
            b.setStroke(new BasicStroke(2f + r.nextInt(3)));
            b.draw(new java.awt.geom.Line2D.Double(x1, yy, x2, yy));
        }
    }

    private static final java.util.Map<Integer, BufferedImage> GLOWS = new java.util.HashMap<>();

    /** A soft round glow: a small gradient picture per colour, made once and stretched (a live gradient fill is slow). */
    private static void flash(Graphics2D b, double x, double y, double r, Color c) {
        BufferedImage img = GLOWS.computeIfAbsent(c.getRGB(), k -> {
            BufferedImage im = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gg = im.createGraphics();
            gg.setPaint(new RadialGradientPaint(48f, 48f, 48f, new float[]{0f, 1f},
                    new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), 210), new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
            gg.fillRect(0, 0, 96, 96);
            gg.dispose();
            return im;
        });
        r = Math.max(2, r);
        b.drawImage(img, (int) (x - r), (int) (y - r), (int) (r * 2), (int) (r * 2), null);
    }

    private static void shockRing(Graphics2D b, double x, double y, double f, int h) {
        double r = h * 0.05 + h * 0.2 * (1 - f);
        b.setStroke(new BasicStroke((float) (6 * f + 1)));
        b.setColor(new Color(255, 245, 200, (int) (200 * f)));
        b.draw(new java.awt.geom.Ellipse2D.Double(x - r, y - r * 0.3, r * 2, r * 0.6));
    }
}
