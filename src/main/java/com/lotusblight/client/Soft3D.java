package com.lotusblight.client;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * A software renderer for scenes made of textured boxes, the way a Blockbench model is, with the things a game engine does on top:
 * per-pixel lighting from point lights (with a specular highlight), real shadows from one light (a shadow map), fog, and a chain of
 * post effects (ambient occlusion, bloom, light shafts, depth of field, colour grading). Plain AWT and arrays, so a frame can be
 * rendered with no game at all. World units are metres (a player is 1.8 high); the camera looks along its own +z.
 *
 * A frame: {@link #clear}, set the camera and the lights, submit geometry ({@link #quad}, {@link #box}, {@link #figure}, ...), then
 * {@link #flush} draws it (in parallel bands), and the post effects work on the result.
 */
final class Soft3D {
    // ------------------------------------------------------------------ textures

    /** A texture: ARGB pixels. */
    static final class Tex {
        final int w, h;
        final int[] px;

        Tex(int w, int h) {
            this.w = w;
            this.h = h;
            this.px = new int[w * h];
        }

        Tex(int w, int h, int[] px) {
            this.w = w;
            this.h = h;
            this.px = px;
        }

        /** Stained plaster or concrete: a base colour with grain, blotches and a slow variation across it. */
        static Tex surface(int seed, int w, int h, int r, int g, int b, int grain, int blotches) {
            Tex t = new Tex(w, h);
            Random rnd = new Random(seed);
            for (int i = 0; i < w * h; i++) {
                int n = rnd.nextInt(grain * 2 + 1) - grain;
                t.px[i] = argb(255, r + n, g + n, b + n);
            }
            double[] fx = new double[6], fy = new double[6], ph = new double[6];
            for (int k = 0; k < 6; k++) {
                fx[k] = 1 + rnd.nextInt(4);
                fy[k] = 1 + rnd.nextInt(4);
                ph[k] = rnd.nextDouble() * 6.28;
            }
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double v = 0;
                    for (int k = 0; k < 6; k++) v += Math.sin(x / (double) w * 6.283 * fx[k] + y / (double) h * 6.283 * fy[k] + ph[k]);
                    int d = (int) (v * grain * 0.5);
                    int p = t.px[y * w + x];
                    t.px[y * w + x] = argb(255, ((p >> 16) & 255) + d, ((p >> 8) & 255) + d, (p & 255) + d);
                }
            }
            for (int k = 0; k < blotches; k++) {
                int cx = rnd.nextInt(w), cy = rnd.nextInt(h), rad = 4 + rnd.nextInt(Math.max(5, w / 5));
                int dk = 6 + rnd.nextInt(22);
                for (int y = -rad; y <= rad; y++) {
                    for (int x = -rad; x <= rad; x++) {
                        double d = Math.sqrt(x * x + y * y) / rad;
                        if (d >= 1) continue;
                        int xx = Math.floorMod(cx + x, w), yy = Math.floorMod(cy + y, h);
                        int p = t.px[yy * w + xx];
                        double f = (1 - d) * (1 - d) * dk;
                        t.px[yy * w + xx] = argb(255, (int) (((p >> 16) & 255) - f), (int) (((p >> 8) & 255) - f), (int) ((p & 255) - f));
                    }
                }
            }
            return t;
        }

        /** Dripping grime down from the top edge of a wall texture (stains that run). */
        Tex streaks(int seed, int count, int dark) {
            Random rnd = new Random(seed);
            for (int k = 0; k < count; k++) {
                int x = rnd.nextInt(w), len = h / 4 + rnd.nextInt(h / 2), wd = 1 + rnd.nextInt(3);
                for (int y = 0; y < len; y++) {
                    double f = dark * (1 - y / (double) len) * (0.5 + 0.5 * Math.sin(y * 0.4 + k));
                    for (int xx = 0; xx < wd; xx++) {
                        int i = y * w + Math.floorMod(x + xx, w);
                        int p = px[i];
                        px[i] = argb(255, (int) (((p >> 16) & 255) - f), (int) (((p >> 8) & 255) - f), (int) ((p & 255) - f));
                    }
                }
            }
            return this;
        }

        /** Cracks running through a surface. */
        Tex cracks(int seed, int count, int dark) {
            Random rnd = new Random(seed);
            for (int k = 0; k < count; k++) {
                double x = rnd.nextInt(w), y = rnd.nextInt(h), a = rnd.nextDouble() * 6.28;
                int len = 10 + rnd.nextInt(w / 2);
                for (int s = 0; s < len; s++) {
                    a += (rnd.nextDouble() - 0.5) * 0.7;
                    x += Math.cos(a);
                    y += Math.sin(a);
                    int i = Math.floorMod((int) y, h) * w + Math.floorMod((int) x, w);
                    int p = px[i];
                    px[i] = argb(255, ((p >> 16) & 255) - dark, ((p >> 8) & 255) - dark, (p & 255) - dark);
                }
            }
            return this;
        }

        static Tex solid(int r, int g, int b) {
            Tex t = new Tex(2, 2);
            java.util.Arrays.fill(t.px, argb(255, r, g, b));
            return t;
        }

        /** Text on a transparent (or coloured) ground. */
        static Tex text(String s, Font font, Color fg, Color bg, int pad) {
            BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D pg = probe.createGraphics();
            pg.setFont(font);
            int tw = pg.getFontMetrics().stringWidth(s), th = pg.getFontMetrics().getHeight();
            pg.dispose();
            BufferedImage im = new BufferedImage(tw + pad * 2, th + pad * 2, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = im.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (bg != null) {
                g.setColor(bg);
                g.fillRect(0, 0, im.getWidth(), im.getHeight());
            }
            g.setFont(font);
            g.setColor(fg);
            g.drawString(s, pad, pad + g.getFontMetrics().getAscent());
            g.dispose();
            Tex t = new Tex(im.getWidth(), im.getHeight());
            im.getRGB(0, 0, t.w, t.h, t.px, 0, t.w);
            return t;
        }
    }

    static int argb(int a, int r, int g, int b) {
        return (Math.max(0, Math.min(255, a)) << 24) | (Math.max(0, Math.min(255, r)) << 16) | (Math.max(0, Math.min(255, g)) << 8) | Math.max(0, Math.min(255, b));
    }

    /** A point light. */
    record Light(double x, double y, double z, double r, double g, double b, double range) {}

    // ------------------------------------------------------------------ state

    final int width, height;
    /** The picture: ARGB. */
    final int[] color;
    private final float[] depth;
    /** How much light each pixel gives off by itself (for the bloom). */
    private final float[] glow;
    final double focal;
    double camX, camY, camZ, yaw, pitch;
    double ambR = 0.10, ambG = 0.10, ambB = 0.11;
    double fogR = 0, fogG = 0, fogB = 0, fogDensity = 0.12;
    final List<Light> lights = new ArrayList<>();
    /** The material of what is submitted next: how strongly it shines and how sharply. */
    double matSpec = 0, matShine = 24;
    /** Relief taken from the texture's own light and dark (0 = flat), and a soft wrap of light round the form with a warm tint, for skin and meat. */
    double matBump = 0, matWrap = 0;
    /** If set, light 0 throws shadows; it looks along {@code shadowDir} with a wide view. */
    double[] shadowDir;
    private float[] shadowMap;
    private static final int SHADOW = 320;
    private double[] sRight, sUp, sFwd;
    private double sFocal;

    /** One triangle, ready for the bands: screen position and perspective-divided attributes of each corner, and its surface. */
    private static final class Tri {
        final float[] sx = new float[3], sy = new float[3], iw = new float[3];
        final float[] u = new float[3], v = new float[3];                 // already divided by w
        final float[] wx = new float[3], wy = new float[3], wz = new float[3];   // world position divided by w
        final double[][] world = new double[3][];                          // for the shadow pass
        Tex tex;
        int tr, tg, tb;
        float emissive, spec, shine, bump, wrap;
        float nx, ny, nz;
        int minX, maxX, minY, maxY;
        float area;
    }

    private final List<Tri> tris = new ArrayList<>();

    Soft3D(int width, int height, double fovDegrees) {
        this.width = width;
        this.height = height;
        this.color = new int[width * height];
        this.depth = new float[width * height];
        this.glow = new float[width * height];
        this.focal = (width / 2.0) / Math.tan(Math.toRadians(fovDegrees) / 2);
    }

    void clear(int rgb) {
        java.util.Arrays.fill(color, 0xFF000000 | rgb);
        java.util.Arrays.fill(depth, Float.POSITIVE_INFINITY);
        java.util.Arrays.fill(glow, 0f);
        lights.clear();
        tris.clear();
        matSpec = 0;
        matShine = 24;
        matBump = 0;
        matWrap = 0;
        shadowDir = null;
    }

    void camera(double x, double y, double z, double yawRad, double pitchRad) {
        camX = x; camY = y; camZ = z; yaw = yawRad; pitch = pitchRad;
    }

    // ------------------------------------------------------------------ submitting geometry

    double[] toView(double x, double y, double z) {
        x -= camX; y -= camY; z -= camZ;
        double cy = Math.cos(-yaw), sy = Math.sin(-yaw);
        double x1 = x * cy + z * sy, z1 = -x * sy + z * cy;
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        return new double[]{x1, y * cp - z1 * sp, y * sp + z1 * cp};
    }

    /**
     * One textured quad, corners in order top-left, top-right, bottom-right, bottom-left, with texture coordinates in 0..1 (or beyond, to
     * tile). {@code emissive} 0 = lit by the lights, 1 = shows its own colour at full strength.
     */
    void quad(double[][] p, double[][] uv, Tex tex, int tint, double emissive) {
        double e01 = dist(p[0], p[1]), e12 = dist(p[1], p[2]);
        // a big face is cut into tiles only so far as the clipping needs it; the light itself is per pixel
        int nu = Math.max(1, (int) Math.ceil(e01 / 6.0)), nv = Math.max(1, (int) Math.ceil(e12 / 6.0));
        if (nu * nv > 1) {
            for (int j = 0; j < nv; j++) {
                for (int i = 0; i < nu; i++) {
                    double[][] q = new double[4][], w = new double[4][];
                    int[][] corner = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                    for (int k = 0; k < 4; k++) {
                        double fu = corner[k][0] / (double) nu, fv = corner[k][1] / (double) nv;
                        q[k] = new double[]{bil(p[0][0], p[1][0], p[2][0], p[3][0], fu, fv), bil(p[0][1], p[1][1], p[2][1], p[3][1], fu, fv), bil(p[0][2], p[1][2], p[2][2], p[3][2], fu, fv)};
                        w[k] = new double[]{bil(uv[0][0], uv[1][0], uv[2][0], uv[3][0], fu, fv), bil(uv[0][1], uv[1][1], uv[2][1], uv[3][1], fu, fv)};
                    }
                    submit(q, w, tex, tint, emissive);
                }
            }
            return;
        }
        submit(p, uv, tex, tint, emissive);
    }

    private static double dist(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double bil(double a, double b, double c, double d, double fu, double fv) {
        return (a * (1 - fu) + b * fu) * (1 - fv) + (d * (1 - fu) + c * fu) * fv;
    }

    private static double[] normal(double[][] p) {
        double ax = p[1][0] - p[0][0], ay = p[1][1] - p[0][1], az = p[1][2] - p[0][2];
        double bx = p[3][0] - p[0][0], by = p[3][1] - p[0][1], bz = p[3][2] - p[0][2];
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-9;
        return new double[]{nx / l, ny / l, nz / l};
    }

    private void submit(double[][] p, double[][] uv, Tex tex, int tint, double emissive) {
        double[] n = normal(p);
        double[][] v = new double[4][];
        for (int i = 0; i < 4; i++) v[i] = toView(p[i][0], p[i][1], p[i][2]);
        // clip against the near plane; the world position rides along as attributes
        List<double[]> poly = new ArrayList<>();   // vx vy vz u v wx wy wz
        final double near = 0.05;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            double[] a = vert(v[i], uv[i], p[i]), b = vert(v[j], uv[j], p[j]);
            boolean ain = a[2] >= near, bin = b[2] >= near;
            if (ain) poly.add(a);
            if (ain != bin) {
                double t = (near - a[2]) / (b[2] - a[2]);
                double[] c = new double[8];
                for (int k = 0; k < 8; k++) c[k] = a[k] + (b[k] - a[k]) * t;
                poly.add(c);
            }
        }
        if (poly.size() < 3) return;
        int tr = (tint >> 16) & 255, tg = (tint >> 8) & 255, tb = tint & 255;
        for (int i = 1; i + 1 < poly.size(); i++) {
            Tri t = new Tri();
            double[][] vs = {poly.get(0), poly.get(i), poly.get(i + 1)};
            double cx = width / 2.0, cy = height / 2.0;
            for (int k = 0; k < 3; k++) {
                double iw = 1.0 / vs[k][2];
                t.iw[k] = (float) iw;
                t.sx[k] = (float) (cx + focal * vs[k][0] * iw);
                t.sy[k] = (float) (cy - focal * vs[k][1] * iw);
                t.u[k] = (float) (vs[k][3] * iw);
                t.v[k] = (float) (vs[k][4] * iw);
                t.wx[k] = (float) (vs[k][5] * iw);
                t.wy[k] = (float) (vs[k][6] * iw);
                t.wz[k] = (float) (vs[k][7] * iw);
                t.world[k] = new double[]{vs[k][5], vs[k][6], vs[k][7]};
            }
            t.area = (t.sx[1] - t.sx[0]) * (t.sy[2] - t.sy[0]) - (t.sx[2] - t.sx[0]) * (t.sy[1] - t.sy[0]);
            if (Math.abs(t.area) < 1e-6f) continue;
            t.minX = Math.max(0, (int) Math.floor(Math.min(t.sx[0], Math.min(t.sx[1], t.sx[2]))));
            t.maxX = Math.min(width - 1, (int) Math.ceil(Math.max(t.sx[0], Math.max(t.sx[1], t.sx[2]))));
            t.minY = Math.max(0, (int) Math.floor(Math.min(t.sy[0], Math.min(t.sy[1], t.sy[2]))));
            t.maxY = Math.min(height - 1, (int) Math.ceil(Math.max(t.sy[0], Math.max(t.sy[1], t.sy[2]))));
            if (t.minX > t.maxX || t.minY > t.maxY) continue;
            t.tex = tex;
            t.tr = tr; t.tg = tg; t.tb = tb;
            t.emissive = (float) emissive;
            t.spec = (float) matSpec;
            t.shine = (float) matShine;
            t.bump = (float) matBump;
            t.wrap = (float) matWrap;
            t.nx = (float) n[0]; t.ny = (float) n[1]; t.nz = (float) n[2];
            tris.add(t);
        }
    }

    private static double[] vert(double[] v, double[] uv, double[] world) {
        return new double[]{v[0], v[1], v[2], uv[0], uv[1], world[0], world[1], world[2]};
    }

    // ------------------------------------------------------------------ drawing

    /** Draws everything submitted: the shadow map first, then the picture in parallel bands. */
    void flush() {
        buildShadow();
        final int bands = 12;
        IntStream.range(0, bands).parallel().forEach(b -> {
            int y0 = height * b / bands, y1 = height * (b + 1) / bands;
            for (Tri t : tris) {
                if (t.maxY < y0 || t.minY >= y1) continue;
                raster(t, Math.max(y0, t.minY), Math.min(y1 - 1, t.maxY));
            }
        });
    }

    private void raster(Tri t, int yStart, int yEnd) {
        final float area = t.area;
        final int nl = lights.size();
        final float[] lx = new float[nl], ly = new float[nl], lz = new float[nl], lr = new float[nl], lg = new float[nl], lb = new float[nl], lrange = new float[nl];
        for (int i = 0; i < nl; i++) {
            Light l = lights.get(i);
            lx[i] = (float) l.x; ly[i] = (float) l.y; lz[i] = (float) l.z;
            lr[i] = (float) l.r; lg[i] = (float) l.g; lb[i] = (float) l.b; lrange[i] = (float) l.range;
        }
        final Tex tex = t.tex;
        final float tr = t.tr / 255f, tg = t.tg / 255f, tb = t.tb / 255f;
        final float e = t.emissive;
        final float cxm = (float) camX, cym = (float) camY, czm = (float) camZ;
        for (int y = yStart; y <= yEnd; y++) {
            float py = y + 0.5f;
            for (int x = t.minX; x <= t.maxX; x++) {
                float px = x + 0.5f;
                float w0 = ((t.sx[1] - px) * (t.sy[2] - py) - (t.sx[2] - px) * (t.sy[1] - py)) / area;
                float w1 = ((t.sx[2] - px) * (t.sy[0] - py) - (t.sx[0] - px) * (t.sy[2] - py)) / area;
                float w2 = 1 - w0 - w1;
                if (w0 < -1e-5f || w1 < -1e-5f || w2 < -1e-5f) continue;
                float ip = w0 * t.iw[0] + w1 * t.iw[1] + w2 * t.iw[2];
                float z = 1f / ip;
                int idx = y * width + x;
                if (z >= depth[idx]) continue;
                float u = (w0 * t.u[0] + w1 * t.u[1] + w2 * t.u[2]) * z;
                float v = (w0 * t.v[0] + w1 * t.v[1] + w2 * t.v[2]) * z;
                u -= (float) Math.floor(u);
                v -= (float) Math.floor(v);
                int txi = Math.min(tex.w - 1, (int) (u * tex.w)), tyi = Math.min(tex.h - 1, (int) (v * tex.h));
                int tc = tex.px[tyi * tex.w + txi];
                if ((tc >>> 24) < 128) continue;
                float wxp = (w0 * t.wx[0] + w1 * t.wx[1] + w2 * t.wx[2]) * z;
                float wyp = (w0 * t.wy[0] + w1 * t.wy[1] + w2 * t.wy[2]) * z;
                float wzp = (w0 * t.wz[0] + w1 * t.wz[1] + w2 * t.wz[2]) * z;
                float nx = t.nx, ny = t.ny, nz = t.nz;
                float vx = cxm - wxp, vy = cym - wyp, vz = czm - wzp;
                float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-6f;
                vx /= vl; vy /= vl; vz /= vl;
                if (nx * vx + ny * vy + nz * vz < 0) { nx = -nx; ny = -ny; nz = -nz; }     // the surface faces the viewer
                // relief: the texture's own dark and light, read as height, tilts the light a little
                float relief = 0f;
                if (t.bump > 0) {
                    int tx2 = Math.min(tex.w - 1, txi + 1), ty2 = Math.min(tex.h - 1, tyi + 1);
                    int a1 = tex.px[tyi * tex.w + tx2], a2 = tex.px[ty2 * tex.w + txi];
                    float l0 = lumOf(tc), l1 = lumOf(a1), l2 = lumOf(a2);
                    relief = ((l0 - l1) + (l0 - l2)) * t.bump;
                }
                float rim = (float) Math.pow(1 - Math.max(0, nx * vx + ny * vy + nz * vz), 3);
                float lightR = (float) ambR, lightG = (float) ambG, lightB = (float) ambB;
                float specR = 0, specG = 0, specB = 0;
                if (e < 0.999f) {
                    for (int i = 0; i < nl; i++) {
                        float dx = lx[i] - wxp, dy = ly[i] - wyp, dz = lz[i] - wzp;
                        float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 1e-6f;
                        float att = Math.max(0, 1 - d / lrange[i]);
                        if (att <= 0) continue;
                        att *= att;
                        float nd = (nx * dx + ny * dy + nz * dz) / d;
                        float wrapped = t.wrap > 0 ? (nd + t.wrap) / (1 + t.wrap) : nd;
                        if (wrapped <= 0) continue;
                        float warm = t.wrap > 0 ? Math.max(0, wrapped - Math.max(0, nd)) : 0;       // the light that went round the edge is the red of what is under the skin
                        nd = Math.max(0, wrapped + relief);
                        float sh = 1f;
                        if (i == 0 && shadowMap != null) sh = shadow(wxp, wyp, wzp);
                        float k = nd * att * sh;
                        lightR += lr[i] * (k + warm * att * sh * 0.6f); lightG += lg[i] * (k + warm * att * sh * 0.18f); lightB += lb[i] * (k + warm * att * sh * 0.12f);
                        if (t.spec > 0) {
                            float hx = dx / d + vx, hy = dy / d + vy, hz = dz / d + vz;
                            float hl = (float) Math.sqrt(hx * hx + hy * hy + hz * hz) + 1e-6f;
                            float nh = Math.max(0, (nx * hx + ny * hy + nz * hz) / hl);
                            float s = ((float) Math.pow(nh, t.shine) + 0.35f * rim) * t.spec * att * sh;
                            specR += lr[i] * s; specG += lg[i] * s; specB += lb[i] * s;
                        }
                    }
                }
                float cr = ((tc >> 16) & 255) * tr, cg = ((tc >> 8) & 255) * tg, cb = (tc & 255) * tb;
                float fr = cr * (lightR * (1 - e) + e) + specR * 255f;
                float fg = cg * (lightG * (1 - e) + e) + specG * 255f;
                float fb = cb * (lightB * (1 - e) + e) + specB * 255f;
                float fog = (float) Math.exp(-fogDensity * z);
                fr = fr * fog + (float) fogR * (1 - fog);
                fg = fg * fog + (float) fogG * (1 - fog);
                fb = fb * fog + (float) fogB * (1 - fog);
                depth[idx] = z;
                glow[idx] = e * (cr + cg + cb) / 765f * fog;
                color[idx] = 0xFF000000 | (clamp((int) fr) << 16) | (clamp((int) fg) << 8) | clamp((int) fb);
            }
        }
    }

    private static float lumOf(int c) {
        return (((c >> 16) & 255) * 0.3f + ((c >> 8) & 255) * 0.59f + (c & 255) * 0.11f) / 255f;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    // ------------------------------------------------------------------ shadows

    /** Draws the scene's depth as seen from light 0 (looking along {@link #shadowDir}), for the shadows. */
    private void buildShadow() {
        shadowMap = null;
        if (shadowDir == null || lights.isEmpty()) return;
        Light l = lights.get(0);
        double[] f = norm(shadowDir);
        double[] up = Math.abs(f[1]) > 0.95 ? new double[]{1, 0, 0} : new double[]{0, 1, 0};
        double[] r = norm(cross(up, f));
        double[] u = cross(f, r);
        sRight = r; sUp = u; sFwd = f;
        sFocal = (SHADOW / 2.0) / Math.tan(Math.toRadians(70));
        float[] map = new float[SHADOW * SHADOW];
        java.util.Arrays.fill(map, Float.POSITIVE_INFINITY);
        for (Tri t : tris) {
            if (t.emissive >= 0.99f) continue;               // the glass of the TV and the glowing cards do not cast
            double[][] s = new double[3][];
            boolean ok = true;
            for (int k = 0; k < 3; k++) {
                double dx = t.world[k][0] - l.x, dy = t.world[k][1] - l.y, dz = t.world[k][2] - l.z;
                double x = dx * r[0] + dy * r[1] + dz * r[2], y = dx * u[0] + dy * u[1] + dz * u[2], z = dx * f[0] + dy * f[1] + dz * f[2];
                if (z < 0.05) { ok = false; break; }
                s[k] = new double[]{SHADOW / 2.0 + sFocal * x / z, SHADOW / 2.0 - sFocal * y / z, z};
            }
            if (!ok) continue;
            double area = (s[1][0] - s[0][0]) * (s[2][1] - s[0][1]) - (s[2][0] - s[0][0]) * (s[1][1] - s[0][1]);
            if (Math.abs(area) < 1e-9) continue;
            int x0 = Math.max(0, (int) Math.floor(Math.min(s[0][0], Math.min(s[1][0], s[2][0])))), x1 = Math.min(SHADOW - 1, (int) Math.ceil(Math.max(s[0][0], Math.max(s[1][0], s[2][0]))));
            int y0 = Math.max(0, (int) Math.floor(Math.min(s[0][1], Math.min(s[1][1], s[2][1])))), y1 = Math.min(SHADOW - 1, (int) Math.ceil(Math.max(s[0][1], Math.max(s[1][1], s[2][1]))));
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    double px = x + 0.5, py = y + 0.5;
                    double w0 = ((s[1][0] - px) * (s[2][1] - py) - (s[2][0] - px) * (s[1][1] - py)) / area;
                    double w1 = ((s[2][0] - px) * (s[0][1] - py) - (s[0][0] - px) * (s[2][1] - py)) / area;
                    double w2 = 1 - w0 - w1;
                    if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                    float z = (float) (w0 * s[0][2] + w1 * s[1][2] + w2 * s[2][2]);
                    int i = y * SHADOW + x;
                    if (z < map[i]) map[i] = z;
                }
            }
        }
        shadowMap = map;
    }

    /** 1 = lit by light 0, about 0.18 in its shadow, with a soft edge (3x3 filter). */
    private float shadow(float wx, float wy, float wz) {
        Light l = lights.get(0);
        double dx = wx - l.x, dy = wy - l.y, dz = wz - l.z;
        double x = dx * sRight[0] + dy * sRight[1] + dz * sRight[2], y = dx * sUp[0] + dy * sUp[1] + dz * sUp[2], z = dx * sFwd[0] + dy * sFwd[1] + dz * sFwd[2];
        if (z < 0.05) return 1f;
        int sx = (int) (SHADOW / 2.0 + sFocal * x / z), sy = (int) (SHADOW / 2.0 - sFocal * y / z);
        if (sx < 1 || sy < 1 || sx >= SHADOW - 1 || sy >= SHADOW - 1) return 1f;
        float lit = 0;
        float bias = (float) (0.03 + 0.02 * z);
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                if (z - bias <= shadowMap[(sy + j) * SHADOW + sx + i]) lit += 1;
            }
        }
        return 0.18f + 0.82f * (lit / 9f);
    }

    private static double[] norm(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) + 1e-12;
        return new double[]{v[0] / l, v[1] / l, v[2] / l};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    // ------------------------------------------------------------------ post effects (the "shaders")

    /** Screen-space ambient occlusion: creases and the feet of things go darker. */
    void ssao(double strength, double radiusPx) {
        final int[] src = color.clone();
        final int w = width, h = height;
        final double[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {0.7, 0.7}, {-0.7, 0.7}, {0.7, -0.7}, {-0.7, -0.7}};
        IntStream.range(0, h).parallel().forEach(y -> {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float z = depth[i];
                if (Float.isInfinite(z)) continue;
                double rad = radiusPx / (0.6 + z * 0.35);
                double occ = 0;
                for (double[] d : dirs) {
                    for (int s = 1; s <= 2; s++) {
                        int xx = (int) (x + d[0] * rad * s * 0.5), yy = (int) (y + d[1] * rad * s * 0.5);
                        if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                        float zn = depth[yy * w + xx];
                        double diff = z - zn;                      // a neighbour much closer than this pixel hides it
                        if (diff > 0.04 && diff < 0.9) occ += Math.min(1.0, diff * 2.2) / s;
                    }
                }
                double k = 1.0 - strength * Math.min(1.0, occ / 6.0);
                int p = src[i];
                color[i] = 0xFF000000 | (((int) (((p >> 16) & 255) * k)) << 16) | (((int) (((p >> 8) & 255) * k)) << 8) | (int) ((p & 255) * k);
            }
        });
    }

    /** Things that glow bleed light around themselves. */
    void bloom(double threshold, double strength, int radius) {
        final int w = width, h = height, sw = w / 4, sh = h / 4;
        float[] r = new float[sw * sh], g = new float[sw * sh], b = new float[sw * sh];
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                float rr = 0, gg = 0, bb = 0;
                for (int dy = 0; dy < 4; dy++) {
                    for (int dx = 0; dx < 4; dx++) {
                        int i = (y * 4 + dy) * w + x * 4 + dx;
                        int p = color[i];
                        float lum = (((p >> 16) & 255) * 0.3f + ((p >> 8) & 255) * 0.59f + (p & 255) * 0.11f) / 255f;
                        float k = Math.max(0, lum - (float) threshold) + glow[i] * 0.8f;
                        rr += ((p >> 16) & 255) * k; gg += ((p >> 8) & 255) * k; bb += (p & 255) * k;
                    }
                }
                r[y * sw + x] = rr / 16f; g[y * sw + x] = gg / 16f; b[y * sw + x] = bb / 16f;
            }
        }
        for (int pass = 0; pass < 2; pass++) {
            blur(r, sw, sh, radius); blur(g, sw, sh, radius); blur(b, sw, sh, radius);
        }
        IntStream.range(0, h).parallel().forEach(y -> {
            for (int x = 0; x < w; x++) {
                float fx = Math.min(sw - 1.001f, x / 4f), fy = Math.min(sh - 1.001f, y / 4f);
                int x0 = (int) fx, y0 = (int) fy;
                float tx = fx - x0, ty = fy - y0;
                float br = bil(r, sw, x0, y0, tx, ty), bg = bil(g, sw, x0, y0, tx, ty), bb = bil(b, sw, x0, y0, tx, ty);
                int p = color[y * w + x];
                color[y * w + x] = 0xFF000000 | (clamp((int) (((p >> 16) & 255) + br * strength)) << 16) | (clamp((int) (((p >> 8) & 255) + bg * strength)) << 8) | clamp((int) ((p & 255) + bb * strength));
            }
        });
    }

    private static float bil(float[] a, int w, int x, int y, float tx, float ty) {
        return (a[y * w + x] * (1 - tx) + a[y * w + x + 1] * tx) * (1 - ty) + (a[(y + 1) * w + x] * (1 - tx) + a[(y + 1) * w + x + 1] * tx) * ty;
    }

    private static void blur(float[] a, int w, int h, int radius) {
        float[] tmp = new float[a.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float s = 0; int n = 0;
                for (int d = -radius; d <= radius; d++) {
                    int xx = x + d;
                    if (xx < 0 || xx >= w) continue;
                    s += a[y * w + xx]; n++;
                }
                tmp[y * w + x] = s / n;
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float s = 0; int n = 0;
                for (int d = -radius; d <= radius; d++) {
                    int yy = y + d;
                    if (yy < 0 || yy >= h) continue;
                    s += tmp[yy * w + x]; n++;
                }
                a[y * w + x] = s / n;
            }
        }
    }

    /**
     * Light shafts: the bright parts of the picture are smeared towards a light's place on the screen, so a lamp throws visible beams
     * through the dust. {@code worldX/Y/Z} is the light; nothing happens if it is behind the camera.
     */
    void godRays(double worldX, double worldY, double worldZ, double strength, int tintR, int tintG, int tintB) {
        double[] v = toView(worldX, worldY, worldZ);
        if (v[2] < 0.2) return;
        final double lx = width / 2.0 + focal * v[0] / v[2], ly = height / 2.0 - focal * v[1] / v[2];
        final int w = width, h = height;
        final int[] src = color.clone();
        final int samples = 28;
        IntStream.range(0, h).parallel().forEach(y -> {
            for (int x = 0; x < w; x++) {
                double dx = (lx - x) / samples * 0.9, dy = (ly - y) / samples * 0.9;
                double sx = x, sy = y, decay = 1.0, acc = 0;
                for (int s = 0; s < samples; s++) {
                    sx += dx; sy += dy;
                    int xx = (int) sx, yy = (int) sy;
                    if (xx < 0 || yy < 0 || xx >= w || yy >= h) break;
                    int i = yy * w + xx;
                    int p = src[i];
                    double lum = (((p >> 16) & 255) * 0.3 + ((p >> 8) & 255) * 0.59 + (p & 255) * 0.11) / 255.0;
                    double m = Float.isInfinite(depth[i]) ? lum : Math.max(0, lum - 0.55) + glow[i];
                    acc += m * decay;
                    decay *= 0.93;
                }
                acc = acc / samples * strength;
                int p = color[y * w + x];
                color[y * w + x] = 0xFF000000 | (clamp((int) (((p >> 16) & 255) + acc * tintR)) << 16) | (clamp((int) (((p >> 8) & 255) + acc * tintG)) << 8) | clamp((int) ((p & 255) + acc * tintB));
            }
        });
    }

    /** Depth of field: what is far from {@code focus} (metres) goes soft. */
    void dof(double focus, double range, int maxRadius) {
        final int w = width, h = height;
        final int[] src = color.clone();
        IntStream.range(0, h).parallel().forEach(y -> {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float z = depth[i];
                double coc = Float.isInfinite(z) ? 1.0 : Math.min(1.0, Math.abs(z - focus) / range);
                int rad = (int) Math.round(coc * maxRadius);
                if (rad <= 0) continue;
                int r = 0, g = 0, b = 0, n = 0;
                for (int dy = -rad; dy <= rad; dy += Math.max(1, rad / 2)) {
                    for (int dx = -rad; dx <= rad; dx += Math.max(1, rad / 2)) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                        int p = src[yy * w + xx];
                        r += (p >> 16) & 255; g += (p >> 8) & 255; b += p & 255; n++;
                    }
                }
                color[i] = 0xFF000000 | ((r / n) << 16) | ((g / n) << 8) | (b / n);
            }
        });
    }

    /** Colour grading: contrast around the middle, a tint on the shadows and on the highlights, and saturation. */
    void grade(double contrast, double saturation, int[] shadowTint, int[] highlightTint) {
        final int w = width, h = height;
        IntStream.range(0, h).parallel().forEach(y -> {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int p = color[i];
                double r = ((p >> 16) & 255) / 255.0, g = ((p >> 8) & 255) / 255.0, b = (p & 255) / 255.0;
                double lum = r * 0.3 + g * 0.59 + b * 0.11;
                r = lum + (r - lum) * saturation;
                g = lum + (g - lum) * saturation;
                b = lum + (b - lum) * saturation;
                double sh = 1 - Math.min(1, lum * 2.2), hi = Math.max(0, lum * 2 - 1);
                r += (shadowTint[0] * sh + highlightTint[0] * hi) / 255.0;
                g += (shadowTint[1] * sh + highlightTint[1] * hi) / 255.0;
                b += (shadowTint[2] * sh + highlightTint[2] * hi) / 255.0;
                r = (r - 0.22) * contrast + 0.22;
                g = (g - 0.22) * contrast + 0.22;
                b = (b - 0.22) * contrast + 0.22;
                r = filmic(r); g = filmic(g); b = filmic(b);
                color[i] = 0xFF000000 | (clamp((int) (r * 255)) << 16) | (clamp((int) (g * 255)) << 8) | clamp((int) (b * 255));
            }
        });
    }

    /** A film-like shoulder: the bright parts roll off instead of clipping to white. */
    private static double filmic(double x) {
        x = Math.max(0, x);
        double y = (x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14);
        return Math.max(0, Math.min(1, y));
    }

    /** Where a world point lands on the screen: {x, y} in pixels, or null when it is behind the camera. */
    double[] project(double wx, double wy, double wz) {
        double[] v = toView(wx, wy, wz);
        if (v[2] < 0.05) return null;
        return new double[]{width / 2.0 + focal * v[0] / v[2], height / 2.0 - focal * v[1] / v[2]};
    }

    // ------------------------------------------------------------------ helpers for building scenes

    private static final double[][] FULL_UV = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

    /** An axis-aligned box from (x0,y0,z0) to (x1,y1,z1) with one texture on all its faces, tiled {@code tile} times per metre. */
    void box(double x0, double y0, double z0, double x1, double y1, double z1, Tex tex, int tint, double emissive, double tile) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        faceXY(z0, x0, y0, x1, y1, tex, tint, emissive, dx * tile, dy * tile);
        faceXY(z1, x0, y0, x1, y1, tex, tint, emissive, dx * tile, dy * tile);
        faceYZ(x0, y0, z0, y1, z1, tex, tint, emissive, dz * tile, dy * tile);
        faceYZ(x1, y0, z0, y1, z1, tex, tint, emissive, dz * tile, dy * tile);
        faceXZ(y0, x0, z0, x1, z1, tex, tint, emissive, dx * tile, dz * tile);
        faceXZ(y1, x0, z0, x1, z1, tex, tint, emissive, dx * tile, dz * tile);
    }

    private static double[][] uv(double su, double sv) {
        return new double[][]{{0, 0}, {su, 0}, {su, sv}, {0, sv}};
    }

    void faceXY(double z, double x0, double y0, double x1, double y1, Tex tex, int tint, double em, double su, double sv) {
        quad(new double[][]{{x0, y1, z}, {x1, y1, z}, {x1, y0, z}, {x0, y0, z}}, uv(su, sv), tex, tint, em);
    }

    void faceYZ(double x, double y0, double z0, double y1, double z1, Tex tex, int tint, double em, double su, double sv) {
        quad(new double[][]{{x, y1, z0}, {x, y1, z1}, {x, y0, z1}, {x, y0, z0}}, uv(su, sv), tex, tint, em);
    }

    void faceXZ(double y, double x0, double z0, double x1, double z1, Tex tex, int tint, double em, double su, double sv) {
        quad(new double[][]{{x0, y, z0}, {x1, y, z0}, {x1, y, z1}, {x0, y, z1}}, uv(su, sv), tex, tint, em);
    }

    /** A flat card in space: centre, half width, half height, yaw (the card's x axis is the camera's right when the camera yaw is the same). */
    void card(double cx, double cy, double cz, double hw, double hh, double yawRad, Tex tex, int tint, double emissive) {
        double c = Math.cos(yawRad), s = Math.sin(yawRad);
        double[][] p = new double[4][];
        double[][] corners = {{-hw, hh}, {hw, hh}, {hw, -hh}, {-hw, -hh}};
        for (int i = 0; i < 4; i++) p[i] = new double[]{cx + corners[i][0] * c, cy + corners[i][1], cz - corners[i][0] * s};
        quad(p, FULL_UV, tex, tint, emissive);
    }

    /** A card that always faces the camera (for dust, sparks, glows). */
    void billboard(double cx, double cy, double cz, double hw, double hh, Tex tex, int tint, double emissive) {
        double[][] b = basis();
        double[][] p = new double[4][];
        double[][] corners = {{-hw, hh}, {hw, hh}, {hw, -hh}, {-hw, -hh}};
        for (int i = 0; i < 4; i++) {
            p[i] = new double[]{cx + b[0][0] * corners[i][0] + b[1][0] * corners[i][1], cy + b[0][1] * corners[i][0] + b[1][1] * corners[i][1], cz + b[0][2] * corners[i][0] + b[1][2] * corners[i][1]};
        }
        quad(p, FULL_UV, tex, tint, emissive);
    }

    /** The camera's own axes in the world: right, up, forward. */
    double[][] basis() {
        double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
        return new double[][]{{cy, 0, -sy}, {-sy * sp, cp, -cy * sp}, {sy * cp, sp, cy * cp}};
    }

    /** A point given in the camera's own terms (x right, y up, z forward), in the world. */
    double[] local(double lx, double ly, double lz) {
        double[][] b = basis();
        return new double[]{camX + b[0][0] * lx + b[1][0] * ly + b[2][0] * lz, camY + b[0][1] * lx + b[1][1] * ly + b[2][1] * lz, camZ + b[0][2] * lx + b[1][2] * ly + b[2][2] * lz};
    }

    /** A box along the segment a-b with half-widths (sa, sb) around it, for knives, branches, limbs. */
    void bar(double[] a, double[] b, double[] sideA, double[] sideB, Tex tex, int tint, double emissive) {
        double[][] c = new double[8][];
        int i = 0;
        for (double[] e : new double[][]{a, b}) {
            for (int sa = -1; sa <= 1; sa += 2) {
                for (int sb = -1; sb <= 1; sb += 2) {
                    c[i++] = new double[]{e[0] + sa * sideA[0] + sb * sideB[0], e[1] + sa * sideA[1] + sb * sideB[1], e[2] + sa * sideA[2] + sb * sideB[2]};
                }
            }
        }
        int[][] faces = {{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};
        for (int[] f : faces) quad(new double[][]{c[f[0]], c[f[1]], c[f[2]], c[f[3]]}, FULL_UV, tex, tint, emissive);
    }

    // ------------------------------------------------------------------ the player model

    /** The pose of the player model: a turn of every part (in radians) around its joint, and a stretch along its length. */
    static final class Pose {
        final double[] pitch = new double[6], yaw = new double[6], roll = new double[6];
        final double[] stretch = {1, 1, 1, 1, 1, 1};
    }

    static final int ALL_PARTS = 0b111111;

    /** The skin model standing at (x, y, z) facing {@code facing} radians. */
    void figure(PlayerBoxes model, Tex skin, double x, double y, double z, double facing, double scale, Pose pose, int tint, double emissive, int partMask) {
        double cf = Math.cos(facing), sf = Math.sin(facing);
        figure(model, skin, new double[]{x, y, z}, new double[][]{{cf, 0, -sf}, {0, 1, 0}, {sf, 0, cf}}, scale, pose, tint, emissive, partMask);
    }

    /**
     * The skin model placed by a frame: the model's x runs along basis[0], y along basis[1], z along basis[2], one skin pixel =
     * {@code scale} metres, {@code origin} is between the feet. {@code tint} multiplies the colours, so a figure can be dark or red.
     */
    void figure(PlayerBoxes model, Tex skin, double[] origin, double[][] basis, double scale, Pose pose, int tint, double emissive, int partMask) {
        for (PlayerBoxes.Face f : model.faces) {
            int part = f.part();
            if ((partMask & (1 << part)) == 0) continue;
            double[][] w = new double[4][];
            double[] pv = PlayerBoxes.PIVOTS[part];
            for (int i = 0; i < 4; i++) {
                double[] q = f.corners()[i];
                double lx = q[0] - pv[0], ly = (q[1] - pv[1]) * (pose == null ? 1 : pose.stretch[part]), lz = q[2] - pv[2];
                double pr = pose == null ? 0 : pose.pitch[part], py = pose == null ? 0 : pose.yaw[part], rr = pose == null ? 0 : pose.roll[part];
                double y1 = ly * Math.cos(pr) - lz * Math.sin(pr), z1 = ly * Math.sin(pr) + lz * Math.cos(pr);
                double x2 = lx * Math.cos(py) + z1 * Math.sin(py), z2 = -lx * Math.sin(py) + z1 * Math.cos(py);
                double x3 = x2 * Math.cos(rr) - y1 * Math.sin(rr), y3 = x2 * Math.sin(rr) + y1 * Math.cos(rr);
                double bx = (x3 + pv[0]) * scale, by = (y3 + pv[1]) * scale, bz = (z2 + pv[2]) * scale;
                w[i] = new double[]{origin[0] + basis[0][0] * bx + basis[1][0] * by + basis[2][0] * bz,
                        origin[1] + basis[0][1] * bx + basis[1][1] * by + basis[2][1] * bz,
                        origin[2] + basis[0][2] * bx + basis[1][2] * by + basis[2][2] * bz};
            }
            double[][] uvs = {{f.u0() / 64.0, f.v0() / 64.0}, {f.u1() / 64.0, f.v0() / 64.0}, {f.u1() / 64.0, f.v1() / 64.0}, {f.u0() / 64.0, f.v1() / 64.0}};
            quad(w, uvs, skin, tint, emissive);
        }
    }
}
