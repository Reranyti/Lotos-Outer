package com.lotusblight.overlay;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.DoubleFunction;

import com.lotusblight.overlay.AnimClip.Ease;

/**
 * Clips written in code for the {@link Rig15}: a base to build from and, because each leans on the IK, the
 * test that the rig, the solver and the tools hold together. Stage space: y up, floor at 0, facing +z.
 */
final class AnimLibrary {
    private AnimLibrary() {}

    /** Where the ankles are when the legs stand straight down. */
    private static final double[] R_ANKLE = {-2, 3, 0}, L_ANKLE = {2, 3, 0};

    static Map<String, AnimClip> all() {
        Map<String, AnimClip> m = new LinkedHashMap<>();
        for (AnimClip c : new AnimClip[]{idle(), walk(), punch()}) m.put(c.name, c);
        return m;
    }

    /** Adds one key per 1/fps to a channel, from a function of time. */
    private static void sampled(AnimClip c, String channel, double fps, DoubleFunction<double[]> f) {
        int n = (int) Math.round(c.length * fps);
        for (int i = 0; i <= n; i++) {
            double t = i / fps;
            c.key(channel, t, Ease.LINEAR, f.apply(t));
        }
    }

    private static double smooth(double u) {
        u = Math.max(0, Math.min(1, u));
        return u * u * (3 - 2 * u);
    }

    // ------------------------------------------------------------ idle

    /** Standing and breathing: the feet stay put while the hips, chest and head ride the breath. */
    static AnimClip idle() {
        double T = 3.6;
        AnimClip c = new AnimClip("idle", T, true);
        sampled(c, "root.pos", 30, t -> new double[]{0.45 * Math.sin(2 * Math.PI * t / T), -0.7 + 0.22 * Math.sin(2 * Math.PI * t / T - 0.6), 0});
        sampled(c, "root.rot", 30, t -> new double[]{0, 1.6 * Math.sin(2 * Math.PI * t / T + 0.4), 0.9 * Math.sin(2 * Math.PI * t / T)});
        sampled(c, "upper_torso.rot", 30, t -> {
            double b = Math.sin(2 * Math.PI * t / T - 0.9);
            return new double[]{1.2 + 1.3 * b, -1.0 * Math.sin(2 * Math.PI * t / T + 0.4), -0.8 * Math.sin(2 * Math.PI * t / T)};
        });
        sampled(c, "head.rot", 30, t -> new double[]{-1.0 - 1.0 * Math.sin(2 * Math.PI * t / T - 1.3), 3.5 * Math.sin(2 * Math.PI * t / T - 0.3), 0.8 * Math.sin(2 * Math.PI * t / T + 1.0)});
        sampled(c, "r_upper_arm.rot", 30, t -> new double[]{2 + 1.5 * Math.sin(2 * Math.PI * t / T - 1.2), 0, 3.5 + 1.0 * Math.sin(2 * Math.PI * t / T - 0.8)});
        sampled(c, "l_upper_arm.rot", 30, t -> new double[]{2 + 1.5 * Math.sin(2 * Math.PI * t / T - 1.6), 0, -3.5 - 1.0 * Math.sin(2 * Math.PI * t / T - 1.1)});
        sampled(c, "r_lower_arm.rot", 30, t -> new double[]{-7 - 2.0 * Math.sin(2 * Math.PI * t / T - 1.7), 0, 0});
        sampled(c, "l_lower_arm.rot", 30, t -> new double[]{-7 - 2.0 * Math.sin(2 * Math.PI * t / T - 2.0), 0, 0});
        sampled(c, "r_hand.rot", 30, t -> new double[]{-3 - 2.0 * Math.sin(2 * Math.PI * t / T - 2.1), 0, 0});
        sampled(c, "l_hand.rot", 30, t -> new double[]{-3 - 2.0 * Math.sin(2 * Math.PI * t / T - 2.4), 0, 0});
        for (Rig15.Limb l : new Rig15.Limb[]{Rig15.Limb.R_LEG, Rig15.Limb.L_LEG}) {
            double[] a = l == Rig15.Limb.R_LEG ? R_ANKLE : L_ANKLE;
            c.key("ik." + l.key() + ".pos", 0, Ease.LINEAR, a[0], a[1], a[2]);
            c.key("ik." + l.key() + ".pos", T, Ease.LINEAR, a[0], a[1], a[2]);
            c.key("ik." + l.key() + ".w", 0, Ease.LINEAR, 1);
            c.key("ik." + l.key() + ".w", T, Ease.LINEAR, 1);
        }
        return c;
    }

    // ------------------------------------------------------------ walk

    private static final double STRIDE = 3.0, STANCE = 0.6;

