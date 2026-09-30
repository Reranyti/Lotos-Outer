package com.lotusblight.overlay;

import java.util.Arrays;

/**
 * One frame of an animation on the {@link Rig15}: where the hips are and how they are turned, how every
 * joint is turned, and - for the hands and feet - optional targets the limbs are solved towards (IK), plus
 * an optional point for the head to look at. All positions are in stage space: skin pixels, y up, the
 * floor at y = 0, the model facing +z when unturned.
 */
final class Pose15 {
    /** Where a hand or a foot should be, how much that counts against the joint angles, and how the elbow/knee points. */
    static final class Ik {
        /** 0 = the joint angles alone, 1 = the limb is solved to reach {@link #pos}. */
        double w;
        /** Hands: the tip of the fist. Feet: the ankle. */
        final double[] pos = new double[3];
        /** The direction the elbow or knee should point (stage space); a zero vector means the natural one. */
        final double[] pole = new double[3];
        /** Feet: pitch of the sole in degrees (positive = toes down). */
        double roll;
        /** Feet: turn of the foot about the vertical, in degrees, added to the body's own turn. */
        double yaw;

        void copyFrom(Ik o) {
            w = o.w;
            System.arraycopy(o.pos, 0, pos, 0, 3);
            System.arraycopy(o.pole, 0, pole, 0, 3);
            roll = o.roll;
            yaw = o.yaw;
        }
    }

    /** Offset of the hips' centre from where it is at rest. */
    final double[] rootPos = new double[3];
    Quat rootRot = Quat.IDENTITY;
    /** Local turn of every joint (the root's own is {@link #rootRot}). */
    final Quat[] rot = new Quat[Rig15.N];
    /** A move of a joint from where it sits in its parent, in the parent's space (skin pixels). */
    final double[][] offset = new double[Rig15.N][3];
    final Ik[] ik = new Ik[Rig15.Limb.values().length];
    double lookW;
    final double[] lookPos = new double[3];

    Pose15() {
        for (int i = 0; i < ik.length; i++) ik[i] = new Ik();
        reset();
    }

    void reset() {
        Arrays.fill(rootPos, 0);
        rootRot = Quat.IDENTITY;
        Arrays.fill(rot, Quat.IDENTITY);
        for (double[] o : offset) Arrays.fill(o, 0);
        for (Ik k : ik) {
            k.w = 0;
            Arrays.fill(k.pos, 0);
            Arrays.fill(k.pole, 0);
            k.roll = 0;
            k.yaw = 0;
        }
        lookW = 0;
        Arrays.fill(lookPos, 0);
    }

    Pose15 copy() {
        Pose15 p = new Pose15();
        p.copyFrom(this);
        return p;
    }

    void copyFrom(Pose15 o) {
        System.arraycopy(o.rootPos, 0, rootPos, 0, 3);
        rootRot = o.rootRot;
        System.arraycopy(o.rot, 0, rot, 0, rot.length);
        for (int i = 0; i < offset.length; i++) System.arraycopy(o.offset[i], 0, offset[i], 0, 3);
        for (int i = 0; i < ik.length; i++) ik[i].copyFrom(o.ik[i]);
        lookW = o.lookW;
        System.arraycopy(o.lookPos, 0, lookPos, 0, 3);
    }

    /** Sets a joint's local turn from Euler degrees (x pitch, y turn, z roll). */
    Pose15 turn(Rig15.Joint j, double xDeg, double yDeg, double zDeg) {
        if (j == Rig15.Joint.LOWER_TORSO) rootRot = Quat.euler(xDeg, yDeg, zDeg);
        else rot[j.ordinal()] = Quat.euler(xDeg, yDeg, zDeg);
        return this;
    }

