package com.lotusblight.cinema;

import com.lotusblight.overlay.Actor15;

/**
 * Puts a posed {@link Actor15} (a body in the player's skin, with elbows, knees and fingers) into a scene: at a place, turned to a
 * direction, at a size, tinted, with a material. The same call serves the processor's renderer and the graphics card's.
 */
public final class SkinActor {
    private SkinActor() {}

    /** One skin pixel in metres, for a body 1.8 m tall. */
    public static final double PX = 0.0568;

    /**
     * Draws the body. {@code origin} is between the feet (world), {@code facing} the turn about the vertical in radians (0 = looking along
     * +z), {@code scale} metres per skin pixel. The material (shine, relief, wrap) is whatever {@code r} has set when this is called.
     */
    public static void draw(Soft3D r, Actor15 actor, Soft3D.Tex skin, double[] origin, double facing, double scale, int tint, double emissive) {
        double cf = Math.cos(facing), sf = Math.sin(facing);
        double[][] uvTmpl = new double[4][2];
        for (Actor15.Quad q : actor.quads()) {
            double[][] w = new double[4][];
            for (int i = 0; i < 4; i++) w[i] = toWorld(q.corners()[i], origin, cf, sf, scale);
            uvTmpl[0][0] = q.u0() / 64.0; uvTmpl[0][1] = q.v0() / 64.0;
            uvTmpl[1][0] = q.u1() / 64.0; uvTmpl[1][1] = q.v0() / 64.0;
            uvTmpl[2][0] = q.u1() / 64.0; uvTmpl[2][1] = q.v1() / 64.0;
            uvTmpl[3][0] = q.u0() / 64.0; uvTmpl[3][1] = q.v1() / 64.0;
            r.quad(w, new double[][]{uvTmpl[0].clone(), uvTmpl[1].clone(), uvTmpl[2].clone(), uvTmpl[3].clone()}, skin, tint, emissive);
        }
    }

    /** A stage point (skin pixels) as a world point, for placing props in the actor's hands. */
    public static double[] toWorld(double[] stage, double[] origin, double facing, double scale) {
        return toWorld(stage, origin, Math.cos(facing), Math.sin(facing), scale);
    }

    private static double[] toWorld(double[] p, double[] origin, double cf, double sf, double scale) {
        double x = p[0] * scale, y = p[1] * scale, z = p[2] * scale;
        return new double[]{origin[0] + cf * x + sf * z, origin[1] + y, origin[2] - sf * x + cf * z};
    }

    /** A direction in stage space as a direction in the world (no move, no scale). */
    public static double[] dirToWorld(double[] d, double facing) {
        double cf = Math.cos(facing), sf = Math.sin(facing);
        return new double[]{cf * d[0] + sf * d[2], d[1], -sf * d[0] + cf * d[2]};
    }
}
