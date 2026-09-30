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

    private final Actor[] actors;
    private final int[] honchoSkin, glitcherSkin;
    private final double unit;
    private final SoftRenderer[] renderers = new SoftRenderer[2];

    private double seenHit = -1e9, seenMiss = -1e9, hMoveAt = -1e9, gMoveAt = -1e9, specialAt = -1e9;
    private int hits, misses, lastMile;
    private Move hMove = Move.PUNCH, gMove = Move.PUNCH;
    // Where each figure is this frame, for the effects drawn between them.
    private double hx, hy, gx, gy;

    FightScene(int[] honchoSkin, int[] glitcherSkin, int screenH) {
        this.honchoSkin = honchoSkin;
        this.glitcherSkin = glitcherSkin;
        this.unit = screenH * 0.42 / 32.0;
        this.actors = new Actor[]{new Actor(honchoSkin, true), new Actor(glitcherSkin, false)};
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

        double hU = Math.max(0, Math.min(1, (ms - hMoveAt) / MOVE_LEN)), clashU = clash ? ambU / AMBIENT_LEN : 0;
        double chargeK = special ? Math.min(1, specialU / 350.0) * (specialU > 800 ? 1 - (specialU - 800) / 200.0 : 1) : 0;
        drawFighter(b, w, h, ms, 0, hx, hy, Move.values()[hPlay.ordinal()], hB, hU, chargeK, gBell, ambient && ambKind == 1, block, dodge > 0, clash ? clashBell : 0, clashU, 0);
        boolean gFromMove = ms - gMoveAt < MOVE_LEN;
        double gU = gFromMove ? Math.max(0, Math.min(1, (ms - gMoveAt) / MOVE_LEN)) : (ambient ? ambU / AMBIENT_LEN : 0);
        drawFighter(b, w, h, ms, 1, gx, gy, gNow, gBell, gU, 0, hB, false, 0, false, clash ? clashBell : 0, clashU, tearing);

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

    private void drawFighter(Graphics2D g, int w, int h, double ms, int who, double cx, double cy, Move move, double bellV, double moveU,
                             double chargeK, double otherBell, boolean blocking, double blockV, boolean dodging, double clashV, double clashU, double tearing) {
        boolean charging = chargeK > 0.01;
        double b = bellV;
        double k = Math.max(b, clashV), u = clashV > b ? clashU : moveU;
        Actor actor = actors[who];
        double tilt = poseFighter(actor, who, ms / 1000.0, move, k, u, chargeK, otherBell, blocking ? blockV : 0, dodging);
        SoftRenderer r = renderers[who];
        r.clear();
        actor.draw(r, unit * RES, r.width / 2.0, r.height - 6 * unit * RES);
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

    private static double ramp(double u, double from, double to) {
        return smooth((u - from) / (to - from));
    }

    private static final Rig15.Limb R_ARM = Rig15.Limb.R_ARM, L_ARM = Rig15.Limb.L_ARM, R_LEG = Rig15.Limb.R_LEG, L_LEG = Rig15.Limb.L_LEG;
    private static final Rig15.Joint J_TORSO = Rig15.Joint.UPPER_TORSO, J_HEAD = Rig15.Joint.HEAD;

    /**
     * The pose of a fighter falling, with the move in progress laid over it: the fists and feet are solved to
     * points in the character's own space, the chest twists into the blow, the head follows. {@code k} is how much
     * of a move there is (0..1..0), {@code u} how far through it (0..1). Returns the tilt of the picture.
     */
    private double poseFighter(Actor a, int who, double t, Move move, double k, double u, double chargeK, double otherBell,
                               double block, boolean dodging) {
        double face = who == 0 ? 1.0 : -1.0;
        double sway = Math.sin(t * 1.7 + who * 2.1), fl = Math.sin(t * 2.6 + who * 1.3), fl2 = Math.sin(t * 3.4 + who);
        Pose15 p = a.begin();
        a.viewYaw = face * 1.15;
        a.viewPitch = 0.25 * sway;
        double tilt = face * 0.18 + 0.1 * sway * face;
        double e = smooth(k);
        // The chest and head, collected and added to below.
        double tx = 3 + 3 * sway, ty = 0, tz = 2 * fl, hx = -5 + 4 * fl, hy = 0;
        // Falling: arms up and back with the elbows loose, the legs trailing with the toes pointed.
        double armR = 26 + 15 * sway, armL = 40 - 15 * sway, rollR = -30 - 6 * fl, rollL = 30 + 6 * fl;
        a.spread = 0.85;
        p.turn(Rig15.Joint.R_LOWER_ARM, -22 - 10 * fl, 0, 0).turn(Rig15.Joint.L_LOWER_ARM, -26 + 10 * fl2, 0, 0);
        p.turn(Rig15.Joint.R_UPPER_LEG, -20 + 14 * sway, 0, 3).turn(Rig15.Joint.L_UPPER_LEG, 20 - 14 * sway, 0, -3);
        p.turn(Rig15.Joint.R_LOWER_LEG, 22 + 12 * fl, 0, 0).turn(Rig15.Joint.L_LOWER_LEG, 30 - 12 * fl2, 0, 0);
        p.turn(Rig15.Joint.R_FOOT, 30, 0, 0).turn(Rig15.Joint.L_FOOT, 34, 0, 0);

        double[] guardR = {-4.6, 21, 5.5}, guardL = {4.4, 21.5, 6};
        if (k > 0) {
            switch (move) {
                case PUNCH -> {
                    double wind = ramp(u, 0, 0.28) * (1 - ramp(u, 0.28, 0.4)), str = ramp(u, 0.22, 0.42) * (1 - ramp(u, 0.58, 0.95));
                    double[] pos = mix(guardR, new double[]{-6.2, 20.5, -0.5}, wind, new double[]{-3.2, 22, 11.2}, str);
                    a.reach(R_ARM, pos[0], pos[1], pos[2], Math.min(1, (wind + str) * 1.2));
                    a.reach(L_ARM, guardL[0], guardL[1], guardL[2], 0.7 * Math.min(1, wind + str));
                    ty = -14 * wind + 28 * str;
                    tx += 6 * str;
                    hy = 10 * wind - 16 * str;
                    a.fistR = ramp(u, 0.12, 0.3);
                    a.fistL = 0.8 * e;
                    a.viewPitch += 0.15 * str;
                }
                case KICK -> {
                    double cham = ramp(u, 0, 0.3) * (1 - ramp(u, 0.32, 0.45)), str = ramp(u, 0.26, 0.44) * (1 - ramp(u, 0.62, 0.95));
                    double[] pos = mix(new double[]{-2, 4.5, -2}, new double[]{-2, 10.5, 7}, cham, new double[]{-2.3, 16.5, 13.2}, str);
                    a.reach(R_LEG, pos[0], pos[1], pos[2], Math.min(1, (cham + str) * 1.3));
                    a.pole(R_LEG, 0, 0.3, 1);
                    p.ik[R_LEG.ordinal()].roll = 25 * str;
                    tx += -14 * str;
                    rollR -= 50 * e;
                    rollL += 50 * e;
                    armR *= 1 - e;
                    armL *= 1 - e;
                    a.viewPitch += 0.15 * str;
                    a.fistR = a.fistL = 0.3 * e;
                }
                case UPPER -> {
                    double low = ramp(u, 0, 0.3) * (1 - ramp(u, 0.3, 0.42)), up = ramp(u, 0.25, 0.42) * (1 - ramp(u, 0.6, 0.95));
                    double[] pos = mix(guardR, new double[]{-4.0, 13.5, 5}, low, new double[]{-3.6, 27.5, 8.5}, up);
                    a.reach(R_ARM, pos[0], pos[1], pos[2], Math.min(1, (low + up) * 1.2));
                    a.reach(L_ARM, guardL[0], guardL[1], guardL[2], 0.7 * Math.min(1, low + up));
                    tx += 8 * low - 10 * up;
                    ty = -10 * low + 18 * up;
                    hy = -10 * up;
                    hx -= 8 * up;
                    a.fistR = ramp(u, 0.1, 0.3);
                    a.fistL = 0.8 * e;
                    a.viewPitch -= 0.3 * up;
                    tilt -= face * 0.25 * k;
                }
                case DASH -> {
                    double s = ramp(u, 0.05, 0.35) * (1 - ramp(u, 0.6, 0.95));
                    a.reach(R_ARM, -2.8, 22.5, 10.5, s);
                    a.reach(L_ARM, 2.8, 22.5, 10.5, s);
                    tx += 12 * s;
                    hx += 8 * s;
                    p.turn(Rig15.Joint.R_UPPER_LEG, 30 * s + (-20 + 14 * sway) * (1 - s), 0, 3).turn(Rig15.Joint.L_UPPER_LEG, 34 * s + (20 - 14 * sway) * (1 - s), 0, -3);
                    p.turn(Rig15.Joint.R_LOWER_LEG, 10 * s + (22 + 12 * fl) * (1 - s), 0, 0).turn(Rig15.Joint.L_LOWER_LEG, 8 * s + (30 - 12 * fl2) * (1 - s), 0, 0);
                    a.fistR = a.fistL = s;
                    a.viewPitch -= 0.4 * k;
                    tilt = face * 0.5 * k;
                }
                case SLAM -> {
                    double up = ramp(u, 0, 0.3) * (1 - ramp(u, 0.3, 0.45)), down = ramp(u, 0.28, 0.45) * (1 - ramp(u, 0.6, 0.95));
                    double[] r = mix(guardR, new double[]{-1.6, 31.5, 3.5}, up, new double[]{-2.2, 13.5, 10}, down);
                    double[] l = mix(guardL, new double[]{1.6, 31.5, 3.5}, up, new double[]{2.2, 13.5, 10}, down);
                    double wgt = Math.min(1, (up + down) * 1.2);
                    a.reach(R_ARM, r[0], r[1], r[2], wgt);
                    a.reach(L_ARM, l[0], l[1], l[2], wgt);
                    tx += -10 * up + 26 * down;
                    hx += 6 * down;
                    a.fistR = a.fistL = 0.9 * wgt;
                    a.viewPitch += 0.4 * k;
                }
                case GRAB -> {
                    double reach = ramp(u, 0, 0.35), pull = ramp(u, 0.5, 0.85), close = ramp(u, 0.3, 0.5);
                    double z = 10.2 - 4.7 * pull;
                    a.reach(R_ARM, -3.0, 21 - pull, z, reach);
                    a.reach(L_ARM, 3.0, 21 - pull, z, reach);
                    a.fistR = a.fistL = close * (1 - ramp(u, 0.85, 1));
                    a.spread = 1;
                    tx += 8 * reach;
                    hx += 5 * reach;
                }
                case BLAST -> {
                    a.reach(R_ARM, -2.6, 22, 10.5, e);
                    a.reach(L_ARM, 2.6, 22, 10.5, e);
                    a.spread = 1;
                    hx -= 10 * e;
                    tx -= 4 * e;
                }
            }
        }
        if (chargeK > 0.01) {                                                 // Honcho gathering himself for a special
            a.reach(R_ARM, -2.5, 30.5, 1.5, chargeK);
            a.reach(L_ARM, 2.5, 30.5, 1.5, chargeK);
            hx -= 14 * chargeK;
            tx -= 6 * chargeK;
            a.spread = 1;
        }
        if (block > 0) {                                                      // forearms crossed in front to take the blow
            a.reach(R_ARM, 2.2, 25.5, 6.5, block);
            a.reach(L_ARM, -2.2, 25.5, 6.5, block);
            hx += 12 * block;
            a.fistR = a.fistL = 0.7 * block;
        }
        if (dodging) {
            a.viewPitch = 0.5;
            tilt -= face * 0.3;
            tx -= 16;
            rollR -= 25;
            rollL += 25;
        }
        if (otherBell > 0.3 && k <= 0) {                                      // rocked by the other's blow
            hx -= 28 * otherBell;
            tx -= 6 * otherBell;
        }
        p.turn(Rig15.Joint.R_UPPER_ARM, armR, 0, rollR).turn(Rig15.Joint.L_UPPER_ARM, armL, 0, rollL);
        p.turn(J_TORSO, tx, ty, tz).turn(J_HEAD, hx, hy, 0);
        return tilt;
    }

    /** A point {@code base} moved towards {@code a} by {@code wa} and towards {@code b} by {@code wb}. */
    private static double[] mix(double[] base, double[] a, double wa, double[] b, double wb) {
        return new double[]{
                base[0] + (a[0] - base[0]) * wa + (b[0] - base[0]) * wb,
                base[1] + (a[1] - base[1]) * wa + (b[1] - base[1]) * wb,
                base[2] + (a[2] - base[2]) * wa + (b[2] - base[2]) * wb};
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
