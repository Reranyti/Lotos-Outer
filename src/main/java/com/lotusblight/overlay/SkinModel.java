package com.lotusblight.overlay;

import java.util.ArrayList;
import java.util.List;

/**
 * The player model as plain boxes, laid out exactly like Minecraft's slim (3-pixel arm) skin: every
 * part has its base box and its outer layer, each face mapped to its own rectangle of the 64x64 skin.
 * Units are skin pixels, origin between the feet, y up, the model facing +z.
 */
final class SkinModel {
    enum Part { HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

    /** One textured face: four corners in model space and the skin rectangle drawn over them. */
    record Face(Part part, double[][] corners, int u0, int v0, int u1, int v1) {}

    /** Where each limb rotates from. */
    static final double[][] PIVOTS = {
            {0, 24, 0},     // head - the neck
            {0, 24, 0},     // body
            {-5, 22, 0},    // right arm - the shoulder
            {5, 22, 0},     // left arm
            {-2, 12, 0},    // right leg - the hip
            {2, 12, 0},     // left leg
    };

    final List<Face> faces = new ArrayList<>();

    /** The standard skin model. */
    SkinModel() {
        this(false);
    }

    /**
     * Honcho: the boxes of his own model ({@code honcho.geo.json}) - arms and legs 4 pixels wide, each split into
     * an upper and a lower half with a layout of its own - so that his texture lies where it was drawn. The
     * halves move together (his model bends at the elbows and knees; here he is one piece).
     */
    static SkinModel honcho() {
        return new SkinModel(true);
    }

    private SkinModel(boolean honcho) {
        if (honcho) {
            box(Part.HEAD, -4, 24, -4, 8, 8, 8, 0, 0, 0);
            box(Part.HEAD, -4, 24, -4, 8, 8, 8, 32, 0, 0.5);          // the glitching layer over the head
            box(Part.BODY, -4, 12, -2, 8, 12, 4, 16, 16, 0);
            // Per face: top, bottom, then the four sides as they lie in the texture, each {u, v, width, height}.
            cube(Part.RIGHT_ARM, -8, 18, -2, 4, 6, 4, new int[][]{{44, 16, 4, 4}, {44, 25, 4, 1}, {40, 20, 4, 6}, {44, 20, 4, 6}, {48, 20, 4, 6}, {52, 20, 4, 6}});
            cube(Part.RIGHT_ARM, -8, 12, -2, 4, 6, 4, new int[][]{{44, 26, 4, 1}, {48, 20, 4, -4}, {40, 26, 4, 6}, {44, 26, 4, 6}, {48, 26, 4, 6}, {52, 26, 4, 6}});
            cube(Part.LEFT_ARM, 4, 18, -2, 4, 6, 4, new int[][]{{36, 48, 4, 4}, {36, 57, 4, 1}, {32, 52, 4, 6}, {36, 52, 4, 6}, {40, 52, 4, 6}, {44, 52, 4, 6}});
            cube(Part.LEFT_ARM, 4, 12, -2, 4, 6, 4, new int[][]{{36, 58, 4, 1}, {40, 52, 4, -4}, {32, 58, 4, 6}, {36, 58, 4, 6}, {40, 58, 4, 6}, {44, 58, 4, 6}});
            cube(Part.RIGHT_LEG, -4, 6, -2, 4, 6, 4, new int[][]{{4, 16, 4, 4}, {4, 25, 4, 1}, {0, 20, 4, 6}, {4, 20, 4, 6}, {8, 20, 4, 6}, {12, 20, 4, 6}});
            cube(Part.RIGHT_LEG, -4, 0, -2, 4, 6, 4, new int[][]{{4, 26, 4, 1}, {8, 20, 4, -4}, {0, 26, 4, 6}, {4, 26, 4, 6}, {8, 26, 4, 6}, {12, 26, 4, 6}});
            cube(Part.LEFT_LEG, 0, 6, -2, 4, 6, 4, new int[][]{{20, 48, 4, 4}, {20, 57, 4, 1}, {16, 52, 4, 6}, {20, 52, 4, 6}, {24, 52, 4, 6}, {28, 52, 4, 6}});
            cube(Part.LEFT_LEG, 0, 0, -2, 4, 6, 4, new int[][]{{20, 58, 4, 1}, {24, 52, 4, -4}, {16, 58, 4, 6}, {20, 58, 4, 6}, {24, 58, 4, 6}, {28, 58, 4, 6}});
            return;
        }
        // Base layer, then the outer layer puffed out a little so it sits over the base.
        box(Part.HEAD, -4, 24, -4, 8, 8, 8, 0, 0, 0);
        box(Part.HEAD, -4, 24, -4, 8, 8, 8, 32, 0, 0.5);
        box(Part.BODY, -4, 12, -2, 8, 12, 4, 16, 16, 0);
        box(Part.BODY, -4, 12, -2, 8, 12, 4, 16, 32, 0.25);
        box(Part.RIGHT_ARM, -7, 12, -2, 3, 12, 4, 40, 16, 0);
        box(Part.RIGHT_ARM, -7, 12, -2, 3, 12, 4, 40, 32, 0.25);
        box(Part.LEFT_ARM, 4, 12, -2, 3, 12, 4, 32, 48, 0);
        box(Part.LEFT_ARM, 4, 12, -2, 3, 12, 4, 48, 48, 0.25);
        box(Part.RIGHT_LEG, -4, 0, -2, 4, 12, 4, 0, 16, 0);
        box(Part.RIGHT_LEG, -4, 0, -2, 4, 12, 4, 0, 32, 0.25);
        box(Part.LEFT_LEG, 0, 0, -2, 4, 12, 4, 16, 48, 0);
        box(Part.LEFT_LEG, 0, 0, -2, 4, 12, 4, 0, 48, 0.25);
    }

