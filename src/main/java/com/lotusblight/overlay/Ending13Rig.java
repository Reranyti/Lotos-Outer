package com.lotusblight.overlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The arms of the thirteenth ending, animated with the same parts-and-keys system as the rest of the overlay ({@link Rig15},
 * {@link Pose15}, {@link AnimClip}): two-bone IK for the arms, a hand with five fingers of two bones each, easing between keys. The
 * ending's own scene (client side, drawn in 3D) asks for a frame of a clip and gets back the boxes of the arms and hands in stage
 * space (skin pixels, y up, facing +z) with the skin rectangles to colour them with, and where the hands are, so a knife can be put
 * in the fist.
 *
 * Everything is seen from the first person: the eye is at {@link #EYE}.
 */
public final class Ending13Rig {
    /** One textured face: four corners in stage space and the skin rectangle over them. */
    public record Quad(double[][] corners, double[][] rest, int joint, int u0, int v0, int u1, int v1) {}

    /** A hand's place: the wrist, and the three axes of the hand in stage space (x across, y up the forearm, z the front of the fist). */
    public record Hand(double[] wrist, double[] x, double[] y, double[] z) {}

    /** What a clip says at one moment. */
    public record Frame(List<Quad> quads, Hand right, Hand left, double[] rightElbow, double[] leftElbow, double[] rightWrist, double[] leftWrist) {}

    /** The eye, in stage space: in the front of the head. */
    public static final double[] EYE = {0, 28.3, 3.6};

    private static final AnimClip.Ease SM = AnimClip.Ease.SMOOTH, IO = AnimClip.Ease.IN_OUT_CUBIC, OB = AnimClip.Ease.OUT_BACK,
            OC = AnimClip.Ease.OUT_CUBIC, LIN = AnimClip.Ease.LINEAR, IC = AnimClip.Ease.IN_CUBIC;
    private static final Rig15 RIG = Rig15.standard();
    private static final Map<String, AnimClip> CLIPS = new TreeMap<>();

    private Ending13Rig() {}

    static {
        CLIPS.put("stab", stab());
        CLIPS.put("hold", hold());
        CLIPS.put("grab", grab());
        CLIPS.put("cut", cut());
        CLIPS.put("jab", jab());
    }

    public static double length(String clip) {
        return CLIPS.get(clip).length;
    }

    // ------------------------------------------------------------------ solving a frame

    /**
     * The arms at time {@code t} of the clip. {@code withBody} also returns the torso and the legs (for looking down at oneself).
     */
    public static Frame frame(String clip, double t, boolean withBody) {
        AnimClip c = CLIPS.get(clip);
        Pose15 p = c.sample(t);
        double rc = c.value("fx.r_curl.w", t, 1.0), lc = c.value("fx.l_curl.w", t, 0.35);
        double rt = c.value("fx.r_thumb.w", t, 1.0), lt = c.value("fx.l_thumb.w", t, 0.2);
        p.fingers(true, new double[]{rt, rc, rc, rc, rc}, 0.0, t);
        p.fingers(false, new double[]{lt, lc, lc, lc, lc}, 0.2, t);
        Rig15.Skel sk = RIG.solve(p);
        List<Quad> quads = new ArrayList<>();
        for (Rig15.Face f : RIG.faces) {
            Rig15.Joint j = f.joint();
            if (!isArmOrHand(j) && !(withBody && (j == Rig15.Joint.LOWER_TORSO || j == Rig15.Joint.UPPER_TORSO
                    || j.name().contains("LEG") || j == Rig15.Joint.R_FOOT || j == Rig15.Joint.L_FOOT))) continue;
            double[][] cs = new double[4][];
            for (int i = 0; i < 4; i++) cs[i] = sk.carry(j, f.corners()[i]);
            quads.add(new Quad(cs, f.corners(), j.ordinal(), f.u0(), f.v0(), f.u1(), f.v1()));
        }
        return new Frame(quads, handOf(sk, Rig15.Joint.R_HAND), handOf(sk, Rig15.Joint.L_HAND),
                sk.pivotPos(Rig15.Joint.R_LOWER_ARM), sk.pivotPos(Rig15.Joint.L_LOWER_ARM),
                sk.pivotPos(Rig15.Joint.R_HAND), sk.pivotPos(Rig15.Joint.L_HAND));
    }

    private static boolean isArmOrHand(Rig15.Joint j) {
        int o = j.ordinal();
        return (o >= Rig15.Joint.R_UPPER_ARM.ordinal() && o <= Rig15.Joint.L_HAND.ordinal() && o != Rig15.Joint.R_UPPER_LEG.ordinal())
                || o >= Rig15.Joint.R_THUMB_1.ordinal();
    }

    private static Hand handOf(Rig15.Skel sk, Rig15.Joint hand) {
        double[] o = sk.pivotPos(hand);
        double[] p = RIG.pivot[hand.ordinal()];
        double[] px = sk.carry(hand, new double[]{p[0] + 1, p[1], p[2]});
        double[] py = sk.carry(hand, new double[]{p[0], p[1] + 1, p[2]});
        double[] pz = sk.carry(hand, new double[]{p[0], p[1], p[2] + 1});
        return new Hand(o, Quat.sub(px, o), Quat.sub(py, o), Quat.sub(pz, o));
    }

    // ------------------------------------------------------------------ the clips (stage space, skin pixels)


    /** The left arm hanging ready at the side of the view. */
    private static void leftReady(AnimClip c, double len) {
        c.key("ik.l_arm.w", 0, LIN, 1).key("ik.l_arm.w", len, LIN, 1);
        c.key("ik.l_arm.pos", 0, LIN, 6.2, 17.5, 7.5).key("ik.l_arm.pos", len, LIN, 6.2, 17.5, 7.5);
        c.key("ik.l_arm.pole", 0, LIN, 0.4, -1, -0.5).key("ik.l_arm.pole", len, LIN, 0.4, -1, -0.5);
    }

    /** The knife hand at rest, a little raised, trembling. */
    private static AnimClip hold() {
        double T = 3.0;
        AnimClip c = new AnimClip("hold", T, true);
        for (int i = 0; i <= 30; i++) {
            double t = T * i / 30, w = 2 * Math.PI * t / T;
            c.key("ik.r_arm.pos", t, LIN, -6.0 + 0.12 * Math.sin(w * 6), 25.0 + 0.1 * Math.sin(w * 5 + 1), 14.0 + 0.1 * Math.sin(w * 4));
        }
        c.key("ik.r_arm.w", 0, LIN, 1).key("ik.r_arm.w", T, LIN, 1);
        c.key("ik.r_arm.pole", 0, LIN, -1, -1, -0.4).key("ik.r_arm.pole", T, LIN, -1, -1, -0.4);
        c.key("r_hand.rot", 0, LIN, 62, 0, 0).key("r_hand.rot", T, LIN, 62, 0, 0);
        leftReady(c, T);
        return c;
    }

    /** The stab: the arm rises into view, draws back to the shoulder, drives forward fast, and stays in. 2.4 seconds. */
    private static AnimClip stab() {
        double T = 2.6;
        AnimClip c = new AnimClip("stab", T, false);
        c.key("ik.r_arm.pos", 0.0, LIN, -6.0, 25.0, 14.0)
                .key("ik.r_arm.pos", 0.5, SM, -6.3, 26.0, 12.0)
                .key("ik.r_arm.pos", 1.1, IO, -8.5, 31.0, 5.5)              // drawn back and up beside the ear
                .key("ik.r_arm.pos", 1.32, OC, -3.0, 25.5, 17.0)            // and driven forward
                .key("ik.r_arm.pos", 1.4, OB, -2.6, 25.2, 17.8)             // in the glass
                .key("ik.r_arm.pos", 1.6, SM, -2.7, 25.3, 17.6)
                .key("ik.r_arm.pos", T, LIN, -2.7, 25.3, 17.6);
        c.key("ik.r_arm.w", 0, LIN, 1).key("ik.r_arm.w", T, LIN, 1);
        c.key("ik.r_arm.pole", 0, LIN, -1, -1, -0.4).key("ik.r_arm.pole", T, LIN, -1, -1, -0.4);
        c.key("r_hand.rot", 0.0, LIN, 62, 0, 0).key("r_hand.rot", 1.1, IO, 100, 0, 8).key("r_hand.rot", 1.32, OC, 88, 0, 0).key("r_hand.rot", T, LIN, 88, 0, 0);
        c.key("fx.r_curl.w", 0, LIN, 1).key("fx.r_curl.w", T, LIN, 1);
        c.key("fx.r_thumb.w", 0, LIN, 1).key("fx.r_thumb.w", T, LIN, 1);
        c.key("fx.l_curl.w", 0, LIN, 0.3).key("fx.l_curl.w", 1.3, LIN, 0.3).key("fx.l_curl.w", 1.4, SM, 0.7).key("fx.l_curl.w", T, LIN, 0.7);
        c.key("ik.l_arm.w", 0, LIN, 1).key("ik.l_arm.w", T, LIN, 1);
        c.key("ik.l_arm.pos", 0, LIN, 6.0, 17.5, 7.5).key("ik.l_arm.pos", 1.1, IO, 7.5, 18.5, 6.0).key("ik.l_arm.pos", 1.34, OC, 5.0, 20.5, 11.0).key("ik.l_arm.pos", T, SM, 6.0, 19.0, 9.0);
        c.key("ik.l_arm.pole", 0, LIN, 0.4, -1, -0.5).key("ik.l_arm.pole", T, LIN, 0.4, -1, -0.5);
        return c;
    }

    /** Both hands going to someone's neck and closing on it: the targets are set by the scene (see {@link #frameAt}). */
    private static AnimClip grab() {
        double T = 14.0;
        AnimClip c = new AnimClip("grab", T, false);
        // from hanging ready, up, out to either side of the neck, in
        c.key("ik.r_arm.pos", 0.0, LIN, -5.5, 17.5, 9.0).key("ik.r_arm.pos", 4.0, SM, -6.5, 19.0, 11.0).key("ik.r_arm.pos", 6.2, IO, -3.4, 23.0, 14.5).key("ik.r_arm.pos", 7.0, IO, -1.6, 23.2, 14.8).key("ik.r_arm.pos", 9.3, LIN, -1.6, 23.2, 14.8).key("ik.r_arm.pos", 10.5, SM, -5.0, 18.0, 9.0).key("ik.r_arm.pos", T, LIN, -5.0, 18.0, 9.0);
        c.key("ik.l_arm.pos", 0.0, LIN, 5.5, 17.5, 9.0).key("ik.l_arm.pos", 4.0, SM, 6.5, 19.0, 11.0).key("ik.l_arm.pos", 6.2, IO, 3.4, 23.0, 14.5).key("ik.l_arm.pos", 7.0, IO, 1.6, 23.2, 14.8).key("ik.l_arm.pos", 9.3, LIN, 1.6, 23.2, 14.8).key("ik.l_arm.pos", 10.5, SM, 5.0, 18.0, 9.0).key("ik.l_arm.pos", T, LIN, 5.0, 18.0, 9.0);
        for (String a : new String[]{"ik.r_arm.w", "ik.l_arm.w"}) c.key(a, 0, LIN, 1).key(a, T, LIN, 1);
        c.key("ik.r_arm.pole", 0, LIN, -1, -1, -0.3).key("ik.r_arm.pole", T, LIN, -1, -1, -0.3);
        c.key("ik.l_arm.pole", 0, LIN, 1, -1, -0.3).key("ik.l_arm.pole", T, LIN, 1, -1, -0.3);
        for (String ch : new String[]{"fx.r_curl.w", "fx.l_curl.w"}) c.key(ch, 0, LIN, 0.25).key(ch, 6.2, SM, 0.2).key(ch, 7.4, SM, 0.92).key(ch, 9.3, LIN, 0.92).key(ch, 10.5, SM, 0.3).key(ch, T, LIN, 0.3);
        for (String ch : new String[]{"fx.r_thumb.w", "fx.l_thumb.w"}) c.key(ch, 0, LIN, 0.2).key(ch, 7.4, SM, 0.8).key(ch, 9.3, LIN, 0.8).key(ch, T, LIN, 0.2);
        c.key("r_hand.rot", 0, LIN, 0, 0, 0).key("r_hand.rot", 6.2, SM, -10, -35, -10).key("r_hand.rot", 9.3, LIN, -10, -35, -10).key("r_hand.rot", 10.5, SM, 0, 0, 0);
        c.key("l_hand.rot", 0, LIN, 0, 0, 0).key("l_hand.rot", 6.2, SM, -10, 35, 10).key("l_hand.rot", 9.3, LIN, -10, 35, 10).key("l_hand.rot", 10.5, SM, 0, 0, 0);
        return c;
    }

    /** When stroke {@code s} of the "cut" clip starts and how long it lasts (seconds): the scene draws its marks to the same schedule. */
    public static double strokeStart(int s) {
        double t = 2.4;
        for (int k = 0; k < s; k++) t += strokeLength(k);
        return t;
    }

    public static double strokeLength(int s) {
        return 0.95 + 0.30 * s;
    }

    /**
     * Seen from above: the left forearm held out across the view with the palm up, the right hand with the knife drawn across it six times,
     * each stroke slower and shorter than the last, and the hand at the end letting go. 14 seconds. Only the movement is here; the scene
     * shows nothing of it but light and colour.
     */
    private static AnimClip cut() {
        double T = 14.0;
        AnimClip c = new AnimClip("cut", T, false);
        c.key("ik.l_arm.w", 0, LIN, 1).key("ik.l_arm.w", T, LIN, 1);
        c.key("ik.l_arm.pos", 0, LIN, 5.5, 17.0, 8.0).key("ik.l_arm.pos", 1.8, SM, -0.5, 19.5, 15.5).key("ik.l_arm.pos", T, LIN, -0.5, 19.5, 15.5);
        c.key("ik.l_arm.pole", 0, LIN, 0.6, -1, -0.2).key("ik.l_arm.pole", T, LIN, 0.6, -1, -0.2);
        c.key("l_hand.rot", 0, LIN, 0, 0, 0).key("l_hand.rot", 1.8, SM, 0, -80, -60).key("l_hand.rot", T, LIN, 0, -80, -60);
        c.key("fx.l_curl.w", 0, LIN, 0.3).key("fx.l_curl.w", 4.0, SM, 0.4).key("fx.l_curl.w", T, LIN, 0.55);
        c.key("ik.r_arm.w", 0, LIN, 1).key("ik.r_arm.w", T, LIN, 1);
        c.key("ik.r_arm.pole", 0, LIN, -1, -1, -0.3).key("ik.r_arm.pole", T, LIN, -1, -1, -0.3);
        c.key("ik.r_arm.pos", 0.0, LIN, -6.0, 25.0, 14.0).key("ik.r_arm.pos", 1.8, SM, -4.0, 24.0, 16.0);
        for (int st = 0; st < 6; st++) {
            double t0 = strokeStart(st), dur = strokeLength(st), reach = 6.5 - st * 0.6, sag = st * 0.35;
            c.key("ik.r_arm.pos", t0, IO, -1.8, 22.5 - sag, 16.8);
            c.key("ik.r_arm.pos", t0 + dur * 0.4, SM, -1.8 + reach, 20.8 - sag, 16.4);
            c.key("ik.r_arm.pos", t0 + dur * 0.85, SM, -1.8 + reach * 0.5, 23.2 - sag, 15.8);
        }
        double end = strokeStart(6);
        c.key("ik.r_arm.pos", end + 0.3, SM, 2.5, 20.0, 15.0).key("ik.r_arm.pos", T, SM, 4.0, 16.0, 12.0);
        c.key("r_hand.rot", 0, LIN, 62, 0, 0).key("r_hand.rot", 2.4, SM, 80, 10, 0).key("r_hand.rot", T, LIN, 80, 10, 0);
        c.key("fx.r_curl.w", 0, LIN, 1).key("fx.r_curl.w", end, LIN, 1).key("fx.r_curl.w", T, SM, 0.55);
        c.key("fx.r_thumb.w", 0, LIN, 1).key("fx.r_thumb.w", T, LIN, 1);
        return c;
    }

    /** When jab {@code j} of the "jab" clip lands (seconds): the scene cuts away at those moments and shows nothing of them. */
    public static double jabAt(int j) {
        double t = 1.2;
        for (int k = 0; k < j; k++) t += jabLength(k);
        return t + jabLength(j) * 0.55;
    }

    public static double jabLength(int j) {
        return 0.62 + 0.22 * j;
    }

    /** The knife hand raised and brought down towards the chest, again and again, slower and weaker each time, then falling away. 10 seconds. */
    private static AnimClip jab() {
        double T = 10.0;
        AnimClip c = new AnimClip("jab", T, false);
        c.key("ik.r_arm.w", 0, LIN, 1).key("ik.r_arm.w", T, LIN, 1);
        c.key("ik.r_arm.pole", 0, LIN, -1, -1, -0.4).key("ik.r_arm.pole", T, LIN, -1, -1, -0.4);
        c.key("ik.r_arm.pos", 0.0, LIN, -6.0, 25.0, 14.0);
        for (int j = 0; j < 7; j++) {
            double t0 = 1.2;
            for (int k = 0; k < j; k++) t0 += jabLength(k);
            double len = jabLength(j), rise = 28.0 - j * 1.4;
            c.key("ik.r_arm.pos", t0, IO, -3.0, rise, 9.5);
            c.key("ik.r_arm.pos", t0 + len * 0.55, IC, -1.0, 17.0, 8.0);          // down to the chest
            c.key("ik.r_arm.pos", t0 + len * 0.8, SM, -1.8, 19.0 - j * 0.3, 7.0);
        }
        double end = 1.2;
        for (int k = 0; k < 7; k++) end += jabLength(k);
        c.key("ik.r_arm.pos", end + 0.4, SM, -2.5, 14.0, 6.0).key("ik.r_arm.pos", T, IC, -4.0, 8.0, 4.0);
        c.key("r_hand.rot", 0, LIN, 62, 0, 0).key("r_hand.rot", 1.2, SM, 100, 0, 0).key("r_hand.rot", T, LIN, 100, 0, 0);
        c.key("ik.l_arm.w", 0, LIN, 1).key("ik.l_arm.w", T, LIN, 1);
        c.key("ik.l_arm.pos", 0.0, LIN, 5.5, 17.0, 8.0).key("ik.l_arm.pos", 2.0, SM, 3.0, 17.0, 6.5).key("ik.l_arm.pos", end, LIN, 3.0, 17.0, 6.5).key("ik.l_arm.pos", T, IC, 5.0, 10.0, 4.0);
        c.key("ik.l_arm.pole", 0, LIN, 0.6, -1, -0.2).key("ik.l_arm.pole", T, LIN, 0.6, -1, -0.2);
        c.key("fx.r_curl.w", 0, LIN, 1).key("fx.r_curl.w", end, LIN, 1).key("fx.r_curl.w", T, SM, 0.5);
        c.key("fx.r_thumb.w", 0, LIN, 1).key("fx.r_thumb.w", T, LIN, 1);
        return c;
    }
}
