package com.lotusblight.overlay;

import java.util.ArrayList;
import java.util.List;

/**
 * The 15-joint rig (the "R15" layout): hips, waist, neck, and for each arm a shoulder, elbow and wrist, for
 * each leg a hip, knee and ankle. Units are skin pixels, origin between the feet, y up, facing +z - the same
 * space as {@link SkinModel}, so the same 64x64 skins fit. The boxes of a limb are cut at the joints, the
 * cuts being where the texture of the limb is cut as well.
 *
 * <p>{@link #solve} turns a {@link Pose15} into the world transform of every joint: forward kinematics for the
 * turns, then two-bone IK for the limbs that have a target, and a look-at for the head.
 */
final class Rig15 {
    static final int N = 15;

    enum Joint {
        LOWER_TORSO, UPPER_TORSO, HEAD,
        R_UPPER_ARM, R_LOWER_ARM, R_HAND,
        L_UPPER_ARM, L_LOWER_ARM, L_HAND,
        R_UPPER_LEG, R_LOWER_LEG, R_FOOT,
        L_UPPER_LEG, L_LOWER_LEG, L_FOOT;

        /** Channel-name form: {@code r_upper_arm}. */
        String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        static Joint byKey(String key) {
            for (Joint j : values()) if (j.key().equals(key)) return j;
            return null;
        }
    }

    /** A chain that can be solved to a target: the upper and lower joint and the end (hand or foot). */
    enum Limb {
        R_ARM(Joint.R_UPPER_ARM, Joint.R_LOWER_ARM, Joint.R_HAND, true),
        L_ARM(Joint.L_UPPER_ARM, Joint.L_LOWER_ARM, Joint.L_HAND, true),
        R_LEG(Joint.R_UPPER_LEG, Joint.R_LOWER_LEG, Joint.R_FOOT, false),
        L_LEG(Joint.L_UPPER_LEG, Joint.L_LOWER_LEG, Joint.L_FOOT, false);

        final Joint upper, lower, end;
        final boolean arm;

        Limb(Joint upper, Joint lower, Joint end, boolean arm) {
            this.upper = upper;
            this.lower = lower;
            this.end = end;
            this.arm = arm;
        }

        String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        static Limb byKey(String key) {
            for (Limb l : values()) if (l.key().equals(key)) return l;
            return null;
        }
    }

    /** One textured face of one joint's box: corners in rest-pose model space and the skin rectangle over them. */
    record Face(Joint joint, double[][] corners, int u0, int v0, int u1, int v1) {}

    private static final int[] PARENT = {
            -1, 0, 1,
            1, 3, 4,
            1, 6, 7,
            0, 9, 10,
            0, 12, 13};

    /** Length of a hand from the wrist to the fist's tip, and a foot's height from the ankle to the sole. */
    static final double HAND_LENGTH = 3, ANKLE_HEIGHT = 3;

    final boolean honcho;
    /** Rest-pose world position of every joint's pivot. */
    final double[][] pivot = new double[N][];
    final double[] limbUpper = new double[4], limbLower = new double[4];
    final List<Face> faces = new ArrayList<>();

    private Rig15(boolean honcho) {
        this.honcho = honcho;
        double armC = honcho ? 6 : 5.5;
        pivot[Joint.LOWER_TORSO.ordinal()] = new double[]{0, 13, 0};
        pivot[Joint.UPPER_TORSO.ordinal()] = new double[]{0, 17, 0};
        pivot[Joint.HEAD.ordinal()] = new double[]{0, 24, 0};
        for (int side = 0; side < 2; side++) {
            double s = side == 0 ? -1 : 1;                              // right limbs are on -x, as in the skin model
            int arm = Joint.R_UPPER_ARM.ordinal() + side * 3, leg = Joint.R_UPPER_LEG.ordinal() + side * 3;
            pivot[arm] = new double[]{s * armC, 22, 0};
            pivot[arm + 1] = new double[]{s * armC, 18, 0};
            pivot[arm + 2] = new double[]{s * armC, 15, 0};
            pivot[leg] = new double[]{s * 2, 12, 0};
            pivot[leg + 1] = new double[]{s * 2, 7, 0};
            pivot[leg + 2] = new double[]{s * 2, 3, 0};
        }
        for (Limb l : Limb.values()) {
            double[] a = pivot[l.upper.ordinal()], b = pivot[l.lower.ordinal()], c = pivot[l.end.ordinal()];
            limbUpper[l.ordinal()] = Quat.length(Quat.sub(b, a));
            // The arm is solved to the tip of the fist, taking the hand as straight on from the forearm.
            limbLower[l.ordinal()] = Quat.length(Quat.sub(c, b)) + (l.arm ? HAND_LENGTH : 0);
        }
        buildModel(armC);
    }

