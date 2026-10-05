package com.lotusblight.cinema;

import java.util.ArrayList;
import java.util.List;

/**
 * The player model as plain boxes, laid out like Minecraft's slim (3-pixel arm) skin: every part has its base box and its outer
 * layer, each face mapped to its own rectangle of the 64x64 skin. Units are skin pixels, origin between the feet, y up, facing +z.
 * (The same boxes as the overlay's SkinModel, which is private to that package.)
 */
public final class PlayerBoxes {
    public static final int HEAD = 0, BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5;

    /** One textured face: four corners (top-left, top-right, bottom-right, bottom-left) and the skin rectangle drawn over them. */
    public record Face(int part, double[][] corners, int u0, int v0, int u1, int v1) {}

    public static final double[][] PIVOTS = {{0, 24, 0}, {0, 24, 0}, {-5, 22, 0}, {5, 22, 0}, {-2, 12, 0}, {2, 12, 0}};

    public final List<Face> faces = new ArrayList<>();

    public PlayerBoxes() {
// Base layer, then the outer layer puffed out a little so it sits over the base.
        box(HEAD, -4, 24, -4, 8, 8, 8, 0, 0, 0);
        box(HEAD, -4, 24, -4, 8, 8, 8, 32, 0, 0.5);
        box(BODY, -4, 12, -2, 8, 12, 4, 16, 16, 0);
        box(BODY, -4, 12, -2, 8, 12, 4, 16, 32, 0.25);
        box(RIGHT_ARM, -7, 12, -2, 3, 12, 4, 40, 16, 0);
        box(RIGHT_ARM, -7, 12, -2, 3, 12, 4, 40, 32, 0.25);
        box(LEFT_ARM, 4, 12, -2, 3, 12, 4, 32, 48, 0);
        box(LEFT_ARM, 4, 12, -2, 3, 12, 4, 48, 48, 0.25);
        box(RIGHT_LEG, -4, 0, -2, 4, 12, 4, 0, 16, 0);
        box(RIGHT_LEG, -4, 0, -2, 4, 12, 4, 0, 32, 0.25);
        box(LEFT_LEG, 0, 0, -2, 4, 12, 4, 16, 48, 0);
        box(LEFT_LEG, 0, 0, -2, 4, 12, 4, 0, 48, 0.25);
    }

    private void box(int part, double x, double y, double z, int w, int h, int d, int u, int v, double grow) {
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
