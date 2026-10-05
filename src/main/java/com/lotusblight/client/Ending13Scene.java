package com.lotusblight.client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import com.lotusblight.cinema.GpuScene;
import com.lotusblight.cinema.PlayerBoxes;
import com.lotusblight.cinema.Soft3D;
import com.lotusblight.cinema.SkinActor;
import com.lotusblight.overlay.Actor15;
import com.lotusblight.overlay.Ending13Rig;
import java.util.Random;

/**
 * The thirteenth ending: a 3D scene built the way a Blockbench animation is (boxes, the player's own skin, keyframes, a camera), cut to
 * the track "INVERSION": quiet until 0:16, the drop, a dip at 1:08-1:20, the second drop at 1:20, then heavier and slower to the end.
 * A dark basement, an empty chair and an old TV, seen from the first person; with every stab at the TV a memory plays and the figures in
 * the dark stand a little closer. 2:56 long. Only plain AWT and arrays, so a frame can be rendered with no game (see {@link #main}).
 *
 * The harm itself is never drawn: the arm, the body and the heart are only hinted at with light, colour and shape, and it cuts away.
 */
public final class Ending13Scene implements com.lotusblight.cinema.Cutscene {
    public static final int W = 720;
    public static final int H = 405;
    /** The track's first 5 seconds are cut: the scene's clock starts at 0, which is 0:05 of the track. All times below are track times. */
    public static final double T0 = 5.0;
    public static final double LENGTH = 176.0 - T0;

    // ---- the timeline, in seconds of the track
    private static final double SMASH_START = 5, CASSETTE_SNAP = 11, SMASH_END = 15;                 // the TV is broken, the tape too
    private static final double ARM_ENTER = 22;                                                      // the dark room: the arm comes into view
    private static final double[] MEMORY_START = {32, 46, 60, 80, 96};                               // each starts with a cut of the arm
    private static final double[] MEMORY_END = {46, 60, 80, 96, 111};
    private static final int[] MEMORY_ID = {4, 1, 0, 2, 3};                                         // forest, object, neck, office, cell
    private static final double CUTTING = 111, ARCHITECTS = 115, STAB = 151.5, WHITE = 155, LIMBO = 162;
    private static final double CUT_LEN = 2.4;                                                       // the cut before a memory
    /** The digital stutters of the track (found by analysis): the picture glitches on each. */
    private static final double[] GLITCH = glitchTimes();
    private static final double[] LIGHT_AT = {115, 133, 136, 141, 146, 150};                         // 6, Starlight, Mischievous, Moon, Chromo, Glitch

    private static double[] glitchTimes() {
        java.util.List<Double> l = new java.util.ArrayList<>();
        for (double t = 5.39; t < 15.8; t += 0.3295) l.add(t);
        for (double t : new double[]{68.7, 68.86, 69.03, 69.2, 79.69, 79.87, 80.03, 80.36, 80.54, 111.46, 111.62, 111.78, 111.99,
                133.36, 133.7, 134.04, 134.37, 156.7, 156.94, 157.36, 157.62, 158.7, 158.95}) l.add(t);
        return l.stream().mapToDouble(Double::doubleValue).sorted().toArray();
    }

    private final Soft3D r = new Soft3D(W, H, 76);
    private final BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
    private Graphics2D g = image.createGraphics();
    private final Graphics2D gCpu = g;
    private boolean gpuMode;                  // the 2D layer is being drawn for the graphics card: no pixels to copy, lights go as sprites
    private GpuScene.Frame curFrame;
    private double ovScale = 1;               // the 2D layer's pixels per logical pixel
    private double outScaleX = 1, outScaleY = 1;   // output pixels per logical pixel
    private final int[] pixels = ((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData();
    private final Random rnd = new Random();
    private final PlayerBoxes model = new PlayerBoxes();
    private final Actor15 actor = new Actor15();           // the player's body, with elbows, knees and fingers
    private final Soft3D.Tex skin;

    private double[] kicks = new double[0];

    // textures
    private final Soft3D.Tex floor = Soft3D.Tex.surface(1, 256, 256, 70, 66, 60, 14, 60).cracks(11, 14, 22);
    private final Soft3D.Tex wall = Soft3D.Tex.surface(2, 256, 256, 112, 110, 102, 10, 40).streaks(21, 26, 26).cracks(12, 10, 24);
    private final Soft3D.Tex wallLow = Soft3D.Tex.surface(22, 256, 128, 64, 66, 60, 12, 36).streaks(23, 18, 20);
    private final Soft3D.Tex rust = Soft3D.Tex.surface(24, 32, 32, 104, 70, 46, 14, 8);
    private final Soft3D.Tex pipe = Soft3D.Tex.surface(25, 32, 32, 70, 74, 76, 10, 6);
    private final Soft3D.Tex crate = Soft3D.Tex.surface(26, 32, 32, 98, 74, 48, 14, 5);
    private final Soft3D.Tex dust = dustTex();
    private final Soft3D.Tex wallRed = Soft3D.Tex.surface(3, 128, 128, 70, 30, 28, 12, 60);
    private final Soft3D.Tex dark = Soft3D.Tex.solid(18, 18, 20);
    private final Soft3D.Tex wood = Soft3D.Tex.surface(4, 32, 32, 92, 70, 48, 8, 6);
    private final Soft3D.Tex white = Soft3D.Tex.surface(5, 32, 32, 196, 198, 194, 6, 4);
    private final Soft3D.Tex steel = Soft3D.Tex.surface(6, 16, 16, 150, 154, 162, 12, 2);
    private final Soft3D.Tex tvBody = Soft3D.Tex.surface(7, 32, 32, 30, 30, 34, 4, 2);
    private final Soft3D.Tex staticTex = new Soft3D.Tex(96, 72);
    private final Soft3D.Tex bark = Soft3D.Tex.surface(8, 32, 64, 70, 60, 50, 10, 10);
    private final Soft3D.Tex ground = Soft3D.Tex.surface(9, 64, 64, 46, 52, 40, 8, 30);
    private Soft3D.Tex crackTex = new Soft3D.Tex(96, 72);
    private int crackCount = -1;

    public Ending13Scene() {
        this(defaultSkin());
    }

    /** {@code skinPixels} is a 64x64 ARGB skin (the player's own, in the game). */
    public Ending13Scene(int[] skinPixels) {
        this.skin = new Soft3D.Tex(64, 64, skinPixels);
        this.skin.nearest = true;
        staticTex.dynamic = true;
        screenTex.dynamic = true;
        crackTex.dynamic = true;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    /** A plain stand-in skin for when no skin is given (tests). */
    private static int[] defaultSkin() {
        int[] px = new int[64 * 64];
        java.util.Arrays.fill(px, 0xFF6B4F3A);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) if (y >= 16 && x < 56 && (x / 4 + y / 4) % 2 == 0) px[y * 64 + x] = 0xFF3E5C9A;
        for (int y = 8; y < 16; y++) for (int x = 8; x < 16; x++) px[y * 64 + x] = 0xFFC9A27C;
        return px;
    }

    public void setKicks(double[] seconds) {
        this.kicks = seconds;
    }

    /** The kicks of the track, from the beats file shipped with the mod (see tools/analyze_music.py). */
    public static double[] loadKicks() {
        try (java.io.InputStream in = Ending13Scene.class.getResourceAsStream("/assets/lotusblight/ending13/beats.json")) {
            if (in == null) return new double[0];
            String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            int a = json.indexOf("\"kicks\": [") + 10;
            int b = json.indexOf(']', a);
            String[] parts = json.substring(a, b).split(",");
            double[] ks = new double[parts.length];
            for (int i = 0; i < parts.length; i++) ks[i] = Double.parseDouble(parts[i].trim());
            return ks;
        } catch (Exception e) {
            return new double[0];
        }
    }

    // ------------------------------------------------------------------ the picture

    @Override public int width() { return W; }

    @Override public int height() { return H; }

    @Override public double length() { return LENGTH; }

    @Override public int[] renderCpu(double clock) { return render(clock); }

    /** The picture {@code clock} seconds into the scene (which starts at {@link #T0} of the track), ARGB pixels, W*H. */
    public int[] render(double clock) {
        double t = clock + T0;
        String overlay = buildFrame(t);
        double flash = frameFlash;
        // the 3D picture is in r.color; the 2D overlays go on top of it
        r.flush();
        if (DEBUG_ARMS) { long sum = 0; for (int c : r.color) sum += ((c >> 16) & 255) + ((c >> 8) & 255) + (c & 255); System.out.println("t=" + t + " raw mean " + sum / 3.0 / r.color.length); }
        postFx(t);
        if (DEBUG_ARMS) { long sum = 0; for (int c : r.color) sum += ((c >> 16) & 255) + ((c >> 8) & 255) + (c & 255); System.out.println("   after fx " + sum / 3.0 / r.color.length + " fade " + fade + " heavy " + heavy); }
        double gain = exposure * (1 - exposureBoost) * (1 - fade);
        for (int i = 0; i < pixels.length; i++) {
            int p = r.color[i];
            pixels[i] = 0xFF000000 | (clamp255((int) (((p >> 16) & 255) * gain)) << 16) | (clamp255((int) (((p >> 8) & 255) * gain)) << 8) | clamp255((int) ((p & 255) * gain));
        }
        if (overlay != null) overlayText(overlay, tmOverlay, t);
        if (bloodAmount > 0.01) blood(t);
        post(t, Math.max(flash, kickFlash));
        return pixels;
    }

    private double frameFlash;

    /** Builds the 3D picture of the moment {@code t} (track time) into {@link #r} and the state of the post chain; returns the name of the 2D overlay to put on top. */
    private String buildFrame(double t) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, W, H);
        double flash = 0;
        kickFlash = 0;
        fade = 0;
        extraCracks = 0;
        tvDead = 0;
        tvFall = 0;
        heavy = 0;
        bodyPitch = null;
        vmScale = 1.0;
        ARM_TINT = 0xFFFFFFFF;
        glitch = glitchAt(t);
        titleAlpha = 0;
        fxRayTint = new int[]{210, 225, 255};
        exposureBoost = 0;
        bloodAmount = 0;
        String overlay = null;
        int seg = memoryAt(t);
        if (t < SMASH_END) {
            smash(t);
        } else if (t < MEMORY_START[0]) {
            tvDead = 1; tvFall = 1;
            basement(t, 0, -1);                                   // dark: the broken set on the floor, and the arm comes into view
        } else if (seg >= 0) {
            double local = t - MEMORY_START[seg];
            double len = MEMORY_END[seg] - MEMORY_START[seg];
            if (local < CUT_LEN) {
                tvDead = 1; tvFall = 1;
                cutBeat(seg, local, t);
                if (local > CUT_LEN - 0.5) flash = 0.5 * (local - (CUT_LEN - 0.5)) / 0.5;
            } else {
                double tm = (local - CUT_LEN) / (len - CUT_LEN);
                overlay = memory(MEMORY_ID[seg], tm, t);
                if (local < CUT_LEN + 0.10) flash = 0.5 * (1 - (local - CUT_LEN) / 0.10);
                if (len - local < 0.5) {                          // pain ends it: the picture jolts and goes red-white
                    double k = 1 - (len - local) / 0.5;
                    flash = Math.max(flash, 0.6 * k);
                    glitch = Math.max(glitch, k);
                }
            }
        } else if (t < ARCHITECTS) {
            cuttingFirstPerson(t);
        } else if (t < STAB) {
            overlay = lightsRoom(t);
        } else if (t < WHITE) {
            overlay = stabBeat(t);
        } else if (t < LIMBO) {
            whiteCeiling(t);
        } else {
            limbo(t);
        }
        frameFlash = flash;
        return overlay;
    }

    // ------------------------------------------------------------------ the same picture, by the graphics card

    private final BufferedImage[] ovImages = new BufferedImage[2];
    private final Graphics2D[] ovGs = new Graphics2D[2];
    private int ovIdx;

    /**
     * The frame at {@code clock}, collected for the graphics card without touching it (so a thread of its own can do this while the game draws
     * the last frame). Hand the result to {@link GpuScene#draw}.
     */
    public GpuScene.Frame collectGpu(double clock, int outW, int outH) {
        double t = clock + T0;
        GpuScene.Frame frame = new GpuScene.Frame(r);
        String overlay = buildFrame(t);
        double flash = frameFlash;
        // the 2D layer: text, the camera's marks, mist, blood; drawn at its own size (sharp at any output), laid over by the card
        if (overlay != null || bloodAmount > 0.01 || titleAlpha > 0.01) {
            int ow = Math.min(outW, 1280), oh = Math.min(outH, 720);
            ovIdx ^= 1;
            if (ovImages[ovIdx] == null || ovImages[ovIdx].getWidth() != ow || ovImages[ovIdx].getHeight() != oh) {
                ovImages[ovIdx] = new BufferedImage(ow, oh, BufferedImage.TYPE_INT_ARGB);
                ovGs[ovIdx] = ovImages[ovIdx].createGraphics();
                ovGs[ovIdx].setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                ovGs[ovIdx].setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            }
            Graphics2D og = ovGs[ovIdx];
            og.setTransform(new java.awt.geom.AffineTransform());
            og.setComposite(java.awt.AlphaComposite.Clear);
            og.fillRect(0, 0, ow, oh);
            og.setComposite(java.awt.AlphaComposite.SrcOver);
            og.scale(ow / (double) W, oh / (double) H);
            g = og; gpuMode = true; curFrame = frame;
            outScaleX = outW / (double) W; outScaleY = outH / (double) H;
            ovScale = ow / (double) W;
            try {
                if (overlay != null) overlayText(overlay, tmOverlay, t);
                if (bloodAmount > 0.01) bloodGpu(t);
                if (titleAlpha > 0.01) titleGpu();
            } finally {
                g = gCpu; gpuMode = false; curFrame = null;
            }
            frame.setOverlay(((java.awt.image.DataBufferInt) ovImages[ovIdx].getRaster().getDataBuffer()).getData(), ow, oh);
        }
        GpuScene.Params fp = new GpuScene.Params();
        fp.gain = exposure * (1 - exposureBoost) * (1 - fade);
        fp.bloomStrength = fxBloom;
        fp.bloomThreshold = 0.55;
        fp.ssao = fxSsao;
        fp.ssaoRadius = 9;
        fp.rays = fxRays;
        fp.rayAt = fxRayAt;
        fp.rayTint = fxRayTint;
        fp.dofFocus = fxDofFocus;
        fp.dofRange = fxDofRange;
        fp.contrast = fxContrast;
        fp.sat = fxSat;
        fp.flash = Math.max(flash, kickFlash);
        fp.heavy = heavy;
        fp.glitch = glitch;
        fp.time = t;
        fp.seed = rnd.nextLong();
        frame.finish(fp);
        return frame;
    }
    // what the post chain does this frame (each scene sets it)
    private double fxBloom = 0.6, fxSsao = 0.55, fxRays = 0, fxContrast = 1.08, fxSat = 0.95, fxDofFocus = 0, fxDofRange = 6;
    private double[] fxRayAt;
    private int[] fxRayTint = {210, 225, 255};

    /** The shaders: occlusion, light shafts, bloom, depth of field, grading. */
    private void postFx(double t) {
        r.ssao(fxSsao, 9);
        if (fxRayAt != null && fxRays > 0) r.godRays(fxRayAt[0], fxRayAt[1], fxRayAt[2], fxRays, fxRayTint[0], fxRayTint[1], fxRayTint[2]);
        r.bloom(0.55, fxBloom, 5);
        if (fxDofFocus > 0) r.dof(fxDofFocus, fxDofRange, 3);
        r.grade(fxContrast, fxSat, new int[]{-6, -2, 8}, new int[]{14, 8, -4});
    }

    private int memoryAt(double t) {
        for (int i = 0; i < MEMORY_START.length; i++) if (t >= MEMORY_START[i] && t < MEMORY_END[i]) return i;
        return -1;
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static double smooth(double t, double a, double b) {
        double x = clamp01((t - a) / (b - a));
        return x * x * (3 - 2 * x);
    }

    private static double lerp(double a, double b, double k) {
        return a + (b - a) * k;
    }

    /** 1 right on a kick, falling off quickly. */
    private double pulse(double t) {
        int lo = 0, hi = kicks.length - 1, best = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (kicks[mid] <= t) { best = mid; lo = mid + 1; } else hi = mid - 1;
        }
        if (best < 0) return 0;
        return Math.exp(-(t - kicks[best]) * 14);
    }

    // ------------------------------------------------------------------ the basement

    private void liveStatic(double bright, boolean whiteout) {
        for (int i = 0; i < staticTex.px.length; i++) {
            int v = (int) ((rnd.nextInt(256) * 0.85 + (whiteout ? 80 : 0)) * bright);
            v = Math.min(255, v);
            staticTex.px[i] = Soft3D.argb(255, v - 12, v, v - 6);
        }
    }

