package com.lotusblight.overlay;

import com.lotusblight.overlay.Actor.Hand;

/**
 * The Glitcher's movements on the desktop, written for the 15-joint rig: a walk with the feet placed by IK and the
 * hips, chest, head and arms doing what they do in a real walk, and the gestures of the first song - knocking on the
 * glass and waving, picking an icon up with two fingers, throwing it, the stomp, hanging in the air with open hands.
 * Every gesture says what the fingers do. The scenes own the timing; this owns the body.
 */
final class GlitcherPoses {
    private GlitcherPoses() {}

    private static final Rig15.Joint TORSO = Rig15.Joint.UPPER_TORSO, HEAD = Rig15.Joint.HEAD;
    private static final Rig15.Limb R_ARM = Rig15.Limb.R_ARM, L_ARM = Rig15.Limb.L_ARM, R_LEG = Rig15.Limb.R_LEG, L_LEG = Rig15.Limb.L_LEG;

    static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** A point {@code base} moved towards {@code a} by {@code wa} and towards {@code b} by {@code wb}. */
    private static double[] mix(double[] base, double[] a, double wa, double[] b, double wb) {
        return new double[]{
                base[0] + (a[0] - base[0]) * wa + (b[0] - base[0]) * wb,
                base[1] + (a[1] - base[1]) * wa + (b[1] - base[1]) * wb,
                base[2] + (a[2] - base[2]) * wa + (b[2] - base[2]) * wb};
    }

    /**
     * Standing or walking, seen from the side or the front. {@code phi} is the walk's angle in radians (a full step
     * of both legs is 2 pi), {@code walk} how much of a walk there is against standing still, {@code anger} how tense.
     */
    static void walk(Actor a, double phi, double walk, double anger, double t) {
        Pose15 p = a.pose;
        double amp = walk * (0.7 + 0.3 * anger);
        double breath = Math.sin(t * 1.5);
        for (int side = 0; side < 2; side++) {
            double u = phi / (2 * Math.PI) + (side == 0 ? 0 : 0.5);
            double[] f = AnimLibrary.footCycle(u);                         // the ankle along a step: {forward, height, sole pitch}
            double z = f[0] * amp, y = 3 + (f[1] - 3) * amp, roll = f[2] * amp;
            a.reach(side == 0 ? R_LEG : L_LEG, side == 0 ? -2.4 : 2.4, y, z, 1);
            p.ik[(side == 0 ? R_LEG : L_LEG).ordinal()].roll = roll;
        }
        // The hips drop at each step and rise as the legs pass, sway over the standing foot and turn with the stride;
        // the chest turns against them and the head stays level.
        p.rootPos[1] = -0.8 - amp * (0.25 + 0.4 * Math.cos(2 * phi)) - (1 - walk) * 0.25 * breath;
        p.rootPos[0] = -0.6 * Math.sin(phi) * amp;
        p.rootRot = Quat.euler(amp * 1.0 * Math.cos(2 * phi + 0.4), amp * 5.0 * Math.cos(phi), amp * 1.6 * Math.sin(phi));
        p.turn(TORSO, 2 + 1.0 * breath + amp * (1.0 + 2.5 * anger + 0.8 * Math.cos(2 * phi + 0.4)) + 3 * anger, -amp * 9.0 * Math.cos(phi), -amp * 1.4 * Math.sin(phi));
        p.turn(HEAD, -1 - amp * (2.5 - 1.2 * Math.cos(2 * phi + 0.9)) - 1.5 * breath * (1 - walk), amp * 4.0 * Math.cos(phi), 0.6 * Math.sin(t * 0.8) * (1 - walk));
        double swing = 27 * Math.cos(phi) * amp * (0.8 + 0.5 * anger);
        double fwR = Math.max(0, -Math.cos(phi)) * amp, fwL = Math.max(0, Math.cos(phi)) * amp;
        p.turn(Rig15.Joint.R_UPPER_ARM, swing + 2 * breath * (1 - walk), 0, -4 - 2 * breath * (1 - walk));
        p.turn(Rig15.Joint.L_UPPER_ARM, -swing + 2 * breath * (1 - walk), 0, 4 + 2 * breath * (1 - walk));
        p.turn(Rig15.Joint.R_LOWER_ARM, -(10 + 22 * fwR + 10 * anger), 0, 0).turn(Rig15.Joint.L_LOWER_ARM, -(10 + 22 * fwL + 10 * anger), 0, 0);
        p.turn(Rig15.Joint.R_HAND, -(4 + 8 * fwR), 0, 0).turn(Rig15.Joint.L_HAND, -(4 + 8 * fwL), 0, 0);
        a.fistR = a.fistL = 0.85 * anger;
        a.spread = 0.3;
    }

