package com.lotusblight.overlay;

/**
 * A character posed directly on the 15-joint rig, in its own space (feet at the origin, facing +z), by the
 * scene that owns it: joint turns, IK targets for the fists and feet, the hands' fingers. Nothing here is
 * translated from the old six-part poses. On top of what the scene sets it keeps a light follow-through on
 * the head and chest, so they trail a movement instead of stopping with it.
 *
 * <p>The whole body's turn about the vertical ({@link #viewYaw}) and its lean ({@link #viewPitch}) are applied
 * when drawing, about the feet, so the IK targets can be given in the character's own space however it is turned.
 */
final class Actor {
    final Rig15 rig;
    final int[] skin;
    final Pose15 pose = new Pose15();
    double viewYaw, viewPitch;
    /** How closed each hand is, right and left: 0 loose and open, 1 a fist. */
    double fistR, fistL;
    /** How much the open fingers fan out, 0..1. */
    double spread = 0.3;

    private final Quat[] follow = new Quat[2];
    private final double[][] followVel = new double[2][3];
    private boolean primed;
    private long lastNano;

    Actor(int[] skin, boolean honcho) {
        this.rig = honcho ? Rig15.honcho() : Rig15.standard();
        this.skin = skin;
    }

    /** Starts a frame: everything at rest, the hands loose. */
    Pose15 begin() {
        pose.reset();
        viewYaw = 0;
        viewPitch = 0;
        fistR = 0;
        fistL = 0;
        spread = 0.3;
        return pose;
    }

    /** Points a limb's end at a point in the character's own space. {@code weight} blends from the joint turns to the solve. */
    Actor reach(Rig15.Limb limb, double x, double y, double z, double weight) {
        pose.reach(limb, x, y, z, weight);
        return this;
    }

    /** Sets the direction the elbow or knee of a limb points, in the character's own space. */
    Actor pole(Rig15.Limb limb, double x, double y, double z) {
        double[] p = pose.ik[limb.ordinal()].pole;
        p[0] = x;
        p[1] = y;
        p[2] = z;
        return this;
    }

    /** Turns a joint (Euler degrees, x pitch first). */
    Actor turn(Rig15.Joint j, double x, double y, double z) {
        pose.turn(j, x, y, z);
        return this;
    }

    /** Ends the frame: the hands are shaped, the trailing parts follow, and the figure is drawn with its feet at the origin given. */
    void draw(SoftRenderer r, double scale, double originX, double originY) {
        pose.hand(true, fistR, spread);
        pose.hand(false, fistL, spread);
        followThrough();
        Rig15.Skel sk = rig.solve(pose);
        r.drawRig(rig, sk, skin, viewYaw, viewPitch, scale, originX, originY);
    }

    /** The head and chest are carried by springs: they lag a sudden turn and settle with a little give. */
    private void followThrough() {
        long now = System.nanoTime();
        double dt = primed ? Math.min(0.05, (now - lastNano) / 1e9) : 0;
        lastNano = now;
        Rig15.Joint[] joints = {Rig15.Joint.HEAD, Rig15.Joint.UPPER_TORSO};
        double[] stiff = {30, 26}, damp = {0.72, 0.9};
        for (int i = 0; i < 2; i++) {
            Quat target = pose.rot[joints[i].ordinal()];
            if (!primed || follow[i] == null) {
                follow[i] = target;
                continue;
            }
            int n = Math.max(1, (int) Math.ceil(dt / 0.004));
            double h = dt / n;
            for (int k = 0; k < n && dt > 0; k++) {
                Quat e = target.mul(follow[i].conj());
                double sgn = e.w < 0 ? -1 : 1;
                double ew = Math.min(1, e.w * sgn), s = Math.sqrt(Math.max(0, 1 - ew * ew)), ang = 2 * Math.acos(ew);
                double tx = 0, ty = 0, tz = 0;
                if (s > 1e-9) {
                    tx = e.x * sgn / s * ang;
                    ty = e.y * sgn / s * ang;
                    tz = e.z * sgn / s * ang;
                }
                double[] v = followVel[i];
                v[0] += (stiff[i] * stiff[i] * tx - 2 * damp[i] * stiff[i] * v[0]) * h;
                v[1] += (stiff[i] * stiff[i] * ty - 2 * damp[i] * stiff[i] * v[1]) * h;
                v[2] += (stiff[i] * stiff[i] * tz - 2 * damp[i] * stiff[i] * v[2]) * h;
                double sp = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
                if (sp > 1e-9) follow[i] = Quat.axisAngle(v[0], v[1], v[2], sp * h).mul(follow[i]).normalized();
            }
            pose.rot[joints[i].ordinal()] = follow[i];
        }
        primed = true;
    }
}