    /** The standard slim skin: 3-pixel arms, with the outer layers. */
    static Rig15 standard() {
        return new Rig15(false);
    }

    /** Honcho's proportions: 4-pixel arms, only the layer over the head. */
    static Rig15 honcho() {
        return new Rig15(true);
    }

    static Joint parentOf(Joint j) {
        int p = PARENT[j.ordinal()];
        return p < 0 ? null : Joint.values()[p];
    }

    // ------------------------------------------------------------ the model

    private void buildModel(double armC) {
        int armW = honcho ? 4 : 3;
        double armX = honcho ? 8 : 7;
        // Head: the base box and the layer over it.
        slab(Joint.HEAD, -4, 24, -4, 8, 8, 8, 0, 0, 0, 24, 32);
        slab(Joint.HEAD, -4, 24, -4, 8, 8, 8, 32, 0, 0.5, 24, 32);
        // Body, cut at the waist.
        for (double grow : honcho ? new double[]{0} : new double[]{0, 0.25}) {
            int v = grow == 0 ? 16 : 32;
            slab(Joint.LOWER_TORSO, -4, 12, -2, 8, 12, 4, 16, v, grow, 12, 17);
            slab(Joint.UPPER_TORSO, -4, 12, -2, 8, 12, 4, 16, v, grow, 17, 24);
        }
        // Arms: upper (with the shoulder cap), forearm, hand.
        for (double grow : honcho ? new double[]{0} : new double[]{0, 0.25}) {
            int v = grow == 0 ? 16 : 32, lv = grow == 0 ? 48 : 48, lu = grow == 0 ? 32 : 48;
            Joint[] r = {Joint.R_UPPER_ARM, Joint.R_LOWER_ARM, Joint.R_HAND};
            Joint[] l = {Joint.L_UPPER_ARM, Joint.L_LOWER_ARM, Joint.L_HAND};
            double[][] cuts = {{18, 24}, {15, 18}, {12, 15}};
            for (int i = 0; i < 3; i++) {
                slab(r[i], -armX, 12, -2, armW, 12, 4, 40, v, grow, cuts[i][0], cuts[i][1]);
                slab(l[i], 4, 12, -2, armW, 12, 4, lu, lv, grow, cuts[i][0], cuts[i][1]);
            }
        }
        // Legs: thigh, shin, foot.
        for (double grow : honcho ? new double[]{0} : new double[]{0, 0.25}) {
            int rv = grow == 0 ? 16 : 32, lu = grow == 0 ? 16 : 0, lv = 48;
            Joint[] r = {Joint.R_UPPER_LEG, Joint.R_LOWER_LEG, Joint.R_FOOT};
            Joint[] l = {Joint.L_UPPER_LEG, Joint.L_LOWER_LEG, Joint.L_FOOT};
            double[][] cuts = {{7, 12}, {3, 7}, {0, 3}};
            for (int i = 0; i < 3; i++) {
                slab(r[i], -4, 0, -2, 4, 12, 4, 0, rv, grow, cuts[i][0], cuts[i][1]);
                slab(l[i], 0, 0, -2, 4, 12, 4, lu, lv, grow, cuts[i][0], cuts[i][1]);
            }
        }
    }

    /**
     * The part of a Minecraft-layout box between heights y0 and y1, as a joint's box: its four sides cut from
     * the box's own side rectangles, and a cap above and below (inside the model in the straight pose, shown
     * where a joint bends). {@code grow} puffs the box out, at the very top and bottom of the box as well.
     */
    private void slab(Joint j, double bx, double by, double bz, int w, int h, int d, int u, int v, double grow, double y0, double y1) {
        double x0 = bx - grow, x1 = bx + w + grow, z0 = bz - grow, z1 = bz + d + grow;
        double top = by + h, bottom = by;
        double ya = y0 <= bottom ? y0 - grow : y0, yb = y1 >= top ? y1 + grow : y1;
        int r1 = v + d + (int) (top - y1), r0 = v + d + (int) (top - y0);
        faces.add(new Face(j, new double[][]{{x0, yb, z0}, {x1, yb, z0}, {x1, yb, z1}, {x0, yb, z1}}, u + d, v, u + d + w, v + d));
        faces.add(new Face(j, new double[][]{{x0, ya, z1}, {x1, ya, z1}, {x1, ya, z0}, {x0, ya, z0}}, u + d + w, v, u + d + 2 * w, v + d));
        faces.add(new Face(j, new double[][]{{x0, yb, z0}, {x0, yb, z1}, {x0, ya, z1}, {x0, ya, z0}}, u, r1, u + d, r0));
        faces.add(new Face(j, new double[][]{{x0, yb, z1}, {x1, yb, z1}, {x1, ya, z1}, {x0, ya, z1}}, u + d, r1, u + d + w, r0));
        faces.add(new Face(j, new double[][]{{x1, yb, z1}, {x1, yb, z0}, {x1, ya, z0}, {x1, ya, z1}}, u + d + w, r1, u + 2 * d + w, r0));
        faces.add(new Face(j, new double[][]{{x1, yb, z0}, {x0, yb, z0}, {x0, ya, z0}, {x1, ya, z0}}, u + 2 * d + w, r1, u + 2 * d + 2 * w, r0));
    }