    /** Knocking on the glass with the right fist while the left hand waves. {@code pulse} is 1 on a knock. */
    static void knock(Actor a, double ms, double knock, double pulse, double wave) {
        if (knock <= 0.02) return;
        a.reach(R_ARM, -3.6, 24.0, 9.6 - 1.9 * pulse, knock);
        a.pole(R_ARM, -0.3, -1, -0.2);
        a.fistR = Math.max(a.fistR, knock);
        a.blendTurn(TORSO, 4, 8, 0, knock);
        double w = Math.sin(ms * 0.014);
        a.blendTurn(Rig15.Joint.L_UPPER_ARM, 0, 0, 152 + 16 * w, wave);
        a.blendTurn(Rig15.Joint.L_LOWER_ARM, -14 - 12 * Math.sin(ms * 0.014 + 1), 0, 0, wave);
        a.blendTurn(Rig15.Joint.L_HAND, 0, 0, 24 * Math.sin(ms * 0.018), wave);       // the wrist waggles
        if (wave > 0.2) {
            a.shapeL = Hand.OPEN;
            a.spread = 1;
        }
    }

    /** Bending down to an icon at the edge and taking it between finger and thumb. {@code g} is 0..1..0. */
    static void pickUp(Actor a, double g) {
        if (g <= 0) return;
        a.reach(R_ARM, -3.2, 12.0, 9.8, g);
        a.blendTurn(TORSO, 30, 0, 0, g);
        a.blendTurn(HEAD, 16, 0, 0, g);
        a.pose.rootPos[1] -= 1.6 * g;
        a.shapeR = g > 0.55 ? Hand.PINCH : Hand.OPEN;
        a.spread = 0.8;
    }

    /** Throwing what he holds: wind back with a closed hand, sweep, open the hand at the release, let the arm fall. */
    static void throwIcon(Actor a, double sinceMs) {
        if (sinceMs < 0) return;
        double wind = smooth(sinceMs / 500.0), swing = smooth((sinceMs - 600.0) / 140.0), back = smooth((sinceMs - 900.0) / 500.0);
        double wi = wind * (1 - swing), sw = swing * (1 - back);
        double[] rest = {-4.6, 21, 5.5};
        double[] pos = mix(rest, new double[]{-6.2, 29, -7}, wi, new double[]{-3.2, 25, 11.5}, sw);
        double weight = Math.min(1, (wi + sw) * 1.25);
        a.reach(R_ARM, pos[0], pos[1], pos[2], weight);
        a.blendTurn(TORSO, 4, -24 * wi + 28 * sw, 0, Math.min(1, wi + sw));
        a.blendTurn(HEAD, -4, 10 * wi - 14 * sw, 0, Math.min(1, wi + sw));
        a.shapeR = (swing > 0.45 && back < 0.6) ? Hand.OPEN : Hand.FIST;      // it is in his fist until the release
        a.spread = 1;
    }

    /** The stomp: arms up and out with clenched fists, one knee up out to the side, then down hard. */
    static void stomp(Actor a, double ms, double stompMs) {
        if (ms < stompMs - 1800 || ms >= stompMs + 250) return;
        double up = smooth((ms - (stompMs - 1800)) / 1300.0), slam = smooth((ms - stompMs) / 80.0);
        double raised = up * (1 - slam);
        a.blendTurn(Rig15.Joint.R_UPPER_ARM, -30, 0, -44, up);
        a.blendTurn(Rig15.Joint.L_UPPER_ARM, -30, 0, 44, up);
        a.blendTurn(Rig15.Joint.R_LOWER_ARM, -38, 0, 0, up);
        a.blendTurn(Rig15.Joint.L_LOWER_ARM, -38, 0, 0, up);
        a.fistR = Math.max(a.fistR, up);
        a.fistL = Math.max(a.fistL, up);
        if (raised > 0) {
            a.reach(R_LEG, -7.0, 3 + 7.5 * raised, 2.0 * raised, raised);
            a.pole(R_LEG, -0.6, 0, 1);
            a.pose.ik[R_LEG.ordinal()].roll = 30 * raised;
            a.blendTurn(TORSO, -4, 0, 4, raised);
        }
        a.viewPitch += 0.12 * slam * (1 - smooth((ms - stompMs - 80) / 170.0)) - 0.06 * up;
    }