    /** A foot's ankle along the cycle (phase 0..1): {z, y, roll}. Planted for 60% of it, then swung through. */
    static double[] footCycle(double p) {
        p = ((p % 1) + 1) % 1;
        if (p < STANCE) {
            double u = p / STANCE;
            double roll = u < 0.12 ? -11 * (1 - u / 0.12) : u > 0.78 ? 26 * smooth((u - 0.78) / 0.22) : 0;
            double lift = u > 0.8 ? 1.3 * smooth((u - 0.8) / 0.2) : 0;
            return new double[]{STRIDE * (1 - 2 * u), 3 + lift, roll};
        }
        double u = (p - STANCE) / (1 - STANCE);
        double z = -STRIDE + 2 * STRIDE * (0.5 - 0.5 * Math.cos(Math.PI * Math.pow(u, 0.92)));
        double y = 3 + 1.3 * (1 - u) + 2.0 * Math.sin(Math.PI * Math.pow(u, 0.85)) * (1 - 0.35 * u);
        double roll = u < 0.4 ? 26 * (1 - u / 0.4) : -11 * smooth((u - 0.4) / 0.6);
        return new double[]{z, y, roll};
    }

    /** A walk in place: the feet slide back under the body while planted, as on a treadmill. */
    static AnimClip walk() {
        double T = 1.0;
        AnimClip c = new AnimClip("walk", T, true);
        double w = 2 * Math.PI / T;
        sampled(c, "root.pos", 30, t -> new double[]{-0.6 * Math.sin(w * t), -1.05 - 0.4 * Math.cos(2 * w * t), 0});
        sampled(c, "root.rot", 30, t -> new double[]{1.0 * Math.cos(2 * w * t + 0.4), 5.0 * Math.cos(w * t), 1.6 * Math.sin(w * t)});
        sampled(c, "upper_torso.rot", 30, t -> new double[]{3.0 + 0.8 * Math.cos(2 * w * t + 0.4), -9.0 * Math.cos(w * t), -1.4 * Math.sin(w * t)});
        sampled(c, "head.rot", 30, t -> new double[]{-2.5 + 1.2 * Math.cos(2 * w * t + 0.9), 4.0 * Math.cos(w * t), 0.5 * Math.sin(w * t)});
        for (int side = 0; side < 2; side++) {
            final double s = side == 0 ? 1 : -1;                      // the right arm goes back when the right leg is forward
            String arm = side == 0 ? "r" : "l";
            sampled(c, arm + "_upper_arm.rot", 30, t -> new double[]{s * 27 * Math.cos(w * t), 0, s * -3.5});
            sampled(c, arm + "_lower_arm.rot", 30, t -> {
                double fw = Math.max(0, -s * Math.cos(w * t));
                return new double[]{-(9 + 20 * fw), 0, 0};
            });
            sampled(c, arm + "_hand.rot", 30, t -> new double[]{-4 - 6 * Math.max(0, -s * Math.cos(w * t)), 0, 0});
            Rig15.Limb leg = side == 0 ? Rig15.Limb.R_LEG : Rig15.Limb.L_LEG;
            double x = side == 0 ? -2 : 2, shift = side == 0 ? 0 : 0.5;
            sampled(c, "ik." + leg.key() + ".pos", 30, t -> new double[]{x, footCycle(t / T + shift)[1], footCycle(t / T + shift)[0]});
            sampled(c, "ik." + leg.key() + ".roll", 30, t -> new double[]{footCycle(t / T + shift)[2]});
            c.key("ik." + leg.key() + ".w", 0, Ease.LINEAR, 1);
            c.key("ik." + leg.key() + ".w", T, Ease.LINEAR, 1);
        }
        c.event(0.0, "step_right").event(0.5, "step_left");
        return c;
    }

    // ------------------------------------------------------------ punch