    // ------------------------------------------------------------ the solver

    /** What {@link #solve} makes of a pose: the world transform of every joint and what it took to get there. */
    static final class Skel {
        final Rig15 rig;
        /** Maps a joint's own space (origin at its pivot) to the stage. */
        final Quat.Xf[] world = new Quat.Xf[N];
        final Quat[] worldRot = new Quat[N];
        /** The limb solves: how far out of reach each target was (1 = exactly at full stretch, above 1 = too far). */
        final double[] stretch = new double[4];
        final boolean[] solved = new boolean[4];

        Skel(Rig15 rig) {
            this.rig = rig;
        }

        /** A point given in rest-pose model space, carried along with the joint. */
        double[] carry(Joint j, double[] restPoint) {
            return world[j.ordinal()].apply(Quat.sub(restPoint, rig.pivot[j.ordinal()]));
        }

        double[] pivotPos(Joint j) {
            return world[j.ordinal()].t.clone();
        }

        /** Tip of the fist, or the sole under the ankle. */
        double[] endPoint(Limb l) {
            double[] p = rig.pivot[l.end.ordinal()];
            return carry(l.end, new double[]{p[0], p[1] - (l.arm ? HAND_LENGTH : ANKLE_HEIGHT), p[2]});
        }

        double[] headTop() {
            return carry(Joint.HEAD, new double[]{0, 32, 0});
        }
    }

    /** Solves a pose into joint transforms. */
    Skel solve(Pose15 pose) {
        Skel sk = new Skel(this);
        Quat[] local = pose.rot.clone();
        local[0] = pose.rootRot;
        double[] rootPos = Quat.add(pivot[0], pose.rootPos);
        sk.world[0] = Quat.Xf.of(pose.rootRot, rootPos);
        sk.worldRot[0] = pose.rootRot;
        double[] fwd = pose.rootRot.rotate(new double[]{0, 0, 1});
        double yaw = Math.atan2(fwd[0], fwd[2]);
        Quat yawQ = Quat.axisAngle(0, 1, 0, yaw);

        Quat[] ikLocal = new Quat[N];
        double[] ikW = new double[N];
        for (int i = 1; i < N; i++) {
            Joint j = Joint.values()[i];
            int p = PARENT[i];
            // A limb's solve runs when its upper joint is reached, and sets the turns of the whole chain.
            for (Limb l : Limb.values()) {
                Pose15.Ik k = pose.ik[l.ordinal()];
                if (l.upper == j && k.w > 1e-4) solveLimb(sk, l, k, p, yawQ, yaw, ikLocal, ikW);
            }
            if (j == Joint.HEAD && pose.lookW > 1e-4) {
                Quat look = lookAt(sk, pose, p);
                local[i] = Quat.slerp(local[i], look, pose.lookW);
            }
            if (ikLocal[i] != null) local[i] = Quat.slerp(local[i], ikLocal[i], ikW[i]);
            Quat.Xf step = Quat.Xf.of(local[i], Quat.sub(pivot[i], pivot[p]));
            sk.world[i] = sk.world[p].mul(Quat.Xf.of(Quat.IDENTITY, step.t)).mul(Quat.Xf.of(local[i], new double[]{0, 0, 0}));
            sk.worldRot[i] = sk.worldRot[p].mul(local[i]);
        }
        return sk;
    }

    /** The head's turn so that it faces the look target, within what a neck can do. */
    private Quat lookAt(Skel sk, Pose15 pose, int parent) {
        double[] neck = sk.world[parent].apply(Quat.sub(pivot[Joint.HEAD.ordinal()], pivot[parent]));
        double[] dir = Quat.unit(Quat.sub(pose.lookPos, neck));
        double[] d = sk.worldRot[parent].conj().rotate(dir);
        double yaw = Math.atan2(d[0], d[2]);
        double pitch = Math.atan2(-d[1], Math.hypot(d[0], d[2]));
        yaw = Math.max(-Math.toRadians(75), Math.min(Math.toRadians(75), yaw));
        pitch = Math.max(-Math.toRadians(50), Math.min(Math.toRadians(50), pitch));
        return Quat.euler(Math.toDegrees(pitch), Math.toDegrees(yaw), 0);
    }