    /**
     * Held up by the throat: both hands go to the fist that holds the neck and pull at it, the legs kick, the head is
     * thrown back with every gasp. {@code flail} 1 is a full struggle, 0 is hanging limp.
     */
    static void hanging(Actor a, double t, double flail, double phase) {
        double kick = Math.sin(t * 15 + phase) * flail, kick2 = Math.sin(t * 15 + phase + 2.2) * flail;
        double gasp = Math.pow(Math.max(0, Math.sin(t * 6.0 + phase)), 4) * (0.3 + 0.7 * flail);
        for (Rig15.Limb l : new Rig15.Limb[]{R_LEG, L_LEG}) a.pose.ik[l.ordinal()].w = 0;
        double grab = Math.min(1, 1.3 * flail);
        a.reach(R_ARM, -2.2 + 1.2 * Math.sin(t * 17 + phase) * flail - 0.6 * gasp, 26.5 + 1.0 * Math.cos(t * 13) * flail + 0.8 * gasp, 3.6, grab);
        a.reach(L_ARM, 2.2 + 1.2 * Math.cos(t * 19 + phase) * flail + 0.6 * gasp, 26.0 + 1.0 * Math.sin(t * 14) * flail + 0.8 * gasp, 3.8, grab);
        a.pose.turn(Rig15.Joint.R_UPPER_ARM, 8, 0, -8).turn(Rig15.Joint.L_UPPER_ARM, 10, 0, 8);
        a.pose.turn(Rig15.Joint.R_UPPER_LEG, 46 * kick - 6, 0, 4).turn(Rig15.Joint.L_UPPER_LEG, -46 * kick2 - 6, 0, -4);
        a.pose.turn(Rig15.Joint.R_LOWER_LEG, 48 + 34 * Math.max(0, -kick), 0, 0).turn(Rig15.Joint.L_LOWER_LEG, 55 + 34 * Math.max(0, kick2), 0, 0);
        a.pose.turn(Rig15.Joint.R_FOOT, 25 - 10 * Math.max(0, kick), 0, 0).turn(Rig15.Joint.L_FOOT, 25 - 10 * Math.max(0, -kick2), 0, 0);
        a.pose.turn(TORSO, -4 + 5 * kick - 7 * gasp, 6 * kick2, 3 * Math.sin(t * 9) * flail);
        a.pose.turn(HEAD, -14 - 14 * gasp - 12 * (1 - flail), 8 * Math.sin(t * 11) * flail, 6 * Math.sin(t * 9) * flail);
        a.hands(Hand.GRIP, Hand.GRIP);
        a.fistR = a.fistL = 0.3 * flail * (0.6 + 0.3 * Math.sin(t * 13));
        a.spread = 0.4;
        a.pose.rootPos[1] = -0.6 * gasp;
    }

    /** Hanging in the air over the eyes: arms out, hands open and fanned, legs loose, toes pointed. */
    static void hover(Actor a, double lev, double t) {
        if (lev <= 0) return;
        for (Rig15.Limb l : new Rig15.Limb[]{R_LEG, L_LEG, R_ARM, L_ARM}) a.pose.ik[l.ordinal()].w *= 1 - lev;
        double s = Math.sin(t * 1.3);
        a.blendTurn(Rig15.Joint.R_UPPER_ARM, 0, 0, -56 + 4 * Math.sin(t * 1.1), lev);
        a.blendTurn(Rig15.Joint.L_UPPER_ARM, 0, 0, 56 - 4 * Math.sin(t * 1.1 + 0.6), lev);
        a.blendTurn(Rig15.Joint.R_LOWER_ARM, -14 - 5 * s, 0, 0, lev);
        a.blendTurn(Rig15.Joint.L_LOWER_ARM, -14 + 5 * s, 0, 0, lev);
        a.blendTurn(Rig15.Joint.R_HAND, -12 + 6 * Math.sin(t * 1.9), 0, 0, lev);
        a.blendTurn(Rig15.Joint.L_HAND, -12 - 6 * Math.sin(t * 1.9), 0, 0, lev);
        a.blendTurn(Rig15.Joint.R_UPPER_LEG, 6 * s, 0, 5, lev);
        a.blendTurn(Rig15.Joint.L_UPPER_LEG, -6 * s, 0, -5, lev);
        a.blendTurn(Rig15.Joint.R_LOWER_LEG, 14 + 8 * s, 0, 0, lev);
        a.blendTurn(Rig15.Joint.L_LOWER_LEG, 16 - 8 * s, 0, 0, lev);
        a.blendTurn(Rig15.Joint.R_FOOT, 38, 0, 0, lev);
        a.blendTurn(Rig15.Joint.L_FOOT, 38, 0, 0, lev);
        a.blendTurn(HEAD, -9, 0, 0, lev);
        a.blendTurn(TORSO, -3 + 2 * s, 0, 0, lev);
        a.pose.rootPos[1] += 1.2 * lev;
        if (lev > 0.3) {
            a.shapeR = a.shapeL = Hand.OPEN;
            a.spread = 1;
        }
    }
}
