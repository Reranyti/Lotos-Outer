package com.lotusblight.overlay;

import java.util.ArrayList;
import java.util.List;

/**
 * A body with the player's skin that can be posed and moved, for any scene: the whole {@link Rig15} (hips, waist, head, arms with elbows,
 * legs with knees, and five fingers of two bones on each hand) behind a small public face. Describe the pose (turns, where the hands and
 * feet are to be, how the fingers curl, where the head looks), ask for {@link #solve}, and take the boxes of the body back with the skin
 * rectangles to colour them with. Everything is in stage space: skin pixels, y up, the floor at y = 0, the body facing +z when unturned.
 * The engine's skin renderer puts those boxes in a scene, in any size, place and direction.
 */
public final class Actor15 {
    /** One textured face of the posed body: four corners in stage space and the rectangle of the skin over them. */
    public record Quad(double[][] corners, int joint, int u0, int v0, int u1, int v1) {}

    /** A hand's place: the wrist and the three axes of the hand (x across, y up the forearm, z the front of the fist), in stage space. */
    public record Hand(double[] wrist, double[] x, double[] y, double[] z) {}

    /** The joints a turn can be given to. */
    public enum Part {
        LOWER_TORSO(Rig15.Joint.LOWER_TORSO), UPPER_TORSO(Rig15.Joint.UPPER_TORSO), HEAD(Rig15.Joint.HEAD),
        R_UPPER_ARM(Rig15.Joint.R_UPPER_ARM), R_LOWER_ARM(Rig15.Joint.R_LOWER_ARM), R_HAND(Rig15.Joint.R_HAND),
        L_UPPER_ARM(Rig15.Joint.L_UPPER_ARM), L_LOWER_ARM(Rig15.Joint.L_LOWER_ARM), L_HAND(Rig15.Joint.L_HAND),
        R_UPPER_LEG(Rig15.Joint.R_UPPER_LEG), R_LOWER_LEG(Rig15.Joint.R_LOWER_LEG), R_FOOT(Rig15.Joint.R_FOOT),
        L_UPPER_LEG(Rig15.Joint.L_UPPER_LEG), L_LOWER_LEG(Rig15.Joint.L_LOWER_LEG), L_FOOT(Rig15.Joint.L_FOOT);

        final Rig15.Joint joint;

        Part(Rig15.Joint j) {
            this.joint = j;
        }
    }

    private final Rig15 rig = Rig15.standard();
    private final Pose15 pose = new Pose15();
    private Rig15.Skel skel;

    /** Back to standing at rest. */
    public Actor15 reset() {
        pose.reset();
        skel = null;
        return this;
    }

    /** Moves the hips from where they stand at rest (skin pixels). */
    public Actor15 move(double x, double y, double z) {
        pose.rootPos[0] = x;
        pose.rootPos[1] = y;
        pose.rootPos[2] = z;
        return this;
    }

    /** Turns a joint, in Euler degrees (x pitch, y turn, z roll), from where it sits in its parent. The waist turns the whole body. */
    public Actor15 turn(Part part, double xDeg, double yDeg, double zDeg) {
        pose.turn(part.joint, xDeg, yDeg, zDeg);
        return this;
    }

    /** A hand: 0 = open and loose, 1 = a closed fist; {@code spread} 0..1 fans the open fingers. */
    public Actor15 hand(boolean right, double curl, double spread) {
        pose.hand(right, curl, spread);
        return this;
    }

    /** Every finger on its own ({@code curl[0..4]}: thumb, index, middle, ring, little); {@code t} (seconds) drives a slow flutter. */
    public Actor15 fingers(boolean right, double[] curl, double spread, double t) {
        pose.fingers(right, curl, spread, t);
        return this;
    }

    /**
     * An arm solved to reach a point: the tip of the fist, in stage space; {@code pole} is where the elbow should point (zero = naturally);
     * {@code weight} 1 reaches it, 0 leaves the joint turns alone.
     */
    public Actor15 reachArm(boolean right, double[] tip, double[] pole, double weight) {
        Pose15.Ik k = pose.ik[right ? Rig15.Limb.R_ARM.ordinal() : Rig15.Limb.L_ARM.ordinal()];
        k.w = weight;
        System.arraycopy(tip, 0, k.pos, 0, 3);
        if (pole != null) System.arraycopy(pole, 0, k.pole, 0, 3);
        return this;
    }

    /** A leg solved to put the ankle at a point (stage space); {@code rollDeg} pitches the sole, {@code yawDeg} turns the foot. */
    public Actor15 placeFoot(boolean right, double[] ankle, double[] pole, double weight, double rollDeg, double yawDeg) {
        Pose15.Ik k = pose.ik[right ? Rig15.Limb.R_LEG.ordinal() : Rig15.Limb.L_LEG.ordinal()];
        k.w = weight;
        System.arraycopy(ankle, 0, k.pos, 0, 3);
        if (pole != null) System.arraycopy(pole, 0, k.pole, 0, 3);
        k.roll = rollDeg;
        k.yaw = yawDeg;
        return this;
    }

    /** The head turns to look at a point (stage space). */
    public Actor15 lookAt(double[] point, double weight) {
        pose.lookW = weight;
        System.arraycopy(point, 0, pose.lookPos, 0, 3);
        return this;
    }

    /** Works the pose out; call after setting it, before reading the boxes or the hands. */
    public Actor15 solve() {
        skel = rig.solve(pose);
        return this;
    }

    /** The boxes of the posed body (the arms, legs, hands and fingers included). */
    public List<Quad> quads() {
        if (skel == null) solve();
        List<Quad> out = new ArrayList<>(rig.faces.size());
        for (Rig15.Face f : rig.faces) {
            Rig15.Joint j = f.joint();
            double[][] cs = new double[4][];
            for (int i = 0; i < 4; i++) cs[i] = skel.carry(j, f.corners()[i]);
            out.add(new Quad(cs, j.ordinal(), f.u0(), f.v0(), f.u1(), f.v1()));
        }
        return out;
    }

    /** Where a hand is, for putting something in it (a knife, a cassette). */
    public Hand handOf(boolean right) {
        if (skel == null) solve();
        Rig15.Joint hand = right ? Rig15.Joint.R_HAND : Rig15.Joint.L_HAND;
        double[] o = skel.pivotPos(hand);
        double[] p = rig.pivot[hand.ordinal()];
        double[] px = skel.carry(hand, new double[]{p[0] + 1, p[1], p[2]});
        double[] py = skel.carry(hand, new double[]{p[0], p[1] + 1, p[2]});
        double[] pz = skel.carry(hand, new double[]{p[0], p[1], p[2] + 1});
        return new Hand(o, Quat.sub(px, o), Quat.sub(py, o), Quat.sub(pz, o));
    }

    /** The tip of a fist, or the point of the head's top, in stage space. */
    public double[] fistTip(boolean right) {
        if (skel == null) solve();
        return skel.endPoint(right ? Rig15.Limb.R_ARM : Rig15.Limb.L_ARM);
    }

    public double[] headTop() {
        if (skel == null) solve();
        return skel.headTop();
    }
}