    /** A box with a texture rectangle of its own on every face (the order of the arrays as in {@link #box}). */
    private void cube(Part part, double x, double y, double z, int w, int h, int d, int[][] r) {
        double x0 = x, x1 = x + w, y0 = y, y1 = y + h, z0 = z, z1 = z + d;
        double[][][] corners = {
                {{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}},        // top
                {{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}},        // bottom
                {{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}},        // right side (-x)
                {{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}},        // front (+z)
                {{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}},        // left side (+x)
                {{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}},        // back (-z)
        };
        for (int i = 0; i < 6; i++) {
            faces.add(new Face(part, corners[i], r[i][0], r[i][1], r[i][0] + r[i][2], r[i][1] + r[i][3]));
        }
    }

    /**
     * A w x h x d box whose lowest corner is (x, y, z), with Minecraft's box UV layout starting at
     * (u, v): top and bottom in the first row, then right side, front, left side and back.
     */
    private void box(Part part, double x, double y, double z, int w, int h, int d, int u, int v, double grow) {
        double x0 = x - grow, x1 = x + w + grow;
        double y0 = y - grow, y1 = y + h + grow;
        double z0 = z - grow, z1 = z + d + grow;
        // Each face lists its corners as top-left, top-right, bottom-right, bottom-left of its texture.
        faces.add(new Face(part, new double[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}},
                u + d, v, u + d + w, v + d));                          // top
        faces.add(new Face(part, new double[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}},
                u + d + w, v, u + d + 2 * w, v + d));                  // bottom
        faces.add(new Face(part, new double[][]{{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}},
                u, v + d, u + d, v + d + h));                          // right side (-x)
        faces.add(new Face(part, new double[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}},
                u + d, v + d, u + d + w, v + d + h));                  // front (+z)
        faces.add(new Face(part, new double[][]{{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}},
                u + d + w, v + d, u + 2 * d + w, v + d + h));          // left side (+x)
        faces.add(new Face(part, new double[][]{{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}},
                u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h));  // back (-z)
    }
}