    /**
     * Two-bone IK with a true hinge at the elbow or knee: the upper and lower bone share one axis, so the
     * forearm or shin never twists against the arm or thigh. The pole says which way the joint points.
     */
    private void solveLimb(Skel sk, Limb l, Pose15.Ik k, int parent, Quat yawQ, double yaw, Quat[] ikLocal, double[] ikW) {
        int li = l.ordinal();
        double l1 = limbUpper[li], l2 = limbLower[li];
        double[] shoulder = sk.world[parent].apply(Quat.sub(pivot[l.upper.ordinal()], pivot[parent]));
        double[] toTarget = Quat.sub(k.pos, shoulder);
        double dist = Quat.length(toTarget);
        sk.stretch[li] = dist / (l1 + l2);
        sk.solved[li] = true;
        double maxD = (l1 + l2) * 0.9995, minD = Math.abs(l1 - l2) * 1.0005 + 0.02;
        double d = Math.max(minD, Math.min(maxD, dist));
        double[] a = dist < 1e-9 ? new double[]{0, -1, 0} : Quat.scale(toTarget, 1 / dist);
        double[] target = Quat.add(shoulder, Quat.scale(a, d));

        double[] pole = k.pole;
        if (Quat.length(pole) < 1e-6) {
            double side = pivot[l.upper.ordinal()][0] < 0 ? -1 : 1;
            // Arms: the elbow points down and back, so it stays natural when the fist goes out in front. Legs: the knee forward.
            pole = sk.worldRot[parent].rotate(l.arm ? new double[]{side * 0.35, -1, -0.6} : new double[]{side * 0.12, 0, 1});
        }
        double[] pp = Quat.sub(pole, Quat.scale(a, Quat.dot(pole, a)));
        if (Quat.length(pp) < 1e-6) pp = Quat.cross(a, new double[]{1, 0, 0});
        if (Quat.length(pp) < 1e-6) pp = new double[]{0, 0, 1};
        pp = Quat.unit(pp);

        double proj = (l1 * l1 - l2 * l2 + d * d) / (2 * d);
        double hgt = Math.sqrt(Math.max(0, l1 * l1 - proj * proj));
        double[] elbow = Quat.add(Quat.add(shoulder, Quat.scale(a, proj)), Quat.scale(pp, hgt));
        double[] u = Quat.unit(Quat.sub(elbow, shoulder));
        double[] lo = Quat.unit(Quat.sub(target, elbow));

        // The hinge axis is perpendicular to the bend plane; it is taken on the side that keeps the limb's own
        // sideways axis from flipping.
        double[] n = Quat.unit(Quat.cross(pp, a));
        double[] xArc = Quat.arc(new double[]{0, -1, 0}, u).rotate(new double[]{1, 0, 0});
        double[] xw = Quat.dot(n, xArc) >= 0 ? n : Quat.scale(n, -1);

        Quat rUpper = frame(xw, u), rLower = frame(xw, lo);
        Quat parentRot = sk.worldRot[parent];
        int ui = l.upper.ordinal(), lo_i = l.lower.ordinal(), ei = l.end.ordinal();
        ikLocal[ui] = parentRot.conj().mul(rUpper);
        ikLocal[lo_i] = rUpper.conj().mul(rLower);
        ikW[ui] = ikW[lo_i] = k.w;
        if (!l.arm) {
            // The foot lies flat on the floor (pitched by roll), facing where the body does plus its own turn.
            Quat desired = Quat.axisAngle(0, 1, 0, yaw + Math.toRadians(k.yaw)).mul(Quat.axisAngle(1, 0, 0, Math.toRadians(k.roll)));
            ikLocal[ei] = rLower.conj().mul(desired);
            ikW[ei] = k.w;
        }
    }

    /** The turn that puts a limb's hinge axis on {@code xw} and its length axis (down at rest) along {@code dir}. */
    private static Quat frame(double[] xw, double[] dir) {
        double[] third = Quat.cross(xw, dir);
        // Columns: the hinge axis, the direction opposite the length axis, the direction opposite the third.
        double[] c1 = Quat.scale(dir, -1), c2 = Quat.scale(third, -1);
        return Quat.fromMatrix(new double[]{
                xw[0], c1[0], c2[0],
                xw[1], c1[1], c2[1],
                xw[2], c1[2], c2[2]});
    }
}