    /**
     * Shapes a hand: 0 = open and loose, 1 = a closed fist. The fingers curl towards the palm; the thumb comes
     * across. {@code spread} (0..1) fans the open fingers a little.
     */
    Pose15 hand(boolean right, double curl, double spread) {
        double sign = right ? 1 : -1;                              // the palm faces the body: right hand curls towards +x
        double[][] amount = {{38, 52}, {78, 88}, {86, 92}, {90, 94}, {92, 96}};
        double[] relax = {6, 14, 20, 26, 32};
        for (int f = 0; f < 5; f++) {
            double c = Math.max(0, Math.min(1, curl));
            double a = relax[f] * (1 - c) * 0.6 + amount[f][0] * c, b = relax[f] * 0.9 * (1 - c) + amount[f][1] * c;
            double fan = f == 0 ? 0 : (f - 2.5) * 5 * spread * (1 - c);
            rot[Rig15.finger(right, f, 0).ordinal()] = Quat.euler(fan, f == 0 ? -sign * 12 * c : 0, sign * a);
            rot[Rig15.finger(right, f, 1).ordinal()] = Quat.euler(0, 0, sign * b);
        }
        return this;
    }

    /** Puts a limb's end at a stage-space point and switches the solver on for it. */
    Pose15 reach(Rig15.Limb limb, double x, double y, double z, double weight) {
        Ik k = ik[limb.ordinal()];
        k.pos[0] = x;
        k.pos[1] = y;
        k.pos[2] = z;
        k.w = weight;
        return this;
    }

    /** {@code a} at {@code t} = 0 to {@code b} at {@code t} = 1. */
    static Pose15 lerp(Pose15 a, Pose15 b, double t) {
        Pose15 p = new Pose15();
        for (int i = 0; i < 3; i++) p.rootPos[i] = a.rootPos[i] + (b.rootPos[i] - a.rootPos[i]) * t;
        p.rootRot = Quat.slerp(a.rootRot, b.rootRot, t);
        for (int i = 0; i < p.rot.length; i++) p.rot[i] = Quat.slerp(a.rot[i], b.rot[i], t);
        for (int i = 0; i < p.ik.length; i++) {
            Ik x = a.ik[i], y = b.ik[i], o = p.ik[i];
            o.w = x.w + (y.w - x.w) * t;
            for (int c = 0; c < 3; c++) {
                o.pos[c] = x.pos[c] + (y.pos[c] - x.pos[c]) * t;
                o.pole[c] = x.pole[c] + (y.pole[c] - x.pole[c]) * t;
            }
            o.roll = x.roll + (y.roll - x.roll) * t;
            o.yaw = x.yaw + (y.yaw - x.yaw) * t;
        }
        p.lookW = a.lookW + (b.lookW - a.lookW) * t;
        for (int c = 0; c < 3; c++) p.lookPos[c] = a.lookPos[c] + (b.lookPos[c] - a.lookPos[c]) * t;
        return p;
    }

    /**
     * A layer laid over {@code base}: where {@code top} differs from the rest pose it is added on, scaled by
     * {@code weight}; {@code mask} (may be null) limits it to some joints. IK targets and the look-at are taken
     * from {@code top} in proportion to their own weights.
     */
    static Pose15 layer(Pose15 base, Pose15 top, double weight, boolean[] mask) {
        Pose15 p = base.copy();
        for (int c = 0; c < 3; c++) p.rootPos[c] += top.rootPos[c] * weight;
        p.rootRot = Quat.slerp(Quat.IDENTITY, top.rootRot, weight).mul(p.rootRot);
        for (int i = 0; i < p.rot.length; i++) {
            if (mask != null && !mask[i]) continue;
            p.rot[i] = Quat.slerp(Quat.IDENTITY, top.rot[i], weight).mul(p.rot[i]);
        }
        for (int i = 0; i < p.ik.length; i++) {
            double k = top.ik[i].w * weight;
            if (k <= 0) continue;
            Ik mixed = top.ik[i];
            Ik o = p.ik[i];
            double keep = 1 - k;
            for (int c = 0; c < 3; c++) {
                o.pos[c] = o.pos[c] * keep + mixed.pos[c] * k;
                o.pole[c] = o.pole[c] * keep + mixed.pole[c] * k;
            }
            o.w = Math.max(o.w, k);
        }
        if (top.lookW * weight > 0) {
            double k = top.lookW * weight;
            for (int c = 0; c < 3; c++) p.lookPos[c] = p.lookPos[c] * (1 - k) + top.lookPos[c] * k;
            p.lookW = Math.max(p.lookW, k);
        }
        return p;
    }
}