    /** A straight right punch from a guard: wind back, twist through, overshoot a little, come back. */
    static AnimClip punch() {
        double T = 1.15;
        AnimClip c = new AnimClip("punch", T, false);
        // Feet planted: left forward, right back, the heel of the right coming up as the hips drive through.
        c.key("ik.l_leg.pos", 0, Ease.LINEAR, 2.8, 3, 2.6).key("ik.l_leg.pos", T, Ease.LINEAR, 2.8, 3, 2.6);
        c.key("ik.r_leg.pos", 0, Ease.LINEAR, -2.6, 3, -2.4);
        c.key("ik.r_leg.pos", 0.26, Ease.SMOOTH, -2.6, 3, -2.4);
        c.key("ik.r_leg.pos", 0.38, Ease.SMOOTH, -2.4, 3.9, -2.1);
        c.key("ik.r_leg.pos", 0.8, Ease.SMOOTH, -2.6, 3, -2.4);
        c.key("ik.r_leg.pos", T, Ease.LINEAR, -2.6, 3, -2.4);
        c.key("ik.r_leg.roll", 0, Ease.LINEAR, 0).key("ik.r_leg.roll", 0.26, Ease.SMOOTH, 0).key("ik.r_leg.roll", 0.38, Ease.SMOOTH, 24)
                .key("ik.r_leg.roll", 0.8, Ease.SMOOTH, 0).key("ik.r_leg.roll", T, Ease.LINEAR, 0);
        c.key("ik.r_leg.yaw", 0, Ease.LINEAR, 0).key("ik.r_leg.yaw", 0.26, Ease.SMOOTH, 0).key("ik.r_leg.yaw", 0.38, Ease.SMOOTH, 22)
                .key("ik.r_leg.yaw", 0.8, Ease.SMOOTH, 0).key("ik.r_leg.yaw", T, Ease.LINEAR, 0);
        for (String l : new String[]{"ik.l_leg.w", "ik.r_leg.w"}) c.key(l, 0, Ease.LINEAR, 1).key(l, T, Ease.LINEAR, 1);

        // Hips and chest.
        c.key("root.pos", 0, Ease.LINEAR, 0, -1.3, 0).key("root.pos", 0.26, Ease.IN_OUT_CUBIC, -0.2, -1.6, -0.7)
                .key("root.pos", 0.38, Ease.OUT_BACK, 0.3, -1.4, 1.9).key("root.pos", 0.8, Ease.IN_OUT_CUBIC, 0.0, -1.3, 0.2).key("root.pos", T, Ease.LINEAR, 0, -1.3, 0);
        c.key("root.rot", 0, Ease.LINEAR, 3, 0, 0).key("root.rot", 0.26, Ease.IN_OUT_CUBIC, 4, -14, 0)
                .key("root.rot", 0.38, Ease.OUT_BACK, 2, 20, 0).key("root.rot", 0.8, Ease.IN_OUT_CUBIC, 3, 2, 0).key("root.rot", T, Ease.LINEAR, 3, 0, 0);
        c.key("upper_torso.rot", 0, Ease.LINEAR, 6, 0, 0).key("upper_torso.rot", 0.26, Ease.IN_OUT_CUBIC, 7, -16, -2)
                .key("upper_torso.rot", 0.38, Ease.OUT_BACK, 9, 24, 3).key("upper_torso.rot", 0.8, Ease.IN_OUT_CUBIC, 6, 3, 0).key("upper_torso.rot", T, Ease.LINEAR, 6, 0, 0);
        c.key("head.rot", 0, Ease.LINEAR, 4, 0, 0).key("head.rot", 0.26, Ease.IN_OUT_CUBIC, 8, 12, 0).key("head.rot", 0.4, Ease.OUT_CUBIC, 6, -18, 0)
                .key("head.rot", 0.8, Ease.IN_OUT_CUBIC, 4, -2, 0).key("head.rot", T, Ease.LINEAR, 4, 0, 0);
        c.key("look.w", 0, Ease.LINEAR, 0.9).key("look.w", T, Ease.LINEAR, 0.9);
        c.key("look.pos", 0, Ease.LINEAR, 0, 24, 40).key("look.pos", T, Ease.LINEAR, 0, 24, 40);

        // The fists: the right one to the target, the left one guarding and pulling back as the right goes out.
        c.key("ik.r_arm.pos", 0, Ease.LINEAR, -4.6, 21, 5.5)
                .key("ik.r_arm.pos", 0.26, Ease.IN_OUT_CUBIC, -6.0, 19.6, 1.4)
                .key("ik.r_arm.pos", 0.36, Ease.OUT_CUBIC, -3.2, 22.0, 12.6)
                .key("ik.r_arm.pos", 0.42, Ease.IN_OUT_CUBIC, -3.2, 22.1, 13.1)
                .key("ik.r_arm.pos", 0.52, Ease.IN_OUT_CUBIC, -4.2, 21.8, 9.4)
                .key("ik.r_arm.pos", 0.8, Ease.IN_OUT_CUBIC, -4.6, 21, 5.5)
                .key("ik.r_arm.pos", T, Ease.LINEAR, -4.6, 21, 5.5);
        c.key("ik.r_arm.w", 0, Ease.LINEAR, 1).key("ik.r_arm.w", T, Ease.LINEAR, 1);
        c.key("ik.l_arm.pos", 0, Ease.LINEAR, 4.4, 21.5, 6)
                .key("ik.l_arm.pos", 0.26, Ease.IN_OUT_CUBIC, 4.8, 21.6, 7.4)
                .key("ik.l_arm.pos", 0.4, Ease.OUT_CUBIC, 5.2, 20.6, 3.4)
                .key("ik.l_arm.pos", 0.8, Ease.IN_OUT_CUBIC, 4.4, 21.5, 6)
                .key("ik.l_arm.pos", T, Ease.LINEAR, 4.4, 21.5, 6);
        c.key("ik.l_arm.w", 0, Ease.LINEAR, 1).key("ik.l_arm.w", T, Ease.LINEAR, 1);
        c.event(0.36, "impact");
        return c;
    }
}