    private void cracks(int n) {
        if (n == crackCount) return;
        crackCount = n;
        BufferedImage im = new BufferedImage(96, 72, BufferedImage.TYPE_INT_ARGB);
        Graphics2D cg = im.createGraphics();
        cg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int i = 0; i < n; i++) {
            Random q = new Random(1000 + i * 77L);
            double x = 96 * (0.25 + 0.5 * q.nextDouble()), y = 72 * (0.25 + 0.5 * q.nextDouble());
            for (int arm = 0; arm < 7; arm++) {
                double a = q.nextDouble() * Math.PI * 2, px = x, py = y;
                for (int s = 0; s < 6; s++) {
                    a += (q.nextDouble() - 0.5) * 0.9;
                    double len = 3 + q.nextDouble() * 9;
                    double nx = px + Math.cos(a) * len, ny = py + Math.sin(a) * len;
                    cg.setColor(new Color(0, 0, 0, 235));
                    cg.setStroke(new BasicStroke(1.6f));
                    cg.drawLine((int) px, (int) py, (int) nx, (int) ny);
                    cg.setColor(new Color(255, 255, 255, 190));
                    cg.setStroke(new BasicStroke(0.6f));
                    cg.drawLine((int) px, (int) py, (int) nx, (int) ny);
                    px = nx;
                    py = ny;
                }
            }
        }
        cg.dispose();
        im.getRGB(0, 0, 96, 72, crackTex.px, 0, 96);
    }

    /** The room, the chair, the TV: {@code stabs} memories seen; {@code local} seconds into the stab (or -1 when there is none). */
    private void basement(double t, int stabs, double local) {
        double p = pulse(t);
        Random fl = new Random((long) (t * 24));
        double flicker = 0.78 + 0.22 * fl.nextDouble() + 0.35 * p;
        boolean hit = local >= 0 && local > 1.5 && local < 1.75;
        liveStatic(flicker * (1 - tvDead), hit);
        int n = stabs + extraCracks + (local >= 0 && local > 1.5 ? 1 : 0);
        cracks(Math.min(n, 14));

        r.clear(0x050505);
        r.ambR = 0.09; r.ambG = 0.09; r.ambB = 0.10;
        r.fogDensity = 0.10;
        // the TV lights the room cold; a dull lamp hangs by the wall on the left
        // light 0 is the TV: it throws the shadows, looking out into the room; the bulb is the warm one, with its own beam
        r.lights.add(new Soft3D.Light(0, 0.85, 0.15, 1.15 * flicker * (1 - tvDead), 1.30 * flicker * (1 - tvDead), 1.40 * flicker * (1 - tvDead), 7.0));
        r.shadowDir = new double[]{0, -0.05, -1};
        r.lights.add(new Soft3D.Light(-1.7, 2.1, -0.5, 1.05, 0.82, 0.55, 5.0));
        fxBloom = 0.9; fxSsao = 0.7; fxRays = 0.28; fxRayAt = new double[]{-1.7, 2.3, -0.5};
        fxContrast = 1.12; fxSat = 0.9; fxDofFocus = 0;

        // camera: a slow push in, a sway, a shake on every kick; on the stab it leans in until the tip of the blade is in the glass
        double push = smooth(t, 0, 16) * 1.2;
        double shake = p * 0.012 + (local >= 0 ? Math.exp(-Math.abs(local - 1.55) * 7) * 0.05 : 0);
        double lean = lungeAt(local);
        double pitchHit = -0.10;
        double[] hitCam = cameraFor(new double[]{0, 0.80, 0.28}, stabTipStage(), 0, pitchHit);
        double[] baseCam = {Math.sin(t * 0.5) * 0.02, 1.45 + Math.sin(t * 0.9) * 0.012, -3.1 + push};
        double k = Math.max(0, lean), kb = Math.max(0, -lean);
        double cx = lerp(baseCam[0], hitCam[0], k) + rnd.nextGaussian() * shake;
        double cy = lerp(baseCam[1], hitCam[1], k) + rnd.nextGaussian() * shake;
        double cz = lerp(baseCam[2], hitCam[2], k) - 0.08 * kb;
        r.camera(cx, cy, cz, Math.sin(t * 0.3) * 0.02 + rnd.nextGaussian() * shake * 0.4, lerp(-0.13, pitchHit, k) + rnd.nextGaussian() * shake * 0.4);
        if (t >= SMASH_END && t < MEMORY_START[0]) {                // the dark room: he looks round it, slowly, before the first cut; the arm comes in at the end
            double look = smooth(t, SMASH_END, MEMORY_START[0] - 1.0);
            double yawL = lerp(-2.35, 0.15, look) + Math.sin(t * 0.7) * 0.015;
            r.camera(Math.sin(t * 0.4) * 0.05, 1.5 + Math.sin(t * 0.9) * 0.015, lerp(-1.0, -1.7, look), yawL, -0.10 + Math.sin(t * 0.5) * 0.012);
            r.lights.add(new Soft3D.Light(0.2, 2.2, -0.6, 0.55, 0.6, 0.78, 6.5));            // a cold fill, so the room can be seen
        }

        room(t);
        chair(-1.35, 0.15);
        t0 = t;
        tvSet(flicker, n, 0);
        hallucinations(t, stabs);
        // the arm and the knife: rising into view at first, then the stab
        double enter = stabs > 0 ? 1 : smooth(t, ARM_ENTER, ARM_ENTER + 4);
        boolean striking = local >= 0;
        Ending13Rig.Frame fr = Ending13Rig.frame(striking ? "stab" : "hold", striking ? local : t, false);
        double dy = (1 - enter) * 16;
        int cuts = stabs + (striking && local > 1.4 ? 1 : 0);
        drawRig(fr, dy);
        drawKnife(fr.right(), dy, Math.min(1.0, cuts * 0.3));
    }

    private static Soft3D.Tex dustTex() {
        Soft3D.Tex t = new Soft3D.Tex(8, 8);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                double d = Math.hypot(x - 3.5, y - 3.5) / 3.5;
                int a = (int) Math.max(0, 255 * (1 - d));
                t.px[y * 8 + x] = Soft3D.argb(a < 128 ? 0 : 255, 255, 250, 235);
            }
        }
        return t;
    }

    /** The basement: a concrete room, pipes along the walls, a bulb on a cord, crates, a puddle, stairs; the chair and the TV go in it. */
    private void room(double t) {
        double x0 = -3.0, x1 = 3.0, z0 = -3.8, z1 = 1.2, h = 3.0;
        r.matSpec = 0.55; r.matShine = 38; r.matBump = 0.9;                                      // the floor is damp, and rough
        r.faceXZ(0, x0, z0, x1, z1, floor, 0xFFFFFFFF, 0, 6, 4);
        r.matSpec = 0.0; r.matBump = 0.7;
        r.faceXZ(h, x0, z0, x1, z1, wall, 0xFFAAAAAA, 0, 4, 3);
        // the walls, with a dirtier band along the bottom
        for (int side = 0; side < 4; side++) {
            double[][] p;
            switch (side) {
                case 0 -> { r.faceXY(z1, x0, 0.9, x1, h, wall, 0xFFFFFFFF, 0, 3.5, 2.2); r.faceXY(z1 - 0.002, x0, 0, x1, 0.9, wallLow, 0xFFFFFFFF, 0, 3.5, 0.9); }
                case 1 -> { r.faceXY(z0, x0, 0.9, x1, h, wall, 0xFFFFFFFF, 0, 3.5, 2.2); r.faceXY(z0 + 0.002, x0, 0, x1, 0.9, wallLow, 0xFFFFFFFF, 0, 3.5, 0.9); }
                case 2 -> { r.faceYZ(x0, 0.9, z0, h, z1, wall, 0xFFFFFFFF, 0, 3, 2.2); r.faceYZ(x0 + 0.002, 0, z0, 0.9, z1, wallLow, 0xFFFFFFFF, 0, 3, 0.9); }
                default -> { r.faceYZ(x1, 0.9, z0, h, z1, wall, 0xFFFFFFFF, 0, 3, 2.2); r.faceYZ(x1 - 0.002, 0, z0, 0.9, z1, wallLow, 0xFFFFFFFF, 0, 3, 0.9); }
            }
        }
        r.matBump = 0.7;
        // ceiling beams
        for (int i = 0; i < 4; i++) r.box(x0, h - 0.28, z0 + i * 1.4, x1, h, z0 + i * 1.4 + 0.30, wall, 0xFF888888, 0, 2);
        // pipes along the back wall and one across the ceiling, with brackets
        r.matSpec = 0.35; r.matShine = 20;
        r.bar(new double[]{x0, 2.35, z1 - 0.12}, new double[]{x1, 2.35, z1 - 0.12}, new double[]{0, 0.07, 0}, new double[]{0, 0, 0.07}, pipe, 0xFFCCCCCC, 0);
        r.bar(new double[]{x0, 2.12, z1 - 0.10}, new double[]{x1, 2.12, z1 - 0.10}, new double[]{0, 0.035, 0}, new double[]{0, 0, 0.035}, rust, 0xFFFFFFFF, 0);
        r.bar(new double[]{x0, 1.95, z1 - 0.09}, new double[]{x1, 1.95, z1 - 0.09}, new double[]{0, 0.02, 0}, new double[]{0, 0, 0.02}, dark, 0xFFFFFFFF, 0);
        r.bar(new double[]{x0, 2.88, z0 + 0.4}, new double[]{x1, 2.88, z0 + 0.4}, new double[]{0, 0.06, 0}, new double[]{0, 0, 0.06}, pipe, 0xFFAAAAAA, 0);
        for (int i = 0; i < 6; i++) {
            double x = x0 + 0.5 + i * 1.0;
            r.box(x - 0.02, 1.9, z1 - 0.15, x + 0.02, 2.42, z1 - 0.06, steel, 0xFF888888, 0, 1);
        }
        r.matSpec = 0;
        // a valve wheel on the pipe
        r.bar(new double[]{-1.9, 2.35, z1 - 0.2}, new double[]{-1.9, 2.35, z1 - 0.28}, new double[]{0.12, 0, 0}, new double[]{0, 0.012, 0}, rust, 0xFFFFFFFF, 0);
        r.bar(new double[]{-1.9, 2.35, z1 - 0.2}, new double[]{-1.9, 2.35, z1 - 0.28}, new double[]{0, 0.12, 0}, new double[]{0.012, 0, 0}, rust, 0xFFFFFFFF, 0);
        // a pillar by the stairs, the stairs themselves going up on the right
        r.box(2.0, 0, z1 - 0.7, 2.4, h, z1 - 0.2, wall, 0xFFBBBBBB, 0, 2);
        for (int i = 0; i < 6; i++) r.box(x1 - 0.9, i * 0.18, -1.2 + i * 0.28, x1, i * 0.18 + 0.18, 0.2 + i * 0.28, wall, 0xFF9A9A9A, 0, 2);
        // crates and a barrel in the corner behind the chair side
        r.box(-2.9, 0, -1.4, -2.2, 0.7, -0.7, crate, 0xFFFFFFFF, 0, 2);
        r.box(-2.9, 0.7, -1.25, -2.35, 1.15, -0.8, crate, 0xFFEEDDCC, 0, 2);
        r.box(-2.0, 0, -1.2, -1.55, 0.5, -0.78, crate, 0xFFDDCCBB, 0, 2);
        r.matSpec = 0.3; r.matShine = 16;
        r.bar(new double[]{-2.45, 0, -2.4}, new double[]{-2.45, 0.9, -2.4}, new double[]{0.28, 0, 0}, new double[]{0, 0, 0.28}, rust, 0xFFAA8866, 0);
        r.matSpec = 0;
        // a cord over the floor and a little rubble
        r.box(0.1, 0, 0.55, 0.14, 0.018, -1.6, dark, 0xFFFFFFFF, 0, 1);
        Random rb = new Random(12);
        for (int i = 0; i < 14; i++) {
            double rx = -2.5 + rb.nextDouble() * 5, rz = -2.5 + rb.nextDouble() * 3.3, sz = 0.03 + rb.nextDouble() * 0.06;
            r.box(rx, 0, rz, rx + sz, sz * 0.7, rz + sz * 1.2, wall, 0xFF777777, 0, 2);
        }
        // the bulb on its cord
        double sway = Math.sin(t * 0.9) * 0.015;
        r.bar(new double[]{-1.7, h, -0.5}, new double[]{-1.7 + sway, 2.45, -0.5}, new double[]{0.004, 0, 0}, new double[]{0, 0, 0.004}, dark, 0xFFFFFFFF, 0);
        r.box(-1.74 + sway, 2.32, -0.54, -1.66 + sway, 2.45, -0.46, dark, 0xFFFFFFFF, 0, 1);
        r.box(-1.76 + sway, 2.24, -0.56, -1.64 + sway, 2.33, -0.44, Soft3D.Tex.solid(255, 238, 190), 0xFFFFFFFF, 1.0, 1);
        // dust drifting in the beam of the bulb
        Random rd = new Random(99);
        for (int i = 0; i < 70; i++) {
            double ang = rd.nextDouble() * 6.28, rad = rd.nextDouble() * 0.9;
            double px = -1.7 + Math.cos(ang) * rad * (0.4 + 0.8 * rd.nextDouble()) + Math.sin(t * 0.3 + i) * 0.05;
            double py = 2.2 - rd.nextDouble() * 2.0 - ((t * 0.04 + i * 0.013) % 0.5);
            double pz = -0.5 + Math.sin(ang) * rad * 0.8 + Math.cos(t * 0.25 + i) * 0.05;
            r.billboard(px, py, pz, 0.006, 0.006, dust, 0xFFFFFFFF, 0.85);
        }
        basementProps(t);
    }

    // ------------------------------------------------------------------ the basement, lived in

    private Soft3D.Tex noticeTex, tallyTex, handTex, paperTex, stainTex, mattressTex, tarpTex;

    private Soft3D.Tex noticeBoard() {
        if (noticeTex != null) return noticeTex;
        noticeTex = paint(128, 96, g2 -> {
            g2.setColor(new Color(92, 70, 48)); g2.fillRect(0, 0, 128, 96);
            Random q = new Random(61);
            for (int i = 0; i < 9; i++) {
                int x = 6 + q.nextInt(100), y = 6 + q.nextInt(62), w = 16 + q.nextInt(18), h = 20 + q.nextInt(14);
                int v = 170 + q.nextInt(60);
                g2.setColor(new Color(v, v - 6, v - 24)); g2.fillRect(x, y, w, h);
                g2.setColor(new Color(60, 56, 50, 190));
                for (int l = 0; l < 4 + q.nextInt(4); l++) g2.fillRect(x + 2, y + 3 + l * 4, w - 5 - q.nextInt(8), 1);
                g2.setColor(new Color(200, 40, 40)); g2.fillOval(x + w / 2 - 1, y - 1, 3, 3);               // a red pin
            }
            g2.setColor(new Color(150, 30, 30, 160)); g2.setStroke(new BasicStroke(1.2f));               // a string joining them
            g2.drawLine(20, 20, 70, 60); g2.drawLine(70, 60, 108, 18); g2.drawLine(20, 20, 108, 18);
            g2.setColor(new Color(30, 22, 16)); g2.setStroke(new BasicStroke(3f)); g2.drawRect(1, 1, 125, 93);
        });
        return noticeTex;
    }

    private Soft3D.Tex tallyMarks() {
        if (tallyTex != null) return tallyTex;
        tallyTex = paint(128, 64, g2 -> {
            g2.setColor(new Color(0, 0, 0, 0)); g2.fillRect(0, 0, 128, 64);
            Random q = new Random(62);
            g2.setColor(new Color(214, 210, 196, 215)); g2.setStroke(new BasicStroke(1.6f));
            for (int group = 0; group < 14; group++) {
                int bx = 6 + (group % 7) * 17, by = 6 + (group / 7) * 28;
                for (int k = 0; k < 4; k++) g2.drawLine(bx + k * 3 + q.nextInt(2), by + q.nextInt(2), bx + k * 3 + q.nextInt(2), by + 18 + q.nextInt(3));
                if (group < 12) g2.drawLine(bx - 2, by + 15, bx + 12, by + 3);
            }
        });
        return tallyTex;
    }

    private Soft3D.Tex bloodHand() {
        if (handTex != null) return handTex;
        handTex = paint(64, 64, g2 -> {
            g2.setColor(new Color(0, 0, 0, 0)); g2.fillRect(0, 0, 64, 64);
            g2.setColor(new Color(110, 14, 18, 215));
            g2.fillRoundRect(18, 26, 28, 30, 10, 10);                                           // the palm
            for (int f = 0; f < 4; f++) g2.fillRoundRect(16 + f * 8, 6 + (f == 1 || f == 2 ? 0 : 6), 6, 28, 5, 5);
            g2.fillRoundRect(40, 24, 16, 7, 6, 6);                                                // the thumb
            g2.setColor(new Color(90, 8, 12, 150));
            for (int d = 0; d < 6; d++) g2.fillRect(18 + d * 5, 52, 2, 10 + d * 2);               // drips
        });
        return handTex;
    }

    private Soft3D.Tex scrap() {
        if (paperTex != null) return paperTex;
        paperTex = paint(32, 32, g2 -> {
            g2.setColor(new Color(212, 206, 184)); g2.fillRect(0, 0, 32, 32);
            g2.setColor(new Color(70, 66, 60, 170));
            for (int l = 0; l < 7; l++) g2.fillRect(3, 4 + l * 4, 20 + (l * 7) % 7, 1);
            g2.setColor(new Color(120, 60, 30, 70)); g2.fillOval(18, 14, 11, 9);
        });
        return paperTex;
    }

    private Soft3D.Tex puddleTex() {
        if (stainTex != null) return stainTex;
        stainTex = paint(64, 64, g2 -> {
            for (int i = 0; i < 12; i++) {
                int rr = 30 - i * 2;
                g2.setColor(new Color(10 + i, 14 + i, 18 + i * 2, 255));
                g2.fillOval(32 - rr, 32 - rr * 3 / 4, rr * 2, rr * 3 / 2);
            }
        });
        for (int i = 0; i < stainTex.px.length; i++) {
            int x = i % 64, y = i / 64;
            double d = Math.hypot((x - 31.5) / 32.0, (y - 31.5) / 32.0);
            int a = (int) (255 * Math.max(0, Math.min(1, (1 - d) * 3.0)));
            stainTex.px[i] = (a << 24) | (stainTex.px[i] & 0xFFFFFF);
        }
        return stainTex;
    }

    /** Everything that makes the room a room somebody has lived in: shelves, a bench, barrels, a mattress, notices, a drip, light in the air. */
    private void basementProps(double t) {
        double h = 3.0;
        Random q = new Random(2024);
        // the shelves on the right wall, with their stores
        r.matSpec = 0.1; r.matShine = 10;
        for (int side = 0; side < 2; side++) {
            double z = -3.6 + side * 1.2;
            r.box(2.78, 0, z, 2.84, 2.2, z + 0.05, wood, 0xFF8A7A66, 0, 2);
            r.box(2.78, 0, z + 1.1, 2.84, 2.2, z + 1.15, wood, 0xFF8A7A66, 0, 2);
            for (int sh = 0; sh < 4; sh++) {
                double y = 0.25 + sh * 0.6;
                r.box(2.5, y, z, 2.84, y + 0.04, z + 1.15, wood, 0xFFA09080, 0, 2);
                for (int it = 0; it < 4; it++) {
                    double iz = z + 0.06 + it * 0.27 + q.nextDouble() * 0.04, hh = 0.1 + q.nextDouble() * 0.28, ww = 0.08 + q.nextDouble() * 0.14;
                    int kind = q.nextInt(3);
                    if (kind == 0) r.box(2.52, y + 0.04, iz, 2.52 + ww + 0.1, y + 0.04 + hh, iz + ww + 0.06, crate, 0xFFCCBBAA, 0, 3);
                    else if (kind == 1) {                                                          // a jar with something in it
                        r.box(2.58, y + 0.04, iz, 2.58 + 0.1, y + 0.04 + hh, iz + 0.1, Soft3D.Tex.solid(150 + q.nextInt(80), 120 + q.nextInt(80), 60 + q.nextInt(120)), 0xFFAAAAAA, 0.08, 1);
                    } else r.box(2.56, y + 0.04, iz, 2.56 + 0.12, y + 0.04 + hh * 0.7, iz + 0.07, steel, 0xFF889098, 0, 1);
                }
            }
        }
        // a workbench in the back left, with a vise, tools on the wall over it, and a lamp
        r.box(-2.9, 0.82, -3.7, -1.5, 0.88, -2.9, wood, 0xFFB09A80, 0, 2);
        for (double lx : new double[]{-2.85, -1.55}) for (double lz : new double[]{-3.65, -2.95}) r.box(lx, 0, lz, lx + 0.06, 0.82, lz + 0.06, wood, 0xFF70604A, 0, 2);
        r.box(-2.0, 0.88, -3.6, -1.85, 1.0, -3.4, steel, 0xFF8A8F96, 0, 1);                                      // the vise
        for (int i = 0; i < 6; i++) r.box(-2.8 + i * 0.22, 1.55, -3.78, -2.78 + i * 0.22, 1.55 + 0.16 + (i % 3) * 0.07, -3.76, dark, 0xFFFFFFFF, 0, 1);   // tools on the pegboard
        r.box(-2.9, 1.2, -3.795, -1.5, 1.95, -3.78, wood, 0xFF6A5A48, 0, 2);
        r.matSpec = 0;
        // barrels and crates in the far corner and by the door
        r.matSpec = 0.3; r.matShine = 16;
        for (int b = 0; b < 4; b++) {
            double bx = 1.3 + (b % 2) * 0.62, bz = -3.2 + (b / 2) * 0.62;
            r.bar(new double[]{bx, 0, bz}, new double[]{bx, 0.9, bz}, new double[]{0.27, 0, 0}, new double[]{0, 0, 0.27}, rust, 0xFF9A7A60, 0);
            r.bar(new double[]{bx, 0.3, bz}, new double[]{bx, 0.34, bz}, new double[]{0.29, 0, 0}, new double[]{0, 0, 0.29}, steel, 0xFF666A70, 0);
            r.bar(new double[]{bx, 0.62, bz}, new double[]{bx, 0.66, bz}, new double[]{0.29, 0, 0}, new double[]{0, 0, 0.29}, steel, 0xFF666A70, 0);
        }
        r.matSpec = 0;
        r.box(0.2, 0, -3.5, 0.9, 0.5, -2.9, crate, 0xFFDDCCBB, 0, 2);
        r.box(0.28, 0.5, -3.45, 0.82, 0.95, -3.0, crate, 0xFFCCBBAA, 0, 2);
        // a mattress in the left corner with a blanket thrown over it
        r.box(-2.9, 0.0, -2.2, -1.95, 0.2, -0.95, mattress(), 0xFFFFFFFF, 0, 1);
        r.box(-2.88, 0.2, -1.5, -2.2, 0.27, -1.0, tarp(), 0xFF8A8A8A, 0, 1);                                  // the blanket
        r.box(-2.86, 0.2, -2.1, -2.4, 0.3, -1.8, white, 0xFFBBBBAA, 0, 2);                                    // a flat pillow
        // notices, tally marks, and a hand, on the walls
        r.faceXY(-3.795, -0.9, 1.15, 0.4, 2.05, noticeBoard(), 0xFFB0B0B0, 0, 1, 1);
        r.faceXY(-3.79, 0.7, 1.0, 1.6, 1.5, tallyMarks(), 0xFFFFFFFF, 0.05, 1, 1);
        r.faceYZ(-2.995, 1.0, -0.4, 1.9, 0.5, bloodHand(), 0xFFFFFFFF, 0, 1, 1);
        // a drip from the pipe, and its puddle
        r.matSpec = 0.9; r.matShine = 80;
        r.faceXZ(0.004, -0.9, -2.6, 0.5, -1.7, puddleTex(), 0xFFFFFFFF, 0, 1, 1);
        r.matSpec = 0;
        double ph = (t * 0.75) % 1.0;
        if (ph < 0.8) sprite(-0.2, 2.35 - ph * 2.35 * 1.0 / 0.8 * (0.35 + 0.65 * ph / 0.8), -2.15, 0.012, 0.03, 0xFFB8D0E8, 0.9, 0.5);
        double ring = ((t * 0.75) % 1.0);
        if (ring > 0.8) {                                                                                  // the ripple
            double rr = (ring - 0.8) * 1.5;
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8;
                sprite(-0.2 + Math.cos(a) * rr, 0.012, -2.15 + Math.sin(a) * rr * 0.7, 0.012, 0.012, 0xFFC8D8E8, 0.7 * (1 - (ring - 0.8) / 0.2), 0.6);
            }
        }
        // paper and rubbish on the floor
        for (int k = 0; k < 14; k++) {
            double px = -2.2 + q.nextDouble() * 4.6, pz = -3.2 + q.nextDouble() * 3.8, a = q.nextDouble() * 6.28, sz = 0.07 + q.nextDouble() * 0.08;
            double ca = Math.cos(a) * sz, sa = Math.sin(a) * sz;
            r.quad(new double[][]{{px - ca, 0.006 + k * 0.0004, pz - sa}, {px + sa, 0.006 + k * 0.0004, pz - ca}, {px + ca, 0.006 + k * 0.0004, pz + sa}, {px - sa, 0.006 + k * 0.0004, pz + ca}},
                    new double[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}}, scrap(), 0xFFFFFFFF, 0);
        }
        // a chain hoist from a beam
        r.bar(new double[]{1.0, h - 0.3, 0.0}, new double[]{1.0, 1.9, 0.0}, new double[]{0.012, 0, 0}, new double[]{0, 0, 0.012}, steel, 0xFF70767E, 0);
        r.box(0.94, 1.78, -0.06, 1.06, 1.92, 0.06, steel, 0xFF70767E, 0, 1);
        // light in the air: the bulb's halo and cone, dust, low mist, cold moon through a slit
        sprite(-1.7, 2.3, -0.5, 0.6, 0.6, 0xFFFFE6B0, 0.55, 1.0);
        lightCone(new double[]{-1.7, 2.28, -0.5}, new double[]{-1.7, 0.0, -0.5}, 0.05, 1.5, 0xFFFFE2A8, 0.13, 18);
        motes(new double[]{-1.6, 1.4, -0.6}, 1.3, 1.1, 1.3, 70, t, 11, 0xFFFFF0D0, 0.011, 0.9);
        mistLayer(0.22, 3.5, 0, -1.2, 0xFFB8BCC8, 0.22, t, 0.01, 1.6);
        lightCone(new double[]{-2.98, 2.7, -2.4}, new double[]{-1.6, 0.0, -1.7}, 0.03, 0.6, 0xFFA8C4F0, 0.10, 12);
        r.box(-3.0, 2.55, -2.7, -2.97, 2.85, -2.1, Soft3D.Tex.solid(190, 210, 245), 0xFFFFFFFF, 0.9, 1);       // the slit of window
    }

    private Soft3D.Tex mattress() {
        if (mattressTex != null) return mattressTex;
        mattressTex = paint(32, 32, g2 -> {
            g2.setColor(new Color(112, 108, 90)); g2.fillRect(0, 0, 32, 32);
            g2.setColor(new Color(70, 64, 50, 140));
            for (int i = 0; i < 5; i++) g2.fillOval(2 + i * 6, 4 + (i * 7) % 20, 9, 7);
            g2.setColor(new Color(60, 56, 46)); for (int x = 0; x < 32; x += 8) g2.fillRect(x, 0, 1, 32);
        });
        return mattressTex;
    }

    private Soft3D.Tex tarp() {
        if (tarpTex != null) return tarpTex;
        tarpTex = paint(32, 32, g2 -> {
            g2.setColor(new Color(58, 66, 76)); g2.fillRect(0, 0, 32, 32);
            g2.setColor(new Color(40, 46, 54)); for (int x = 0; x < 32; x += 5) g2.fillRect(x, 0, 2, 32);
        });
        return tarpTex;
    }

    private void chair(double x, double z) {
        double s = 0.22;
        r.box(x - s, 0.43, z - s, x + s, 0.47, z + s, wood, 0xFFFFFFFF, 0, 2);
        for (int ix = -1; ix <= 1; ix += 2) for (int iz = -1; iz <= 1; iz += 2) r.box(x + ix * (s - 0.02) - 0.015, 0, z + iz * (s - 0.02) - 0.015, x + ix * (s - 0.02) + 0.015, 0.43, z + iz * (s - 0.02) + 0.015, wood, 0xFFFFFFFF, 0, 2);
        for (int ix = -1; ix <= 1; ix += 2) r.box(x + ix * (s - 0.02) - 0.015, 0.47, z + s - 0.04, x + ix * (s - 0.02) + 0.015, 0.95, z + s - 0.01, wood, 0xFFFFFFFF, 0, 2);
        for (int i = 0; i < 4; i++) r.box(x - s + 0.04 + i * 0.12, 0.62, z + s - 0.035, x - s + 0.075 + i * 0.12, 0.92, z + s - 0.015, wood, 0xFFFFFFFF, 0, 2);
        r.box(x - s, 0.90, z + s - 0.04, x + s, 0.95, z + s - 0.01, wood, 0xFFFFFFFF, 0, 2);
    }

    /** Figures at the edges of the light: one more after every memory, a little closer with time, turned towards the camera. */
    private void hallucinations(double t, int stabs) {
        int n = Math.min(7, 1 + stabs);
        for (int i = 0; i < n; i++) {
            Random q = new Random(40 + i * 13L);
            double side = i % 2 == 0 ? -1 : 1;
            double bx = side * (1.3 + q.nextDouble() * 1.4);
            double bz = 0.5 + q.nextDouble() * 0.9;
            double approach = clamp01((t - (i * 14 + 6)) / 90.0);
            double x = lerp(bx, bx * 0.55, approach), z = lerp(bz, bz - 1.3, approach);
            double alpha = smooth(t, i * 14 + 4, i * 14 + 9);
            if (alpha < 0.02) continue;
            Soft3D.Pose pose = new Soft3D.Pose();
            double sway = Math.sin(t * 0.8 + i) * 0.04;
            pose.pitch[Soft3D_ARM_R] = sway; pose.pitch[Soft3D_ARM_L] = -sway;
            pose.yaw[0] = Math.sin(t * 0.3 + i) * 0.3;
            double face = Math.atan2(r.camX - x, r.camZ - z);
            int shade = (int) (16 * alpha);
            r.figure(model, skin, x, 0, z, face, 0.0625 * 1.05, pose, Soft3D.argb(255, shade, shade, shade + 2), 0, Soft3D.ALL_PARTS);
            // two pale eyes that glint
            double[] e = {x + Math.sin(face) * 0.19, 1.67, z + Math.cos(face) * 0.19};
            double hw = 0.025;
            r.card(e[0] + Math.cos(face) * 0.07, e[1], e[2] - Math.sin(face) * 0.07, hw, 0.012, face, Soft3D.Tex.solid(235, 235, 215), 0xFFFFFFFF, 1.0 * alpha);
            r.card(e[0] - Math.cos(face) * 0.07, e[1], e[2] + Math.sin(face) * 0.07, hw, 0.012, face, Soft3D.Tex.solid(235, 235, 215), 0xFFFFFFFF, 1.0 * alpha);
        }
    }

    private static final int Soft3D_ARM_R = PlayerBoxes.RIGHT_ARM, Soft3D_ARM_L = PlayerBoxes.LEFT_ARM;

    // ------------------------------------------------------------------ the hand with the knife

    /** The stab: 0 at rest, -1 wound up, +1 with the blade in the glass; eased, with a wind-up, a hit and a slow pull back. */
    private static double lungeAt(double local) {
        if (local < 0) return 0;
        if (local < 1.1) return -smooth(local, 0.0, 1.1) * 0.9;                       // the arm draws back and up
        if (local < 1.35) return -0.9 + 1.9 * smooth(local, 1.1, 1.35);              // and drives forward fast
        if (local < 1.5) return 1.0 + 0.04 * Math.sin((local - 1.35) * 60) * (1.5 - local) * 6;     // it hits and shudders
        return 1.0 - 0.0 * (local - 1.5);                                             // and stays in the glass while the memory begins
    }

    // ------------------------------------------------------------------ the arms (our parts-and-keys animation system)

    private static final double PX = 0.0568;                // one skin pixel in metres: a 32-pixel player 1.8 m high, the eye at 1.62 m

    /** The mirrored camera frame the first-person rig is placed in: its right is the screen's right, because the rig's right limbs are on -x. */
    private static double[][] mirroredBasis(double yaw, double pitch) {
        double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
        double[] right = {cy, 0, -sy}, up = {-sy * sp, cp, -cy * sp}, fwd = {sy * cp, sp, cy * cp};
        return new double[][]{{-right[0], -right[1], -right[2]}, up, fwd};
    }

    private static double[] rigOrigin(double[] cam, double[][] bm) {
        double[] e = Ending13Rig.EYE;
        return new double[]{cam[0] - (bm[0][0] * e[0] + bm[1][0] * e[1] + bm[2][0] * e[2]) * PX,
                cam[1] - (bm[0][1] * e[0] + bm[1][1] * e[1] + bm[2][1] * e[2]) * PX,
                cam[2] - (bm[0][2] * e[0] + bm[1][2] * e[1] + bm[2][2] * e[2]) * PX};
    }

    /**
     * The arms are seen as a game shows them, not as a body would: drawn nearer the middle of the view, a little forward and up (a body's own
     * arms hang out of sight beside and below the eyes).
     */
    private static double vmScale = 1.0;      // how big the arms are drawn (smaller when looking down at them, so they do not fill the picture)

    private static double[] viewmodel(double[] p) {
        double cx = 0, cy = 21, cz = 5;
        double x = cx + (p[0] - cx) * vmScale, y = cy + (p[1] - cy) * vmScale, z = cz + (p[2] - cz) * vmScale;
        return new double[]{x * 0.9, y + 1.2, z + 4.5};
    }

    private static double[] stageToWorld(double[] stage, double[][] bm, double[] origin) {
        double[] p = viewmodel(stage);
        return new double[]{origin[0] + (bm[0][0] * p[0] + bm[1][0] * p[1] + bm[2][0] * p[2]) * PX,
                origin[1] + (bm[0][1] * p[0] + bm[1][1] * p[1] + bm[2][1] * p[2]) * PX,
                origin[2] + (bm[0][2] * p[0] + bm[1][2] * p[1] + bm[2][2] * p[2]) * PX};
    }

    /** When set, the arms follow the body (which does not tilt when the head looks down) instead of the camera. */
    private Double bodyPitch;

    private double[][] frameBasis() {
        return mirroredBasis(r.yaw, bodyPitch != null ? bodyPitch : r.pitch);
    }

    private double[] frameOrigin(double[][] bm) {
        return rigOrigin(new double[]{r.camX, r.camY, r.camZ}, bm);
    }

    /** The boxes of the arms of a frame, in the player's skin, seen from the first person; {@code dy} sinks them (for rising into view). */
    private void drawRig(Ending13Rig.Frame f, double dy) {
        double[][] bm = frameBasis();
        double[] o = frameOrigin(bm);
        r.matSpec = 0.12; r.matShine = 12; r.matBump = 0.5; r.matWrap = 0.45;
        for (Ending13Rig.Quad q : f.quads()) {
            double[][] w = new double[4][];
            for (int i = 0; i < 4; i++) w[i] = stageToWorld(new double[]{q.corners()[i][0], q.corners()[i][1] - dy, q.corners()[i][2]}, bm, o);
            double[][] uv = {{q.u0() / 64.0, q.v0() / 64.0}, {q.u1() / 64.0, q.v0() / 64.0}, {q.u1() / 64.0, q.v1() / 64.0}, {q.u0() / 64.0, q.v1() / 64.0}};
            r.quad(w, uv, skin, ARM_TINT, DEBUG_ARMS ? 0.9 : 0);
        }
        r.matSpec = 0; r.matBump = 0; r.matWrap = 0;
    }

    static boolean DEBUG_ARMS = false;
    private double kickFlash = 0;
    private double flashKick = 0;   // the frame's answer to a cut: a beat of light and a drop of focus
    private int ARM_TINT = 0xFFFFFFFF;

    private static final double BLADE = 6.2;                // blade length in skin pixels (about 35 cm)

    private final Soft3D.Tex blade = paint(32, 32, g2 -> {
        g2.setPaint(new java.awt.GradientPaint(0, 0, new Color(235, 238, 246), 0, 32, new Color(120, 126, 140)));
        g2.fillRect(0, 0, 32, 32);
        g2.setColor(new Color(255, 255, 255, 200)); g2.fillRect(0, 12, 32, 2);                   // the fuller catching the light
        g2.setColor(new Color(70, 76, 88)); g2.fillRect(0, 28, 32, 4);                             // the ground edge
    });
    private final Soft3D.Tex grip = paint(16, 16, g2 -> {
        g2.setColor(new Color(24, 22, 24)); g2.fillRect(0, 0, 16, 16);
        g2.setColor(new Color(52, 50, 54)); for (int i = 0; i < 16; i += 3) g2.fillRect(i, 0, 1, 16);
    });

    /**
     * A knife in a fist: the handle through the closed fingers (across the hand, so the fingers go round it), a cross-guard, a clip-point blade
     * with a ground edge and a groove, pointing out of the front of the fist. {@code blood} stains the blade a dull red up from the tip.
     */
    private void drawKnife(Ending13Rig.Hand h, double dy, double blood) {
        double[][] bm = frameBasis();
        double[] o = frameOrigin(bm);
        double[] x = h.x(), y = h.y(), z = h.z();
        double[] fist = {h.wrist()[0] - y[0] * 1.7, h.wrist()[1] - y[1] * 1.7 - dy, h.wrist()[2] - y[2] * 1.7};
        java.util.function.BiFunction<double[], double[], double[]> at = (c, d) -> stageToWorld(new double[]{
                fist[0] + x[0] * d[0] * 0 + c[0] * x[0] + c[1] * y[0] + c[2] * z[0],
                fist[1] + c[0] * x[1] + c[1] * y[1] + c[2] * z[1],
                fist[2] + c[0] * x[2] + c[1] * y[2] + c[2] * z[2]}, bm, o);
        double[] none = {0, 0, 0};
        // the handle, a little longer than the fist is wide, and a pommel
        r.matSpec = 0.15; r.matShine = 8;
        r.bar(at.apply(new double[]{0, 0, -2.3}, none), at.apply(new double[]{0, 0, 2.0}, none), scaled(x, 0.42 * PX), scaled(y, 0.55 * PX), grip, 0xFFFFFFFF, 0);
        r.matSpec = 0.8; r.matShine = 50;
        r.bar(at.apply(new double[]{0, 0, -2.5}, none), at.apply(new double[]{0, 0, -2.3}, none), scaled(x, 0.6 * PX), scaled(y, 0.7 * PX), steel, 0xFFBBBBBB, 0);
        // the guard
        r.bar(at.apply(new double[]{0, 0, 2.0}, none), at.apply(new double[]{0, 0, 2.35}, none), scaled(x, 0.45 * PX), scaled(y, 1.7 * PX), steel, 0xFFD0C8B0, 0);
        // the blade: two sides (a quad with the tip as a pinched corner), the spine, the edge
        double z0 = 2.35, spine = 0.95, edge = -0.95, th = 0.20, zt = z0 + BLADE, zs = z0 + BLADE * 0.84;
        r.matSpec = 1.0; r.matShine = 70;
        for (int side = -1; side <= 1; side += 2) {
            double[][] s = {at.apply(new double[]{side * th, spine, z0}, none), at.apply(new double[]{side * th, spine, zs}, none),
                    at.apply(new double[]{side * th * 0.2, edge * 0.1, zt}, none), at.apply(new double[]{side * th * 0.5, edge, z0}, none)};
            r.quad(s, new double[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}}, blade, 0xFFFFFFFF, 0);
        }
        double[][] top = {at.apply(new double[]{-th, spine, z0}, none), at.apply(new double[]{th, spine, z0}, none), at.apply(new double[]{th, spine, zs}, none), at.apply(new double[]{-th, spine, zs}, none)};
        r.quad(top, new double[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}}, blade, 0xFFAAAAAA, 0);
        double[][] clip = {at.apply(new double[]{-th, spine, zs}, none), at.apply(new double[]{th, spine, zs}, none), at.apply(new double[]{th * 0.2, edge * 0.1, zt}, none), at.apply(new double[]{-th * 0.2, edge * 0.1, zt}, none)};
        r.quad(clip, new double[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}}, blade, 0xFFCCCCCC, 0);
        if (blood > 0) {                                                            // a dark red wash from the tip back along the blade
            double bz = zt - BLADE * 0.55 * blood;
            Soft3D.Tex red = Soft3D.Tex.solid(95, 6, 10);
            for (int side = -1; side <= 1; side += 2) {
                double[][] s = {at.apply(new double[]{side * (th + 0.02), spine * 0.2, bz}, none), at.apply(new double[]{side * (th + 0.02), spine * 0.2, zs}, none),
                        at.apply(new double[]{side * th * 0.25, edge * 0.1, zt}, none), at.apply(new double[]{side * (th + 0.02), edge * 0.6, bz}, none)};
                r.quad(s, new double[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}}, red, 0xFFFFFFFF, 0);
            }
        }
        r.matSpec = 0;
    }

    private static double[] scaled(double[] v, double k) {
        return new double[]{v[0] * k, v[1] * k, v[2] * k};
    }

    /** Where the tip of the knife is at the moment of the hit (stage space): worked out once from the clip. */
    private double[] stabTip;

    private double[] stabTipStage() {
        if (stabTip == null) {
            Ending13Rig.Frame f = Ending13Rig.frame("stab", 1.45, false);
            Ending13Rig.Hand h = f.right();
            double[] fist = {h.wrist()[0] - h.y()[0] * 1.7, h.wrist()[1] - h.y()[1] * 1.7, h.wrist()[2] - h.y()[2] * 1.7};
            double len = 2.35 + BLADE;
            stabTip = new double[]{fist[0] + h.z()[0] * len, fist[1] + h.z()[1] * len, fist[2] + h.z()[2] * len};
        }
        return stabTip;
    }

    /** The middle of the two fists at the moment they hold on (stage space), worked out from the "grab" clip. */
    private double[] gripMid() {
        Ending13Rig.Frame f = Ending13Rig.frame("grab", 8.0, false);
        double[] a = f.rightWrist(), b = f.leftWrist();
        return new double[]{(a[0] + b[0]) / 2, (a[1] + b[1]) / 2 - 0.6, (a[2] + b[2]) / 2 + 1.0};
    }

    /** The camera position that puts a stage-space point of the rig at a given world point, for the camera looking as given. */
    private static double[] cameraFor(double[] world, double[] stage, double yaw, double pitch) {
        double[][] bm = mirroredBasis(yaw, pitch);
        double[] e = Ending13Rig.EYE;
        double[] vm = viewmodel(stage);
        double[] rel = {vm[0] - e[0], vm[1] - e[1], vm[2] - e[2]};
        return new double[]{world[0] - (bm[0][0] * rel[0] + bm[1][0] * rel[1] + bm[2][0] * rel[2]) * PX,
                world[1] - (bm[0][1] * rel[0] + bm[1][1] * rel[1] + bm[2][1] * rel[2]) * PX,
                world[2] - (bm[0][2] * rel[0] + bm[1][2] * rel[1] + bm[2][2] * rel[2]) * PX};
    }

    // ------------------------------------------------------------------ the set and the deck, as they are in the endings menu

    private double glitch;              // 1 right on a stutter of the track, falling off fast
    private double tvFall = 0;          // 0 = the set stands on the deck, 1 = it has gone over the back of the cabinet
    private double tvShakeX, tvShakeY;
    private final Soft3D.Tex screenTex = new Soft3D.Tex(160, 120);
    private final BufferedImage screenImg = new BufferedImage(160, 120, BufferedImage.TYPE_INT_ARGB);
    private final Soft3D.Tex tvBrown = Soft3D.Tex.surface(7, 32, 32, 46, 38, 33, 4, 2);
    private final Soft3D.Tex tvBevel = Soft3D.Tex.surface(70, 32, 32, 84, 72, 62, 4, 2);
    private final Soft3D.Tex deckBody = Soft3D.Tex.surface(71, 32, 32, 34, 28, 25, 3, 2);
    private final Soft3D.Tex ledRed = Soft3D.Tex.solid(255, 40, 30);
    private final Soft3D.Tex amber = Soft3D.Tex.solid(224, 160, 48);
    private final Soft3D.Tex black = Soft3D.Tex.solid(4, 3, 3);
    private final Soft3D.Tex cassetteTex = paint(64, 40, g2 -> {
        g2.setColor(new Color(34, 34, 40)); g2.fillRect(0, 0, 64, 40);
        g2.setColor(new Color(201, 194, 166)); g2.fillRect(5, 4, 54, 18);                 // the label
        g2.setColor(new Color(138, 31, 31)); g2.fillRect(8, 7, 48, 3);
        g2.setColor(new Color(106, 100, 84)); g2.fillRect(8, 13, 30, 1); g2.fillRect(8, 16, 22, 1);
        g2.setColor(new Color(10, 10, 12)); g2.fillOval(12, 25, 12, 12); g2.fillOval(40, 25, 12, 12);
        g2.setColor(new Color(224, 224, 224)); g2.fillOval(16, 29, 4, 4); g2.fillOval(44, 29, 4, 4);
        g2.setColor(new Color(58, 42, 26)); g2.fillRect(24, 29, 16, 4);
    });

    /** 1 on a stutter of the track, falling off fast. */
    private static double glitchAt(double t) {
        int lo = 0, hi = GLITCH.length - 1, best = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (GLITCH[mid] <= t) { best = mid; lo = mid + 1; } else hi = mid - 1;
        }
        return best < 0 ? 0 : Math.exp(-(t - GLITCH[best]) * 16);
    }

    /** A point of the set, which can be standing or going over backwards about the back-bottom edge of the cabinet top. */
    private double[] tvT(double x, double y, double z) {
        double f = tvFall, th = 1.5 * f * f;
        double py = 0.68, pz = 0.78;
        double dy = y - py, dz = z - pz;
        double ny = dy * Math.cos(th) - dz * Math.sin(th), nz = dy * Math.sin(th) + dz * Math.cos(th);
        double drop = 0.62 * smooth(f, 0.6, 1.0);
        return new double[]{x + tvShakeX, py + ny - drop + tvShakeY, pz + nz};
    }

    /** A box of the set (it moves with it): corners are given in the set's own place, then carried. */
    private void tvBox(double x0, double y0, double z0, double x1, double y1, double z1, Soft3D.Tex tex, int tint, double em) {
        double[][] c = new double[8][];
        for (int i = 0; i < 8; i++) c[i] = tvT((i & 4) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 1) == 0 ? z0 : z1);
        int[][] faces = {{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};
        for (int[] f : faces) r.quad(new double[][]{c[f[0]], c[f[1]], c[f[2]], c[f[3]]}, new double[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}}, tex, tint, em);
    }

    private void tvQuad(double[][] p, Soft3D.Tex tex, int tint, double em) {
        double[][] w = new double[4][];
        for (int i = 0; i < 4; i++) w[i] = tvT(p[i][0], p[i][1], p[i][2]);
        r.quad(w, new double[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}}, tex, tint, em);
    }

    /** The picture on the glass: the ring of the endings, scanlines, the tracking band, snow; torn and tinted on a stutter; dead when broken. */
    private void paintScreen(double t, double brightness, double dead, int cracks) {
        Graphics2D sg = screenImg.createGraphics();
        sg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Random q = new Random((long) (t * 30));
        int w = 160, h = 120;
        if (dead >= 0.99) {
            sg.setColor(new Color(5, 6, 7)); sg.fillRect(0, 0, w, h);
            sg.setColor(new Color(22, 26, 28)); sg.fillRect(8, 6, 50, 3);                    // a faint reflection on the dead glass
        } else {
            sg.setPaint(new java.awt.GradientPaint(0, 0, new Color(14, 34, 40), 0, h, new Color(8, 18, 24)));
            sg.fillRect(0, 0, w, h);
            double spin = t * 0.25;
            for (int i = 0; i < 13; i++) {
                double a = i / 13.0 * Math.PI * 2 + spin;
                int cx = (int) (w / 2 + Math.cos(a) * 44), cy = (int) (h / 2 + Math.sin(a) * 36);
                Color base = Color.getHSBColor(i / 13f, 0.30f, 0.95f);
                sg.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 70 + q.nextInt(60)));
                sg.fillOval(cx - 7, cy - 7, 14, 14);
                sg.setColor(new Color(200, 235, 245, 150)); sg.drawOval(cx - 7, cy - 7, 14, 14);
                sg.setColor(new Color(235, 250, 255, 190)); sg.fillOval(cx - 2, cy - 2, 4, 4);
            }
            sg.setColor(new Color(170, 225, 235, 120)); sg.drawOval(w / 2 - 20, h / 2 - 20, 40, 40);
            sg.setColor(new Color(0, 0, 0, 70));
            for (int y = 0; y < h; y += 2) sg.drawLine(0, y, w, y);                           // scanlines
            int band = h - (int) ((t * 40) % (h + 30));
            sg.setColor(new Color(255, 255, 255, 36)); sg.fillRect(0, band, w, 4);
            for (int i = 0; i < 90; i++) {                                                    // snow
                int sx = q.nextInt(w), sy = q.nextInt(h);
                sg.setColor(new Color(255, 255, 255, 30 + q.nextInt(60)));
                sg.fillRect(sx, sy, 1 + q.nextInt(2), 1);
            }
            if (glitch > 0.15) {                                                              // a stutter: torn rows, a cold flash, colour bars
                for (int i = 0; i < 7; i++) {
                    int y0 = q.nextInt(h - 8), hh = 2 + q.nextInt(8), dx = q.nextInt(60) - 30;
                    java.awt.image.BufferedImage sub = screenImg.getSubimage(Math.max(0, -dx) % w, y0, w - Math.abs(dx), hh);
                    sg.drawImage(sub, Math.max(0, dx), y0, null);
                }
                sg.setColor(new Color(q.nextInt(2) * 255, 255, q.nextInt(2) * 255, (int) (110 * glitch)));
                sg.fillRect(0, q.nextInt(h - 10), w, 2 + q.nextInt(8));
            }
            if (brightness < 1) {
                sg.setColor(new Color(0, 0, 0, (int) (255 * (1 - clamp01(brightness)))));
                sg.fillRect(0, 0, w, h);
            }
        }
        sg.dispose();
        screenImg.getRGB(0, 0, w, h, screenTex.px, 0, w);
    }

    /**
     * The set as the endings menu has it: a brown-black body with a lighter bevel, the glass on the left with the picture, a strip on the right
     * (speaker slots, two knobs, a row of buttons, the red power light), and the VHS deck under it with its slot and the amber label. The
     * cabinet it all stands on is the old one. {@code cassetteOut} pushes the tape out of the slot (0..1).
     */
    private void tvSet(double flicker, int cracks, double cassetteOut) {
        // the cabinet and the deck do not move when the set goes over
        r.box(-0.32, 0, 0.30, 0.32, 0.55, 0.78, wood, 0xFF8A7A6A, 0, 2);
        r.box(-0.30, 0.55, 0.30, 0.30, 0.68, 0.74, deckBody, 0xFFFFFFFF, 0, 2);
        r.box(-0.20, 0.585, 0.296, 0.20, 0.612, 0.30, black, 0xFFFFFFFF, 0, 1);               // the slot
        r.box(-0.20, 0.612, 0.298, 0.20, 0.616, 0.30, steel, 0xFF555555, 0, 1);
        r.box(-0.26, 0.64, 0.296, -0.18, 0.66, 0.30, tvBevel, 0xFF888888, 0, 1);              // its little buttons
        r.box(-0.16, 0.64, 0.296, -0.12, 0.66, 0.30, tvBevel, 0xFF888888, 0, 1);
        r.box(0.15, 0.636, 0.296, 0.27, 0.664, 0.30, amber, 0xFFFFFFFF, 0.55, 1);              // the amber VHS label
        if (cassetteOut > 0) {
            double zOut = 0.30 - 0.11 * cassetteOut;
            r.box(-0.095, 0.575, zOut, 0.095, 0.625, zOut + 0.105, cassetteTex, 0xFFFFFFFF, 0, 1);
        }
        // the set
        tvBox(-0.36, 0.68, 0.30, 0.36, 1.20, 0.78, tvBrown, 0xFFFFFFFF, 0);
        tvBox(-0.36, 0.68, 0.296, 0.36, 0.70, 0.30, tvBevel, 0xFF888888, 0);                    // bevels round the front
        tvBox(-0.36, 1.18, 0.296, 0.36, 1.20, 0.30, tvBevel, 0xFF888888, 0);
        tvBox(-0.36, 0.70, 0.296, -0.34, 1.18, 0.30, tvBevel, 0xFF888888, 0);
        tvBox(0.34, 0.70, 0.296, 0.36, 1.18, 0.30, tvBevel, 0xFF888888, 0);
        double zf = 0.2985;
        double dead = tvDead;
        paintScreen(t0, (0.55 + 0.45 * flicker) * (1 - dead), dead, cracks);
        tvQuad(new double[][]{{-0.30, 1.14, zf}, {0.17, 1.14, zf}, {0.17, 0.74, zf}, {-0.30, 0.74, zf}}, screenTex, 0xFFFFFFFF, dead >= 0.99 ? 0.02 : 1.0);
        cracks(cracks);
        tvQuad(new double[][]{{-0.30, 1.14, zf - 0.001}, {0.17, 1.14, zf - 0.001}, {0.17, 0.74, zf - 0.001}, {-0.30, 0.74, zf - 0.001}}, crackTex, 0xFFFFFFFF, 0.9);
        // the control strip
        for (int i = 0; i < 9; i++) tvBox(0.215, 1.12 - i * 0.026, 0.296, 0.325, 1.132 - i * 0.026, 0.30, black, 0xFFFFFFFF, 0);
        tvBox(0.235, 0.92, 0.292, 0.305, 0.99, 0.30, tvBevel, 0xFFAAAAAA, 0);                  // the big knob
        tvBox(0.24, 0.82, 0.294, 0.30, 0.88, 0.30, tvBevel, 0xFF999999, 0);                    // the small one
        for (int i = 0; i < 4; i++) tvBox(0.215 + i * 0.028, 0.74, 0.295, 0.235 + i * 0.028, 0.77, 0.30, tvBevel, 0xFF888888, 0);
        tvBox(0.30, 0.715, 0.294, 0.32, 0.73, 0.30, ledRed, 0xFFFFFFFF, dead >= 0.99 ? 0.05 : 0.9);
    }

    private double[] smashFocus;   // where the hands hold the tape
    private double t0;      // the track time of the frame being drawn (the set's picture moves with it)

    // ------------------------------------------------------------------ the first beat: he breaks the set, then the tape

    private static final double FIRST_STRIKE = 5.4, STRIKE_GAP = 0.66;
    private static final int STRIKES = 7;
    private static final double FALL_A = 9.7, FALL_B = 10.45, PULL_A = 10.45, PULL_B = 10.95;
    private static final int FIG_TINT = 0xFF746E68;

    /** How far a swinging arm is turned at {@code v} seconds from the moment of impact: back, a hard drive forward, a slow recovery. */
    private static double swing(double v) {
        if (v < -0.24) return 0.18;
        if (v < 0) return lerp(0.18, 1.25, smooth(v, -0.24, 0.0));
        if (v < 0.09) return lerp(1.25, -0.80, smooth(v, 0.0, 0.09));
        if (v < 0.30) return lerp(-0.80, 0.18, smooth(v, 0.09, 0.30));
        return 0.18;
    }

    /** A slow, never-repeating drift, for a camera held in the hands. */
    private static double hand(double t, double seed, double amp) {
        return amp * (0.6 * Math.sin(t * 0.9 + seed) + 0.3 * Math.sin(t * 2.3 + seed * 2.1) + 0.1 * Math.sin(t * 5.7 + seed * 3.7));
    }

    /** The world position of the end of an arm turned by {@code pitch}, for a figure standing at (x, z) facing +z . */
    private double[] handPos(boolean right, double pitch, double x, double z, double scale, double face) {
        double mx = right ? -5.5 : 5.5;
        double ly = -10.5;
        double lx = mx * scale, lz = ly * Math.sin(pitch) * scale;                 // in the figure's own frame: x to its right, z ahead
        double cf = Math.cos(face), sf = Math.sin(face);
        return new double[]{x + lx * cf + lz * sf, (22 + ly * Math.cos(pitch)) * scale, z - lx * sf + lz * cf};
    }

    /** A world point as a stage point (skin pixels) of a body standing at {@code org} turned {@code face}. */
    private static double[] worldToStage(double[] w, double[] org, double face) {
        double cf = Math.cos(face), sf = Math.sin(face);
        double dx = w[0] - org[0], dy = w[1] - org[1], dz = w[2] - org[2];
        return new double[]{(cf * dx - sf * dz) / SkinActor.PX, dy / SkinActor.PX, (sf * dx + cf * dz) / SkinActor.PX};
    }

    /** A knife in the fist of a posed body: a handle through the fingers, a guard, a blade along the front of the fist. */
    private void knifeInHand(Actor15.Hand h, double[] org, double face, double bladePx) {
        double[] x = h.x(), y = h.y(), z = h.z();
        double[] fist = {h.wrist()[0] - y[0] * 1.7, h.wrist()[1] - y[1] * 1.7, h.wrist()[2] - y[2] * 1.7};
        java.util.function.BiFunction<Double, double[], double[]> at = (k, dir) -> SkinActor.toWorld(new double[]{fist[0] + dir[0] * k, fist[1] + dir[1] * k, fist[2] + dir[2] * k}, org, face, SkinActor.PX);
        double[] wx = SkinActor.dirToWorld(x, face), wy = SkinActor.dirToWorld(y, face);
        double px = SkinActor.PX;
        r.matSpec = 0.15; r.matShine = 8;
        r.bar(at.apply(-2.3, z), at.apply(2.0, z), scaled(wx, 0.42 * px), scaled(wy, 0.55 * px), grip, 0xFFFFFFFF, 0);
        r.matSpec = 0.8; r.matShine = 50;
        r.bar(at.apply(2.0, z), at.apply(2.35, z), scaled(wx, 0.45 * px), scaled(wy, 1.7 * px), steel, 0xFFD0C8B0, 0);
        r.matSpec = 1.0; r.matShine = 70;
        r.bar(at.apply(2.35, z), at.apply(2.35 + bladePx * 0.84, z), scaled(wx, 0.2 * px), scaled(wy, 0.95 * px), blade, 0xFFFFFFFF, 0);
        r.bar(at.apply(2.35 + bladePx * 0.84, z), at.apply(2.35 + bladePx, z), scaled(wx, 0.08 * px), scaled(wy, 0.2 * px), blade, 0xFFDDDDDD, 0);
        r.matSpec = 0;
    }

    private void smash(double t) {
        t0 = t;
        double p = pulse(t);
        Random fl = new Random((long) (t * 24));
        double u = t - SMASH_START;
        double fall = smooth(t, FALL_A, FALL_B);
        tvFall = fall;
        tvDead = smooth(t, FALL_A + 0.2, FALL_B);
        int hits = 0;
        for (int k = 0; k < STRIKES; k++) if (t >= FIRST_STRIKE + k * STRIKE_GAP) hits = k + 1;
        double flicker = (0.78 + 0.22 * fl.nextDouble() + 0.35 * p) * (1 - tvDead);
        int kNear = (int) Math.max(0, Math.min(STRIKES - 1, Math.round((t - FIRST_STRIKE) / STRIKE_GAP)));
        double v = t - (FIRST_STRIKE + kNear * STRIKE_GAP);
        boolean striking = t < FALL_A + 0.1;
        double impact = striking && v >= 0 && v < 0.3 ? Math.exp(-v * 22) : 0;
        tvShakeX = (fl.nextDouble() - 0.5) * 0.02 * impact;
        tvShakeY = (fl.nextDouble() - 0.5) * 0.02 * impact;
        double sh = hits > 0 ? 1 : 0;

        r.clear(0x050505);
        r.ambR = 0.15; r.ambG = 0.15; r.ambB = 0.17;
        r.fogDensity = 0.10;
        double lit = flicker * (1 + 1.6 * impact);
        r.lights.add(new Soft3D.Light(0, 0.95, 0.15, 1.15 * lit, 1.30 * lit, 1.40 * lit, 7.0));
        r.shadowDir = new double[]{0, -0.05, -1};
        r.lights.add(new Soft3D.Light(-1.7, 2.1, -0.5, 1.45, 1.12, 0.75, 6.0));
        r.lights.add(new Soft3D.Light(1.4, 1.7, -1.5, 0.55, 0.62, 0.78, 5.0));          // a cold fill from the door side, so the figure reads
        fxBloom = 0.9; fxSsao = 0.7; fxRays = 0.28; fxRayAt = new double[]{-1.7, 2.3, -0.5};
        fxContrast = 1.14; fxSat = 0.68; fxDofFocus = 0;

        // the figure: stands at the cabinet; strikes alternate hands; after the fall he leans in to the deck and takes the tape
        double step = smooth(t, PULL_A - 0.2, PULL_B);
        double fz = -0.12 + 0.12 * step;
        double fx = -0.42 * (1 - step * 0.6);
        boolean rightHits = kNear % 2 == 0;
        double sw = striking ? swing(v) : 0.18;
        double face = Math.atan2(0.0 - fx, 0.30 - fz) * 0.85;
        double[] org = {fx, 0, fz};
        actor.reset();
        // the feet planted, a little apart, the near one forward
        actor.placeFoot(true, new double[]{-3.4, 3, 0.8}, new double[]{0, 0, 1}, 1, 0, 0).placeFoot(false, new double[]{3.4, 3, -1.8}, new double[]{0, 0, 1}, 1, 0, 0);
        double[] tvStage = worldToStage(new double[]{0.0, 0.92, 0.30}, org, face);
        double[] tipR, tipL;
        if (striking) {
            double wind = clamp01((sw - 0.18) / 1.07), hit = clamp01((0.18 - sw) / 0.98);
            double side = rightHits ? -1 : 1;
            double[] rest = {side * 5.5, 13.5, 3.5}, windUp = {side * 7.5, 28.0, -3.5}, struck = {tvStage[0] + side * 1.3, tvStage[1], tvStage[2] + 0.5};
            double[] hitTip = lerp3(lerp3(rest, windUp, wind), struck, hit);
            double[] guard = {-side * 5.0, 17.0, 6.5};
            tipR = rightHits ? hitTip : guard;
            tipL = rightHits ? guard : hitTip;
            actor.reachArm(true, tipR, new double[]{-1, -0.5, -0.6}, 1).reachArm(false, tipL, new double[]{1, -0.5, -0.6}, 1);
            actor.hand(true, 1, 0).hand(false, 1, 0);
            actor.turn(Actor15.Part.LOWER_TORSO, impact * 9 + 3, side * (-9) * wind, 0);
            actor.lookAt(tvStage, 0.9);
        } else {
            double reach = smooth(t, PULL_A, PULL_B);
            double[] slot = worldToStage(new double[]{0.0, 0.60, 0.27}, org, face);
            double[] chest = {0, 19.5, 7.5};
            double[] mid = lerp3(new double[]{0, 14, 4}, slot, reach);
            if (t >= PULL_B) mid = lerp3(slot, chest, smooth(t, PULL_B, PULL_B + 0.45));
            double apart = t >= CASSETTE_SNAP ? smooth(t, CASSETTE_SNAP, CASSETTE_SNAP + 0.25) * 3.6 : 0;
            tipR = new double[]{mid[0] - 1.0 - apart, mid[1], mid[2]};
            tipL = new double[]{mid[0] + 1.0 + apart, mid[1], mid[2]};
            actor.reachArm(true, tipR, new double[]{-1, -0.7, -0.3}, 1).reachArm(false, tipL, new double[]{1, -0.7, -0.3}, 1);
            actor.hand(true, 0.55, 0.1).hand(false, 0.55, 0.1);
            actor.turn(Actor15.Part.LOWER_TORSO, 8 * reach, 0, 0);
            actor.lookAt(mid, 0.8);
        }
        actor.solve();
        r.matSpec = 0.08; r.matShine = 10; r.matBump = 0.4; r.matWrap = 0.45;
        SkinActor.draw(r, actor, skin, org, face, SkinActor.PX, FIG_TINT, 0);
        r.matSpec = 0; r.matBump = 0; r.matWrap = 0;

        // the room, the chair, the set
        room(t);
        chair(-1.35, 0.15);
        double cassetteOut = t < PULL_A - 0.35 ? 0 : smooth(t, PULL_A - 0.35, PULL_A + 0.1);
        boolean cassetteInHand = t >= PULL_B - 0.1;
        tvSet(flicker, hits, cassetteInHand ? 0 : cassetteOut);
        // sparks off every blow
        if (impact > 0.05) {
            Random sp = new Random(kNear * 31L + 5);
            Soft3D.Tex spark = Soft3D.Tex.solid(255, 214, 140);
            for (int i = 0; i < 12; i++) {
                double a = sp.nextDouble() * 6.28, sp2 = 0.3 + sp.nextDouble() * 0.9;
                double age = v;
                r.billboard(rightHits ? 0.1 : -0.1 + Math.cos(a) * sp2 * age, 0.95 + Math.sin(a) * sp2 * age - 2.5 * age * age, 0.28 - sp.nextDouble() * 0.4 * age,
                        0.006, 0.006, spark, 0xFFFFFFFF, 1.0);
            }
        }
        // the tape in his hands, then broken
        if (cassetteInHand) {
            double[] hr = SkinActor.toWorld(actor.fistTip(true), org, face, SkinActor.PX), hl = SkinActor.toWorld(actor.fistTip(false), org, face, SkinActor.PX);
            double[] mid = {(hr[0] + hl[0]) / 2, (hr[1] + hl[1]) / 2 + 0.03, (hr[2] + hl[2]) / 2 + 0.03};
            smashFocus = new double[]{mid[0], mid[1], mid[2]};
            r.lights.add(new Soft3D.Light(mid[0] + 0.25, mid[1] + 0.25, mid[2] - 0.35, 0.95, 0.95, 1.05, 2.2));      // the tape catches the light
            double snap = smooth(t, CASSETTE_SNAP, CASSETTE_SNAP + 0.25);
            double drop = Math.max(0, t - (CASSETTE_SNAP + 0.5));
            double fallY = Math.min(mid[1] - 0.02, 0.5 * 5.5 * drop * drop);
            for (int half = -1; half <= 1; half += 2) {
                double off = half * (0.0475 + 0.12 * snap);
                double tilt = half * 0.9 * snap;
                double dropHalf = t > CASSETTE_SNAP + 0.5 ? fallY * (0.9 + 0.1 * half) : 0;
                double[] c = {mid[0] + off, Math.max(0.03, mid[1] - dropHalf), mid[2]};
                double[] uv = {0, Math.cos(tilt), Math.sin(tilt)};
                double[] a1 = {c[0] + uv[0] * 0.052, c[1] + uv[1] * 0.052, c[2] + uv[2] * 0.052};
                double[] b1 = {c[0] - uv[0] * 0.052, c[1] - uv[1] * 0.052, c[2] - uv[2] * 0.052};
                r.bar(a1, b1, new double[]{0.0475, 0, 0}, new double[]{0, -Math.sin(tilt) * 0.015, Math.cos(tilt) * 0.015}, cassetteTex, 0xFFFFFFFF, 0);
            }
        }
        // the camera: held in the hands, from the side of him, drifting round behind his shoulder and in
        double orbit = lerp(0.55, 1.0, smooth(t, 5, 9.6)) + 0.30 * smooth(t, 9.8, 11) - 0.25 * smooth(t, 11.5, 14.5);
        double rad = lerp(2.1, 2.3, smooth(t, 9.4, 10.8));
        double ty = lerp(1.0, 0.95, smooth(t, 10, 11));
        double[] tgt = {lerp(-0.15, -0.05, smooth(t, 9.4, 10.8)), ty, 0.1};
        double kick = impact * 0.04;
        double cx = tgt[0] + Math.sin(orbit) * rad + hand(t, 1, 0.03) + fl.nextGaussian() * kick;
        double cy = lerp(1.5, 1.2, smooth(t, 9.4, 11)) + hand(t, 2, 0.025) + fl.nextGaussian() * kick;
        double cz = tgt[2] - Math.cos(orbit) * rad + hand(t, 3, 0.03);
        double yaw = Math.atan2(tgt[0] - cx, tgt[2] - cz) + hand(t, 4, 0.012);
        double pitch = Math.atan2(tgt[1] - cy, Math.hypot(tgt[0] - cx, tgt[2] - cz)) + hand(t, 5, 0.01);
        r.camera(cx, cy, cz, yaw, pitch);
        if (DEBUG_ARMS) System.out.printf("t=%.2f cam=(%.2f %.2f %.2f) yaw=%.2f pitch=%.2f face=%.2f fig=(%.2f %.2f)%n", t, cx, cy, cz, yaw, pitch, face, fx, fz);
        if (t > CASSETTE_SNAP + 1.8) fade = smooth(t, SMASH_END - 1.6, SMASH_END);
    }

    // ------------------------------------------------------------------ the cuts, and the memories they bring

    /** The cut that brings a memory: the arm, the knife drawn across it once more; the marks of the ones before stay. */
    private void cutBeat(int seg, double local, double t) {
        double st = Ending13Rig.strokeStart(seg), ln = Ending13Rig.strokeLength(seg);
        double ct = st - 0.8 + local / CUT_LEN * (ln + 1.4);
        armAt(ct, Math.min(1, ct / 14.0), t);
        kickFlash = Math.max(kickFlash, 0.25 * glitch);
    }

    /** 1:51: he cuts and cuts, one after another, while the track stutters. */
    private void cuttingFirstPerson(double t) {
        double u = (t - CUTTING) / (ARCHITECTS - CUTTING);
        double ct = lerp(Ending13Rig.strokeStart(5) - 0.5, Ending13Rig.strokeStart(5) + 2.5, u);
        armAt(ct, 1.0, t);
        kickFlash = Math.max(kickFlash, 0.4 * glitch);
    }

    // ------------------------------------------------------------------ the Architects, in the room

    private static final String[] LIGHT_TEXT = {"PROCEED.", "STOP!", "Talk to me.", "Your route has gone beyond the LIMITS.",
            "Your fun even goes beyond the bounds of HARDCORE.", "STOP GIVING IN TO RAIDEN'S MANIPULATIONS."};
    private static final double[][] LIGHT_RGB = {{0.75, 0.45, 1.0}, {1.0, 0.8, 0.4}, {1.0, 0.25, 0.25}, {0.45, 0.85, 1.0}, {1.0, 1.0, 1.0}, {0.8, 0.4, 1.0}};
    private final Soft3D.Tex[] lightTex = new Soft3D.Tex[6];
    private double lightsT;
    private double stabU = -1;          // seconds into the blow, or -1

    private Soft3D.Tex lightShape(int idx) {
        if (lightTex[idx] != null) return lightTex[idx];
        final int S = 128;
        Soft3D.Tex tx = paint(S, S, g2 -> {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            switch (idx) {
                case 0 -> {                                                                       // Greinert: a cross, half red and half blue, with a green dot
                    int a = 14, l = 56;
                    g2.setColor(new Color(235, 60, 70)); g2.fillRect(S / 2 - l, S / 2 - a, l, 2 * a); g2.fillRect(S / 2 - a, S / 2 - l, a, 2 * l);
                    g2.setColor(new Color(70, 120, 255)); g2.fillRect(S / 2, S / 2 - a, l, 2 * a); g2.fillRect(S / 2, S / 2 - l, a, 2 * l);
                    g2.setColor(new Color(70, 255, 110)); g2.fillOval(S / 2 - 9, S / 2 - 9, 18, 18);
                }
                case 1 -> {                                                                       // Starlight: a gold star
                    g2.setColor(new Color(255, 205, 100));
                    g2.fillOval(S / 2 - 26, S / 2 - 26, 52, 52);
                    for (int k = 0; k < 8; k++) {
                        double an = k * Math.PI / 4, w = k % 2 == 0 ? 7 : 3, len = k % 2 == 0 ? 60 : 42;
                        g2.fillPolygon(new int[]{(int) (S / 2 + Math.cos(an + 1.57) * w), (int) (S / 2 + Math.cos(an) * len), (int) (S / 2 - Math.cos(an + 1.57) * w)},
                                new int[]{(int) (S / 2 + Math.sin(an + 1.57) * w), (int) (S / 2 + Math.sin(an) * len), (int) (S / 2 - Math.sin(an + 1.57) * w)}, 3);
                    }
                    g2.setColor(new Color(255, 250, 220)); g2.fillOval(S / 2 - 14, S / 2 - 14, 28, 28);
                }
                case 2 -> {                                                                       // Mischievous: a red diamond with a spiral
                    g2.setColor(new Color(255, 70, 70));
                    g2.fillPolygon(new int[]{S / 2, S / 2 + 48, S / 2, S / 2 - 48}, new int[]{S / 2 - 48, S / 2, S / 2 + 48, S / 2}, 4);
                    g2.setColor(new Color(120, 10, 16)); g2.setStroke(new BasicStroke(5f));
                    int px = S / 2, py = S / 2;
                    for (int k = 0; k < 60; k++) {
                        double an = k * 0.5, rr = 3 + k * 0.55, an2 = (k + 1) * 0.5, rr2 = 3 + (k + 1) * 0.55;
                        g2.drawLine((int) (S / 2 + Math.cos(an) * rr * 0.8), (int) (S / 2 + Math.sin(an) * rr * 0.8), (int) (S / 2 + Math.cos(an2) * rr2 * 0.8), (int) (S / 2 + Math.sin(an2) * rr2 * 0.8));
                    }
                }
                case 3 -> {                                                                       // Moonlight: a blue crescent
                    g2.setColor(new Color(150, 225, 255)); g2.fillOval(S / 2 - 46, S / 2 - 46, 92, 92);
                    g2.setComposite(java.awt.AlphaComposite.Clear); g2.fillOval(S / 2 - 22, S / 2 - 52, 88, 88);
                }
                case 4 -> {                                                                       // Chromo: a four-pointed star, rainbow on one side, a black-and-white check on the other
                    for (int k = 0; k < 4; k++) {
                        double an = k * Math.PI / 2 - Math.PI / 2;
                        int[] xs = {(int) (S / 2 + Math.cos(an) * 60), (int) (S / 2 + Math.cos(an + 0.7) * 12), (int) (S / 2 + Math.cos(an - 0.7) * 12)};
                        int[] ys = {(int) (S / 2 + Math.sin(an) * 60), (int) (S / 2 + Math.sin(an + 0.7) * 12), (int) (S / 2 + Math.sin(an - 0.7) * 12)};
                        g2.setColor(k == 0 || k == 3 ? Color.getHSBColor(k * 0.3f, 0.8f, 1f) : (k == 1 ? new Color(245, 245, 245) : new Color(60, 60, 60)));
                        g2.fillPolygon(xs, ys, 3);
                    }
                    for (int i = 0; i < 6; i++) for (int j = 0; j < 6; j++) {
                        g2.setColor((i + j) % 2 == 0 ? new Color(250, 250, 250) : new Color(20, 20, 20));
                        if (i >= 3) g2.fillRect(S / 2 + (i - 3) * 6, S / 2 - 18 + j * 6, 6, 6);
                        else { g2.setColor(Color.getHSBColor((i * 6 + j) / 36f, 0.7f, 1f)); g2.fillRect(S / 2 - 18 + i * 6, S / 2 - 18 + j * 6, 6, 6); }
                    }
                }
                default -> {                                                                      // Glitch: a three-pointed star among stray squares
                    g2.setColor(new Color(205, 110, 255)); g2.setStroke(new BasicStroke(12f));
                    for (int k = 0; k < 3; k++) {
                        double an = -Math.PI / 2 + k * Math.PI * 2 / 3;
                        g2.drawLine(S / 2, S / 2, (int) (S / 2 + Math.cos(an) * 54), (int) (S / 2 + Math.sin(an) * 54));
                    }
                    Random q = new Random(77);
                    for (int k = 0; k < 18; k++) {
                        g2.setColor(new Color(60 + q.nextInt(195), 60 + q.nextInt(195), 60 + q.nextInt(195)));
                        g2.fillRect(q.nextInt(S - 8), q.nextInt(S - 8), 5 + q.nextInt(5), 5 + q.nextInt(5));
                    }
                }
            }
        });
        // the transparent pixels must be fully transparent for the cut-out
        for (int i = 0; i < tx.px.length; i++) if (((tx.px[i] >> 24) & 255) < 128) tx.px[i] = 0;
        lightTex[idx] = tx;
        return tx;
    }

    /** The world position of the end of an arm of a figure at (x, z) facing {@code face}, the arm turned by pitch and yaw. */
    private double[] armTip(boolean right, double pitch, double yaw, double ox, double oz, double face, double sc) {
        double ly = -11, y1 = ly * Math.cos(pitch), z1 = ly * Math.sin(pitch);
        double bx = (right ? -5.5 : 5.5) + z1 * Math.sin(yaw), bz = z1 * Math.cos(yaw);
        double cf = Math.cos(face), sf = Math.sin(face);
        return new double[]{ox + (cf * bx + sf * bz) * sc, (22 + y1) * sc, oz + (-sf * bx + cf * bz) * sc};
    }

    // ------------------------------------------------------------------ atmosphere: soft sprites, cones of light, dust, mist

    private Soft3D.Tex softDotTex, coneTex, mistTexA;

    /** A round soft dot: opaque in the middle, nothing at the rim. */
    private Soft3D.Tex softDot() {
        if (softDotTex != null) return softDotTex;
        softDotTex = new Soft3D.Tex(64, 64);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
            double d = Math.hypot(x - 31.5, y - 31.5) / 32.0;
            double a = Math.pow(Math.max(0, 1 - d), 2.2);
            softDotTex.px[y * 64 + x] = ((int) (255 * a) << 24) | 0xFFFFFF;
        }
        return softDotTex;
    }

    /** Along a cone: bright at the apex, thinning out towards its base. */
    private Soft3D.Tex cone() {
        if (coneTex != null) return coneTex;
        coneTex = new Soft3D.Tex(4, 64);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 4; x++) {
            double v = y / 63.0;
            double a = Math.pow(1 - v, 1.1) * smooth(v, 0.0, 0.06);
            coneTex.px[y * 4 + x] = ((int) (255 * a) << 24) | 0xFFFFFF;
        }
        return coneTex;
    }

    /** Drifting cloud, tileable: many soft blobs of different weight. */
    private Soft3D.Tex mistCloud() {
        if (mistTexA != null) return mistTexA;
        final int S = 128;
        float[] a = new float[S * S];
        Random q = new Random(404);
        for (int k = 0; k < 90; k++) {
            double cx = q.nextDouble() * S, cy = q.nextDouble() * S, rad = 10 + q.nextDouble() * 26, w = 0.25 + q.nextDouble() * 0.75;
            for (int oy = -1; oy <= 1; oy++) for (int ox = -1; ox <= 1; ox++) {
                double bx = cx + ox * S, by = cy + oy * S;
                int x0 = (int) Math.max(0, bx - rad), x1 = (int) Math.min(S - 1, bx + rad), y0 = (int) Math.max(0, by - rad), y1 = (int) Math.min(S - 1, by + rad);
                for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
                    double d = Math.hypot(x - bx, y - by) / rad;
                    if (d < 1) a[y * S + x] += (float) (w * (1 - d) * (1 - d));
                }
            }
        }
        float mx = 0;
        for (float v : a) mx = Math.max(mx, v);
        mistTexA = new Soft3D.Tex(S, S);
        for (int i = 0; i < a.length; i++) mistTexA.px[i] = ((int) (255 * Math.min(1, a[i] / mx * 1.25)) << 24) | 0xFFFFFF;
        return mistTexA;
    }

    /** A soft glowing sprite facing the camera: a bulb's halo, a spark, a mote. */
    private void sprite(double x, double y, double z, double hw, double hh, int tint, double alpha, double emissive) {
        r.matBlend = true; r.matAlpha = alpha;
        r.billboard(x, y, z, hw, hh, softDot(), tint, emissive);
        r.matBlend = false; r.matAlpha = 1;
    }

    /** A cone of light, as through dust: from an apex to a base, translucent, brighter near the apex. */
    private void lightCone(double[] apex, double[] base, double rApex, double rBase, int tint, double alpha, int sides) {
        double[] ax = {base[0] - apex[0], base[1] - apex[1], base[2] - apex[2]};
        double al = Math.sqrt(ax[0] * ax[0] + ax[1] * ax[1] + ax[2] * ax[2]) + 1e-9;
        for (int k = 0; k < 3; k++) ax[k] /= al;
        double[] ref = Math.abs(ax[1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
        double[] e1 = cross(ax, ref);
        double l1 = Math.sqrt(e1[0] * e1[0] + e1[1] * e1[1] + e1[2] * e1[2]) + 1e-9;
        for (int k = 0; k < 3; k++) e1[k] /= l1;
        double[] e2 = cross(ax, e1);
        r.matBlend = true; r.matAlpha = alpha * 0.45;
        for (int layer = 0; layer < 3; layer++) for (int i = 0; i < sides; i++) {
            double lk = 1.0 - 0.30 * layer;
            double a0 = i * 2 * Math.PI / sides, a1 = (i + 1) * 2 * Math.PI / sides;
            double[] p0 = new double[3], p1 = new double[3], q0 = new double[3], q1 = new double[3];
            for (int k = 0; k < 3; k++) {
                double d0 = Math.cos(a0) * e1[k] + Math.sin(a0) * e2[k], d1 = Math.cos(a1) * e1[k] + Math.sin(a1) * e2[k];
                p0[k] = apex[k] + d0 * rApex * lk; p1[k] = apex[k] + d1 * rApex * lk;
                q0[k] = base[k] + d0 * rBase * lk; q1[k] = base[k] + d1 * rBase * lk;
            }
            r.quad(new double[][]{p0, p1, q1, q0}, new double[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}}, cone(), tint, 1.0);
        }
        r.matBlend = false; r.matAlpha = 1;
    }

    /** Specks of dust hanging and drifting in a box of air; they twinkle where they catch the light. */
    private void motes(double[] c, double rx, double ry, double rz, int n, double t, long seed, int tint, double size, double alpha) {
        Random q = new Random(seed);
        for (int i = 0; i < n; i++) {
            double x = c[0] + (q.nextDouble() * 2 - 1) * rx, y = c[1] + (q.nextDouble() * 2 - 1) * ry, z = c[2] + (q.nextDouble() * 2 - 1) * rz;
            double ph = q.nextDouble() * 6.28, sp = 0.15 + q.nextDouble() * 0.3;
            x += Math.sin(t * sp + ph) * 0.18; y += Math.sin(t * sp * 0.7 + ph * 2) * 0.12 - ((t * 0.02 * sp + q.nextDouble()) % 1.0) * 0.25; z += Math.cos(t * sp + ph) * 0.18;
            double tw = 0.45 + 0.55 * Math.max(0, Math.sin(t * (1.5 + sp * 3) + ph));
            sprite(x, y, z, size * (0.6 + q.nextDouble() * 0.8), size * (0.6 + q.nextDouble() * 0.8), tint, alpha * tw, 1.0);
        }
    }

    /** A flat layer of drifting mist at a height: big, faint, scrolling slowly; layer a few for depth. */
    private void mistLayer(double y, double half, double cx, double cz, int tint, double alpha, double t, double speed, double tiles) {
        double off = t * speed;
        r.matBlend = true; r.matAlpha = alpha;
        r.quad(new double[][]{{cx - half, y, cz - half}, {cx + half, y, cz - half}, {cx + half, y, cz + half}, {cx - half, y, cz + half}},
                new double[][]{{off, off * 0.6}, {off + tiles, off * 0.6}, {off + tiles, off * 0.6 + tiles}, {off, off * 0.6 + tiles}}, mistCloud(), tint, 0.55);
        r.matBlend = false; r.matAlpha = 1;
    }

    private static double[] polar(double a, double rad, double y) {
        return new double[]{Math.cos(a) * rad, y, -1.0 + Math.sin(a) * rad};
    }

    private static final double[][] UV1 = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

    private void ringQuad(double a0, double a1, double rIn, double yIn, double rOut, double yOut, Soft3D.Tex tex, int tint, double em, double su, double sv) {
        r.quad(new double[][]{polar(a0, rIn, yIn), polar(a1, rIn, yIn), polar(a1, rOut, yOut), polar(a0, rOut, yOut)}, new double[][]{{0, 0}, {su, 0}, {su, sv}, {0, sv}}, tex, tint, em);
    }

    /**
     * The chamber of PQ-001: a round hall under a dome. A ring of columns round the wall, a balcony over them with a rail, slits of window high
     * in the dark upper wall, ribs up the dome to an eye at the top, lamps on chains, a low dais in the middle with rings drawn round it.
     */
    private void chamber(double t, double glowRed, double glowBlue) {
        final int N = 28;
        final double R = 11.5, wallH = 8.0, bal = 4.2;
        double step = 2 * Math.PI / N;
        Soft3D.Tex pale = Soft3D.Tex.solid(214, 226, 240);
        // the floor: plates, in rings
        double[] fr = {0, 3.2, 7.0, R};
        r.matSpec = 0.35; r.matShine = 30; r.matBump = 0.8;
        for (int k = 0; k < N; k++) {
            double a0 = k * step, a1 = (k + 1) * step;
            for (int j = 0; j < 3; j++) ringQuad(a0, a1, fr[j], 0, fr[j + 1], 0, floor, 0xFFB0B0B0, 0, 2.2, 2.2 + j);
        }
        r.matSpec = 0; r.matBump = 0.7;
        for (int k = 0; k < N; k++) {
            double a0 = k * step, a1 = (k + 1) * step;
            ringQuad(a0, a1, R, 0, R, bal, wall, 0xFFA0A0A0, 0, 1.6, 1.8);                            // the lower wall, concrete
            ringQuad(a0, a1, R, bal, R, wallH, wallLow, 0xFF707070, 0, 1.6, 1.6);                      // the upper wall, darker
            // the slits of window in the upper wall: pale, long, one to a bay
            double am = (k + 0.5) * step, dw = step * 0.22;
            r.quad(new double[][]{polar(am - dw, R - 0.02, 6.3), polar(am + dw, R - 0.02, 6.3), polar(am + dw, R - 0.02, 7.4), polar(am - dw, R - 0.02, 7.4)}, UV1, pale, 0xFFFFFFFF, 0.55);
        }
        r.matBump = 0;
        // the columns, with a hazard foot
        r.matSpec = 0.3; r.matShine = 24;
        for (int k = 0; k < N; k++) {
            double a = k * step;
            double[] b0 = polar(a, R - 0.35, 0), b1 = polar(a, R - 0.35, wallH);
            double[] tang = {-Math.sin(a) * 0.30, 0, Math.cos(a) * 0.30}, rad = {Math.cos(a) * 0.34, 0, Math.sin(a) * 0.34};
            r.bar(b0, b1, tang, rad, steel, 0xFF787C84, 0);
            r.bar(polar(a, R - 0.36, 0), polar(a, R - 0.36, 0.55), new double[]{tang[0] * 1.15, 0, tang[2] * 1.15}, new double[]{rad[0] * 1.15, 0, rad[2] * 1.15}, hazard, 0xFF8A8A8A, 0);
            // the balcony: a deck on a bracket and a rail with posts
            ringQuad(a, a + step, R - 1.3, bal, R - 0.05, bal, steel, 0xFF60646C, 0, 1, 1);
            r.bar(polar(a, R - 1.3, bal + 0.05), polar(a + step, R - 1.3, bal + 0.05), new double[]{0, 0.02, 0}, new double[]{0.02, 0, 0.02}, steel, 0xFF9A9EA6, 0);
            r.bar(polar(a, R - 1.3, bal + 1.0), polar(a + step, R - 1.3, bal + 1.0), new double[]{0, 0.025, 0}, new double[]{0.025, 0, 0.025}, steel, 0xFF9A9EA6, 0);
            r.bar(polar(a, R - 1.3, bal), polar(a, R - 1.3, bal + 1.0), new double[]{0.02, 0, 0}, new double[]{0, 0, 0.02}, steel, 0xFF9A9EA6, 0);
        }
        r.matSpec = 0;
        // the dome, in rings, with ribs, and an eye of pale light at the top
        double[] dr = {R, R * 0.86, R * 0.62, R * 0.36, R * 0.14}, dy = {wallH, wallH + 1.7, wallH + 3.1, wallH + 3.9, wallH + 4.2};
        for (int k = 0; k < N; k++) {
            double a0 = k * step, a1 = (k + 1) * step;
            for (int j = 0; j < 4; j++) ringQuad(a0, a1, dr[j], dy[j], dr[j + 1], dy[j + 1], wallLow, 0xFF606060, 0, 1.5, 1);
            if (k % 2 == 0) {
                r.matSpec = 0.3; r.matShine = 24;
                for (int j = 0; j < 4; j++) r.bar(polar(a0, dr[j] - 0.05, dy[j] - 0.05), polar(a0, dr[j + 1] - 0.05, dy[j + 1] - 0.05), new double[]{0.12, 0, 0}, new double[]{0, 0.12, 0}, steel, 0xFF4A4E56, 0);
                r.matSpec = 0;
            }
            r.quad(new double[][]{polar(a0, dr[4], dy[4]), polar(a1, dr[4], dy[4]), polar(0, 0, dy[4]), polar(0, 0, dy[4])}, UV1, pale, 0xFFFFFFFF, 0.8);
        }
        // the door they came by, in the wall at the near end, with its sign over it
        double dA = -Math.PI / 2, dw = 0.085;
        r.quad(new double[][]{polar(dA - dw, R - 0.04, 0), polar(dA + dw, R - 0.04, 0), polar(dA + dw, R - 0.04, 3.1), polar(dA - dw, R - 0.04, 3.1)}, UV1, Soft3D.Tex.solid(188, 214, 236), 0xFFFFFFFF, 0.85);
        r.quad(new double[][]{polar(dA - 0.11, R - 0.05, 3.5), polar(dA + 0.11, R - 0.05, 3.5), polar(dA + 0.11, R - 0.05, 4.1), polar(dA - 0.11, R - 0.05, 4.1)}, UV1, signTex(), 0xFFFFFFFF, 0.3);
        // lamps on chains, in a ring
        for (int k = 0; k < 10; k++) {
            double a = (k + 0.5) * 2 * Math.PI / 10;
            double[] top = polar(a, 7.2, wallH + 1.4), low = polar(a, 7.2, 6.0);
            r.bar(top, low, new double[]{0.012, 0, 0}, new double[]{0, 0, 0.012}, dark, 0xFFFFFFFF, 0);
            double[] lc = polar(a, 7.2, 5.9);
            r.box(lc[0] - 0.28, 5.78, lc[2] - 0.28, lc[0] + 0.28, 5.96, lc[2] + 0.28, pale, 0xFFFFFFFF, k % 3 == 0 ? 0.9 : 0.3, 1);
        }
        // the dais under the Architect: a ring of blocks, and the rings drawn on the floor in its two colours
        r.matSpec = 0.5; r.matShine = 40;
        for (int k = 0; k < 32; k++) {
            double a = k * 2 * Math.PI / 32;
            double px = Math.cos(a) * 3.25, pz = Math.sin(a) * 3.25 - 1.0;
            r.box(px - 0.2, 0, pz - 0.2, px + 0.2, 0.16, pz + 0.2, steel, 0xFF8A8E96, 0, 1);
        }
        r.matSpec = 0;
        for (int ring = 0; ring < 3; ring++) {
            double rad = 1.2 + ring * 1.05;
            int n = 56;
            for (int k = 0; k < n; k++) {
                double a = k * 2 * Math.PI / n, a2 = (k + 0.62) * 2 * Math.PI / n;
                boolean left = Math.cos(a) < 0;
                Soft3D.Tex col = left ? Soft3D.Tex.solid(235, 60, 70) : Soft3D.Tex.solid(70, 120, 255);
                double em = (left ? glowRed : glowBlue) * (0.55 + 0.45 * Math.sin(t * 1.4 + ring));
                r.quad(new double[][]{{Math.cos(a) * rad, 0.17, Math.sin(a) * rad - 1.0}, {Math.cos(a2) * rad, 0.17, Math.sin(a2) * rad - 1.0},
                        {Math.cos(a2) * (rad + 0.07), 0.17, Math.sin(a2) * (rad + 0.07) - 1.0}, {Math.cos(a) * (rad + 0.07), 0.17, Math.sin(a) * (rad + 0.07) - 1.0}},
                        new double[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}}, col, 0xFFFFFFFF, em);
            }
        }
        r.faceXZ(0.16, -3.2, -4.2, 3.2, 2.2, floor, 0xFF555560, 0, 3, 3);                           // the dais top (a square under the ring of rings)
        // radial seams of the floor plates
        Soft3D.Tex seam = Soft3D.Tex.solid(6, 6, 8);
        for (int k = 0; k < N; k += 2) {
            double a = k * step;
            r.quad(new double[][]{polar(a - 0.004, 3.6, 0.004), polar(a + 0.004, 3.6, 0.004), polar(a + 0.004, R, 0.004), polar(a - 0.004, R, 0.004)}, UV1, seam, 0xFFFFFFFF, 0);
        }
        // cables from the dome
        for (int c = 0; c < 9; c++) {
            double a = c * 0.7 + 0.2, rd = 6.0 + (c % 3) * 1.4;
            double[] top = polar(a, rd, wallH + 1.0), low = polar(a + 0.03 * Math.sin(t * 0.3 + c), rd + 0.2, 4.8);
            r.bar(top, low, new double[]{0.02, 0, 0}, new double[]{0, 0, 0.02}, dark, 0xFFFFFFFF, 0);
        }
        // the light comes down from the eye of the dome in a cone, there is dust in it, and mist lies low
        lightCone(polar(0, 0, wallH + 4.1), polar(0, 0, 0.18), 0.55, 3.6, 0xFFB8CCEE, 0.20, 24);
        lightCone(polar(0, 0, wallH + 4.1), polar(0, 0, 0.18), 0.30, 2.0, 0xFFE0ECFF, 0.18, 20);
        for (int k = 0; k < 10; k++) {                                                        // and a faint halo under each lamp of the ring
            double a = (k + 0.5) * 2 * Math.PI / 10;
            double[] lc = polar(a, 7.2, 5.9);
            sprite(lc[0], lc[1], lc[2], 0.9, 0.9, 0xFFC8DAF5, k % 3 == 0 ? 0.45 : 0.22, 1.0);
        }
        motes(new double[]{0, 3.2, -1.0}, 4.6, 3.0, 4.6, 140, t, 7, 0xFFEAF2FF, 0.022, 0.8);
        mistLayer(0.28, 12, 0, -1, 0xFFB0BEDA, 0.34, t, 0.012, 3.0);
        mistLayer(0.85, 12, 0, -1, 0xFF9AA8C8, 0.22, t, -0.008, 2.4);
    }

    private Soft3D.Tex signTexCache;

    /** The painted sign over the door of the chamber. */
    private Soft3D.Tex signTex() {
        if (signTexCache != null) return signTexCache;
        signTexCache = paint(256, 64, g2 -> {
            g2.setColor(new Color(20, 20, 22)); g2.fillRect(0, 0, 256, 64);
            g2.setColor(new Color(214, 176, 30));
            for (int i = -64; i < 256; i += 24) g2.fillPolygon(new int[]{i, i + 12, i + 12 + 14, i + 14}, new int[]{0, 0, 10, 10}, 4);
            for (int i = -64; i < 256; i += 24) g2.fillPolygon(new int[]{i, i + 12, i + 12 + 14, i + 14}, new int[]{54, 54, 64, 64}, 4);
            g2.setColor(new Color(225, 225, 215));
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
            g2.drawString("PQ-001", 62, 44);
            g2.setColor(new Color(190, 40, 40)); g2.fillRect(14, 22, 30, 6); g2.fillRect(212, 22, 30, 6);
        });
        return signTexCache;
    }

    private static final double[][] CHAMBER_LIGHT_POS = {{0, 3.2, -1.0}, {-3.4, 2.3, 0.2}, {3.4, 2.3, 0.2}, {-3.3, 3.9, 0.5}, {3.3, 3.9, 0.5}, {0, 1.95, -1.9}};

    /**
     * 1:55 on: the chamber of PQ-001, seen from a high corner like a camera: he runs in from the door and stops before the great last
     * Architect in the middle, and cuts his arm; the others come one by one into the air round it, each in its colour, each with its words
     * written over the picture.
     */
    private String lightsRoom(double t) {
        lightsT = t;
        t0 = t;
        double p = pulse(t);
        r.clear(0x030304);
        r.ambR = 0.17; r.ambG = 0.17; r.ambB = 0.21;
        r.fogDensity = 0.03; r.fogR = 5; r.fogG = 5; r.fogB = 8;
        fxRays = 0; fxRayAt = null;
        java.util.Arrays.fill(lightA, 0);
        double sc = 0.0568;
        double glowRed = 0.5 + 0.3 * p, glowBlue = 0.5 + 0.3 * p;
        double[] gl = new double[3];
        for (int i = 0; i < 6; i++) {
            double a = i == 0 ? 1.0 : smooth(t, LIGHT_AT[i], LIGHT_AT[i] + 0.9);        // the first is there as the door opens
            if (i == 0) a = smooth(t, LIGHT_AT[0] - 0.01, LIGHT_AT[0] + 0.6);
            if (a <= 0.01) continue;
            double[] pos = CHAMBER_LIGHT_POS[i];
            boolean speaking = t >= LIGHT_AT[i] && t < (i < 5 ? LIGHT_AT[i + 1] : STAB + 2.5);
            double throb = speaking ? 1 + 0.16 * p + 0.05 * Math.sin(t * 7 + i) : 0.88;
            double bob = Math.sin(t * 0.7 + i * 1.7) * 0.07;
            double size = (i == 0 ? 1.15 : i == 5 ? 0.95 : 0.85) * a * throb;
            double k = 1.0 * a * throb;
            r.lights.add(new Soft3D.Light(pos[0], pos[1] + bob, pos[2] - 0.3, LIGHT_RGB[i][0] * k, LIGHT_RGB[i][1] * k, LIGHT_RGB[i][2] * k, i == 0 ? 11.0 : 7.0));
            if (i == 0) r.shadowDir = null;
            lightA[i] = a * throb; lightSz[i] = size;
        }
        // the light of the Architects stains the air of the hall: a fog in their colours, and shafts from whoever speaks
        double fr = 0, fg = 0, fb = 0, strongest = 0;
        int lead = -1;
        for (int i = 0; i < 6; i++) {
            double w = lightA[i];
            fr += LIGHT_RGB[i][0] * w; fg += LIGHT_RGB[i][1] * w; fb += LIGHT_RGB[i][2] * w;
            if (w > strongest) { strongest = w; lead = i; }
        }
        r.fogR = 5 + 14 * fr; r.fogG = 5 + 14 * fg; r.fogB = 8 + 14 * fb;
        r.fogDensity = 0.028 + 0.018 * Math.min(1, fr + fg + fb);
        if (lead >= 0) {
            for (int i = 0; i < 6; i++) if (t >= LIGHT_AT[i] && t < (i < 5 ? LIGHT_AT[i + 1] : STAB + 2.5)) lead = i;
            double[] lp = CHAMBER_LIGHT_POS[lead];
            fxRays = 0.22 * Math.min(1, lightA[lead]); fxRayAt = new double[]{lp[0], lp[1], lp[2]};
            fxRayTint = new int[]{(int) (140 + 115 * LIGHT_RGB[lead][0]), (int) (140 + 115 * LIGHT_RGB[lead][1]), (int) (140 + 115 * LIGHT_RGB[lead][2])};
        }
        r.lights.add(new Soft3D.Light(0, 7.5, -1.0, 0.55, 0.57, 0.70, 16.0));                 // a dull light from the ceiling
        r.lights.add(new Soft3D.Light(0.8, 3.2, -4.8, 0.95, 1.0, 1.15, 6.5));                  // and a cold one over the way he came, so he can be seen
        double rays = fxRays; double[] rayAt = fxRayAt;
        fxBloom = 0.9 + 0.3 * p; fxSsao = 0.55; fxRays = rays; fxRayAt = rayAt; fxContrast = 1.12; fxSat = 0.9; fxDofFocus = 0;

        chamber(t, glowRed, glowBlue);

        // him: runs in from the door, stops before the Architect, and cuts his left arm with the right hand to the beat
        double run = smooth(t, LIGHT_AT[0], LIGHT_AT[0] + 4.0);
        double fz = lerp(-8.3, -2.7, run), fx = 0.0, face = 0.0;
        boolean running = run > 0.0 && run < 0.985;
        double[] org = {fx, 0, fz};
        actor.reset();
        double bounce = 0;
        if (running) {
            double ph = t * 11.0;
            double zr = 6.5 * Math.sin(ph), zl = -zr;
            double liftR = Math.max(0, Math.cos(ph)) * 4.0, liftL = Math.max(0, -Math.cos(ph)) * 4.0;
            actor.placeFoot(true, new double[]{-2.2, 3 + liftR, zr}, new double[]{0, 0, 1}, 1, 0, 0).placeFoot(false, new double[]{2.2, 3 + liftL, zl}, new double[]{0, 0, 1}, 1, 0, 0);
            actor.reachArm(true, new double[]{-5.2, 15.0 + Math.max(0, zl) * 0.2, zl * 0.8 + 2}, new double[]{-1, -0.3, -1}, 1);
            actor.reachArm(false, new double[]{5.2, 15.0 + Math.max(0, zr) * 0.2, zr * 0.8 + 2}, new double[]{1, -0.3, -1}, 1);
            actor.turn(Actor15.Part.LOWER_TORSO, 12, 0, 0).hand(true, 0.7, 0).hand(false, 0.7, 0);
            bounce = Math.abs(Math.sin(ph)) * 0.7;
            actor.move(0, -bounce, 0);
        } else {
            double saw = Math.sin(t * Math.PI * 4.0);
            double weight = smooth(t, 119, 150);
            double[] tipL = {3.2, 17.2, 10.8}, tipR = {-0.8 + 3.4 * saw, 18.6, 9.8};
            double[] chestAim = {-0.6, 17.0, 3.0};
            if (stabU >= 0) {                                            // the blow: the blade rises over his head, trembles, comes down into his chest
                double up = smooth(stabU, 0.0, 1.2), down = smooth(stabU, 1.45, 1.6);
                double[] raised = {-3.5, 36.0 + 0.5 * Math.sin(stabU * 40) * up, 2.0};
                tipR = lerp3(lerp3(tipR, raised, up), chestAim, down);
                tipL = lerp3(tipL, new double[]{6.0, 15.5, 6.0}, up);
            }
            actor.placeFoot(true, new double[]{-3.0, 3, -0.5}, new double[]{0, 0, 1}, 1, 0, 0).placeFoot(false, new double[]{3.0, 3, 1.0}, new double[]{0, 0, 1}, 1, 0, 0);
            actor.reachArm(true, tipR, new double[]{-1, -0.9, -0.2}, 1).reachArm(false, tipL, new double[]{1, -0.9, 0.2}, 1);
            actor.hand(true, 1.0, 0).hand(false, 0.12, 0.45);
            actor.turn(Actor15.Part.R_HAND, 62, 0, 0);
            actor.turn(Actor15.Part.LOWER_TORSO, 5 + 9 * weight + 1.5 * Math.sin(t * 1.3), 0, 0);
            actor.lookAt(stabU >= 0 ? new double[]{0, 14, 10} : new double[]{1.0, 14, 12}, 0.85);
        }
        actor.solve();
        r.matSpec = 0.08; r.matShine = 10; r.matBump = 0.4; r.matWrap = 0.45;
        SkinActor.draw(r, actor, skin, org, face, SkinActor.PX, FIG_TINT, 0);
        r.matSpec = 0; r.matBump = 0; r.matWrap = 0;
        if (!running) knifeInHand(actor.handOf(true), org, face, 4.6);

        // the camera: high in the corner by the door, looking down the hall at him and the Architect; it drifts, as a held camera does
        double a = lerp(-0.25, 0.45, smooth(t, 115, 151));
        double[] tgt = {0, 2.35, -1.6};
        double kick = p * 0.012;
        double cx = lerp(-3.4, 2.6, smooth(t, 115, 151)) + hand(t, 11, 0.05) + rnd.nextGaussian() * kick;
        double cy = 1.75 + hand(t, 12, 0.04) + rnd.nextGaussian() * kick;
        double cz = -6.9 + hand(t, 13, 0.05);
        double yaw = Math.atan2(tgt[0] - cx, tgt[2] - cz) + hand(t, 14, 0.012);
        double pitch = Math.atan2(tgt[1] - cy, Math.hypot(tgt[0] - cx, tgt[2] - cz)) + hand(t, 15, 0.01);
        r.camera(cx, cy, cz, yaw, pitch);
        if (DEBUG_ARMS) System.out.printf("lights t=%.2f cam=(%.2f %.2f %.2f) yaw=%.2f pitch=%.2f%n", t, cx, cy, cz, yaw, pitch);
        kickFlash = Math.max(kickFlash, 0.15 * glitch);
        tmOverlay = 0;
        stabU = -1;
        return "LIGHTS";
    }

    // ------------------------------------------------------------------ the Architects, drawn as light

    private final double[] lightA = new double[6], lightSz = new double[6];

    private static Color rgba(double r, double g, double b, double a) {
        return new Color(clamp255((int) r), clamp255((int) g), clamp255((int) b), clamp255((int) (255 * Math.max(0, Math.min(1, a)))));
    }

    /** A soft round glow: bright in the middle, fading out to nothing. */
    private BufferedImage radialImg;

    /** A round soft falloff as a picture: white, its alpha the glow's profile. */
    private BufferedImage radial() {
        if (radialImg != null) return radialImg;
        final int N = 128;
        radialImg = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            double d = Math.hypot(x - (N - 1) / 2.0, y - (N - 1) / 2.0) / (N / 2.0);
            double a = d >= 1 ? 0 : d < 0.18 ? 1 - (d / 0.18) * 0.45 : d < 0.5 ? 0.55 - (d - 0.18) / 0.32 * 0.39 : 0.16 * (1 - (d - 0.5) / 0.5);
            radialImg.setRGB(x, y, ((int) (255 * Math.max(0, a)) << 24) | 0xFFFFFF);
        }
        return radialImg;
    }

    private void softGlow(double cx, double cy, double radius, double r, double gr, double b, double a) {
        if (radius < 1) return;
        if (gpuMode) {                                              // the card draws the wide soft ones: far cheaper than filling them here
            double fx = outScaleX, fy = outScaleY;
            curFrame.addSprite(radial(), new double[]{(cx - radius) * fx, (cy - radius) * fy, (cx + radius) * fx, (cy - radius) * fy, (cx + radius) * fx, (cy + radius) * fy, (cx - radius) * fx, (cy + radius) * fy},
                    a, r / 255.0, gr / 255.0, b / 255.0);
            return;
        }
        java.awt.RadialGradientPaint paint = new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Double(cx, cy), (float) radius,
                new float[]{0f, 0.18f, 0.5f, 1f},
                new Color[]{rgba(r, gr, b, a), rgba(r, gr, b, a * 0.55), rgba(r, gr, b, a * 0.16), rgba(r, gr, b, 0)});
        g.setPaint(paint);
        g.fill(new java.awt.geom.Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));
    }

    /** Draws a shape three times, wide and faint to narrow and bright, so it glows. */
    private void glowStroke(java.awt.Shape s, double w, double r, double gr, double b, double a) {
        for (int k = 3; k >= 0; k--) {
            g.setStroke(new BasicStroke((float) (w * (1 + k * 1.1)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double aa = a * (k == 0 ? 1.0 : 0.22 / k);
            g.setColor(k == 0 ? rgba(Math.min(255, r + 70), Math.min(255, gr + 70), Math.min(255, b + 70), aa) : rgba(r, gr, b, aa));
            g.draw(s);
        }
    }

    // the references the Architects are made from: laid over the picture as light, so the black of them is nothing
    private static final String[] REF_NAME = {"gen", "star", "mischief", "moon", "gen", "glitch"};
    private static final double[][] REF_CENTER = {{200, 200}, {385, 126}, {343, 88}, {400, 125}, {200, 200}, {322, 125}};      // where the sign is in each file (after the crop)
    private final BufferedImage[] refImg = new BufferedImage[6];
    private double addGain = 1;

    /** Adds the source to what is under it, instead of laying over it: for light. */
    private static final java.awt.Composite ADD = (srcCM, dstCM, hints) -> new java.awt.CompositeContext() {
        @Override public void dispose() { }
        @Override public void compose(java.awt.image.Raster src, java.awt.image.Raster dstIn, java.awt.image.WritableRaster dstOut) {
            int w = Math.min(src.getWidth(), dstIn.getWidth()), h = Math.min(src.getHeight(), dstIn.getHeight());
            int sb = src.getNumBands(), db = dstIn.getNumBands();
            int[] s = new int[sb], d = new int[db];
            double gain = ADD_GAIN[0];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    src.getPixel(src.getMinX() + x, src.getMinY() + y, s);
                    dstIn.getPixel(dstIn.getMinX() + x, dstIn.getMinY() + y, d);
                    double al = sb > 3 ? s[3] / 255.0 : 1.0;
                    for (int b = 0; b < 3 && b < db; b++) d[b] = Math.min(255, d[b] + (int) (s[b] * al * gain));
                    dstOut.setPixel(dstOut.getMinX() + x, dstOut.getMinY() + y, d);
                }
            }
        }
    };
    private static final double[] ADD_GAIN = {1};

    /** A box blur in three passes (near a gaussian) over the colour channels of opaque pixels. */
    private static int[] blurRgb(int[] src, int w, int h, int rad) {
        float[][] ch = new float[3][w * h];
        for (int i = 0; i < src.length; i++) { ch[0][i] = (src[i] >> 16) & 255; ch[1][i] = (src[i] >> 8) & 255; ch[2][i] = src[i] & 255; }
        float[] tmp = new float[w * h];
        for (int c = 0; c < 3; c++) {
            float[] a = ch[c];
            for (int pass = 0; pass < 3; pass++) {
                for (int y = 0; y < h; y++) {                                           // along the rows
                    float sum = 0;
                    for (int x = -rad; x <= rad; x++) sum += a[y * w + Math.max(0, Math.min(w - 1, x))];
                    for (int x = 0; x < w; x++) {
                        tmp[y * w + x] = sum / (2 * rad + 1);
                        sum += a[y * w + Math.min(w - 1, x + rad + 1)] - a[y * w + Math.max(0, x - rad)];
                    }
                }
                for (int x = 0; x < w; x++) {                                           // and down the columns
                    float sum = 0;
                    for (int y = -rad; y <= rad; y++) sum += tmp[Math.max(0, Math.min(h - 1, y)) * w + x];
                    for (int y = 0; y < h; y++) {
                        a[y * w + x] = sum / (2 * rad + 1);
                        sum += tmp[Math.min(h - 1, y + rad + 1) * w + x] - tmp[Math.max(0, y - rad) * w + x];
                    }
                }
            }
        }
        int[] out = new int[w * h];
        for (int i = 0; i < out.length; i++) out[i] = 0xFF000000 | (Math.min(255, (int) ch[0][i]) << 16) | (Math.min(255, (int) ch[1][i]) << 8) | Math.min(255, (int) ch[2][i]);
        return out;
    }

    /** The two Architects that have no picture of their own: a sharp sign, and round it the light it throws, wide and soft, in its own colours. */
    private BufferedImage glowSign(int idx) {
        final int N = 400, C = N / 2;
        BufferedImage sharp = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = sharp.createGraphics();
        sg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        sg.setColor(Color.BLACK); sg.fillRect(0, 0, N, N);
        if (idx == 0) {                                                                // the last Architect: a cross, red on the left half and blue on the right, a green point in the middle
            double L = 105, T = 26;
            java.awt.geom.Path2D cross = new java.awt.geom.Path2D.Double();
            cross.append(new java.awt.geom.Rectangle2D.Double(C - L, C - T, 2 * L, 2 * T), false);
            cross.append(new java.awt.geom.Rectangle2D.Double(C - T, C - L, 2 * T, 2 * L), false);
            for (int side = 0; side < 2; side++) {
                java.awt.Shape clip = new java.awt.geom.Rectangle2D.Double(side == 0 ? 0 : C, 0, side == 0 ? C : N - C, N);
                sg.setClip(clip);
                sg.setPaint(new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Double(C, C), (float) L, new float[]{0f, 0.3f, 1f},
                        side == 0 ? new Color[]{new Color(255, 236, 240), new Color(255, 105, 125), new Color(225, 35, 70)}
                                : new Color[]{new Color(236, 246, 255), new Color(115, 165, 255), new Color(45, 85, 235)}));
                sg.fill(cross);
            }
            sg.setClip(null);
            sg.setColor(new Color(255, 255, 255, 150)); sg.setStroke(new BasicStroke(2f)); sg.draw(cross);
            sg.setStroke(new BasicStroke(3f));
            for (int k = 0; k < 2; k++) {                                              // a thin pillar of light through it
                int far = C - 190 * (k == 0 ? 1 : -1);
                sg.setPaint(new java.awt.GradientPaint(C, far, new Color(255, 255, 255, 0), C, C, new Color(255, 255, 255, 170)));
                sg.drawLine(C, far, C, C);
            }
            sg.setPaint(new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Double(C, C), 18f, new float[]{0f, 0.5f, 1f}, new Color[]{Color.WHITE, new Color(90, 255, 130), new Color(30, 200, 80)}));
            sg.fill(new java.awt.geom.Ellipse2D.Double(C - 18, C - 18, 36, 36));
        } else {                                                                       // Chromo: a four-pointed star, rainbow on one side, a black-and-white check on the other
            double R = 118;
            for (int k = 0; k < 4; k++) {
                double an = k * Math.PI / 2 - Math.PI / 2;
                double tipx = C + Math.cos(an) * R, tipy = C + Math.sin(an) * R;
                java.awt.geom.Path2D arm = new java.awt.geom.Path2D.Double();
                arm.moveTo(tipx, tipy);
                arm.quadTo(C + Math.cos(an + 0.5) * R * 0.22, C + Math.sin(an + 0.5) * R * 0.22, C + Math.cos(an + 1.25) * R * 0.17, C + Math.sin(an + 1.25) * R * 0.17);
                arm.lineTo(C, C);
                arm.lineTo(C + Math.cos(an - 1.25) * R * 0.17, C + Math.sin(an - 1.25) * R * 0.17);
                arm.quadTo(C + Math.cos(an - 0.5) * R * 0.22, C + Math.sin(an - 0.5) * R * 0.22, tipx, tipy);
                arm.closePath();
                if (k == 0 || k == 3) {
                    sg.setPaint(new java.awt.GradientPaint((float) C, (float) C, Color.getHSBColor(k * 0.17f, 0.45f, 1f), (float) tipx, (float) tipy, Color.getHSBColor(k * 0.17f + 0.4f, 0.95f, 1f)));
                    sg.fill(arm);
                } else {
                    java.awt.Shape clip = sg.getClip();
                    sg.setClip(arm);
                    for (int ix = -12; ix < 12; ix++) for (int iy = -12; iy < 12; iy++) {
                        sg.setColor((ix + iy) % 2 == 0 ? new Color(250, 250, 250) : new Color(22, 22, 26));
                        sg.fillRect(C + ix * 12, C + iy * 12, 12, 12);
                    }
                    sg.setClip(clip);
                }
                sg.setColor(new Color(255, 255, 255, 140)); sg.setStroke(new BasicStroke(1.5f)); sg.draw(arm);
            }
            sg.setPaint(new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Double(C, C), 22f, new float[]{0f, 1f}, new Color[]{Color.WHITE, new Color(255, 255, 255, 0)}));
            sg.fill(new java.awt.geom.Ellipse2D.Double(C - 22, C - 22, 44, 44));
        }
        sg.dispose();
        int[] px = blurRgb(sharp.getRGB(0, 0, N, N, null, 0, N), N, N, 1);
        int[] b1 = blurRgb(px, N, N, 7), b2 = blurRgb(px, N, N, 24), b3 = blurRgb(px, N, N, 60);
        BufferedImage out = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < px.length; i++) {
            int x = i % N, y = i / N;
            double dn = Math.hypot((x - C) / (N * 0.5), (y - C) / (N * 0.5));
            double m = Math.pow(1 - smooth(dn, 0.3, 1.0), 1.3);
            int[] v3 = new int[3];
            for (int c = 0; c < 3; c++) {
                int sh = 16 - 8 * c;
                double v = ((px[i] >> sh) & 255) + 0.26 * ((b1[i] >> sh) & 255) + 0.30 * ((b2[i] >> sh) & 255) + 0.36 * ((b3[i] >> sh) & 255);
                v3[c] = Math.min(255, (int) (v * m));
            }
            out.setRGB(x, y, 0xFF000000 | (v3[0] << 16) | (v3[1] << 8) | v3[2]);
        }
        return out;
    }

    private BufferedImage ref(int i) {
        if (refImg[i] != null || REF_NAME[i] == null) return refImg[i];
        if (REF_NAME[i].equals("gen")) { refImg[i] = glowSign(i); return refImg[i]; }
        try (java.io.InputStream in = Ending13Scene.class.getResourceAsStream("/assets/lotusblight/textures/ending13/architect_" + REF_NAME[i] + ".png")) {
            if (in == null) return null;
            BufferedImage src = javax.imageio.ImageIO.read(in);
            int crop = i == 1 ? 24 : 0;                                                    // the star's picture has a bar of someone's screen along its top
            BufferedImage im = new BufferedImage(src.getWidth(), src.getHeight() - crop, BufferedImage.TYPE_INT_ARGB);
            double cx = REF_CENTER[i][0], cy = REF_CENTER[i][1];
            for (int y = 0; y < im.getHeight(); y++) {
                for (int x = 0; x < im.getWidth(); x++) {
                    int p = src.getRGB(x, y + crop);
                    double dn = Math.hypot((x - cx) / (im.getWidth() * 0.5), (y - cy) / (im.getHeight() * 0.5));
                    double m = Math.pow(1 - smooth(dn, 0.22, 1.0), 1.4);                    // the edges of the picture melt into nothing
                    im.setRGB(x, y, 0xFF000000 | (((int) (((p >> 16) & 255) * m)) << 16) | (((int) (((p >> 8) & 255) * m)) << 8) | (int) ((p & 255) * m));
                }
            }
            refImg[i] = im;
        } catch (Exception e) {
            return null;
        }
        return refImg[i];
    }

    private boolean drawRef(int i, double cx, double cy, double unit, double a, double sizeNorm) {
        BufferedImage im = ref(i);
        if (im == null) return false;
        double scale = unit * 5.0 * sizeNorm / im.getWidth();
        if (scale <= 0.01) return true;
        ADD_GAIN[0] = a * 0.85;
        java.awt.geom.AffineTransform at = new java.awt.geom.AffineTransform();
        at.translate(cx - scale * REF_CENTER[i][0], cy - scale * REF_CENTER[i][1]);
        at.scale(scale, scale);
        if (gpuMode) {                                               // the card adds it itself, at its own size and sharpness
            double fx = outScaleX, fy = outScaleY, iw = im.getWidth(), ih = im.getHeight();
            double[] pts = {0, 0, iw, 0, iw, ih, 0, ih};
            double[] dst = new double[8];
            at.transform(pts, 0, dst, 0, 4);
            for (int k = 0; k < 4; k++) { dst[k * 2] *= fx; dst[k * 2 + 1] *= fy; }
            curFrame.addSprite(im, dst, a * 0.85);
            return true;
        }
        g.setComposite(ADD);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(im, at, null);
        g.setComposite(java.awt.AlphaComposite.SrcOver);
        return true;
    }

    /** Low mist in the air of the hall, drifting; it takes the colour of whoever is lit. */
    private void drawMist(double tt) {
        double fr = 0, fg = 0, fb = 0;
        for (int i = 0; i < 6; i++) { fr += LIGHT_RGB[i][0] * lightA[i]; fg += LIGHT_RGB[i][1] * lightA[i]; fb += LIGHT_RGB[i][2] * lightA[i]; }
        double n = Math.max(0.2, Math.min(1.6, fr + fg + fb));
        java.awt.geom.AffineTransform old = g.getTransform();
        for (int k = 0; k < 9; k++) {
            double x = ((k * 173 + tt * (5 + k * 1.7)) % (W + 520)) - 260;
            double y = H * (0.60 + 0.12 * ((k * 37) % 5) / 4.0) + Math.sin(k * 1.7 + tt * 0.25) * 12;
            double rx = 240 + k * 22, ry = 30 + (k % 3) * 14;
            double tr = 150 + 40 * fr / n, tg = 150 + 40 * fg / n, tb = 160 + 40 * fb / n;
            if (gpuMode) {
                double sx = outScaleX, sy = outScaleY;
                curFrame.addSprite(radial(), new double[]{(x - rx) * sx, (y - ry) * sy, (x + rx) * sx, (y - ry) * sy, (x + rx) * sx, (y + ry) * sy, (x - rx) * sx, (y + ry) * sy},
                        0.20 * Math.min(1, n), tr / 255.0, tg / 255.0, tb / 255.0);
                continue;
            }
            g.setTransform(old);
            g.translate(x, y);
            g.scale(1.0, ry / rx);
            java.awt.RadialGradientPaint paint = new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Double(0, 0), (float) rx,
                    new float[]{0f, 1f}, new Color[]{rgba(tr, tg, tb, 0.085 * Math.min(1, n)), rgba(tr, tg, tb, 0)});
            g.setPaint(paint);
            g.fill(new java.awt.geom.Ellipse2D.Double(-rx, -rx, rx * 2, rx * 2));
        }
        g.setTransform(old);
    }

    /** The six Lights over the picture: where the 3D scene put them, as the soft, glowing signs of the references. */
    private void drawLights2D(double tt) {
        java.awt.Composite old = g.getComposite();
        g.setComposite(java.awt.AlphaComposite.SrcOver);
        double[][] right = r.basis();
        for (int i = 0; i < 6; i++) {
            double a = lightA[i] * (1 - fade);
            if (a <= 0.01) continue;
            double[] pos = CHAMBER_LIGHT_POS[i];
            double[] c0 = r.project(pos[0], pos[1], pos[2]);
            double[] c1 = r.project(pos[0] + right[0][0], pos[1] + right[0][1], pos[2] + right[0][2]);
            if (c0 == null || c1 == null) continue;
            double unit = Math.hypot(c1[0] - c0[0], c1[1] - c0[1]);                       // pixels in a metre at that distance
            double R = lightSz[i] / Math.max(0.0001, a) * a * unit * 0.80;
            double cx = c0[0], cy = c0[1];
            softGlow(cx, cy, W * 0.75, LIGHT_RGB[i][0] * 255, LIGHT_RGB[i][1] * 255, LIGHT_RGB[i][2] * 255, 0.05 * a);          // its colour spreads over everything
            if (REF_NAME[i] != null && drawRef(i, cx, cy, unit, a, lightSz[i] / 0.85)) continue;
            double[] col = LIGHT_RGB[i];
            double cr = col[0] * 255, cg = col[1] * 255, cb = col[2] * 255;
            Random q = new Random(1000 + i * 71L);
            // the haze
            softGlow(cx, cy, R * 3.6, cr, cg, cb, 0.30 * a);
            softGlow(cx, cy, R * 1.9, Math.min(255, cr + 40), Math.min(255, cg + 40), Math.min(255, cb + 40), 0.5 * a);
            // dust that drifts and twinkles round it
            for (int k = 0; k < 46; k++) {
                double an = q.nextDouble() * 6.283 + tt * (0.05 + q.nextDouble() * 0.12) * (k % 2 == 0 ? 1 : -1);
                double d = R * (0.9 + q.nextDouble() * 2.6);
                double tw = 0.4 + 0.6 * Math.sin(tt * (2 + q.nextDouble() * 4) + k);
                double sz = 0.8 + q.nextDouble() * 1.8;
                g.setColor(rgba(255, 245, 230, 0.55 * a * Math.max(0, tw)));
                g.fill(new java.awt.geom.Ellipse2D.Double(cx + Math.cos(an) * d - sz / 2, cy + Math.sin(an) * d * 0.8 - sz / 2, sz, sz));
            }
            switch (i) {
                case 0 -> {                                                              // the last Architect: a cross, red on one half and blue on the other, a green point in the middle
                    double th = R * 0.30, len = R * 1.05;
                    for (int side = -1; side <= 1; side += 2) {
                        double rr = side < 0 ? 255 : 90, gg = side < 0 ? 80 : 140, bb = side < 0 ? 95 : 255;
                        softGlow(cx + side * len * 0.5, cy, len * 0.9, rr, gg, bb, 0.35 * a);
                        java.awt.geom.Path2D arm = new java.awt.geom.Path2D.Double();
                        double x0 = side < 0 ? cx - len : cx, x1 = side < 0 ? cx : cx + len;
                        arm.append(new java.awt.geom.Rectangle2D.Double(x0, cy - th, x1 - x0, th * 2), false);
                        arm.append(new java.awt.geom.Rectangle2D.Double(side < 0 ? cx - th : cx, cy - len, th, len * 2), false);
                        g.setPaint(new java.awt.GradientPaint((float) x0, (float) cy, rgba(rr, gg, bb, a), (float) x1, (float) cy, rgba(Math.min(255, rr + 90), Math.min(255, gg + 90), Math.min(255, bb + 90), a)));
                        g.fill(arm);
                    }
                    softGlow(cx, cy, R * 0.55, 90, 255, 130, 0.9 * a);
                    g.setColor(rgba(230, 255, 235, a)); g.fill(new java.awt.geom.Ellipse2D.Double(cx - R * 0.1, cy - R * 0.1, R * 0.2, R * 0.2));
                }
                case 1 -> {                                                              // Starlight: a white-hot core in a gold glow, sparks and rays
                    softGlow(cx, cy, R * 2.4, 255, 170, 60, 0.55 * a);
                    g.setStroke(new BasicStroke(1.2f));
                    for (int k = 0; k < 12; k++) {
                        double an = k * Math.PI / 6 + tt * 0.1, len = R * (k % 2 == 0 ? 2.6 : 1.5);
                        g.setPaint(new java.awt.GradientPaint((float) cx, (float) cy, rgba(255, 235, 170, 0.8 * a), (float) (cx + Math.cos(an) * len), (float) (cy + Math.sin(an) * len), rgba(255, 190, 80, 0)));
                        g.draw(new java.awt.geom.Line2D.Double(cx, cy, cx + Math.cos(an) * len, cy + Math.sin(an) * len));
                    }
                    softGlow(cx, cy, R * 0.9, 255, 250, 225, 1.0 * a);
                    g.setColor(rgba(255, 255, 250, a)); g.fill(new java.awt.geom.Ellipse2D.Double(cx - R * 0.28, cy - R * 0.28, R * 0.56, R * 0.56));
                }
                case 2 -> {                                                              // Mischievous: a diamond, deep red, with a rounded spiral in white-pink
                    double d = R * 0.95;
                    java.awt.geom.Path2D dia = new java.awt.geom.Path2D.Double();
                    dia.moveTo(cx, cy - d); dia.lineTo(cx + d, cy); dia.lineTo(cx, cy + d); dia.lineTo(cx - d, cy); dia.closePath();
                    g.setPaint(new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Double(cx, cy), (float) d, new float[]{0f, 1f}, new Color[]{rgba(255, 90, 90, 0.95 * a), rgba(150, 10, 18, 0.95 * a)}));
                    g.fill(dia);
                    glowStroke(dia, Math.max(1.5, R * 0.05), 255, 70, 80, 0.9 * a);
                    java.awt.geom.Path2D sp = new java.awt.geom.Path2D.Double();
                    for (int k = 0; k <= 70; k++) {
                        double an = k * 0.23, rr = R * (0.06 + k * 0.0095);
                        double x = cx + Math.cos(an) * rr, y = cy + Math.sin(an) * rr;
                        if (k == 0) sp.moveTo(x, y); else sp.lineTo(x, y);
                    }
                    glowStroke(sp, Math.max(1.6, R * 0.07), 255, 170, 190, 0.95 * a);
                    for (int k = 0; k < 4; k++) {                                         // little red stars of light
                        double an = k * 1.7 + tt * 0.2, dd = R * (1.7 + 0.4 * k);
                        double sx = cx + Math.cos(an) * dd, sy = cy + Math.sin(an) * dd * 0.7;
                        softGlow(sx, sy, R * 0.3, 255, 40, 50, 0.7 * a);
                    }
                }
                case 3 -> {                                                              // Moonlight: a crescent of white-blue light in a cold glow
                    softGlow(cx, cy, R * 2.8, 80, 190, 255, 0.5 * a);
                    java.awt.geom.Area moon = new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(cx - R, cy - R, R * 2, R * 2));
                    moon.subtract(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(cx - R * 0.45, cy - R * 1.15, R * 1.9, R * 1.9)));
                    glowStroke(moon, Math.max(1.5, R * 0.06), 110, 210, 255, 0.55 * a);
                    g.setPaint(new java.awt.GradientPaint((float) (cx - R), (float) cy, rgba(255, 255, 255, a), (float) (cx + R * 0.5), (float) cy, rgba(150, 225, 255, a)));
                    g.fill(moon);
                    for (int k = 0; k < 3; k++) {                                         // faint pillars of light behind it
                        g.setPaint(new java.awt.GradientPaint((float) cx, (float) (cy - R * 3), rgba(120, 200, 255, 0), (float) cx, (float) (cy + R * 2), rgba(120, 200, 255, 0.10 * a)));
                        g.fill(new java.awt.geom.Rectangle2D.Double(cx - R * 2.2 + k * R * 1.7, cy - R * 3, R * 0.5, R * 5));
                    }
                }
                case 4 -> {                                                              // Chromo: a four-pointed star, rainbow on one side, a black-and-white check on the other
                    softGlow(cx, cy, R * 2.4, 255, 255, 255, 0.35 * a);
                    for (int k = 0; k < 4; k++) {
                        double an = k * Math.PI / 2 - Math.PI / 2 + Math.sin(tt * 0.6) * 0.04;
                        double tipx = cx + Math.cos(an) * R * 1.35, tipy = cy + Math.sin(an) * R * 1.35;
                        java.awt.geom.Path2D arm = new java.awt.geom.Path2D.Double();
                        arm.moveTo(tipx, tipy);
                        arm.quadTo(cx + Math.cos(an + 0.5) * R * 0.25, cy + Math.sin(an + 0.5) * R * 0.25, cx + Math.cos(an + 1.25) * R * 0.2, cy + Math.sin(an + 1.25) * R * 0.2);
                        arm.lineTo(cx, cy);
                        arm.lineTo(cx + Math.cos(an - 1.25) * R * 0.2, cy + Math.sin(an - 1.25) * R * 0.2);
                        arm.quadTo(cx + Math.cos(an - 0.5) * R * 0.25, cy + Math.sin(an - 0.5) * R * 0.25, tipx, tipy);
                        arm.closePath();
                        if (k == 0 || k == 3) {
                            g.setPaint(new java.awt.GradientPaint((float) cx, (float) cy, Color.getHSBColor((float) ((tt * 0.1 + k * 0.17) % 1), 0.55f, 1f), (float) tipx, (float) tipy, Color.getHSBColor((float) ((tt * 0.1 + k * 0.17 + 0.4) % 1), 0.9f, 1f)));
                            g.fill(arm);
                        } else {
                            java.awt.Shape clip = g.getClip();
                            g.setClip(arm);
                            double cell = Math.max(2, R * 0.16);
                            for (int ix = -8; ix < 8; ix++) for (int iy = -8; iy < 8; iy++) {
                                g.setColor((ix + iy) % 2 == 0 ? new Color(250, 250, 250) : new Color(25, 25, 28));
                                g.fill(new java.awt.geom.Rectangle2D.Double(cx + ix * cell, cy + iy * cell, cell, cell));
                            }
                            g.setClip(clip);
                        }
                    }
                    softGlow(cx, cy, R * 0.5, 255, 255, 255, 0.9 * a);
                }
                default -> {                                                             // Glitch: a three-pointed star of white-violet light, and coloured squares coming apart round it
                    softGlow(cx, cy, R * 2.6, 190, 90, 255, 0.5 * a);
                    for (int k = 0; k < 3; k++) {
                        double an = -Math.PI / 2 + k * Math.PI * 2 / 3 + Math.sin(tt * 0.8) * 0.05;
                        double tipx = cx + Math.cos(an) * R * 1.3, tipy = cy + Math.sin(an) * R * 1.3;
                        java.awt.geom.Path2D arm = new java.awt.geom.Path2D.Double();
                        arm.moveTo(tipx, tipy);
                        arm.quadTo(cx + Math.cos(an + 0.35) * R * 0.5, cy + Math.sin(an + 0.35) * R * 0.5, cx + Math.cos(an + 1.5) * R * 0.28, cy + Math.sin(an + 1.5) * R * 0.28);
                        arm.lineTo(cx + Math.cos(an - 1.5) * R * 0.28, cy + Math.sin(an - 1.5) * R * 0.28);
                        arm.quadTo(cx + Math.cos(an - 0.35) * R * 0.5, cy + Math.sin(an - 0.35) * R * 0.5, tipx, tipy);
                        arm.closePath();
                        glowStroke(arm, Math.max(1.4, R * 0.05), 190, 100, 255, 0.5 * a);
                        g.setPaint(new java.awt.GradientPaint((float) cx, (float) cy, rgba(255, 255, 255, a), (float) tipx, (float) tipy, rgba(205, 120, 255, a)));
                        g.fill(arm);
                    }
                    for (int k = 0; k < 16; k++) {                                        // squares of the picture falling out of it
                        double an = q.nextDouble() * 6.283, d = R * (1.2 + q.nextDouble() * 2.8) + Math.sin(tt * 2 + k) * 3;
                        double sz = R * (0.12 + q.nextDouble() * 0.2);
                        double hue = q.nextDouble();
                        g.setColor(Color.getHSBColor((float) hue, 0.7f, 1f).brighter());
                        java.awt.Color cc = Color.getHSBColor((float) hue, 0.7f, 1f);
                        g.setColor(new Color(cc.getRed(), cc.getGreen(), cc.getBlue(), (int) (130 * a * (0.5 + 0.5 * Math.sin(tt * 5 + k)))));
                        g.fill(new java.awt.geom.Rectangle2D.Double(cx + Math.cos(an) * d, cy + Math.sin(an) * d * 0.8, sz, sz));
                    }
                }
            }
        }
        g.setComposite(old);
    }

    // ------------------------------------------------------------------ the blow, the white, the void

    /** 2:31: the knife comes down at his chest; it cuts to black at the blow, and only a heart is left, beating slow; then the light. */
    private String stabBeat(double t) {
        double u = t - STAB;
        double blowEnd = 2.0;
        if (u < blowEnd) {
            stabU = u;
            lightsRoom(t);
            fade = Math.max(fade, smooth(u, 1.62, 1.9));
            return "LIGHTS";
        }
        double tm = clamp01((u - blowEnd) / (WHITE - STAB - blowEnd));
        heart(0.35 + 0.5 * tm, t);
        fade = 0;
        kickFlash = Math.max(kickFlash, smooth(tm, 0.7, 1.0));
        return null;
    }

    /** 2:35: all white; then, in it, the basement ceiling, seen from the floor, and the blood coming out of him over the lower edge of the view. */
    private void whiteCeiling(double t) {
        double u = (t - WHITE) / (LIMBO - WHITE);
        r.clear(0xF0F0F2);
        r.ambR = 0.9; r.ambG = 0.9; r.ambB = 0.92;
        r.fogDensity = 0.02; r.fogR = 236; r.fogG = 236; r.fogB = 240;
        double sway = Math.sin(t * 0.7) * 0.02;
        r.camera(0, 0.25, -0.3, sway, 1.28 + 0.1 * smooth(u, 0.55, 1.0) + Math.sin(t * 0.5) * 0.015);
        r.lights.add(new Soft3D.Light(0, 2.4, 0.2, 2.2, 2.2, 2.3, 9.0));
        double h = 3.0;
        r.faceXZ(h, -3, -3, 3, 3, wall, 0xFFFFFFFF, 0.5, 4, 4);
        for (int i = 0; i < 4; i++) r.box(-3, h - 0.28, -2.8 + i * 1.5, 3, h, -2.5 + i * 1.5, wall, 0xFFEEEEEE, 0.4, 2);
        r.bar(new double[]{-3, 2.88, 0.4}, new double[]{3, 2.88, 0.4}, new double[]{0, 0.06, 0}, new double[]{0, 0, 0.06}, pipe, 0xFFDDDDDD, 0.3);
        fxBloom = 1.6; fxSsao = 0.2; fxRays = 0; fxRayAt = null; fxContrast = 0.9; fxSat = 0.6; fxDofFocus = 0;
        exposureBoost = 0.55;
        fade = 0;
        kickFlash = 1.0 - smooth(u, 0.0, 0.28);                                       // out of the white
        bloodAmount = smooth(u, 0.18, 0.7);
        if (u > 0.82) kickFlash = Math.max(kickFlash, smooth(u, 0.82, 1.0));          // and into it again
    }

    private double bloodAmount;
    private double exposureBoost;

    /** The end: a white void with the floor drawn as a grid, frames hanging in it where scenes were, bars where the limits are; he is alone in it, and someone stands far ahead. */
    private void limbo(double t) {
        double u = (t - LIMBO) / (176.0 - LIMBO);
        r.clear(0xF4F4F6);
        r.ambR = 0.95; r.ambG = 0.95; r.ambB = 0.97;
        r.fogDensity = 0.009; r.fogR = 244; r.fogG = 244; r.fogB = 247;
        r.lights.add(new Soft3D.Light(0, 4, 0, 1.4, 1.4, 1.5, 24));
        double adv = smooth(u, 0.0, 1.0) * 4.0;
        r.camera(Math.sin(t * 0.4) * 0.03, 1.6 + Math.sin(t * 0.9) * 0.012, adv, Math.sin(t * 0.2) * 0.04, -0.03 + 0.12 * (1 - smooth(u, 0.0, 0.35)));
        Soft3D.Tex line = Soft3D.Tex.solid(70, 70, 88);
        // the floor is only a grid
        for (int i = -15; i <= 15; i++) r.box(i * 2.0, 0.002, -4, i * 2.0 + 0.035, 0.005, 70, line, 0xFFFFFFFF, 1.0, 1);
        for (int j = -2; j < 36; j++) r.box(-30, 0.002, j * 2.0, 30, 0.005, j * 2.0 + 0.035, line, 0xFFFFFFFF, 1.0, 1);
        // fragments of the places he has been, only as outlines hanging in the white
        outline(-6.0, 0.6, 13, -4.8, 1.5, 13.8, 0.03, line);                 // the set
        outline(-5.8, 0.75, 13, -5.0, 1.35, 13.01, 0.02, line);
        r.bar(new double[]{-5.4, 1.5, 13.4}, new double[]{-5.8, 1.9, 13.4}, new double[]{0.012, 0, 0}, new double[]{0, 0, 0.012}, line, 0xFFFFFFFF, 1.0);
        r.bar(new double[]{-5.4, 1.5, 13.4}, new double[]{-5.0, 1.9, 13.4}, new double[]{0.012, 0, 0}, new double[]{0, 0, 0.012}, line, 0xFFFFFFFF, 1.0);
        outline(4.6, 0.0, 17, 6.0, 2.3, 17.05, 0.04, line);                  // a door in its frame
        outline(4.8, 0.1, 17, 5.8, 2.2, 17.05, 0.02, line);
        outline(-3.4, 0.0, 9, -2.9, 0.45, 9.5, 0.025, line);                 // a chair, seat and back
        outline(-3.4, 0.45, 9.45, -2.9, 0.95, 9.5, 0.025, line);
        outline(7.0, 1.1, 22, 8.8, 2.4, 22.04, 0.03, line);                  // a window with its bars
        for (int k = 1; k < 5; k++) r.box(7.0 + k * 0.36, 1.1, 22, 7.0 + k * 0.36 + 0.02, 2.4, 22.04, line, 0xFFFFFFFF, 1.0, 1);
        for (int k = 0; k < 6; k++) outline(-1.6, 0.0, 30 + k * 4.5, 1.6, 3.0, 30.05 + k * 4.5, 0.035, line);         // the arches of a corridor going away
        double cxm = 0, cym = 4.2, czm = 46;                                  // the sign of the last Architect, as lines
        r.box(cxm - 1.6, cym - 0.3, czm, cxm + 1.6, cym + 0.3, czm + 0.04, line, 0xFFFFFFFF, 1.0, 1);
        r.box(cxm - 0.3, cym - 1.6, czm, cxm + 0.3, cym + 1.6, czm + 0.04, line, 0xFFFFFFFF, 1.0, 1);
        // the limits: tall thin bars on both sides, closing in with time
        double gap = lerp(7.0, 4.2, smooth(u, 0, 1));
        for (int i = 0; i < 16; i++) {
            double z = 2 + i * 2.6;
            r.box(-gap, 0, z, -gap + 0.06, 4.0, z + 0.06, line, 0xFFFFFFFF, 1.0, 1);
            r.box(gap, 0, z, gap + 0.06, 4.0, z + 0.06, line, 0xFFFFFFFF, 1.0, 1);
        }
        r.box(-gap, 3.9, 2, -gap + 0.05, 3.96, 44, line, 0xFFFFFFFF, 1.0, 1);
        r.box(gap, 3.9, 2, gap + 0.05, 3.96, 44, line, 0xFFFFFFFF, 1.0, 1);
        // far ahead, someone: himself, the colour gone to red
        double reveal = smooth(u, 0.45, 0.8);
        if (reveal > 0.02) {
            Actor15 other = new Actor15();
            other.reset().turn(Actor15.Part.HEAD, 4, 0, 0).hand(true, 0.3, 0.2).hand(false, 0.3, 0.2);
            other.solve();
            int tintv = (int) (255 * (1 - 0.5 * reveal));
            r.matWrap = 0.3;
            SkinActor.draw(r, other, skin, new double[]{0, 0, lerp(26, 18, smooth(u, 0.45, 1.0))}, Math.PI, SkinActor.PX, 0xFF000000 | (tintv << 16) | ((int) (70 + 40 * (1 - reveal)) << 8) | ((int) (70 + 40 * (1 - reveal))), 0);
            r.matWrap = 0;
        }
        fxBloom = 0.5; fxSsao = 0; fxRays = 0; fxRayAt = null; fxContrast = 0.95; fxSat = 0.5; fxDofFocus = 0;
        exposureBoost = 0.35;
        fade = smooth(u, 0.93, 1.0);
        titleAlpha = smooth(u, 0.55, 0.7) * (1 - smooth(u, 0.9, 1.0));
    }

    /** The edges of a box as thin bars: a drawing of a thing, not the thing. */
    private void outline(double x0, double y0, double z0, double x1, double y1, double z1, double th, Soft3D.Tex tex) {
        r.box(x0, y0, z0, x1, y0 + th, z0 + th, tex, 0xFFFFFFFF, 1.0, 1);
        r.box(x0, y1 - th, z0, x1, y1, z0 + th, tex, 0xFFFFFFFF, 1.0, 1);
        r.box(x0, y0, z0, x0 + th, y1, z0 + th, tex, 0xFFFFFFFF, 1.0, 1);
        r.box(x1 - th, y0, z0, x1, y1, z0 + th, tex, 0xFFFFFFFF, 1.0, 1);
        if (z1 - z0 > 2 * th) {
            r.box(x0, y0, z1 - th, x1, y0 + th, z1, tex, 0xFFFFFFFF, 1.0, 1);
            r.box(x0, y1 - th, z1, x1, y1, z1 - th, tex, 0xFFFFFFFF, 1.0, 1);
            r.box(x0, y0, z0, x0 + th, y0 + th, z1, tex, 0xFFFFFFFF, 1.0, 1);
            r.box(x1 - th, y0, z0, x1, y0 + th, z1, tex, 0xFFFFFFFF, 1.0, 1);
        }
    }

    // ------------------------------------------------------------------ the memories

    private double tmOverlay;
    private double exposure = 1.7;
    private double fade = 0;      // 0 = as drawn, 1 = black
    private double heavy = 0;     // how heavy it has become for the player: 0 .. 1
    private int extraCracks = 0;  // the TV is cracked more than the memories alone did it
    private double tvDead = 0;    // 0 = on, 1 = the glass is gone and the light with it

    // ---- textures made with Graphics2D
    private interface Painter { void paint(Graphics2D g); }

    private static Soft3D.Tex paint(int w, int h, Painter p) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = im.createGraphics();
        pg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        p.paint(pg);
        pg.dispose();
        Soft3D.Tex t = new Soft3D.Tex(w, h);
        im.getRGB(0, 0, w, h, t.px, 0, w);
        return t;
    }

    private final Soft3D.Tex tiles = paint(128, 128, g2 -> {
        Random q = new Random(31);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) {
            int v = 188 + q.nextInt(24);
            g2.setColor(new Color(v, v + 4, v + 2));
            g2.fillRect(x * 32, y * 32, 32, 32);
        }
        g2.setColor(new Color(90, 94, 92));
        for (int i = 0; i <= 4; i++) { g2.fillRect(i * 32 - 1, 0, 2, 128); g2.fillRect(0, i * 32 - 1, 128, 2); }
        for (int k = 0; k < 60; k++) {                                               // grime
            g2.setColor(new Color(40, 36, 28, 18 + q.nextInt(26)));
            int cx = q.nextInt(128), cy = q.nextInt(128), r2 = 6 + q.nextInt(24);
            g2.fillOval(cx - r2, cy - r2 / 2, r2 * 2, r2);
        }
    });
    private final Soft3D.Tex checker = paint(128, 128, g2 -> {
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) {
            g2.setColor((x + y) % 2 == 0 ? new Color(160, 162, 158) : new Color(52, 54, 56));
            g2.fillRect(x * 32, y * 32, 32, 32);
        }
        Random q = new Random(32);
        for (int k = 0; k < 70; k++) { g2.setColor(new Color(20, 18, 14, 20 + q.nextInt(30))); int cx = q.nextInt(128), cy = q.nextInt(128), r2 = 4 + q.nextInt(18); g2.fillOval(cx - r2, cy - r2, r2 * 2, r2 * 2); }
    });
    private final Soft3D.Tex hazard = paint(64, 64, g2 -> {
        g2.setColor(new Color(220, 180, 20)); g2.fillRect(0, 0, 64, 64);
        g2.setColor(new Color(20, 20, 20));
        for (int i = -64; i < 128; i += 32) g2.fillPolygon(new int[]{i, i + 16, i + 16 + 64, i + 64}, new int[]{0, 0, 64, 64}, 4);
    });
    private final Soft3D.Tex steelDoor = paint(128, 256, g2 -> {
        g2.setColor(new Color(92, 96, 100)); g2.fillRect(0, 0, 128, 256);
        Random q = new Random(33);
        for (int i = 0; i < 700; i++) { g2.setColor(new Color(60 + q.nextInt(60), 62 + q.nextInt(60), 66 + q.nextInt(60), 90)); g2.fillRect(q.nextInt(128), q.nextInt(256), 1 + q.nextInt(3), 1 + q.nextInt(8)); }
        g2.setColor(new Color(40, 42, 46)); g2.drawRect(6, 6, 116, 244); g2.drawRect(10, 10, 108, 236);
        for (int y = 14; y < 250; y += 22) for (int x : new int[]{14, 114}) { g2.setColor(new Color(130, 134, 138)); g2.fillOval(x - 2, y - 2, 5, 5); }
        g2.setColor(new Color(30, 32, 36)); g2.fillRect(56, 0, 3, 256);
    });
    private final Soft3D.Tex barsTex = paint(16, 16, g2 -> { g2.setColor(new Color(60, 62, 66)); g2.fillRect(0, 0, 16, 16); g2.setColor(new Color(110, 112, 118)); g2.fillRect(2, 0, 3, 16); });
    private final Soft3D.Tex plaster = Soft3D.Tex.surface(41, 128, 128, 168, 160, 146, 8, 16).streaks(42, 12, 16);
    private final Soft3D.Tex deskWood = Soft3D.Tex.surface(43, 64, 64, 96, 62, 38, 12, 8);
    private final Soft3D.Tex paper = Soft3D.Tex.surface(44, 16, 16, 226, 222, 208, 6, 2);
    private final Soft3D.Tex blinds = paint(64, 64, g2 -> {
        g2.setColor(new Color(20, 20, 20)); g2.fillRect(0, 0, 64, 64);
        g2.setColor(new Color(255, 140, 90)); for (int y = 4; y < 64; y += 10) g2.fillRect(0, y, 64, 5);
    });
    private final Soft3D.Tex grass = paint(32, 32, g2 -> {
        Random q = new Random(34);
        g2.setColor(new Color(0, 0, 0, 0));
        for (int i = 0; i < 40; i++) { g2.setColor(new Color(14 + q.nextInt(20), 28 + q.nextInt(30), 14 + q.nextInt(14))); int x = q.nextInt(32); g2.fillPolygon(new int[]{x, x + 2, x + q.nextInt(7) - 3}, new int[]{32, 32, 32 - 10 - q.nextInt(20)}, 3); }
    });
    private final Soft3D.Tex chain = paint(32, 32, g2 -> {
        g2.setColor(new Color(150, 154, 160));
        for (int i = -32; i < 64; i += 8) { g2.drawLine(i, 0, i + 32, 32); g2.drawLine(i, 32, i + 32, 0); }
    });

    /** A solid colour tinted material for a figure: a body tint that dims the skin. */
    private static int dim(int pct) {
        int v = Math.max(0, Math.min(255, pct * 255 / 100));
        return 0xFF000000 | (v << 16) | (v << 8) | v;
    }

    /** The skin model tilted about its feet (falling), by an angle (0 = upright, positive = backwards towards -z). */
    private double[][] tilt(double a) {
        return new double[][]{{1, 0, 0}, {0, Math.cos(a), -Math.sin(a)}, {0, Math.sin(a), Math.cos(a)}};
    }

    /** The skin model tilted sideways about its feet (falling to the right of the picture). */
    private double[][] tiltZ(double a) {
        return new double[][]{{Math.cos(a), -Math.sin(a), 0}, {Math.sin(a), Math.cos(a), 0}, {0, 0, 1}};
    }

    private String lastText;

    /** Draws memory {@code idx} in 3D and returns the text to put over it (or null). */
    private String memory(int idx, double tm, double t) {
        tmOverlay = tm;
        fxRays = 0; fxRayAt = null; fxDofFocus = 0; fxSsao = 0.6; fxBloom = 0.8; fxContrast = 1.1; fxSat = 0.95;
        switch (idx) {
            case 0: return memNeck(tm, t);
            case 1: return memObject(tm, t);
            case 2: return memOffice(tm, t);
            case 3: return memCell(tm, t);
            case 4: return memForest(tm, t);
            default: return memFault(tm, t);
        }
    }

    private Soft3D.Pose pose() {
        return new Soft3D.Pose();
    }

    private void fluorescent(double x, double y, double z, double len, boolean on) {
        r.box(x - len / 2, y, z - 0.1, x + len / 2, y + 0.06, z + 0.1, Soft3D.Tex.solid(235, 245, 255), 0xFFFFFFFF, on ? 1.0 : 0.05, 1);
        r.box(x - len / 2 - 0.03, y + 0.04, z - 0.13, x + len / 2 + 0.03, y + 0.09, z + 0.13, steel, 0xFF666666, 0, 1);
    }

    /** A laboratory corridor: tiles, a flickering tube, red emergency light at the end; a man from behind, the player's hands, the neck. */
    private String memNeck(double tm, double t) {
        r.clear(0x020204);
        r.ambR = 0.07; r.ambG = 0.08; r.ambB = 0.09;
        r.fogDensity = 0.07; r.fogR = 12; r.fogG = 8; r.fogB = 10;
        boolean flick = rnd.nextInt(6) != 0 || tm < 0.1;
        double fl = flick ? 1.0 : 0.2;
        r.lights.add(new Soft3D.Light(0, 2.55, 0.2, 1.45 * fl, 1.5 * fl, 1.55 * fl, 7.5));
        r.shadowDir = new double[]{0, -1, 0.001};
        r.lights.add(new Soft3D.Light(0, 2.3, 5.0, 1.6 + 0.4 * Math.sin(t * 7), 0.12, 0.08, 6.5));
        r.lights.add(new Soft3D.Light(r.camX, 1.9, r.camZ - 0.4, 0.55, 0.57, 0.62, 6.0));
        fxBloom = 0.9; fxSsao = 0.8; fxContrast = 1.18; fxSat = 0.9;
        double snapT = 0.5;
        double walk = smooth(tm, 0, 0.42);
        double shake = Math.exp(-Math.abs(tm - snapT) * 18) * 0.04;
        double back = smooth(tm, snapT + 0.04, snapT + 0.3);
        double[] full = cameraFor(new double[]{0, 1.43, 0.32}, gripMid(), 0, -0.12);
        r.camera(Math.sin(walk * 11) * 0.012 + rnd.nextGaussian() * shake, lerp(1.62, full[1], walk) - 0.25 * back + Math.abs(Math.sin(walk * 11)) * 0.02 + rnd.nextGaussian() * shake,
                lerp(lerp(-5.3, full[2], walk), -1.7, back), 0.25 * back, -0.12 - 0.3 * back);
        double x0 = -1.5, x1 = 1.5, z0 = -9, z1 = 7, h = 2.9;
        r.matSpec = 0.6; r.matShine = 50;
        r.faceXZ(0, x0, z0, x1, z1, checker, 0xFFFFFFFF, 0, 3, 16);
        r.matSpec = 0;
        r.faceXZ(h, x0, z0, x1, z1, tiles, 0xFF999999, 0, 3, 16);
        r.faceYZ(x0, 0, z0, h, z1, tiles, 0xFFFFFFFF, 0, 16, 2.2);
        r.faceYZ(x1, 0, z0, h, z1, tiles, 0xFFFFFFFF, 0, 16, 2.2);
        r.faceXY(z1, x0, 0, x1, h, steelDoor, 0xFFBBAAAA, 0, 1.5, 1);
        r.faceXY(z0, x0, 0, x1, h, tiles, 0xFF555555, 0, 3, 2.2);
        for (int i = 0; i < 4; i++) fluorescent(0, 2.78, -5.5 + i * 3.4, 1.0, i != 2 || flick);
        for (int i = 0; i < 5; i++) {                                                     // doors with little windows on the left
            r.box(x0 + 0.01, 0, -6 + i * 3 - 0.5, x0 + 0.08, 2.2, -6 + i * 3 + 0.5, steelDoor, 0xFFAAAAAA, 0, 1);
        }
        r.bar(new double[]{x1 - 0.06, 2.4, z0}, new double[]{x1 - 0.06, 2.4, z1}, new double[]{0, 0.05, 0}, new double[]{0.05, 0, 0}, pipe, 0xFFAAAAAA, 0);
        // the man, in a pale coat: his head snaps round and he drops back towards us
        double snap = smooth(tm, snapT, snapT + 0.025);
        double fall = smooth(tm, snapT + 0.04, snapT + 0.22);
        Soft3D.Pose v = pose();
        v.yaw[PlayerBoxes.HEAD] = snap * 1.5 + Math.sin(t * 60) * 0.012 * (1 - snap);
        v.roll[PlayerBoxes.HEAD] = snap * 0.35;
        v.pitch[PlayerBoxes.RIGHT_ARM] = 0.1 + fall * 0.9; v.pitch[PlayerBoxes.LEFT_ARM] = 0.1 + fall * 1.1;
        r.matSpec = 0.05;
        r.figure(model, skin, new double[]{0, 0, 0.35}, tiltZ(fall * 1.5), 0.0625 * 1.0, v, dim(100), 0, Soft3D.ALL_PARTS);
        r.matSpec = 0;
        dressCorridor(-1.5, 1.5, -9, 7, 2.9, 0, t);
        // the hands close on his neck, then let go: the clip says when
        drawRig(Ending13Rig.frame("grab", tm * 15.6, false), 0);
        return null;
    }

    /** A containment corridor, hazard stripes, the cell door QW-178; what comes out from under it. */
    private String memObject(double tm, double t) {
        r.clear(0x040202);
        r.ambR = 0.16; r.ambG = 0.09; r.ambB = 0.09;
        r.fogDensity = 0.07; r.fogR = 24; r.fogG = 6; r.fogB = 6;
        double beat = 0.5 + 0.5 * Math.sin(t * 5);
        r.lights.add(new Soft3D.Light(0, 2.6, -0.2, 1.2, 0.20 + 0.25 * beat, 0.16, 7.5));           // the warning light
        r.shadowDir = new double[]{0, -1, 0.35};
        r.lights.add(new Soft3D.Light(0, 1.6, 4.4, 0.9 * smooth(tm, 0.2, 0.7), 0.7 * smooth(tm, 0.2, 0.7), 0.4 * smooth(tm, 0.2, 0.7), 6));    // light from under the door
        r.lights.add(new Soft3D.Light(r.camX, 1.9, r.camZ + 0.2, 0.9, 0.55, 0.5, 7.5));
        r.lights.add(new Soft3D.Light(0, 2.2, 2.6, 1.3, 0.7, 0.55, 8.0));
        fxBloom = 0.85; fxSsao = 0.8; fxContrast = 1.2; fxSat = 0.85; fxRays = 0.35; fxRayAt = new double[]{0, 2.6, -0.2};
        double walk = smooth(tm, 0, 0.5);
        r.camera(Math.sin(walk * 10) * 0.012, 1.62 + Math.abs(Math.sin(walk * 10)) * 0.018, lerp(-4.2, -1.3, walk), 0, -0.05);
        double x0 = -1.7, x1 = 1.7, z0 = -7, z1 = 4.6, h = 3.0;
        r.matSpec = 0.5; r.matShine = 40;
        r.faceXZ(0, x0, z0, x1, z1, floor, 0xFF997777, 0, 3, 10);
        r.matSpec = 0;
        r.faceXZ(h, x0, z0, x1, z1, wall, 0xFF444444, 0, 3, 10);
        r.faceYZ(x0, 0, z0, h, z1, wall, 0xFFAA9999, 0, 10, 2);
        r.faceYZ(x1, 0, z0, h, z1, wall, 0xFFAA9999, 0, 10, 2);
        r.faceXY(z0, x0, 0, x1, h, wall, 0xFF555555, 0, 3, 2);
        // hazard stripes along the floor edges and the base of the walls
        r.box(x0, 0, z0, x0 + 0.25, 0.01, z1, hazard, 0xFFFFFFFF, 0, 4);
        r.box(x1 - 0.25, 0, z0, x1, 0.01, z1, hazard, 0xFFFFFFFF, 0, 4);
        // the door at the end, with its plate, and the vents
        r.matSpec = 0.4; r.matShine = 24;
        r.box(-1.0, 0, 4.4, 1.0, 2.5, 4.6, steelDoor, 0xFFAA8888, 0, 1);
        r.matSpec = 0;
        r.card(0, 1.95, 4.38, 0.38, 0.12, 0, Soft3D.Tex.text("QW-178", new Font(Font.MONOSPACED, Font.BOLD, 36), new Color(235, 235, 220), new Color(10, 10, 10), 6), 0xFFFFFFFF, 0.45);
        r.box(-0.5, 0, 4.34, 0.5, 0.05, 4.4, Soft3D.Tex.solid(255, 200, 120), 0xFFFFFFFF, smooth(tm, 0.2, 0.7), 1);    // the crack of light under it
        for (int i = 0; i < 2; i++) r.box(-1.4 + i * 2.4, 2.2, 1.6 - 0.0, -1.1 + i * 2.4, 2.7, 1.72, barsTex, 0xFFFFFFFF, 0, 3);
        for (int i = 0; i < 4; i++) fluorescent(0, 2.92, -5 + i * 3.2, 0.9, i != 1 || beat > 0.3);
        dressCorridor(-1.7, 1.7, -7, 4.6, 3.0, 1, t);
        // the shape from under the door: dark, long-armed, low, two yellow eyes
        double rise = smooth(tm, 0.25, 0.92);
        Soft3D.Pose c = pose();
        c.pitch[PlayerBoxes.RIGHT_ARM] = -1.6 + 0.2 * Math.sin(t * 6); c.pitch[PlayerBoxes.LEFT_ARM] = -1.6 - 0.2 * Math.sin(t * 6);
        c.stretch[PlayerBoxes.RIGHT_ARM] = 2.6; c.stretch[PlayerBoxes.LEFT_ARM] = 2.6;
        c.stretch[PlayerBoxes.RIGHT_LEG] = 1.5; c.stretch[PlayerBoxes.LEFT_LEG] = 1.5;
        c.pitch[PlayerBoxes.HEAD] = 0.4 + 0.2 * Math.sin(t * 9);
        double z = lerp(4.2, 0.3, rise);
        double[][] lean = tilt(-0.5 * (1 - rise) - 0.2);
        r.figure(model, skin, new double[]{0 + Math.sin(t * 4) * 0.05, 0, z}, new double[][]{{-1, 0, 0}, {0, lean[1][1], lean[1][2]}, {0, -lean[2][1], -lean[2][2]}}, 0.0625 * lerp(0.9, 1.45, rise), c, 0xFF040404, 0, Soft3D.ALL_PARTS);
        Soft3D.Tex yellow = Soft3D.Tex.solid(255, 214, 90);
        double hy = lerp(0.9, 2.3, rise);
        r.card(-0.11, hy, z - 0.3, 0.04, 0.016, 0, yellow, 0xFFFFFFFF, 1.0);
        r.card(0.11, hy, z - 0.3, 0.04, 0.016, 0, yellow, 0xFFFFFFFF, 1.0);
        return null;
    }

    /** The office: a desk with a lamp and a monitor, blinds with red light through them (the slats throw shadows over the room), the patient. */
    private String memOffice(double tm, double t) {
        r.clear(0x050202);
        r.ambR = 0.09; r.ambG = 0.05; r.ambB = 0.05;
        r.fogDensity = 0.06; r.fogR = 14; r.fogG = 3; r.fogB = 3;
        // light 0 is the window: red sun through the blinds, throwing slat shadows
        r.lights.add(new Soft3D.Light(3.5, 2.2, 1.6, 1.9, 0.55, 0.3, 11));
        r.shadowDir = new double[]{-1, -0.25, -0.35};
        r.lights.add(new Soft3D.Light(-0.55, 1.2, -0.45, 1.0, 0.7, 0.35, 3.5));                      // the desk lamp
        fxBloom = 0.85; fxSsao = 0.8; fxContrast = 1.18; fxSat = 0.92; fxRays = 0.3; fxRayAt = new double[]{3.5, 2.2, 1.6}; fxDofFocus = 2.2; fxDofRange = 2.4;
        double drift = smooth(tm, 0, 1);
        r.camera(lerp(-0.2, 0.05, drift), 1.28, lerp(-2.6, -1.9, drift), lerp(0.18, 0.0, drift), -0.08);
        double x0 = -2.8, x1 = 3.6, z0 = -3.4, z1 = 2.4, h = 3.0;
        r.matSpec = 0.3; r.matShine = 30;
        r.faceXZ(0, x0, z0, x1, z1, floor, 0xFF887766, 0, 5, 4);
        r.matSpec = 0;
        r.faceXZ(h, x0, z0, x1, z1, plaster, 0xFF886666, 0, 4, 3);
        r.faceXY(z1, x0, 0, x1, h, plaster, 0xFFAA8888, 0, 4, 2.2);
        r.faceXY(z0, x0, 0, x1, h, plaster, 0xFF887777, 0, 4, 2.2);
        r.faceYZ(x0, 0, z0, h, z1, plaster, 0xFF998888, 0, 4, 2.2);
        r.faceYZ(x1, 0, z0, h, z1, plaster, 0xFF998888, 0, 4, 2.2);
        // the window with its blinds on the right wall, glowing
        r.card(3.58, 1.7, 0.8, 1.0, 0.9, Math.PI / 2, blinds, 0xFFFFFFFF, 0.9);
        r.bar(new double[]{3.55, 2.62, -0.3}, new double[]{3.55, 2.62, 1.9}, new double[]{0, 0.03, 0}, new double[]{0.03, 0, 0}, steel, 0xFF888888, 0);
        // the desk, a monitor, papers, a lamp, a chair behind it, shelves on the back wall
        r.matSpec = 0.25; r.matShine = 18;
        r.box(-1.1, 0.72, -0.7, 1.1, 0.78, 0.3, deskWood, 0xFFFFFFFF, 0, 2);
        r.matSpec = 0;
        for (int ix = -1; ix <= 1; ix += 2) for (int iz = -1; iz <= 1; iz += 2) r.box(ix * 1.0 - 0.04, 0, -0.2 + iz * 0.4 - 0.04, ix * 1.0 + 0.04, 0.72, -0.2 + iz * 0.4 + 0.04, deskWood, 0xFFBBBBBB, 0, 2);
        r.box(0.2, 0.78, -0.1, 0.8, 1.15, 0.05, Soft3D.Tex.solid(18, 18, 20), 0xFFFFFFFF, 0, 1);
        r.card(0.5, 0.97, -0.115, 0.26, 0.15, 0, Soft3D.Tex.solid(60, 150, 70), 0xFFFFFFFF, 0.7);
        r.box(-0.6, 0.78, -0.55, -0.1, 0.785, -0.2, paper, 0xFFFFFFFF, 0, 4);
        r.box(-0.9, 0.78, -0.45, -0.8, 0.80, -0.35, dark, 0xFFFFFFFF, 0, 1);
        r.bar(new double[]{-0.85, 0.8, -0.4}, new double[]{-0.62, 1.2, -0.45}, new double[]{0.008, 0, 0}, new double[]{0, 0, 0.008}, steel, 0xFF887766, 0);
        r.box(-0.72, 1.18, -0.52, -0.5, 1.24, -0.38, Soft3D.Tex.solid(255, 225, 160), 0xFFFFFFFF, 1.0, 1);
        for (int i = 0; i < 3; i++) r.box(-2.6, 0.2 + i * 0.7, 2.0, -1.0, 0.24 + i * 0.7, 2.35, deskWood, 0xFFBBBBBB, 0, 2);
        r.box(-2.65, 0, 2.0, -2.6, 2.1, 2.35, deskWood, 0xFFBBBBBB, 0, 2);
        r.box(-1.0, 0, 2.0, -0.95, 2.1, 2.35, deskWood, 0xFFBBBBBB, 0, 2);
        // the patient sits across the desk, tired: he slumps as the words come out
        double spill = smooth(tm, 0.12, 0.95);
        Soft3D.Pose p = pose();
        p.pitch[PlayerBoxes.RIGHT_LEG] = -1.55; p.pitch[PlayerBoxes.LEFT_LEG] = -1.55;
        p.pitch[PlayerBoxes.HEAD] = 0.35 + 0.15 * spill;
        p.pitch[PlayerBoxes.RIGHT_ARM] = -0.5; p.pitch[PlayerBoxes.LEFT_ARM] = -0.5;
        r.matSpec = 0.05;
        r.figure(model, skin, 0, 0.5, 1.0, Math.PI, 0.0625 * 1.0, p, dim(62), 0, Soft3D.ALL_PARTS);
        r.matSpec = 0;
        r.box(-0.28, 0, 0.7, 0.28, 0.5, 1.2, deskWood, 0xFF666666, 0, 2);                    // his chair
        // the words come out of him and fly at the player
        Soft3D.Tex words = Soft3D.Tex.text("Don't forget who you are", new Font(Font.SERIF, Font.BOLD, 28), new Color(255, 40, 30), null, 2);
        Random q = new Random(5);
        for (int i = 0; i < 46; i++) {
            double life = clamp01((tm - 0.1 - i * 0.017) / 0.45);
            if (life <= 0) continue;
            double ang = q.nextDouble() * Math.PI * 2, spread = 0.1 + q.nextDouble() * 1.7;
            double x = Math.cos(ang) * spread * life * 1.1, y = 1.35 + Math.sin(ang) * spread * life * 0.7;
            double z = lerp(0.9, r.camZ + 0.5, life) + q.nextDouble() * 0.1;
            double a = 1 - 0.5 * life;
            r.card(x, y, z, 0.4, 0.075, (q.nextDouble() - 0.5) * 0.4 * life, words, 0xFFFFFFFF, 0.95 * a);
        }
        dressOffice(t);
        return tm > 0.55 ? "CRACKS" : null;
    }

    /** A concrete cell with bars and chains, one red bulb, the Mischievous Architect shackled under his sigil; the hands close. */
    private String memCell(double tm, double t) {
        r.clear(0x060101);
        r.ambR = 0.09; r.ambG = 0.03; r.ambB = 0.03;
        r.fogDensity = 0.10; r.fogR = 26; r.fogG = 2; r.fogB = 2;
        double pulse = 0.85 + 0.15 * Math.sin(t * 3);
        r.lights.add(new Soft3D.Light(0, 2.5, 0.5, 1.9 * pulse, 0.18 * pulse, 0.13 * pulse, 7));
        r.shadowDir = new double[]{0, -1, 0.001};
        r.lights.add(new Soft3D.Light(0, 2.6, 1.4, 0.9, 0.2, 0.2, 4.5));
        fxBloom = 1.0; fxSsao = 0.85; fxContrast = 1.2; fxSat = 0.9; fxRays = 0.4; fxRayAt = new double[]{0, 2.5, 0.5};
        double step = smooth(tm, 0.05, 0.45);
        double[] full = cameraFor(new double[]{0, 1.12, 0.9}, gripMid(), 0, -0.1);
        r.camera(Math.sin(t * 0.7) * 0.01, lerp(1.58, full[1], step), lerp(-2.7, full[2], step), 0, -0.1);
        double x0 = -1.6, x1 = 1.6, z0 = -3.4, z1 = 2.2, h = 2.9;
        r.matSpec = 0.45; r.matShine = 30;
        r.faceXZ(0, x0, z0, x1, z1, floor, 0xFF774444, 0, 3, 4);
        r.matSpec = 0;
        r.faceXZ(h, x0, z0, x1, z1, wall, 0xFF442222, 0, 3, 3);
        r.faceXY(z1, x0, 0, x1, h, wall, 0xFF994444, 0, 3, 2.2);
        r.faceYZ(x0, 0, z0, h, z1, wall, 0xFF994444, 0, 4, 2.2);
        r.faceYZ(x1, 0, z0, h, z1, wall, 0xFF994444, 0, 4, 2.2);
        r.faceXY(z0, x0, 0, x1, h, wall, 0xFF663333, 0, 3, 2.2);
        // bars in front, chains from the wall, the bulb
        for (int i = 0; i < 9; i++) r.box(x0 + i * 0.4 - 0.02, 0, -3.0, x0 + i * 0.4 + 0.02, h, -2.96, barsTex, 0xFFAAAAAA, 0, 3);
        r.box(x0, 2.2, -3.02, x1, 2.26, -2.94, barsTex, 0xFFAAAAAA, 0, 3);
        r.box(x0, 0.5, -3.02, x1, 0.56, -2.94, barsTex, 0xFFAAAAAA, 0, 3);
        r.matSpec = 0.6; r.matShine = 40;
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 9; i++) r.box(side * 0.72 - 0.015 + Math.sin(i * 0.9 + t) * 0.015, 2.2 - i * 0.16, 1.9 - 0.02 * i, side * 0.72 + 0.015 + Math.sin(i * 0.9 + t) * 0.015, 2.2 - i * 0.16 + 0.1, 1.94 - 0.02 * i, steel, 0xFFAA9999, 0, 1);
        }
        r.matSpec = 0;
        r.box(-0.05, 2.4, 0.45, 0.05, 2.6, 0.55, Soft3D.Tex.solid(255, 140, 120), 0xFFFFFFFF, 1.0, 1);
        r.bar(new double[]{0, h, 0.5}, new double[]{0, 2.6, 0.5}, new double[]{0.006, 0, 0}, new double[]{0, 0, 0.006}, dark, 0xFFFFFFFF, 0);
        // the sigil glowing behind him
        r.card(0, 2.0, 2.15, 0.55, 0.55, 0, sigil(), 0xFFFFFFFF, 0.95);
        // the Architect: red, on his knees against the wall, head up
        Soft3D.Pose p = pose();
        p.pitch[PlayerBoxes.HEAD] = -0.2 + smooth(tm, 0.5, 0.8) * 0.5;
        p.pitch[PlayerBoxes.RIGHT_ARM] = -1.2; p.pitch[PlayerBoxes.LEFT_ARM] = -1.2;
        p.pitch[PlayerBoxes.RIGHT_LEG] = -1.4; p.pitch[PlayerBoxes.LEFT_LEG] = -1.4;
        p.stretch[PlayerBoxes.RIGHT_LEG] = 0.7; p.stretch[PlayerBoxes.LEFT_LEG] = 0.7;
        r.matSpec = 0.15; r.matShine = 20;
        r.figure(model, skin, 0, 0.18, 1.0, Math.PI, 0.0625 * 1.12, p, 0xFFB03028, 0.08, Soft3D.ALL_PARTS);
        r.matSpec = 0;
        // the hands, closing on his throat
        drawRig(Ending13Rig.frame("grab", tm * 14.0, false), 0);
        dressCell(t);
        return tm > 0.4 ? "THIS IS YOUR FAULT." : null;
    }

    private Soft3D.Tex sigilTex;

    private Soft3D.Tex sigil() {
        if (sigilTex != null) return sigilTex;
        sigilTex = paint(128, 128, sg -> {
            sg.setColor(new Color(255, 70, 70));
            sg.setStroke(new BasicStroke(5f));
            sg.drawPolygon(new int[]{64, 118, 64, 10}, new int[]{10, 64, 118, 64}, 4);
            java.awt.geom.Path2D sp = new java.awt.geom.Path2D.Double();
            for (double a = 0; a < Math.PI * 3.2; a += 0.1) {
                double rr = 4 + a * 5.4;
                double px = 64 + Math.cos(a) * rr, py = 64 + Math.sin(a) * rr;
                if (a == 0) sp.moveTo(px, py); else sp.lineTo(px, py);
            }
            sg.draw(sp);
        });
        return sigilTex;
    }

    /** A dark forest in moonlight (the trees throw shadows over the ground), mist, a fence, a torch; "you are lost"; THIS behind; the blue noise. */
    private String memForest(double tm, double t) {
        r.clear(0x0A1226);
        r.ambR = 0.14; r.ambG = 0.17; r.ambB = 0.30;
        r.fogDensity = 0.065; r.fogR = 12; r.fogG = 20; r.fogB = 42;
        double walk = smooth(tm, 0, 0.55) * 6.0;
        double turn = smooth(tm, 0.55, 0.68) * Math.PI;
        double bob = Math.sin(walk * 5) * 0.035;
        double cz = -3 + (turn > 1.5 ? 6.0 : walk);
        r.camera(Math.sin(walk * 2.5) * 0.08, 1.65 + bob, cz, turn + Math.sin(t * 0.4) * 0.02, -0.05 + (turn > 0 ? 0.05 : 0));
        // light 0 is the moon, high and cold, throwing the shadows of the trunks
        r.lights.add(new Soft3D.Light(r.camX - 4, 22, r.camZ + 3, 2.2, 2.5, 3.2, 70));
        r.shadowDir = new double[]{0.18, -1, -0.12};
        r.lights.add(new Soft3D.Light(r.camX, 1.4, r.camZ + (turn > 1.5 ? -0.8 : 0.8), 1.5, 1.55, 1.6, 8.5));      // the torch
        fxBloom = 0.8; fxSsao = 0.75; fxContrast = 1.15; fxSat = 0.85; fxRays = 0.25; fxRayAt = new double[]{r.camX - 4, 14, r.camZ + (turn > 1.5 ? -14 : 14)};
        r.matSpec = 0.1; r.matShine = 12;
        r.faceXZ(-0.01, -40, -40, 40, 40, ground, 0xFFFFFFFF, 0, 22, 22);
        r.matSpec = 0;
        r.card(r.camX - 4, 15, r.camZ + (turn > 1.5 ? -30 : 30), 1.8, 1.8, turn > 1.5 ? Math.PI : 0, Soft3D.Tex.solid(235, 242, 255), 0xFFFFFFFF, 1.0);
        Random q = new Random(77);
        for (int i = 0; i < 120; i++) {
            double x = (q.nextDouble() - 0.5) * 18, z = -10 + q.nextDouble() * 34;
            if (Math.abs(x) < 1.4) x += x < 0 ? -1.8 : 1.8;
            double h = 6 + q.nextDouble() * 4, w = 0.14 + q.nextDouble() * 0.12;
            r.box(x - w, 0, z - w, x + w, h, z + w, bark, 0xFFFFFFFF, 0, 1.2);
            for (int b = 0; b < 4; b++) {
                double by = 2.2 + q.nextDouble() * 4, len = 0.8 + q.nextDouble() * 1.3, ang = q.nextDouble() * Math.PI * 2;
                double ex = x + Math.cos(ang) * len, ey = by + 0.3 + q.nextDouble() * 0.6, ez = z + Math.sin(ang) * len;
                r.bar(new double[]{x, by, z}, new double[]{ex, ey, ez}, new double[]{0.035, 0, 0}, new double[]{0, 0, 0.035}, bark, 0xFFFFFFFF, 0);
                r.bar(new double[]{ex, ey, ez}, new double[]{ex + Math.cos(ang + 0.7) * 0.5, ey + 0.35, ez + Math.sin(ang + 0.7) * 0.5}, new double[]{0.02, 0, 0}, new double[]{0, 0, 0.02}, bark, 0xFFFFFFFF, 0);
            }
        }
        for (int i = 0; i < 160; i++) {                                                  // dead grass
            double gx = (q.nextDouble() - 0.5) * 16, gz = -8 + q.nextDouble() * 30;
            r.billboard(gx, 0.18, gz, 0.2, 0.18, grass, 0xFFFFFFFF, 0);
        }
        // the fence, chain-link, along the path
        r.matSpec = 0.5; r.matShine = 30;
        for (int i = 0; i < 18; i++) {
            double z = -3 + i * 1.5;
            r.box(-1.7, 0, z - 0.04, -1.6, 1.5, z + 0.04, steel, 0xFFAAAAAA, 0, 1);
            r.faceYZ(-1.65, 0.1, z, 1.35, z + 1.5, chain, 0xFFFFFFFF, 0, 1.5, 1.2);
        }
        r.matSpec = 0;
        // low mist cards
        dressForest(t, turn);
        // THIS, far behind, and then close
        double near = smooth(tm, 0.62, 0.97);
        Soft3D.Pose p = pose();
        p.pitch[PlayerBoxes.RIGHT_ARM] = -0.25; p.pitch[PlayerBoxes.LEFT_ARM] = -0.25;
        p.stretch[PlayerBoxes.RIGHT_ARM] = 2.2; p.stretch[PlayerBoxes.LEFT_ARM] = 2.2;
        p.stretch[PlayerBoxes.RIGHT_LEG] = 1.6; p.stretch[PlayerBoxes.LEFT_LEG] = 1.6;
        p.pitch[PlayerBoxes.HEAD] = 0.15 * Math.sin(t * 7);
        double fz = r.camZ - lerp(9.0, 2.4, near);
        if (turn > 1.5) {
            r.figure(model, skin, 0.4, 0, fz, 0, 0.0625 * 1.7, p, 0xFF000000, 0, Soft3D.ALL_PARTS);
            r.card(0.25, 3.15, fz + 0.5, 0.06, 0.022, 0, Soft3D.Tex.solid(255, 220, 140), 0xFFFFFFFF, 1.0);
            r.card(0.55, 3.15, fz + 0.5, 0.06, 0.022, 0, Soft3D.Tex.solid(255, 220, 140), 0xFFFFFFFF, 1.0);
        }
        return tm > 0.82 ? "BLUE" : tm > 0.1 ? "LOST" : null;
    }

    // ------------------------------------------------------------------ dressing the places, so none is an empty box

    private Soft3D.Tex cabinetTex, clockTex, rugTex, cobwebTex, signTexNo;

    private Soft3D.Tex cabinet() {
        if (cabinetTex != null) return cabinetTex;
        cabinetTex = paint(32, 64, g2 -> {
            g2.setColor(new Color(92, 98, 104)); g2.fillRect(0, 0, 32, 64);
            g2.setColor(new Color(60, 64, 70));
            for (int i = 0; i < 4; i++) { g2.drawRect(1, 1 + i * 16, 29, 14); g2.fillRect(11, 6 + i * 16, 10, 2); }
            g2.setColor(new Color(130, 90, 60, 90)); g2.fillRect(0, 50, 32, 14);
        });
        return cabinetTex;
    }

    private Soft3D.Tex clockFace() {
        if (clockTex != null) return clockTex;
        clockTex = paint(64, 64, g2 -> {
            g2.setColor(new Color(0, 0, 0, 0)); g2.fillRect(0, 0, 64, 64);
            g2.setColor(new Color(30, 26, 24)); g2.fillOval(2, 2, 60, 60);
            g2.setColor(new Color(222, 214, 190)); g2.fillOval(6, 6, 52, 52);
            g2.setColor(new Color(30, 26, 24)); g2.setStroke(new BasicStroke(2f));
            for (int i = 0; i < 12; i++) { double a = i * Math.PI / 6; g2.drawLine(32 + (int) (Math.cos(a) * 22), 32 + (int) (Math.sin(a) * 22), 32 + (int) (Math.cos(a) * 26), 32 + (int) (Math.sin(a) * 26)); }
            g2.setStroke(new BasicStroke(3f)); g2.drawLine(32, 32, 32, 14);
            g2.setStroke(new BasicStroke(2f)); g2.drawLine(32, 32, 44, 38);
        });
        return clockTex;
    }

    private Soft3D.Tex rug() {
        if (rugTex != null) return rugTex;
        rugTex = paint(64, 64, g2 -> {
            g2.setColor(new Color(92, 36, 34)); g2.fillRect(0, 0, 64, 64);
            g2.setColor(new Color(150, 120, 70)); g2.setStroke(new BasicStroke(2f)); g2.drawRect(3, 3, 57, 57); g2.drawRect(8, 8, 47, 47);
            g2.setColor(new Color(60, 22, 22)); g2.fillOval(20, 20, 24, 24);
            g2.setColor(new Color(0, 0, 0, 60)); g2.fillOval(8, 40, 34, 18);
        });
        return rugTex;
    }

    private Soft3D.Tex cobweb() {
        if (cobwebTex != null) return cobwebTex;
        cobwebTex = paint(64, 64, g2 -> {
            g2.setColor(new Color(0, 0, 0, 0)); g2.fillRect(0, 0, 64, 64);
            g2.setColor(new Color(220, 220, 215, 215)); g2.setStroke(new BasicStroke(1f));
            for (int i = 0; i <= 6; i++) g2.drawLine(0, 0, (int) (64 * Math.sin(i * Math.PI / 12)), (int) (64 * Math.cos(i * Math.PI / 12)));
            for (int ring = 1; ring < 7; ring++) { int rr = ring * 9; g2.drawArc(-rr, -rr, rr * 2, rr * 2, 270, 90); }
        });
        return cobwebTex;
    }

    private Soft3D.Tex noEntry() {
        if (signTexNo != null) return signTexNo;
        signTexNo = paint(64, 32, g2 -> {
            g2.setColor(new Color(150, 24, 24)); g2.fillRect(0, 0, 64, 32);
            g2.setColor(new Color(235, 232, 220)); g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            g2.drawString("NO ENTRY", 6, 14); g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8)); g2.drawString("PQ SECTOR", 8, 26);
            g2.setColor(new Color(0, 0, 0, 120)); g2.drawRect(0, 0, 63, 31);
        });
        return signTexNo;
    }

    /** A long corridor made less of a tube: pilasters and lintels every few metres, cable trays, inset panels, doors with numbers, lamps with halos, puddles. */
    private void dressCorridor(double x0, double x1, double z0, double z1, double h, int style, double t) {
        boolean red = style == 1;
        int arch = red ? 0xFF8A5450 : 0xFF98A0A8, panel = red ? 0xFF5A3030 : 0xFF777E86;
        r.matSpec = 0.3; r.matShine = 24;
        for (double z = z0 + 1.5; z < z1 - 1.2; z += 3.0) {
            r.box(x0, 0, z, x0 + 0.24, h, z + 0.2, steel, arch, 0, 1);
            r.box(x1 - 0.24, 0, z, x1, h, z + 0.2, steel, arch, 0, 1);
            r.box(x0, h - 0.34, z, x1, h, z + 0.2, steel, arch, 0, 1);
            r.box(x0 + 0.24, h - 0.42, z, x1 - 0.24, h - 0.34, z + 0.2, dark, 0xFFFFFFFF, 0, 1);
        }
        // trays and cables along both walls
        for (int side = 0; side < 2; side++) {
            double wx = side == 0 ? x0 : x1 - 0.2;
            r.box(wx, 2.1, z0, wx + 0.2, 2.16, z1, steel, arch, 0, 1);
            for (int c = 0; c < 4; c++) {
                double sag = 0.03;
                r.bar(new double[]{wx + 0.04 + c * 0.04, 2.2, z0}, new double[]{wx + 0.04 + c * 0.04, 2.2, z1}, new double[]{0.012, 0, 0}, new double[]{0, 0.012, 0}, dark, 0xFFFFFFFF, 0);
            }
        }
        r.matSpec = 0;
        // inset panels with a lighter border between the pilasters
        for (double z = z0 + 0.6; z < z1 - 1.5; z += 1.5) {
            r.box(x0 + 0.01, 0.6, z, x0 + 0.06, 1.9, z + 1.0, wallLow, panel, 0, 1);
            r.box(x1 - 0.06, 0.6, z, x1 - 0.01, 1.9, z + 1.0, wallLow, panel, 0, 1);
        }
        // doors with plates, a notice, an extinguisher, an emergency lamp, drains and puddles
        Random q = new Random(red ? 71 : 17);
        for (double z = z0 + 2.4; z < z1 - 2.0; z += 3.0) {
            boolean left = q.nextBoolean();
            double dx = left ? x0 : x1;
            double sgn = left ? 1 : -1;
            r.box(left ? dx + 0.02 : dx - 0.12, 0, z - 0.45, left ? dx + 0.12 : dx - 0.02, 2.15, z + 0.45, steelDoor, 0xFFAAAAAA, 0, 1);
            r.faceYZ(left ? dx + 0.125 : dx - 0.125, 1.5, z - 0.12, 1.66, z + 0.12, noticeBoard(), 0xFFFFFFFF, 0.1, 1, 1);
            r.box(left ? dx + 0.13 : dx - 0.14, 1.78, z - 0.1, left ? dx + 0.14 : dx - 0.13, 1.95, z + 0.1, Soft3D.Tex.solid(240, 236, 210), 0xFFFFFFFF, 0.4, 1);
        }
        for (double z = z0 + 3.6; z < z1 - 2.0; z += 4.5) {
            r.bar(new double[]{x1 - 0.25, 0.1, z}, new double[]{x1 - 0.25, 0.5, z}, new double[]{0.07, 0, 0}, new double[]{0, 0, 0.07}, Soft3D.Tex.solid(180, 30, 30), 0xFFFFFFFF, 0);
            r.box(x1 - 0.1, 1.0, z - 0.01, x1 - 0.04, 1.3, z + 0.01, Soft3D.Tex.solid(170, 170, 160), 0xFFFFFFFF, 0, 1);
        }
        for (double z = z0 + 3.0; z < z1 - 2.0; z += 6.0) {                                          // an emergency lamp on the wall, with its halo
            r.box(x0 + 0.02, 2.35, z - 0.1, x0 + 0.12, 2.5, z + 0.1, red ? Soft3D.Tex.solid(255, 70, 60) : Soft3D.Tex.solid(255, 235, 200), 0xFFFFFFFF, 0.9, 1);
            sprite(x0 + 0.2, 2.42, z, 0.45, 0.45, red ? 0xFFFF5040 : 0xFFFFE8C8, 0.40, 1.0);
        }
        r.matSpec = 0.9; r.matShine = 80;
        for (int k = 0; k < 4; k++) r.faceXZ(0.004 + k * 0.0003, -0.7 + q.nextDouble() * 1.4 - 0.4, z0 + 2 + k * 3.2, 0.3 + q.nextDouble() * 0.6, z0 + 3.1 + k * 3.2, puddleTex(), 0xFFFFFFFF, 0, 1, 1);
        r.matSpec = 0;
        for (double z = z0 + 2.2; z < z1 - 1; z += 3.4) r.box(-0.3, 0.003, z, 0.3, 0.007, z + 0.5, Soft3D.Tex.solid(8, 8, 10), 0xFFFFFFFF, 0, 1);     // drains
        // the lamps' light in the air, and the dust in it
        for (double z = z0 + 2.5; z < z1 - 1; z += 3.2) {
            lightCone(new double[]{0, h - 0.08, z}, new double[]{0, 0.0, z}, 0.12, 1.1, red ? 0xFFFF9888 : 0xFFE4ECFF, 0.10, 14);
            sprite(0, h - 0.1, z, 0.55, 0.55, red ? 0xFFFFC0A8 : 0xFFEAF2FF, 0.35, 1.0);
        }
        motes(new double[]{0, 1.4, (z0 + z1) / 2}, (x1 - x0) / 2, 1.3, (z1 - z0) / 2, 120, t, 31, red ? 0xFFFFD0C0 : 0xFFE8F0FF, 0.012, 0.8);
        mistLayer(0.2, 10, 0, (z0 + z1) / 2, red ? 0xFFC09890 : 0xFFB0BCC8, 0.28, t, 0.012, 3.0);
        mistLayer(0.9, 10, 0, (z0 + z1) / 2, red ? 0xFFA07870 : 0xFF98A4B0, 0.16, t, -0.008, 2.4);
    }

    /** The office: filing cabinets, books, a clock, pictures, a rug, a coat on a stand, a cup of steam, and the window's light lying across the room in dust. */
    private void dressOffice(double t) {
        r.matSpec = 0.3; r.matShine = 24;
        for (int i = 0; i < 3; i++) r.box(-2.78, 0, -2.6 + i * 0.7, -2.2, 1.35, -2.1 + i * 0.7, cabinet(), 0xFFCCBBBB, 0, 1);       // filing cabinets along the left wall
        r.box(-2.78, 1.35, -2.6, -2.2, 1.4, -0.5, steel, 0xFF888888, 0, 1);
        r.matSpec = 0;
        Random q = new Random(88);
        for (int i = 0; i < 3; i++) {                                                               // books on the shelves of the back wall
            double y = 0.24 + i * 0.7;
            double x = -2.55;
            while (x < -1.15) {
                double w = 0.05 + q.nextDouble() * 0.07, hh = 0.2 + q.nextDouble() * 0.2;
                r.box(x, y, 2.02, x + w, y + hh, 2.3, Soft3D.Tex.solid(60 + q.nextInt(150), 30 + q.nextInt(90), 30 + q.nextInt(80)), 0xFFAAAAAA, 0, 1);
                x += w + 0.005;
            }
        }
        r.faceXY(2.39, -0.5, 1.5, 0.3, 2.3, noticeBoard(), 0xFF998888, 0.05, 1, 1);               // frames on the back wall
        r.faceXY(2.39, 0.6, 1.7, 1.2, 2.2, scrap(), 0xFFCCBBAA, 0.05, 1, 1);
        r.card(1.9, 2.15, 2.38, 0.2, 0.2, 0, clockFace(), 0xFFFFFFFF, 0.1);                         // a clock
        r.faceXZ(0.005, -1.5, -1.4, 1.5, 1.7, rug(), 0xFF998888, 0, 1, 1);                          // the rug under the desk
        r.bar(new double[]{3.1, 0, -2.8}, new double[]{3.1, 1.7, -2.8}, new double[]{0.02, 0, 0}, new double[]{0, 0, 0.02}, steel, 0xFF777777, 0);       // a coat stand with a coat
        r.box(2.95, 1.0, -2.88, 3.25, 1.62, -2.72, Soft3D.Tex.solid(70, 62, 58), 0xFFFFFFFF, 0, 1);
        r.box(0.9, 0.78, -0.5, 0.98, 0.86, -0.42, white, 0xFFDDDDDD, 0, 1);                         // a cup
        for (int k = 0; k < 5; k++) sprite(0.94 + Math.sin(t * 1.3 + k) * 0.015, 0.9 + ((t * 0.3 + k * 0.2) % 1.0) * 0.3, -0.46, 0.02, 0.03, 0xFFE8E0D8, 0.20 * (1 - ((t * 0.3 + k * 0.2) % 1.0)), 0.5);
        for (int k = 0; k < 7; k++) r.box(-0.62 + k * 0.01, 0.78 + k * 0.004, -0.55 + k * 0.03, -0.14 + k * 0.01, 0.785 + k * 0.004, -0.2 + k * 0.03, scrap(), 0xFFFFFFFF, 0, 1);   // a stack of papers
        // the window's light across the room in dust
        lightCone(new double[]{3.5, 2.6, 0.8}, new double[]{0.6, 0.0, 0.2}, 0.7, 2.0, 0xFFFF9A58, 0.12, 16);
        lightCone(new double[]{3.5, 1.7, 0.8}, new double[]{0.0, 0.0, -0.3}, 0.5, 1.6, 0xFFFFB070, 0.10, 14);
        motes(new double[]{1.4, 1.3, 0.4}, 1.6, 1.1, 1.4, 130, t, 41, 0xFFFFC890, 0.012, 0.9);
        sprite(-0.64, 1.22, -0.45, 0.6, 0.6, 0xFFFFE0B0, 0.40, 1.0);                                // the desk lamp's halo
        mistLayer(0.3, 6, 0.4, 0, 0xFFC09080, 0.16, t, 0.01, 2.0);
    }

    /** The cell: straw, a bucket, a drain, scratches and stains on the walls, webs in the corners, a rat, a drip, the bulb's red light in the air. */
    private void dressCell(double t) {
        Random q = new Random(404);
        for (int i = 0; i < 40; i++) {                                                               // straw on the floor
            double px = -1.4 + q.nextDouble() * 1.4, pz = -0.5 + q.nextDouble() * 2.4, a = q.nextDouble() * 6.28;
            r.bar(new double[]{px, 0.012, pz}, new double[]{px + Math.cos(a) * 0.35, 0.012, pz + Math.sin(a) * 0.35}, new double[]{0.008, 0, 0}, new double[]{0, 0.004, 0}, Soft3D.Tex.solid(196, 170, 90), 0xFFAA8866, 0);
        }
        r.matSpec = 0.4; r.matShine = 18;
        r.bar(new double[]{1.0, 0, 0.2}, new double[]{1.0, 0.32, 0.2}, new double[]{0.17, 0, 0}, new double[]{0, 0, 0.17}, steel, 0xFF7A7E86, 0);    // a bucket
        r.bar(new double[]{1.0, 0.31, 0.2}, new double[]{1.0, 0.33, 0.2}, new double[]{0.19, 0, 0}, new double[]{0, 0, 0.19}, dark, 0xFFFFFFFF, 0);
        r.matSpec = 0;
        r.box(-0.35, 0.003, -1.2, 0.35, 0.007, -0.7, Soft3D.Tex.solid(8, 4, 4), 0xFFFFFFFF, 0, 1);       // the drain
        r.matSpec = 0.9; r.matShine = 70;
        r.faceXZ(0.005, -0.8, -1.4, 0.2, -0.4, puddleTex(), 0xFFAA6666, 0, 1, 1);
        r.matSpec = 0;
        r.faceYZ(-1.595, 0.9, -0.6, 1.8, 1.4, tallyMarks(), 0xFFFFFFFF, 0.05, 1, 1);                 // scratches on the left wall
        r.faceYZ(1.595, 0.8, -1.2, 1.6, -0.3, bloodHand(), 0xFFCC8888, 0, 1, 1);                     // a hand on the right
        r.faceXY(2.19, -1.2, 0.6, -0.5, 1.2, noEntry(), 0xFFAAAAAA, 0, 1, 1);
        for (int k = 0; k < 3; k++) {                                                                // webs in the corners
            double sx = k % 2 == 0 ? 1.0 : -1.0;
            r.quad(new double[][]{{sx * 1.59, 2.88, 2.0 - k * 0.3}, {sx * 1.59, 2.88, 2.0 - k * 0.3 - 0.5 * sx}, {sx * 1.59, 2.4, 2.0 - k * 0.3 - 0.5 * sx}, {sx * 1.59, 2.4, 2.0 - k * 0.3}},
                    new double[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}}, cobweb(), 0xFFFFFFFF, 0.2);
        }
        // a rat along the foot of the wall, a drip
        double rx = -1.4 + ((t * 0.4) % 1.0) * 2.6;
        r.box(rx, 0.0, 1.8, rx + 0.12, 0.07, 1.86, dark, 0xFFAA9988, 0, 1);
        r.bar(new double[]{rx, 0.02, 1.83}, new double[]{rx - 0.12, 0.012, 1.83 + Math.sin(t * 10) * 0.03}, new double[]{0.004, 0, 0}, new double[]{0, 0.004, 0}, dark, 0xFFFFFFFF, 0);
        double ph = (t * 0.6) % 1.0;
        sprite(-1.1, 2.7 - ph * 2.69, 0.4, 0.01, 0.025, 0xFFC09090, 0.9, 0.5);
        // the red light in the air
        lightCone(new double[]{0, 2.6, 0.5}, new double[]{0, 0.0, 0.5}, 0.05, 1.6, 0xFFFF6048, 0.12, 16);
        sprite(0, 2.5, 0.5, 0.7, 0.7, 0xFFFF5038, 0.45, 1.0);
        motes(new double[]{0, 1.3, 0.2}, 1.4, 1.2, 2.0, 90, t, 51, 0xFFFFB0A0, 0.010, 0.8);
        mistLayer(0.25, 5, 0, 0, 0xFFC08078, 0.20, t, 0.01, 1.6);
    }

    /** The forest: the moon's light coming down through the trees in beams, fireflies, ferns, fallen logs, rocks, mist in layers, a sign on the fence. */
    private void dressForest(double t, double turn) {
        Random q = new Random(909);
        double cz = r.camZ, cx = r.camX;
        for (int i = 0; i < 14; i++) {                                                               // rocks
            double rx = (q.nextDouble() - 0.5) * 12, rz = cz - 6 + q.nextDouble() * 22;
            if (Math.abs(rx) < 1.2) continue;
            double sz = 0.15 + q.nextDouble() * 0.35;
            r.box(rx - sz, 0, rz - sz * 0.8, rx + sz, sz * 0.9, rz + sz * 0.8, rust, 0xFF667070, 0, 1.5);
        }
        for (int i = 0; i < 4; i++) {                                                                // fallen logs
            double lx = (q.nextDouble() - 0.5) * 9, lz = cz - 3 + q.nextDouble() * 18, a = q.nextDouble() * 3.14;
            if (Math.abs(lx) < 1.6) lx += lx < 0 ? -2.2 : 2.2;
            r.bar(new double[]{lx, 0.2, lz}, new double[]{lx + Math.cos(a) * 3.0, 0.22, lz + Math.sin(a) * 3.0}, new double[]{0.2, 0, 0}, new double[]{0, 0.2, 0}, bark, 0xFF8A8A88, 0);
        }
        for (int i = 0; i < 60; i++) {                                                               // ferns along the path
            double fx = (q.nextDouble() - 0.5) * 12, fz = cz - 4 + q.nextDouble() * 24;
            if (Math.abs(fx) < 0.9) continue;
            for (int leaf = 0; leaf < 4; leaf++) {
                double a = leaf * 1.57 + q.nextDouble();
                r.bar(new double[]{fx, 0.02, fz}, new double[]{fx + Math.cos(a) * 0.45, 0.28, fz + Math.sin(a) * 0.45}, new double[]{0.03, 0, 0}, new double[]{0, 0.004, 0}, grass, 0xFFAABBAA, 0);
            }
        }
        r.faceYZ(-1.62, 0.9, cz + 3.0, 1.3, cz + 3.7, noEntry(), 0xFFAAAAAA, 0, 1, 1);               // a sign on the fence
        // beams of the moon through the trunks
        double dir = turn > 1.5 ? -1 : 1;
        for (int k = 0; k < 5; k++) {
            double bx = cx - 4 + k * 1.1, bz = cz + dir * (3.0 + k * 1.8);
            lightCone(new double[]{bx - 2.0, 14, bz + 2.0}, new double[]{bx, 0.0, bz}, 0.25, 1.1, 0xFFB0C8FF, 0.10, 12);
        }
        motes(new double[]{cx, 1.4, cz + dir * 4}, 5, 1.6, 7, 160, t, 61, 0xFFA8D8FF, 0.014, 0.9);       // fireflies of cold light
        for (int k = 0; k < 4; k++) mistLayer(0.3 + k * 0.7, 24, cx, cz + dir * 6, 0xFF8AA0C8, 0.20 - k * 0.03, t, (k % 2 == 0 ? 1 : -1) * 0.006, 4.0);
    }

    private Soft3D.Tex mist;

    private Soft3D.Tex mistTex() {
        if (mist != null) return mist;
        mist = paint(32, 32, g2 -> {
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
                double d = Math.hypot(x - 15.5, y - 15.5) / 15.5;
                int a = (int) Math.max(0, 255 * (1 - d) * (1 - d));
                // the texture is alpha-tested, so give it a ragged edge instead of a soft one
                if (a < 140) continue;
                g2.setColor(new Color(255, 255, 255, 255));
                g2.fillRect(x, y, 1, 1);
            }
        });
        return mist;
    }

    /** The void: a black glossy floor, the Architects around the player in their colours, and the same two words everywhere. */
    private String memFault(double tm, double t) {
        r.clear(0x020203);
        r.ambR = 0.05; r.ambG = 0.05; r.ambB = 0.06;
        r.fogDensity = 0.05;
        double spin = t * 0.22;
        r.camera(0, 1.6, 0, spin, -0.04);
        int[] cols = {0xFF4678FF, 0xFFFFC23C, 0xFFFF3030, 0xFFB25CFF};
        double[][] lc = {{0.3, 0.5, 1.0}, {1.0, 0.76, 0.24}, {1.0, 0.18, 0.18}, {0.7, 0.36, 1.0}};
        double a = smooth(tm, 0, 0.2);
        r.matSpec = 0.9; r.matShine = 60;
        r.faceXZ(0, -12, -12, 12, 12, Soft3D.Tex.solid(10, 10, 12), 0xFFFFFFFF, 0, 1, 1);
        r.matSpec = 0;
        for (int i = 0; i < 4; i++) {
            double ang = i * Math.PI / 2;
            double x = Math.sin(ang) * 3.4, z = Math.cos(ang) * 3.4;
            r.lights.add(new Soft3D.Light(x * 0.8, 2.6, z * 0.8, lc[i][0] * a * 1.8, lc[i][1] * a * 1.8, lc[i][2] * a * 1.8, 9));
            Soft3D.Pose p = pose();
            p.pitch[PlayerBoxes.HEAD] = 0.2 + 0.05 * Math.sin(t * 2 + i);
            r.matSpec = 0.3; r.matShine = 24;
            r.figure(model, skin, x, 0, z, ang + Math.PI, 0.0625 * 1.2, p, mix(cols[i], 0.55), 0.12 * a, Soft3D.ALL_PARTS);
            r.matSpec = 0;
            Soft3D.Tex orb = Soft3D.Tex.solid((cols[i] >> 16) & 255, (cols[i] >> 8) & 255, cols[i] & 255);
            r.billboard(x, 2.9 + 0.05 * Math.sin(t * 2 + i), z, 0.32, 0.32, orb, 0xFFFFFFFF, 1.0 * a);
        }
        r.shadowDir = new double[]{0, -1, 0.001};
        fxBloom = 1.2; fxSsao = 0.6; fxContrast = 1.25; fxSat = 1.0;
        Soft3D.Tex words = Soft3D.Tex.text("YOUR FAULT.", new Font(Font.SERIF, Font.BOLD, 44), new Color(255, 30, 24), null, 2);
        Random q = new Random(3);
        int count = (int) (12 + 80 * smooth(tm, 0.1, 0.9));
        for (int i = 0; i < count; i++) {
            double ang = q.nextDouble() * Math.PI * 2, dist = 2.2 + q.nextDouble() * 3.2;
            double y = 0.4 + q.nextDouble() * 3.2;
            r.card(Math.sin(ang) * dist, y, Math.cos(ang) * dist, 0.5, 0.12, ang, words, 0xFFFFFFFF, 0.95);
        }
        return null;
    }

    private static int mix(int argb, double k) {
        int rr = (int) (((argb >> 16) & 255) * k), gg = (int) (((argb >> 8) & 255) * k), bb = (int) ((argb & 255) * k);
        return 0xFF000000 | (rr << 16) | (gg << 8) | bb;
    }

    // ------------------------------------------------------------------ the finale (only hinted at)

    /** The look of the picture when it has become hard to carry: the sides close in, the colour drains, the focus slides, the breath is loud. */
    private void weigh(double w, double t) {
        heavy = w;
        fxContrast = 1.05 + 0.12 * w;
        fxSat = 0.95 - 0.55 * w;
        fxBloom = 0.9 + 0.7 * w;
        fxSsao = 0.8;
    }

    /**
     * Looking down at one's own arms in weak light: the left forearm held out palm up, the right hand with the knife drawn across it six times.
     * Nothing is shown of it but movement, light and colour: a line of red light for each stroke, a glow, the picture breathing with it, the
     * head sinking and the world closing in a little more with every one.
     */
    private void arm(double tm, double t) {
        armAt(tm * 14.0, tm, t);
    }

    /** The same, at a given moment {@code ct} of the clip (tm only drives the red glow). */
    private void armAt(double ct, double tm, double t) {
        bodyPitch = -0.15;
        vmScale = 0.62;
        ARM_TINT = 0xFF9C948E;
        int done = 0;
        for (int i = 0; i < 6; i++) if (ct > Ending13Rig.strokeStart(i) + Ending13Rig.strokeLength(i) * 0.8) done = i + 1;
        double w = clamp01((done + smooth(ct, Ending13Rig.strokeStart(Math.min(5, done)), Ending13Rig.strokeStart(Math.min(5, done)) + 1.0) * 0.5) / 6.0);
        weigh(w * 0.7, t);
        flashKick = 0;
        r.clear(0x040405);
        r.ambR = 0.22; r.ambG = 0.22; r.ambB = 0.25;
        r.fogDensity = 0.05;
        double breathe = 0.5 + 0.5 * Math.sin(t * (4.5 - 2.0 * w));
        double sag = 0.25 * w + 0.03 * Math.sin(t * 0.9) * w;
        r.camera(Math.sin(t * 0.8) * 0.004, 1.52 - 0.12 * w, 0, Math.sin(t * 0.37) * 0.01 * (1 + 2 * w), -0.80 - sag * 0.4 + Math.sin(t * 0.6) * 0.01);
        r.lights.add(new Soft3D.Light(0, 2.0, 0.45, 1.25 * (1 - 0.35 * w), 1.3 * (1 - 0.35 * w), 1.4 * (1 - 0.35 * w), 6.5));
        r.shadowDir = new double[]{0, -1, 0.2};
        double red = smooth(tm, 0.1, 1.0);
        r.lights.add(new Soft3D.Light(0, 1.0, 0.45, 1.3 * red * (0.7 + 0.3 * breathe), 0.04, 0.03, 2.8));
        fxDofFocus = 0.75; fxDofRange = 0.6 - 0.35 * w;
        r.matSpec = 0.15; r.matShine = 14;
        r.faceXZ(0.2, -3, -1, 3, 3, floor, 0xFFB0B0B0, 0, 3, 3);
        r.matSpec = 0;
        Ending13Rig.Frame f = Ending13Rig.frame("cut", ct, false);
        drawRig(f, 0);
        double kick = 0;
        for (int i = 0; i < 6; i++) {                                                  // a mark on the skin for each stroke
            double st = Ending13Rig.strokeStart(i), ln = Ending13Rig.strokeLength(i);
            double a = smooth(ct, st + ln * 0.4, st + ln * 0.8);
            kick = Math.max(kick, Math.exp(-Math.abs(ct - (st + ln * 0.6)) * 5.0));
            if (a <= 0) continue;
            double[] c = markOnForearm(f, 20.8 - i * 0.95 + (((i * 7) % 5) - 2) * 0.16, a, i);
            if (c != null) r.lights.add(new Soft3D.Light(c[0], c[1] + 0.04, c[2], 0.8 * a, 0.06 * a, 0.05 * a, 0.45));
        }
        flashKick = kick;
        drawKnife(f.right(), 0, Math.min(1.0, done / 4.0));
        fxBloom += 0.9 * flashKick;
        fxDofRange = Math.max(0.12, fxDofRange - 0.4 * flashKick);
        fade = Math.max(fade, 0);
        kickFlash = 0.30 * flashKick;
    }


    /** A ragged mark with swollen edges and a dark depth, wider in the middle; each one a little different. */
    private Soft3D.Tex markTex(int seed) {
        Soft3D.Tex cached = marks[seed % marks.length];
        if (cached != null) return cached;
        Random q = new Random(900 + seed * 31L);
        Soft3D.Tex t = paint(96, 14, g2 -> {
            double phase = q.nextDouble() * 6, tilt = (q.nextDouble() - 0.5) * 3.2;
            double ws = 0.55 + q.nextDouble() * 1.15;                             // how wide this one is
            double skew = 0.55 + q.nextDouble() * 1.1;                            // where along it is the widest
            double deepness = 0.25 + q.nextDouble() * 0.5;                        // how dark its middle goes
            int n = 48;
            double[] cy = new double[n + 1], w = new double[n + 1];
            for (int i = 0; i < n; i++) { }
            for (int i = 0; i <= n; i++) {
                double x = i / (double) n;
                cy[i] = 7 + tilt * (x - 0.5) * 2 + Math.sin(x * (6 + q.nextDouble() * 5) + phase) * (0.5 + 0.9 * q.nextDouble()) + (q.nextDouble() - 0.5) * 0.9;
                double prof = Math.sin(Math.PI * Math.pow(Math.min(1, x * 1.02), skew));
                w[i] = Math.max(0.35, (0.7 + 2.1 * prof) * ws + (q.nextDouble() - 0.5) * 0.8);
            }
            int gapAt = q.nextInt(3) == 0 ? 10 + q.nextInt(28) : -1;              // some are broken in two
            for (int layer = 0; layer < 3; layer++) {
                double k = layer == 0 ? 1.55 : layer == 1 ? 1.0 : deepness;
                java.awt.geom.Path2D path = new java.awt.geom.Path2D.Double();
                boolean open = false;
                for (int i = 0; i <= n; i++) {
                    if (gapAt >= 0 && i >= gapAt && i < gapAt + 3) {
                        if (open) { for (int j = i - 1; j >= 0 && j >= i - 1; j--) { } path.closePath(); open = false; }
                        continue;
                    }
                    double x = 2 + (i / (double) n) * 92;
                    if (!open) { path.moveTo(x, cy[i] - w[i] * k); open = true; } else path.lineTo(x, cy[i] - w[i] * k);
                }
                // down the other edge, back to the start of the last run
                int start = gapAt >= 0 && gapAt + 3 <= n ? gapAt + 3 : 0;
                for (int i = n; i >= start; i--) path.lineTo(2 + (i / (double) n) * 92, cy[i] + w[i] * k);
                path.closePath();
                g2.setColor(layer == 0 ? new Color(176, 70, 66) : layer == 1 ? new Color(112, 14, 20) : new Color(46, 2, 6));
                g2.fill(path);
            }
            g2.setColor(new Color(150, 40, 40));
            g2.setStroke(new BasicStroke(0.8f));
            for (int i = 4; i < n - 4; i += 2) if (q.nextInt(3) > 0) g2.drawLine((int) (2 + i * 92.0 / n), (int) (cy[i] - w[i] * 0.5), (int) (2 + (i + 1) * 92.0 / n), (int) (cy[i + 1] - w[i + 1] * 0.5));
            // a short cut branching off some of them
            if (q.nextInt(2) == 0) {
                int at = 8 + q.nextInt(30);
                g2.setColor(new Color(112, 14, 20));
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawLine((int) (2 + at * 92.0 / n), (int) cy[at], (int) (2 + at * 92.0 / n) + 5 + q.nextInt(6), (int) cy[at] + (q.nextBoolean() ? -4 : 4));
            }
        });
        marks[seed % marks.length] = t;
        return t;
    }

    private final Soft3D.Tex[] marks = new Soft3D.Tex[12];

    /**
     * Puts a mark on the skin of the left forearm at a height of the arm (in rest-pose pixels), across the face that is turned towards the
     * camera, carried with the arm wherever it is. Returns where it is in the world (for a light), or null.
     */
    private double[] markOnForearm(Ending13Rig.Frame f, double restY, double across, int seed) {
        double[][] bm = frameBasis();
        double[] o = frameOrigin(bm);
        double[] cam = {r.camX, r.camY, r.camZ};
        Ending13Rig.Quad best = null;
        double bestScore = -1e9;
        for (Ending13Rig.Quad q : f.quads()) {
            if (q.joint() != 6 && q.joint() != 7) continue;                      // L_UPPER_ARM, L_LOWER_ARM
            double[][] rc = q.rest();
            double ya = Math.min(rc[0][1], rc[3][1]), yb = Math.max(rc[0][1], rc[3][1]);
            if (restY < ya - 1e-6 || restY > yb + 1e-6 || Math.abs(rc[0][1] - rc[3][1]) < 1e-6) continue;       // a side face that holds this height
            double[][] w = new double[4][];
            for (int i = 0; i < 4; i++) w[i] = stageToWorld(q.corners()[i], bm, o);
            double[] n = cross(sub(w[1], w[0]), sub(w[3], w[0]));
            double nl = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]) + 1e-9;
            double[] mid = {(w[0][0] + w[2][0]) / 2, (w[0][1] + w[2][1]) / 2, (w[0][2] + w[2][2]) / 2};
            double[] toCam = sub(cam, mid);
            double tl = Math.sqrt(toCam[0] * toCam[0] + toCam[1] * toCam[1] + toCam[2] * toCam[2]) + 1e-9;
            double facing = Math.abs((n[0] * toCam[0] + n[1] * toCam[1] + n[2] * toCam[2]) / (nl * tl));
            double outer = Math.abs(rc[0][0] - (rc[0][0] > 0 ? 5.5 : -5.5)) + Math.abs(rc[0][2]);          // the outer layer stands further from the arm's axis
            double score = facing * 10 + outer * 0.2 - tl * 6 - Math.abs(restY - (ya + yb) / 2) * 0.01;      // turned to us, on the outer layer, and the nearer of two opposite faces
            if (score > bestScore) { bestScore = score; best = q; }
        }
        if (best == null) return null;
        double[][] rc = best.rest();
        double top = rc[0][1], bot = rc[3][1];
        double thick = 0.30 + ((seed * 29) % 5) * 0.10;                          // how deep the mark is drawn on the arm
        double slant = (((seed * 53) % 7) - 3) * 0.20;                          // one end higher than the other
        double lenF = 0.55 + ((seed * 17) % 6) * 0.09;                           // how far across the arm it goes
        double t0 = (top - (restY + thick)) / (top - bot), t1 = (top - (restY - thick)) / (top - bot);
        double slantT = slant / (top - bot);
        double sMid = 0.5 + (((seed * 37) % 11) - 5) * 0.035, half = 0.5 * across * lenF;
        double[] n = cross(sub(best.corners()[1], best.corners()[0]), sub(best.corners()[3], best.corners()[0]));
        // pick the normal's sign towards the camera
        double[][] wc = new double[4][];
        for (int i = 0; i < 4; i++) wc[i] = stageToWorld(best.corners()[i], bm, o);
        double[] wn = cross(sub(wc[1], wc[0]), sub(wc[3], wc[0]));
        double wl = Math.sqrt(wn[0] * wn[0] + wn[1] * wn[1] + wn[2] * wn[2]) + 1e-9;
        double[] midW = {(wc[0][0] + wc[2][0]) / 2, (wc[0][1] + wc[2][1]) / 2, (wc[0][2] + wc[2][2]) / 2};
        double[] toC = sub(cam, midW);
        double sign = (wn[0] * toC[0] + wn[1] * toC[1] + wn[2] * toC[2]) >= 0 ? 1 : -1;
        double off = 0.0016 * sign / wl;
        double[][] d = new double[4][];
        double[][] st = {{sMid - half, t0 - slantT}, {sMid + half, t0 + slantT}, {sMid + half, t1 + slantT}, {sMid - half, t1 - slantT}};
        for (int i = 0; i < 4; i++) {
            double s = Math.max(0.02, Math.min(0.98, st[i][0])), t = st[i][1];
            double[] topP = lerp3(wc[0], wc[1], s), botP = lerp3(wc[3], wc[2], s);
            double[] p = lerp3(topP, botP, t);
            d[i] = new double[]{p[0] + wn[0] * off, p[1] + wn[1] * off, p[2] + wn[2] * off};
        }
        r.matSpec = 0.9; r.matShine = 70; r.matBump = 0.7; r.matWrap = 0.3;
        r.quad(d, new double[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}}, markTex(seed), 0xFFFFFFFF, 0);
        r.matSpec = 0; r.matBump = 0; r.matWrap = 0;
        return midW;
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] lerp3(double[] a, double[] b, double t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    /** The voices: the arm lies still under a dull red glow and what was said in the memories comes back, over and over, through the picture. */
    private String voices(double tm, double t) {
        // the last picture of the arm, and the memories flickering through it
        arm(1.0, t);
        weigh(0.75 + 0.1 * tm, t);
        heavy = 0.75 + 0.1 * tm;
        int idx = (int) (tm * 14) % 6;
        double local = (tm * 14) % 1.0;
        if (local < 0.14) {
            memory(idx, local * 3 + 0.1, t);
            fade = 0.35;
        }
        return "VOICES";
    }

    /**
     * The last of it: looking down at oneself, the knife hand raised and brought down towards the chest, again and again, slower and weaker
     * each time. The moment of each blow is never in the picture: it cuts to black, and what comes back is a little darker, a little narrower,
     * a little further away.
     */
    private void jab(double tm, double t) {
        double ct = tm * 10.0;
        bodyPitch = -0.1;
        vmScale = 0.62;
        ARM_TINT = 0xFF8E8680;
        int landed = 0;
        for (int j = 0; j < 7; j++) if (ct > Ending13Rig.jabAt(j)) landed = j + 1;
        double w = 0.8 + 0.2 * landed / 7.0;
        weigh(w, t);
        r.clear(0x030304);
        r.ambR = 0.20; r.ambG = 0.19; r.ambB = 0.21;
        r.fogDensity = 0.06;
        double breathe = 0.5 + 0.5 * Math.sin(t * (3.2 - 1.8 * tm));
        r.camera(0, 1.55 - 0.18 * tm, 0, Math.sin(t * 0.3) * 0.015, -1.02 - 0.10 * tm + 0.03 * Math.sin(t * 0.7));
        r.lights.add(new Soft3D.Light(0, 2.2, 0.3, 1.2 * (1 - 0.6 * tm), 1.2 * (1 - 0.6 * tm), 1.3 * (1 - 0.6 * tm), 6));
        r.shadowDir = new double[]{0, -1, 0.1};
        r.lights.add(new Soft3D.Light(0, 1.1, 0.5, (0.6 + 1.0 * smooth(tm, 0, 0.8)) * (0.7 + 0.3 * breathe), 0.03, 0.02, 3.0));
        fxDofFocus = 0.7; fxDofRange = 0.5 - 0.3 * tm;
        r.matSpec = 0.1; r.matShine = 10;
        r.faceXZ(0.0, -3, -2, 3, 3, floor, 0xFF909090, 0, 3, 3);
        r.matSpec = 0;
        Ending13Rig.Frame f = Ending13Rig.frame("jab", ct, true);
        drawRig(f, 0);
        drawKnife(f.right(), 0, 1.0);
        // the blows: a cut to black around each, shorter to start with and then longer, so the picture comes back later and later
        for (int j = 0; j < 7; j++) {
            double at = Ending13Rig.jabAt(j), len = 0.10 + 0.05 * j;
            if (ct > at - 0.03 && ct < at + len) fade = 1.0;
        }
        fade = Math.max(fade, smooth(tm, 0.7, 1.0) * 0.55);
    }

    /** A heart of voxels, beating slower and slower, red light on nothing; the blade's tip comes down; black. */
    private void heart(double tm, double t) {
        r.clear(0x000000);
        r.ambR = 0.05; r.ambG = 0.0; r.ambB = 0.0;
        r.fogDensity = 0.02;
        double rate = 2.4 - 1.8 * tm;
        double beat = Math.pow(Math.max(0, Math.sin(t * Math.PI * rate)), 6);
        r.lights.add(new Soft3D.Light(0, 0.3, -1.4, 1.4 + 0.8 * beat, 0.1, 0.1, 5));
        r.camera(0, 0, -2.6, Math.sin(t * 0.3) * 0.05, 0);
        Soft3D.Tex red = Soft3D.Tex.surface(11, 8, 8, 170, 12, 22, 14, 1);
        double s = 0.075 * (1 + 0.1 * beat);
        for (int gy = -9; gy <= 9; gy++) {
            for (int gx = -9; gx <= 9; gx++) {
                double x = gx / 6.5, y = -gy / 6.5 + 0.1;
                double v = Math.pow(x * x + y * y - 1, 3) - x * x * y * y * y;
                if (v > 0) continue;
                r.box(gx * s, gy * s * -1, -0.1 * (1 + beat * 0.3), gx * s + s, gy * s * -1 + s, 0.1, red, 0xFFFFFFFF, 0, 6);
            }
        }
        // the tip, from above
        double reach = smooth(tm, 0.35, 0.92);
        double[] a = {0, 1.8, -0.3}, b = {0, lerp(1.7, 0.18, reach), -0.3};
        r.bar(a, b, new double[]{0.03, 0, 0}, new double[]{0, 0, 0.006}, steel, 0xFFEEEEFF, 0);
        fade = smooth(tm, 0.93, 1.0);
    }

    private void black() {
        java.util.Arrays.fill(r.color, 0xFF000000);
    }

    private void title(double tm) {
        // drawn after the copy: see overlayText with the name "TITLE"
        titleAlpha = smooth(tm, 0.15, 0.4) * (1 - smooth(tm, 0.8, 1.0));
    }

    private double titleAlpha;

    // ------------------------------------------------------------------ 2D on top

    private void overlayText(String what, double tm, double t) {
        // the pixels are in the image's own buffer: draw with Graphics2D onto it, then bring them back
        if (!gpuMode) image.setRGB(0, 0, W, H, pixels, 0, W);
        switch (what) {
            case "CRACKS" -> {
                Random q = new Random(7);
                g.setColor(new Color(255, 255, 255, 190));
                g.setStroke(new BasicStroke(1.3f));
                for (int i = 0; i < (int) (14 * smooth(tm, 0.55, 0.95)); i++) {
                    double x = W / 2.0 + q.nextGaussian() * 60, y = H / 2.0 + q.nextGaussian() * 40;
                    java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
                    p.moveTo(x, y);
                    for (int s = 0; s < 7; s++) {
                        x += q.nextGaussian() * 50;
                        y += q.nextGaussian() * 36;
                        p.lineTo(x, y);
                    }
                    g.draw(p);
                }
            }
            case "VOICES" -> {
                String[] lines = {"Don't forget who you are", "THIS IS YOUR FAULT.", "you are lost", "YOUR FAULT.", "Dead again.", "It was very foolish..."};
                Random q = new Random((long) (t * 6));
                g.setFont(new Font(Font.SERIF, Font.BOLD, 20 + (int) (tm * 14)));
                for (int i = 0; i < 9; i++) {
                    String line = lines[q.nextInt(lines.length)];
                    int x = q.nextInt(W - 220) - 10, y = 40 + q.nextInt(H - 80);
                    g.setColor(new Color(220, 30, 26, 70 + q.nextInt(150)));
                    g.drawString(line, x + (int) (rnd.nextGaussian() * 2), y);
                }
            }
            case "LIGHTS" -> {
                double tt = lightsT;
                drawMist(tt);
                drawLights2D(tt);
                g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
                g.setColor(new Color(235, 235, 235, 225));
                g.drawString("PQ-001", 24, 32);
                if (((int) (tt * 1.6)) % 2 == 0) { g.setColor(new Color(230, 30, 30)); g.fillOval(24, 42, 9, 9); g.setColor(new Color(235, 235, 235, 200)); g.drawString("REC", 40, 51); }
                int sec = (int) tt;
                g.setColor(new Color(235, 235, 235, 170));
                g.drawString(String.format("%02d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60), 24, 70);
                for (int i = 0; i < 6; i++) {
                    double st = LIGHT_AT[i], en = i < 5 ? LIGHT_AT[i + 1] : STAB + 2.5;
                    if (tt < st || tt >= en) continue;
                    lightText(i, smooth(tt, st, st + 0.35) * (1 - smooth(tt, en - 0.45, en)), tt);
                }
            }
            case "THIS IS YOUR FAULT." -> glitchText(what, W / 2, 300, 38, new Color(255, 30, 20), smooth(tm, 0.35, 0.45));
            case "LOST" -> {
                double a = smooth(tm, 0.1, 0.25);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
                g.setColor(new Color(30, 70, 220, (int) (255 * a)));
                g.fillOval(110, 36, 34, 34);
                g.setColor(new Color(255, 255, 255, (int) (255 * a)));
                g.drawString("i", 123, 63);
                g.drawString("you are lost", 154, 64);
            }
            case "BLUE" -> {
                g.setColor(new Color(0, 40, 200));
                g.fillRect(0, 0, W, H);
                g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
                g.setColor(new Color(120, 220, 255));
                String chars = "#%&$@0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz+=*<>?/\\|~";
                StringBuilder sb = new StringBuilder();
                for (int row = 0; row < 32; row++) {
                    sb.setLength(0);
                    for (int col = 0; col < 96; col++) sb.append(rnd.nextInt(5) == 0 ? ' ' : chars.charAt(rnd.nextInt(chars.length())));
                    g.drawString(sb.toString(), 4, 12 + row * 11);
                }
            }
            default -> { }
        }
        if (!gpuMode) image.getRGB(0, 0, W, H, pixels, 0, W);
    }

    /** The words of an Architect, in its own colour and hand. */
    private void lightText(int i, double a, double tt) {
        String s = LIGHT_TEXT[i];
        double[] c = LIGHT_RGB[i];
        Color col = new Color((int) (c[0] * 255), (int) (c[1] * 255), (int) (c[2] * 255));
        int size = i == 1 ? 46 : i == 5 ? 30 : i == 0 ? 36 : 24;
        int style = Font.BOLD | (i == 3 || i == 2 ? Font.ITALIC : 0);
        String family = i == 3 || i == 2 ? Font.SERIF : i == 4 ? Font.MONOSPACED : Font.SANS_SERIF;
        Font f = new Font(family, style, size);
        g.setFont(f);
        while (g.getFontMetrics().stringWidth(s) > W - 50 && size > 11) { size--; f = f.deriveFont((float) size); g.setFont(f); }
        int w = g.getFontMetrics().stringWidth(s);
        int x = (W - w) / 2, y = i == 5 ? H / 2 + 10 : H - 62;
        if (i == 5) { x += (int) (rnd.nextGaussian() * 3 * glitch); y += (int) (rnd.nextGaussian() * 2); }
        for (int k = 4; k >= 1; k--) {                                                    // the glow
            g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), (int) (34 * a)));
            for (int dx = -k; dx <= k; dx += k) for (int dy = -k; dy <= k; dy += k) g.drawString(s, x + dx, y + dy);
        }
        if (i == 0) {                                                                     // the last Architect: red on one side of the letters, blue on the other
            g.setColor(new Color(235, 60, 70, (int) (200 * a))); g.drawString(s, x - 2, y);
            g.setColor(new Color(70, 120, 255, (int) (200 * a))); g.drawString(s, x + 2, y);
        } else if (i == 4) {                                                              // Chromo: a rainbow ghost behind the white
            for (int k = 0; k < 3; k++) { g.setColor(Color.getHSBColor((float) ((tt * 0.5 + k * 0.33) % 1), 0.8f, 1f)); g.drawString(s, x + (k - 1) * 3, y + (k - 1)); }
        } else if (i == 5) {                                                              // Glitch: torn colour layers
            g.setColor(new Color(0, 255, 255, (int) (120 * a))); g.drawString(s, x - 4, y);
            g.setColor(new Color(255, 0, 90, (int) (150 * a))); g.drawString(s, x + 4, y);
        }
        g.setColor(new Color(i == 4 || i == 1 ? 255 : col.getRed(), i == 4 ? 255 : col.getGreen(), i == 4 ? 255 : col.getBlue(), (int) (255 * a)));
        g.drawString(s, x, y);
    }

    /** The same blood as {@link #blood}, drawn as shapes: a wavy dark edge climbing the picture, with drips. */
    private void bloodGpu(double t) {
        int top = (int) (H * (1 - 0.34 * bloodAmount));
        java.awt.geom.Path2D edge = new java.awt.geom.Path2D.Double();
        edge.moveTo(0, H + 10);
        for (int x = 0; x <= W; x += 3) {
            double ey = top + Math.sin(x * 0.045 + t * 0.9) * 9 + Math.sin(x * 0.11 - t * 0.5) * 5 + (x % 37 < 5 ? 14 : 0);
            edge.lineTo(x, ey);
        }
        edge.lineTo(W, H + 10);
        edge.closePath();
        g.setPaint(new java.awt.GradientPaint(0, top - 10, new Color(130, 14, 20, 235), 0, top + 60, new Color(60, 4, 8, 250)));
        g.fill(edge);
        g.setColor(new Color(190, 50, 50, 70));
        g.setStroke(new BasicStroke(1.6f));
        for (int x = 0; x < W; x += 29) g.drawLine(x, top + 4 + (x * 7) % 9, x + 17, top + 6 + (x * 5) % 9);       // a wet shine along the edge
    }

    private void titleGpu() {
        g.setFont(new Font(Font.SERIF, Font.BOLD, 24));
        String s = "YOUR TRUTH AND YOUR POWER.";
        int w = g.getFontMetrics().stringWidth(s);
        g.setColor(new Color(200, 20, 20, (int) (255 * titleAlpha)));
        g.drawString(s, (W - w) / 2, H / 2 + 8);
    }

    /** Blood coming up over the lower edge of the picture: dark, wet, moving a little. */
    private void blood(double t) {
        int top = (int) (H * (1 - 0.34 * bloodAmount));
        for (int x = 0; x < W; x++) {
            int edge = top + (int) (Math.sin(x * 0.045 + t * 0.9) * 9 + Math.sin(x * 0.11 - t * 0.5) * 5 + (x % 37 < 5 ? 14 : 0));
            for (int y = Math.max(0, edge); y < H; y++) {
                double depth = Math.min(1, (y - edge) / 40.0);
                int p = pixels[y * W + x];
                int rr = (int) lerp(((p >> 16) & 255) * 0.5 + 110, 90, depth), gg = (int) (((p >> 8) & 255) * 0.2 * (1 - depth) + 6), bb = (int) ((p & 255) * 0.2 * (1 - depth) + 8);
                pixels[y * W + x] = 0xFF000000 | (clamp255(rr) << 16) | (clamp255(gg) << 8) | clamp255(bb);
            }
        }
    }

    private void glitchText(String s, int cx, int baseline, int size, Color color, double alpha) {
        g.setFont(new Font(Font.SERIF, Font.BOLD, size));
        int w = g.getFontMetrics().stringWidth(s);
        for (int pass = 0; pass < 3; pass++) {
            double jx = (pass - 1) * 3 + rnd.nextGaussian() * 1.4;
            Color col = pass == 0 ? new Color(0, 255, 255, (int) (110 * alpha)) : pass == 1 ? new Color(255, 0, 40, (int) (255 * alpha)) : new Color(255, 255, 255, (int) (60 * alpha));
            g.setColor(col);
            g.drawString(s, (float) (cx - w / 2.0 + jx), baseline);
        }
    }

    // ------------------------------------------------------------------ the tape

    /** The VHS look over everything: scanlines, grain, the colour channels slipping, torn rows, the tracking band, a vignette. */
    private void post(double t, double flash) {
        final int[] copy = pixels.clone();
        final int slip = 2 + (rnd.nextInt(40) == 0 ? 6 : 0);
        final long seed = rnd.nextLong();
        final double hv = heavy;
        final int fl = (int) (255 * clamp01(flash));
        java.util.stream.IntStream.range(0, H).parallel().forEach(y -> {
            Random q = new Random(seed + y * 7919L);
            int row = y * W;
            double dy = (y - H / 2.0) / (H / 2.0);
            double k = (y & 1) == 0 ? 1.0 : 0.84;
            for (int x = 0; x < W; x++) {
                int p = copy[row + x];
                int rr = (copy[row + Math.max(0, x - slip)] >> 16) & 255;
                int bb = copy[row + Math.min(W - 1, x + slip)] & 255;
                int gg = (p >> 8) & 255;
                int n = q.nextInt(17) - 8;
                double dx = (x - W / 2.0) / (W / 2.0);
                double vig = 1.0 - (0.38 + 0.55 * hv) * (dx * dx + dy * dy);
                int r2 = clamp255((int) ((rr + n) * k * vig) + fl);
                int g2 = clamp255((int) ((gg + n) * k * vig) + fl);
                int b2 = clamp255((int) ((bb + n) * k * vig) + fl);
                pixels[row + x] = 0xFF000000 | (r2 << 16) | (g2 << 8) | b2;
            }
        });
        if (rnd.nextInt(5) == 0) {
            int y = rnd.nextInt(H - 8), h = 2 + rnd.nextInt(5), shift = rnd.nextInt(40) - 20;
            for (int yy = y; yy < y + h; yy++) {
                int[] row = new int[W];
                System.arraycopy(pixels, yy * W, row, 0, W);
                for (int x = 0; x < W; x++) pixels[yy * W + x] = row[Math.floorMod(x - shift, W)];
            }
        }
        int band = (int) ((t * 37) % (H + 60)) - 30;
        for (int y = Math.max(0, band); y < Math.min(H, band + 10); y++) {
            for (int x = 0; x < W; x++) {
                int p = pixels[y * W + x];
                pixels[y * W + x] = 0xFF000000 | (clamp255(((p >> 16) & 255) + 16) << 16) | (clamp255(((p >> 8) & 255) + 16) << 8) | clamp255((p & 255) + 16);
            }
        }
        if (titleAlpha > 0.01) {
            image.setRGB(0, 0, W, H, pixels, 0, W);
            g.setFont(new Font(Font.SERIF, Font.BOLD, 24));
            String s = "YOUR TRUTH AND YOUR POWER.";
            int w = g.getFontMetrics().stringWidth(s);
            g.setColor(new Color(200, 20, 20, (int) (255 * titleAlpha)));
            g.drawString(s, (W - w) / 2, H / 2 + 8);
            image.getRGB(0, 0, W, H, pixels, 0, W);
        }
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    /** Writes frames to a folder, to look at them without the game: {@code Ending13Scene <folder> [skin.png] [beats.json] [seconds...]}. */
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        DEBUG_ARMS = System.getProperty("arms") != null;
        java.io.File dir = new java.io.File(args[0]);
        dir.mkdirs();
        int[] skinPx = null;
        if (args.length > 1 && !args[1].equals("-")) {
            BufferedImage s = javax.imageio.ImageIO.read(new java.io.File(args[1]));
            skinPx = new int[64 * 64];
            s.getRGB(0, 0, 64, 64, skinPx, 0, 64);
        }
        Ending13Scene scene = skinPx == null ? new Ending13Scene() : new Ending13Scene(skinPx);
        if (args.length > 2 && !args[2].equals("-")) {
            String json = new String(java.nio.file.Files.readAllBytes(new java.io.File(args[2]).toPath()), java.nio.charset.StandardCharsets.UTF_8);
            int a = json.indexOf("\"kicks\": [") + 10;
            if (a < 10) a = json.indexOf("\"kicks\":[") + 9;
            int b = json.indexOf(']', a);
            String[] parts = json.substring(a, b).split(",");
            double[] ks = new double[parts.length];
            for (int i = 0; i < parts.length; i++) ks[i] = Double.parseDouble(parts[i].trim());
            scene.setKicks(ks);
        }
        double[] times = args.length > 3 ? java.util.Arrays.stream(args).skip(3).mapToDouble(Double::parseDouble).toArray()
                : new double[]{3, 11, 15, 17.4, 19, 24, 36, 40, 45, 55, 60, 66, 74, 82, 88, 98, 104, 114, 122, 140, 146, 155, 165, 170, 174};
        scene.render(0);
        for (double t : times) {
            scene.render(t - T0);
            BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            out.setRGB(0, 0, W, H, scene.pixels, 0, W);
            javax.imageio.ImageIO.write(out, "png", new java.io.File(dir, String.format(java.util.Locale.ROOT, "t%06.2f.png", t)));
        }
    }
}
