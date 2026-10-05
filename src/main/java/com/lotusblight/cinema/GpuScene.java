package com.lotusblight.cinema;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The same 3D picture as {@link Soft3D}, drawn by the graphics card: every quad the scene makes is collected, sent in a few batches, and lit
 * per pixel in a shader (point lights, one shadow-casting light, relief, wrap, shine, fog), then taken through a chain of passes (bloom,
 * occlusion, light shafts, depth of field, grading) and the tape's look, at any size, 60 times a second. The scene code is the same; only
 * what draws it differs. Needs an OpenGL 3.2 core context to be current on the calling thread.
 */
public final class GpuScene {
    /** What the post chain does this frame; the scene fills it in. */
    public static final class Params {
        public double gain = 1, bloomStrength = 0.6, bloomThreshold = 0.55, ssao = 0.55, ssaoRadius = 9, rays = 0, dofFocus = 0, dofRange = 6, contrast = 1.08, sat = 0.95;
        public double[] rayAt;
        public int[] rayTint = {210, 225, 255};
        public int[] shadowTint = {-6, -2, 8}, highlightTint = {14, 8, -4};
        public double flash, heavy, glitch, time;
        public long seed;
        public double titleAlpha;
    }

    private static final int MAX_LIGHTS = 24;
    private static final int STRIDE = 18;                              // floats per vertex: pos3 uv2 normal3 tint4 mat4 blend2

    public final int outW, outH;
    private final int shadowSize = 2048;

    // programs
    private int progScene, progShadow, progBloomExtract, progBlur, progBloomAdd, progGrade, progVhs, progSprite, progSsao, progDof, progRays, progCopy;
    private int vaoFull, vaoScene, vboScene, vaoSprite, vboSprite;
    // targets
    private Target scene, ssaoT, rtA, rtB, bloomA, bloomB, ldr, out;
    private int shadowFbo, shadowTex, shadowDepth;
    private int whiteTex, overlayTex;
    private int overlayW, overlayH;

    private static final class Target {
        int fbo, tex0, tex1, depth, w, h;
    }

    private static final class GlTex {
        int id, w, h;
    }

    /** The sprites of light and the texture they use. */
    public static final class SpriteCmd {
        public java.awt.image.BufferedImage img;
        public double[] c = new double[8];
        public double gain;
        public float tr = 1, tg = 1, tb = 1;
    }

    private static final class Batch {
        Soft3D.Tex tex;            // null = the white texel
        int[] snapshot;            // the pixels of a texture that changes every frame, as they were when the frame was made
        float[] data = new float[STRIDE * 6 * 64];
        int floats;
        void add(float[] v, int n) {
            if (floats + n > data.length) data = java.util.Arrays.copyOf(data, Math.max(data.length * 2, floats + n));
            System.arraycopy(v, 0, data, floats, n);
            floats += n;
        }
    }

    private static final class Blended {
        float[] v = new float[STRIDE * 6];
        Soft3D.Tex tex;
        int[] snapshot;
        float z;
    }

    /**
     * Everything one frame needs, collected without touching the graphics card (so a thread of its own can build it while the game draws the last
     * one): the quads, the state of the camera and the lights, what the post chain is to do, and the 2D layer.
     */
    public static final class Frame implements Soft3D.Sink {
        final IdentityHashMap<Object, Batch> batches = new IdentityHashMap<>();
        public final List<Blended> blended = new ArrayList<>();
        public final List<SpriteCmd> sprites = new ArrayList<>();
        public Soft3D soft;
        public double camX, camY, camZ, yaw, pitch, focal, width, height, ambR, ambG, ambB, fogR, fogG, fogB, fogDensity;
        public int clearRgb;
        public double[] shadowDir;
        public final List<Soft3D.Light> lights = new ArrayList<>();
        public Params params;
        public int[] overlay;
        public int ovW, ovH;

        public Frame(Soft3D soft) {
            this.soft = soft;
            soft.sink = this;
        }

        public double[] toView(double x, double y, double z) {
            x -= camX; y -= camY; z -= camZ;
            double cy = Math.cos(-yaw), sy = Math.sin(-yaw);
            double x1 = x * cy + z * sy, z1 = -x * sy + z * cy;
            double cp = Math.cos(pitch), sp = Math.sin(pitch);
            return new double[]{x1, y * cp - z1 * sp, y * sp + z1 * cp};
        }

        /** Ends the collecting: takes a copy of the scene's state. */
        public void finish(Params fp) {
            Soft3D r = soft;
            r.sink = null;
            camX = r.camX; camY = r.camY; camZ = r.camZ; yaw = r.yaw; pitch = r.pitch; focal = r.focal; width = r.width; height = r.height;
            ambR = r.ambR; ambG = r.ambG; ambB = r.ambB; fogR = r.fogR; fogG = r.fogG; fogB = r.fogB; fogDensity = r.fogDensity;
            clearRgb = r.clearRgb;
            shadowDir = r.shadowDir == null ? null : r.shadowDir.clone();
            lights.addAll(r.lights);
            params = fp;
            soft = null;
        }

        public void setOverlay(int[] argb, int w, int h) {
            overlay = argb;
            ovW = w;
            ovH = h;
        }

        public void addSprite(java.awt.image.BufferedImage img, double[] corners, double gain) {
            addSprite(img, corners, gain, 1, 1, 1);
        }

        /** An image laid over the picture as light, tinted: corners in output pixels, clockwise from the top left. */
        public void addSprite(java.awt.image.BufferedImage img, double[] corners, double gain, double r, double g, double b) {
            SpriteCmd s = new SpriteCmd();
            s.img = img;
            s.c = corners;
            s.gain = gain;
            s.tr = (float) r; s.tg = (float) g; s.tb = (float) b;
            sprites.add(s);
        }

        private Batch batchFor(Soft3D.Tex tex) {
            Object key = tex == null ? WHITE_KEY : tex;
            Batch b = batches.get(key);
            if (b == null) {
                b = new Batch();
                b.tex = tex;
                if (tex != null && tex.dynamic) b.snapshot = tex.px.clone();
                batches.put(key, b);
            }
            return b;
        }

        @Override
        public void quad(double[][] p, double[][] uv, Soft3D.Tex tex, int tint, double emissive, double spec, double shine, double bump, double wrap, boolean blend, double alpha) {
            float tr = ((tint >> 16) & 255) / 255f, tg = ((tint >> 8) & 255) / 255f, tb = (tint & 255) / 255f;
            Soft3D.Tex use = tex;
            if (tex.solid) {                                    // a plain colour needs no texture of its own: it rides in the tint over a white texel
                use = null;
                tr *= ((tex.solidArgb >> 16) & 255) / 255f;
                tg *= ((tex.solidArgb >> 8) & 255) / 255f;
                tb *= (tex.solidArgb & 255) / 255f;
            }
            double ax = p[1][0] - p[0][0], ay = p[1][1] - p[0][1], az = p[1][2] - p[0][2];
            double bx = p[3][0] - p[0][0], by = p[3][1] - p[0][1], bz = p[3][2] - p[0][2];
            double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            double nl = Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-9;
            nx /= nl; ny /= nl; nz /= nl;
            float[] v = new float[STRIDE * 6];
            int[] order = {0, 1, 2, 0, 2, 3};
            for (int k = 0; k < 6; k++) {
                int c = order[k], o = k * STRIDE;
                v[o] = (float) p[c][0]; v[o + 1] = (float) p[c][1]; v[o + 2] = (float) p[c][2];
                v[o + 3] = (float) uv[c][0]; v[o + 4] = (float) uv[c][1];
                v[o + 5] = (float) nx; v[o + 6] = (float) ny; v[o + 7] = (float) nz;
                v[o + 8] = tr; v[o + 9] = tg; v[o + 10] = tb; v[o + 11] = (float) emissive;
                v[o + 12] = (float) spec; v[o + 13] = (float) shine; v[o + 14] = (float) bump; v[o + 15] = (float) wrap;
                v[o + 16] = (float) alpha; v[o + 17] = blend ? 1f : 0f;
            }
            if (blend) {
                Blended b = new Blended();
                b.v = v;
                b.tex = use;
                if (use != null && use.dynamic) b.snapshot = use.px.clone();
                double[] vz = soft.toView((p[0][0] + p[2][0]) / 2, (p[0][1] + p[2][1]) / 2, (p[0][2] + p[2][2]) / 2);
                b.z = (float) vz[2];
                blended.add(b);
            } else {
                batchFor(use).add(v, v.length);
            }
        }
    }

    private static final Object WHITE_KEY = new Object();

    public GpuScene(int outW, int outH) {
        this.outW = outW;
        this.outH = outH;
        init();
    }

    // ------------------------------------------------------------------ set-up

    private static int compile(String vs, String fs, String... attribs) {
        int v = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(v, vs);
        GL20.glCompileShader(v);
        if (GL20.glGetShaderi(v, GL20.GL_COMPILE_STATUS) == 0) throw new IllegalStateException("vertex shader: " + GL20.glGetShaderInfoLog(v));
        int f = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(f, fs);
        GL20.glCompileShader(f);
        if (GL20.glGetShaderi(f, GL20.GL_COMPILE_STATUS) == 0) throw new IllegalStateException("fragment shader: " + GL20.glGetShaderInfoLog(f));
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, v);
        GL20.glAttachShader(p, f);
        for (int i = 0; i < attribs.length; i++) GL20.glBindAttribLocation(p, i, attribs[i]);
        GL20.glLinkProgram(p);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException("link: " + GL20.glGetProgramInfoLog(p));
        GL20.glDeleteShader(v);
        GL20.glDeleteShader(f);
        return p;
    }

    private static final String FULL_VS = "#version 150\nout vec2 vUV;\nvoid main(){ vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2)); vUV = p; gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0); }\n";

    private void init() {
        progScene = compile(SCENE_VS, SCENE_FS, "aPos", "aUV", "aNormal", "aTint", "aMat", "aBlend");
        progShadow = compile(SHADOW_VS, SHADOW_FS, "aPos", "aUV", "aNormal", "aTint", "aMat", "aBlend");
        progBloomExtract = compile(FULL_VS, BLOOM_EXTRACT_FS);
        progBlur = compile(FULL_VS, BLUR_FS);
        progBloomAdd = compile(FULL_VS, BLOOM_ADD_FS);
        progSsao = compile(FULL_VS, SSAO_FS);
        progRays = compile(FULL_VS, RAYS_FS);
        progDof = compile(FULL_VS, DOF_FS);
        progGrade = compile(FULL_VS, GRADE_FS);
        progVhs = compile(FULL_VS, VHS_FS);
        progSprite = compile(SPRITE_VS, SPRITE_FS, "aPos", "aUV");
        progCopy = compile(FULL_VS, COPY_FS);
        vaoFull = GL30.glGenVertexArrays();
        vaoScene = GL30.glGenVertexArrays();
        vboScene = GL15.glGenBuffers();
        GL30.glBindVertexArray(vaoScene);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboScene);
        int[] sizes = {3, 2, 3, 4, 4, 2};
        int off = 0;
        for (int i = 0; i < sizes.length; i++) {
            GL20.glEnableVertexAttribArray(i);
            GL20.glVertexAttribPointer(i, sizes[i], GL11.GL_FLOAT, false, STRIDE * 4, (long) off * 4);
            off += sizes[i];
        }
        vaoSprite = GL30.glGenVertexArrays();
        vboSprite = GL15.glGenBuffers();
        GL30.glBindVertexArray(vaoSprite);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboSprite);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 16, 0);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 16, 8);
        GL30.glBindVertexArray(0);

        scene = target(outW, outH, true, true);
        ssaoT = target(outW, outH, false, false);
        rtA = target(outW, outH, false, false);
        rtB = target(outW, outH, false, false);
        bloomA = target(outW / 4, outH / 4, false, false);
        bloomB = target(outW / 4, outH / 4, false, false);
        ldr = target(outW, outH, false, false);
        out = targetLdr(outW, outH);
        // the shadow map: the distance along the light's view, one float per texel
        shadowTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, shadowTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F, shadowSize, shadowSize, 0, GL11.GL_RED, GL11.GL_FLOAT, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        shadowDepth = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, shadowDepth);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, shadowSize, shadowSize);
        shadowFbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, shadowFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, shadowTex, 0);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, shadowDepth);
        // a white texel for everything that is one plain colour, and an empty overlay
        whiteTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, whiteTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, new int[]{0xFFFFFFFF});
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        overlayTex = GL11.glGenTextures();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        whiteGl = new GlTex();
        whiteGl.id = whiteTex;
        whiteGl.w = 1;
        whiteGl.h = 1;
    }

    private static int floatTex(int w, int h) {
        int t = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, t);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, w, h, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return t;
    }

    private static Target target(int w, int h, boolean withGlow, boolean withDepth) {
        Target t = new Target();
        t.w = w;
        t.h = h;
        t.tex0 = floatTex(w, h);
        t.fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, t.fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, t.tex0, 0);
        if (withGlow) {
            t.tex1 = floatTex(w, h);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1, GL11.GL_TEXTURE_2D, t.tex1, 0);
        }
        if (withDepth) {
            t.depth = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, t.depth);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH_COMPONENT32F, w, h, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, t.depth, 0);
        }
        return t;
    }

    private static Target targetLdr(int w, int h) {
        Target t = new Target();
        t.w = w;
        t.h = h;
        t.tex0 = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, t.tex0);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        t.fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, t.fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, t.tex0, 0);
        return t;
    }

    /** The game's own graphics-card state, kept to be put back exactly as it was after the scene has drawn. */
    public static final class State {
        int fbo, prog, vao, arrayBuf, active, depthFunc, srcRgb, dstRgb, srcA, dstA;
        int[] tex = new int[4];
        int[] viewport = new int[4];
        boolean depthTest, blend, cull, scissor, depthMask;
        java.nio.ByteBuffer colorMask = BufferUtils.createByteBuffer(4);
    }

    public static State saveState() {
        State s = new State();
        s.fbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        s.prog = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        s.vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        s.arrayBuf = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        s.active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        for (int i = 0; i < 4; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
            s.tex[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        GL13.glActiveTexture(s.active);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, s.viewport);
        s.depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        s.blend = GL11.glIsEnabled(GL11.GL_BLEND);
        s.cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        s.scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        s.depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        s.depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, s.colorMask);
        s.srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        s.dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        s.srcA = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        s.dstA = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        return s;
    }

    public static void restoreState(State s) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, s.fbo);
        GL11.glViewport(s.viewport[0], s.viewport[1], s.viewport[2], s.viewport[3]);
        GL20.glUseProgram(s.prog);
        GL30.glBindVertexArray(s.vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, s.arrayBuf);
        for (int i = 0; i < 4; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, s.tex[i]);
        }
        GL13.glActiveTexture(s.active);
        setEnabled(GL11.GL_DEPTH_TEST, s.depthTest);
        setEnabled(GL11.GL_BLEND, s.blend);
        setEnabled(GL11.GL_CULL_FACE, s.cull);
        setEnabled(GL11.GL_SCISSOR_TEST, s.scissor);
        GL11.glDepthMask(s.depthMask);
        GL11.glDepthFunc(s.depthFunc);
        GL11.glColorMask(s.colorMask.get(0) != 0, s.colorMask.get(1) != 0, s.colorMask.get(2) != 0, s.colorMask.get(3) != 0);
        GL14.glBlendFuncSeparate(s.srcRgb, s.dstRgb, s.srcA, s.dstA);
    }

    private static void setEnabled(int cap, boolean on) {
        if (on) GL11.glEnable(cap); else GL11.glDisable(cap);
    }

    /** The finished picture, as a texture of the output size (RGBA, 8 bits). */
    public int outputTexture() {
        return out.tex0;
    }

    // ------------------------------------------------------------------ textures (on the graphics-card side)

    private final IdentityHashMap<Soft3D.Tex, GlTex> texCache = new IdentityHashMap<>();
    private final IdentityHashMap<java.awt.image.BufferedImage, GlTex> imgCache = new IdentityHashMap<>();
    private final ReferenceQueue<Object> dead = new ReferenceQueue<>();
    private final Map<java.lang.ref.Reference<Object>, Integer> deadIds = new IdentityHashMap<>();
    private GlTex whiteGl;

    private void freeDead() {
        // free the textures of Tex objects that were thrown away
        java.lang.ref.Reference<?> ref;
        while ((ref = dead.poll()) != null) {
            Integer id = deadIds.remove(ref);
            if (id != null) GL11.glDeleteTextures(id);
        }
    }

    private GlTex glTex(Soft3D.Tex tex, int[] snapshot) {
        if (tex == null) return whiteGl;
        GlTex g = texCache.get(tex);
        int[] px = snapshot != null ? snapshot : tex.px;
        if (g == null) {
            g = new GlTex();
            g.id = GL11.glGenTextures();
            g.w = tex.w;
            g.h = tex.h;
            upload(g, tex, px, true);
            texCache.put(tex, g);
            deadIds.put(new PhantomReference<Object>(tex, dead), g.id);
        } else if (tex.dynamic) {
            upload(g, tex, px, false);
        }
        return g;
    }

    private void upload(GlTex g, Soft3D.Tex tex, int[] px, boolean first) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, g.id);
        if (first) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, tex.w, tex.h, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, px);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            if (tex.nearest) {
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            } else {
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameterf(GL11.GL_TEXTURE_2D, 0x84FE, 8f);                       // anisotropy, where the card has it
            }
        } else {
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, tex.w, tex.h, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, px);
        }
    }

    private int imageTexture(java.awt.image.BufferedImage im) {
        GlTex g = imgCache.get(im);
        if (g != null) return g.id;
        g = new GlTex();
        g.id = GL11.glGenTextures();
        g.w = im.getWidth();
        g.h = im.getHeight();
        int[] px = im.getRGB(0, 0, g.w, g.h, null, 0, g.w);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, g.id);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, g.w, g.h, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, px);
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        imgCache.put(im, g);
        return g.id;
    }

    private void sendOverlay(Frame f) {
        if (f.overlay == null) { hasOverlay = false; return; }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, overlayTex);
        if (f.ovW != overlayW || f.ovH != overlayH) {
            overlayW = f.ovW;
            overlayH = f.ovH;
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, f.ovW, f.ovH, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, f.overlay);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        } else {
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, f.ovW, f.ovH, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, f.overlay);
        }
        hasOverlay = true;
    }

    private boolean hasOverlay;

    // ------------------------------------------------------------------ drawing

    private float[] viewProj(Frame f) {
        double cy = Math.cos(-f.yaw), sy = Math.sin(-f.yaw), cp = Math.cos(f.pitch), sp = Math.sin(f.pitch);
        // view space (x right, y up, z forward) from a world point, exactly as Soft3D.toView
        double[][] v = new double[4][4];
        v[0][0] = cy; v[0][1] = 0; v[0][2] = sy;
        v[1][0] = -sp * (-sy); v[1][1] = cp; v[1][2] = -sp * cy;
        v[2][0] = cp * (-sy); v[2][1] = sp; v[2][2] = cp * cy;
        for (int i = 0; i < 3; i++) v[i][3] = -(v[i][0] * f.camX + v[i][1] * f.camY + v[i][2] * f.camZ);
        v[3][3] = 1;
        double fx = 2 * f.focal / f.width, fy = 2 * f.focal / f.height;
        double n = 0.05, fa = 80;
        double[][] pm = {{fx, 0, 0, 0}, {0, fy, 0, 0}, {0, 0, (fa + n) / (fa - n), -2 * fa * n / (fa - n)}, {0, 0, 1, 0}};
        double[][] m = new double[4][4];
        for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) for (int k = 0; k < 4; k++) m[i][j] += pm[i][k] * v[k][j];
        float[] colMajor = new float[16];
        for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) colMajor[j * 4 + i] = (float) m[i][j];
        return colMajor;
    }

    private FloatBuffer vbuf = BufferUtils.createFloatBuffer(STRIDE * 6 * 4096);

    /** Draws every batch: opaque ones texture by texture, then the soft ones far to near. {@code shadowPass} writes only depth along the light. */
    private void drawGeometry(Frame f, boolean shadowPass) {
        GL30.glBindVertexArray(vaoScene);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboScene);
        int prog = shadowPass ? progShadow : progScene;
        int locInv = GL20.glGetUniformLocation(prog, "uTexInv");
        int total = 0;
        for (Batch b : f.batches.values()) total += b.floats;
        int bl = 0;
        if (!shadowPass) for (Blended b : f.blended) bl += b.v.length;
        int need = total + bl;
        if (vbuf.capacity() < need) vbuf = BufferUtils.createFloatBuffer(Math.max(need, vbuf.capacity() * 2));
        vbuf.clear();
        List<Object[]> ranges = new ArrayList<>();               // {GlTex, first vertex, vertex count}
        int vertex = 0;
        for (Batch b : f.batches.values()) {
            if (b.floats == 0) continue;
            vbuf.put(b.data, 0, b.floats);
            ranges.add(new Object[]{glTex(b.tex, b.snapshot), vertex, b.floats / STRIDE});
            vertex += b.floats / STRIDE;
        }
        List<Object[]> softRanges = new ArrayList<>();
        if (!shadowPass && !f.blended.isEmpty()) {
            List<Blended> sorted = new ArrayList<>(f.blended);
            sorted.sort((a, c) -> Float.compare(c.z, a.z));
            Soft3D.Tex cur = null;
            boolean first = true;
            Object[] run = null;
            for (Blended b : sorted) {
                vbuf.put(b.v);
                if (first || b.tex != cur) {
                    run = new Object[]{glTex(b.tex, b.snapshot), vertex, 0};
                    softRanges.add(run);
                    cur = b.tex;
                    first = false;
                }
                run[2] = (Integer) run[2] + 6;
                vertex += 6;
            }
        }
        vbuf.flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vbuf, GL15.GL_STREAM_DRAW);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        for (Object[] rg : ranges) {
            GlTex t = (GlTex) rg[0];
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, t.id);
            if (locInv >= 0) GL20.glUniform2f(locInv, 1f / t.w, 1f / t.h);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, (Integer) rg[1], (Integer) rg[2]);
        }
        if (!softRanges.isEmpty()) {
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            for (Object[] rg : softRanges) {
                GlTex t = (GlTex) rg[0];
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, t.id);
                if (locInv >= 0) GL20.glUniform2f(locInv, 1f / t.w, 1f / t.h);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, (Integer) rg[1], (Integer) rg[2]);
            }
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_BLEND);
        }
    }

    private static void setView(Target t) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, t.fbo);
        GL11.glViewport(0, 0, t.w, t.h);
    }

    private void fullscreen() {
        GL30.glBindVertexArray(vaoFull);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    private static void tex(int unit, int id) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
    }

    private static int loc(int prog, String name) {
        return GL20.glGetUniformLocation(prog, name);
    }

    /** Draws the frame: the scene's geometry, the post chain, the sprites, the overlay and the tape. The result is {@link #outputTexture()}. */
    public void draw(Frame f) {
        Params fp = f.params;
        freeDead();
        sendOverlay(f);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);
        GL11.glColorMask(true, true, true, true);

        // ---- the shadow of light 0
        boolean hasShadow = f.shadowDir != null && !f.lights.isEmpty();
        double[] sR = new double[3], sU = new double[3], sF = new double[3];
        Soft3D.Light l0 = f.lights.isEmpty() ? null : f.lights.get(0);
        if (hasShadow) {
            double[] fw = norm(f.shadowDir);
            double[] up = Math.abs(fw[1]) > 0.95 ? new double[]{1, 0, 0} : new double[]{0, 1, 0};
            double[] rr = norm(cross(up, fw));
            double[] uu = cross(fw, rr);
            sR = rr; sU = uu; sF = fw;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, shadowFbo);
            GL11.glViewport(0, 0, shadowSize, shadowSize);
            GL11.glClearColor(1000f, 0, 0, 1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GL20.glUseProgram(progShadow);
            GL20.glUniform3f(loc(progShadow, "uLPos"), (float) l0.x(), (float) l0.y(), (float) l0.z());
            GL20.glUniform3f(loc(progShadow, "uSR"), (float) sR[0], (float) sR[1], (float) sR[2]);
            GL20.glUniform3f(loc(progShadow, "uSU"), (float) sU[0], (float) sU[1], (float) sU[2]);
            GL20.glUniform3f(loc(progShadow, "uSF"), (float) sF[0], (float) sF[1], (float) sF[2]);
            GL20.glUniform1i(loc(progShadow, "uTex"), 0);
            drawGeometry(f, true);
        }

        // ---- the scene
        setView(scene);
        GL30.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1});
        int clear = f.clearRgb;
        GL11.glClearColor(((clear >> 16) & 255) / 255f, ((clear >> 8) & 255) / 255f, (clear & 255) / 255f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL20.glUseProgram(progScene);
        GL20.glUniformMatrix4fv(loc(progScene, "uVP"), false, viewProj(f));
        GL20.glUniform3f(loc(progScene, "uCam"), (float) f.camX, (float) f.camY, (float) f.camZ);
        GL20.glUniform3f(loc(progScene, "uAmb"), (float) f.ambR, (float) f.ambG, (float) f.ambB);
        GL20.glUniform4f(loc(progScene, "uFog"), (float) (f.fogR / 255.0), (float) (f.fogG / 255.0), (float) (f.fogB / 255.0), (float) f.fogDensity);
        int n = Math.min(MAX_LIGHTS, f.lights.size());
        float[] lp = new float[MAX_LIGHTS * 4], lc = new float[MAX_LIGHTS * 3];
        for (int i = 0; i < n; i++) {
            Soft3D.Light l = f.lights.get(i);
            lp[i * 4] = (float) l.x(); lp[i * 4 + 1] = (float) l.y(); lp[i * 4 + 2] = (float) l.z(); lp[i * 4 + 3] = (float) l.range();
            lc[i * 3] = (float) l.r(); lc[i * 3 + 1] = (float) l.g(); lc[i * 3 + 2] = (float) l.b();
        }
        GL20.glUniform1i(loc(progScene, "uN"), n);
        GL20.glUniform4fv(loc(progScene, "uLightPos"), lp);
        GL20.glUniform3fv(loc(progScene, "uLightCol"), lc);
        GL20.glUniform1i(loc(progScene, "uHasShadow"), hasShadow ? 1 : 0);
        if (hasShadow) {
            GL20.glUniform3f(loc(progScene, "uLPos"), (float) l0.x(), (float) l0.y(), (float) l0.z());
            GL20.glUniform3f(loc(progScene, "uSR"), (float) sR[0], (float) sR[1], (float) sR[2]);
            GL20.glUniform3f(loc(progScene, "uSU"), (float) sU[0], (float) sU[1], (float) sU[2]);
            GL20.glUniform3f(loc(progScene, "uSF"), (float) sF[0], (float) sF[1], (float) sF[2]);
            GL20.glUniform1f(loc(progScene, "uShadowTexel"), 1f / shadowSize);
        }
        GL20.glUniform1i(loc(progScene, "uTex"), 0);
        GL20.glUniform1i(loc(progScene, "uShadow"), 1);
        tex(1, shadowTex);
        drawGeometry(f, false);
        GL30.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        // ---- the post chain, each pass reading the last result
        Target cur = scene;
        float[] lumW = {0.3f, 0.59f, 0.11f};
        // occlusion
        if (fp.ssao > 0.001) {
            setView(ssaoT);
            GL20.glUseProgram(progSsao);
            tex(0, cur.tex0);
            tex(1, scene.depth);
            GL20.glUniform1i(loc(progSsao, "uColor"), 0);
            GL20.glUniform1i(loc(progSsao, "uDepth"), 1);
            GL20.glUniform1f(loc(progSsao, "uStrength"), (float) fp.ssao);
            GL20.glUniform1f(loc(progSsao, "uRadius"), (float) (fp.ssaoRadius * outW / 720.0));
            GL20.glUniform2f(loc(progSsao, "uSize"), outW, outH);
            fullscreen();
            cur = ssaoT;
        }
        // light shafts
        if (fp.rayAt != null && fp.rays > 0.001) {
            double[] v = f.toView(fp.rayAt[0], fp.rayAt[1], fp.rayAt[2]);
            if (v[2] >= 0.2) {
                setView(rtA);
                GL20.glUseProgram(progRays);
                tex(0, cur.tex0);
                tex(1, scene.tex1);
                tex(2, scene.depth);
                GL20.glUniform1i(loc(progRays, "uColor"), 0);
                GL20.glUniform1i(loc(progRays, "uGlow"), 1);
                GL20.glUniform1i(loc(progRays, "uDepth"), 2);
                GL20.glUniform2f(loc(progRays, "uLight"), (float) (0.5 + f.focal * v[0] / v[2] / f.width), (float) (0.5 + f.focal * v[1] / v[2] / f.height));
                GL20.glUniform1f(loc(progRays, "uStrength"), (float) fp.rays);
                GL20.glUniform3f(loc(progRays, "uTint"), fp.rayTint[0] / 255f, fp.rayTint[1] / 255f, fp.rayTint[2] / 255f);
                fullscreen();
                cur = rtA;
            }
        }
        // bloom: the bright and the glowing, quarter size, blurred, added back
        {
            setView(bloomA);
            GL20.glUseProgram(progBloomExtract);
            tex(0, cur.tex0);
            tex(1, scene.tex1);
            GL20.glUniform1i(loc(progBloomExtract, "uColor"), 0);
            GL20.glUniform1i(loc(progBloomExtract, "uGlow"), 1);
            GL20.glUniform1f(loc(progBloomExtract, "uThreshold"), (float) fp.bloomThreshold);
            GL20.glUniform2f(loc(progBloomExtract, "uTexel"), 1f / outW, 1f / outH);
            fullscreen();
            int radius = Math.max(2, (int) Math.round(5 * outW / 720.0 / 4.0));
            for (int pass = 0; pass < 2; pass++) {
                setView(bloomB);
                GL20.glUseProgram(progBlur);
                tex(0, bloomA.tex0);
                GL20.glUniform1i(loc(progBlur, "uTex"), 0);
                GL20.glUniform2f(loc(progBlur, "uDir"), 1f / bloomA.w, 0);
                GL20.glUniform1i(loc(progBlur, "uRadius"), radius);
                fullscreen();
                setView(bloomA);
                tex(0, bloomB.tex0);
                GL20.glUniform2f(loc(progBlur, "uDir"), 0, 1f / bloomA.h);
                fullscreen();
            }
            Target dst = cur == rtA ? rtB : rtA;
            setView(dst);
            GL20.glUseProgram(progBloomAdd);
            tex(0, cur.tex0);
            tex(1, bloomA.tex0);
            GL20.glUniform1i(loc(progBloomAdd, "uColor"), 0);
            GL20.glUniform1i(loc(progBloomAdd, "uBloom"), 1);
            GL20.glUniform1f(loc(progBloomAdd, "uStrength"), (float) fp.bloomStrength);
            fullscreen();
            cur = dst;
        }
        // depth of field
        if (fp.dofFocus > 0) {
            Target dst = cur == rtA ? rtB : rtA;
            setView(dst);
            GL20.glUseProgram(progDof);
            tex(0, cur.tex0);
            tex(1, scene.depth);
            GL20.glUniform1i(loc(progDof, "uColor"), 0);
            GL20.glUniform1i(loc(progDof, "uDepth"), 1);
            GL20.glUniform1f(loc(progDof, "uFocus"), (float) fp.dofFocus);
            GL20.glUniform1f(loc(progDof, "uRange"), (float) fp.dofRange);
            GL20.glUniform1f(loc(progDof, "uMaxRadius"), (float) (3 * outW / 720.0));
            GL20.glUniform2f(loc(progDof, "uTexel"), 1f / outW, 1f / outH);
            fullscreen();
            cur = dst;
        }
        // grade and exposure
        setView(ldr);
        GL20.glUseProgram(progGrade);
        tex(0, cur.tex0);
        GL20.glUniform1i(loc(progGrade, "uColor"), 0);
        GL20.glUniform1f(loc(progGrade, "uContrast"), (float) fp.contrast);
        GL20.glUniform1f(loc(progGrade, "uSat"), (float) fp.sat);
        GL20.glUniform3f(loc(progGrade, "uShadowTint"), fp.shadowTint[0] / 255f, fp.shadowTint[1] / 255f, fp.shadowTint[2] / 255f);
        GL20.glUniform3f(loc(progGrade, "uHighTint"), fp.highlightTint[0] / 255f, fp.highlightTint[1] / 255f, fp.highlightTint[2] / 255f);
        GL20.glUniform1f(loc(progGrade, "uGain"), (float) fp.gain);
        fullscreen();
        // sprites of light, added
        if (!f.sprites.isEmpty()) {
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
            GL20.glUseProgram(progSprite);
            GL20.glUniform1i(loc(progSprite, "uTex"), 0);
            GL30.glBindVertexArray(vaoSprite);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboSprite);
            for (SpriteCmd sp : f.sprites) {
                float[] cn = new float[8];
                for (int i = 0; i < 4; i++) {
                    cn[i * 2] = (float) (sp.c[i * 2] / outW * 2 - 1);
                    cn[i * 2 + 1] = (float) (1 - sp.c[i * 2 + 1] / outH * 2);
                }
                float[] vd = {cn[0], cn[1], 0, 0, cn[2], cn[3], 1, 0, cn[4], cn[5], 1, 1, cn[0], cn[1], 0, 0, cn[4], cn[5], 1, 1, cn[6], cn[7], 0, 1};
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vd, GL15.GL_STREAM_DRAW);
                tex(0, imageTexture(sp.img));
                GL20.glUniform1f(loc(progSprite, "uGain"), (float) sp.gain);
                GL20.glUniform3f(loc(progSprite, "uTint"), sp.tr, sp.tg, sp.tb);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            }
            GL11.glDisable(GL11.GL_BLEND);
        }
        // the tape: the overlay, scanlines, slip, grain, tracking, vignette
        setView(out);
        GL20.glUseProgram(progVhs);
        tex(0, ldr.tex0);
        tex(1, overlayTex);
        GL20.glUniform1i(loc(progVhs, "uColor"), 0);
        GL20.glUniform1i(loc(progVhs, "uOverlay"), 1);
        GL20.glUniform1f(loc(progVhs, "uHasOverlay"), hasOverlay ? 1f : 0f);
        java.util.Random q = new java.util.Random(fp.seed);
        GL20.glUniform1f(loc(progVhs, "uSlip"), (float) ((2 + (q.nextInt(40) == 0 ? 6 : 0) + 10 * fp.glitch) * outW / 720.0));
        GL20.glUniform1f(loc(progVhs, "uHeavy"), (float) fp.heavy);
        GL20.glUniform1f(loc(progVhs, "uFlash"), (float) fp.flash);
        GL20.glUniform1f(loc(progVhs, "uSeed"), (float) (q.nextInt(100000) / 100000.0));
        GL20.glUniform1f(loc(progVhs, "uBand"), (float) (((fp.time * 37) % (405 + 60) - 30) / 405.0));
        GL20.glUniform2f(loc(progVhs, "uSize"), outW, outH);
        double tearY = q.nextDouble(), tearH = (2 + q.nextInt(5)) / 405.0, tearShift = (q.nextInt(40) - 20) / 720.0;
        boolean tear = q.nextInt(5) == 0;
        GL20.glUniform3f(loc(progVhs, "uTear"), (float) (tear ? tearY : -1), (float) tearH, (float) tearShift);
        GL20.glUniform1f(loc(progVhs, "uGlitch"), (float) fp.glitch);
        fullscreen();
        hasOverlay = false;
        GL30.glBindVertexArray(0);
        GL20.glUseProgram(0);
    }

    /** The finished picture as ARGB ints, top row first (for the test stand and for saving frames). */
    public int[] readPixels() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, out.fbo);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        IntBuffer buf = BufferUtils.createIntBuffer(outW * outH);
        GL11.glReadPixels(0, 0, outW, outH, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buf);
        int[] px = new int[outW * outH];
        for (int y = 0; y < outH; y++) buf.position((outH - 1 - y) * outW).get(px, y * outW, outW);
        return px;
    }

    public void delete() {
        for (GlTex t : texCache.values()) GL11.glDeleteTextures(t.id);
        texCache.clear();
        for (GlTex t : imgCache.values()) GL11.glDeleteTextures(t.id);
        imgCache.clear();
        for (int pr : new int[]{progScene, progShadow, progBloomExtract, progBlur, progBloomAdd, progGrade, progVhs, progSprite, progSsao, progDof, progRays, progCopy}) GL20.glDeleteProgram(pr);
        for (Target t : new Target[]{scene, ssaoT, rtA, rtB, bloomA, bloomB, ldr, out}) {
            GL30.glDeleteFramebuffers(t.fbo);
            GL11.glDeleteTextures(t.tex0);
            if (t.tex1 != 0) GL11.glDeleteTextures(t.tex1);
            if (t.depth != 0) GL11.glDeleteTextures(t.depth);
        }
        GL30.glDeleteFramebuffers(shadowFbo);
        GL11.glDeleteTextures(shadowTex);
        GL30.glDeleteRenderbuffers(shadowDepth);
        GL11.glDeleteTextures(whiteTex);
        GL11.glDeleteTextures(overlayTex);
        GL15.glDeleteBuffers(vboScene);
        GL15.glDeleteBuffers(vboSprite);
        GL30.glDeleteVertexArrays(vaoFull);
        GL30.glDeleteVertexArrays(vaoScene);
        GL30.glDeleteVertexArrays(vaoSprite);
    }

    private static double[] norm(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) + 1e-12;
        return new double[]{v[0] / l, v[1] / l, v[2] / l};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    // ------------------------------------------------------------------ the shaders

    private static final String SCENE_VS = """
            #version 150
            in vec3 aPos; in vec2 aUV; in vec3 aNormal; in vec4 aTint; in vec4 aMat; in vec2 aBlend;
            uniform mat4 uVP;
            out vec3 vWorld; out vec3 vNormal; out vec2 vUV; out vec4 vTint; out vec4 vMat; out vec2 vBlend; out float vZ;
            void main() {
                vec4 c = uVP * vec4(aPos, 1.0);
                gl_Position = c;
                vWorld = aPos; vNormal = aNormal; vUV = aUV; vTint = aTint; vMat = aMat; vBlend = aBlend; vZ = c.w;
            }
            """;

    private static final String SCENE_FS = """
            #version 150
            in vec3 vWorld; in vec3 vNormal; in vec2 vUV; in vec4 vTint; in vec4 vMat; in vec2 vBlend; in float vZ;
            uniform sampler2D uTex; uniform sampler2D uShadow;
            uniform vec3 uCam; uniform vec3 uAmb; uniform vec4 uFog;
            uniform int uN; uniform vec4 uLightPos[24]; uniform vec3 uLightCol[24];
            uniform int uHasShadow; uniform vec3 uLPos; uniform vec3 uSR; uniform vec3 uSU; uniform vec3 uSF; uniform float uShadowTexel;
            uniform vec2 uTexInv;
            out vec4 oColor; out vec4 oGlow;
            float lum(vec3 c) { return dot(c, vec3(0.3, 0.59, 0.11)); }
            float shadowAt(vec3 w) {
                vec3 d = w - uLPos;
                float x = dot(d, uSR), y = dot(d, uSU), z = dot(d, uSF);
                if (z < 0.05) return 1.0;
                const float T = 2.7474774;                 // tan(70 degrees)
                vec2 st = vec2(0.5 + 0.5 * x / (T * z), 0.5 + 0.5 * y / (T * z));
                if (st.x < 0.002 || st.y < 0.002 || st.x > 0.998 || st.y > 0.998) return 1.0;
                float bias = 0.03 + 0.02 * z;
                float lit = 0.0;
                for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++)
                    lit += step(z - bias, texture(uShadow, st + vec2(float(i), float(j)) * uShadowTexel).r);
                return 0.18 + 0.82 * lit / 9.0;
            }
            void main() {
                vec4 tc = texture(uTex, vUV);
                float emissive = vTint.a;
                float a = 1.0;
                if (vBlend.y > 0.5) { a = tc.a * vBlend.x; if (a < 0.01) discard; }
                else if (tc.a < 0.5) discard;
                vec3 n = normalize(vNormal);
                vec3 v = normalize(uCam - vWorld);
                if (dot(n, v) < 0.0) n = -n;
                float spec = vMat.x, shine = vMat.y, bump = vMat.z, wrap = vMat.w;
                float relief = 0.0;
                if (bump > 0.0) {
                    float l0 = lum(tc.rgb), l1 = lum(texture(uTex, vUV + vec2(uTexInv.x, 0.0)).rgb), l2 = lum(texture(uTex, vUV + vec2(0.0, uTexInv.y)).rgb);
                    relief = ((l0 - l1) + (l0 - l2)) * bump;
                }
                float rim = pow(1.0 - max(0.0, dot(n, v)), 3.0);
                vec3 light = uAmb;
                vec3 specc = vec3(0.0);
                if (emissive < 0.999) {
                    for (int i = 0; i < uN; i++) {
                        vec3 dl = uLightPos[i].xyz - vWorld;
                        float d = length(dl) + 1e-6;
                        float att = max(0.0, 1.0 - d / uLightPos[i].w);
                        if (att <= 0.0) continue;
                        att *= att;
                        float nd = dot(n, dl) / d;
                        float wrapped = wrap > 0.0 ? (nd + wrap) / (1.0 + wrap) : nd;
                        if (wrapped <= 0.0) continue;
                        float warm = wrap > 0.0 ? max(0.0, wrapped - max(0.0, nd)) : 0.0;
                        nd = max(0.0, wrapped + relief);
                        float sh = 1.0;
                        if (i == 0 && uHasShadow == 1) sh = shadowAt(vWorld);
                        float k = nd * att * sh;
                        vec3 lc = uLightCol[i];
                        light += lc * vec3(k + warm * att * sh * 0.6, k + warm * att * sh * 0.18, k + warm * att * sh * 0.12);
                        if (spec > 0.0) {
                            vec3 h = normalize(dl / d + v);
                            float nh = max(0.0, dot(n, h));
                            float s = (pow(nh, shine) + 0.35 * rim) * spec * att * sh;
                            specc += lc * s;
                        }
                    }
                }
                vec3 base = tc.rgb * vTint.rgb;
                vec3 col = base * (light * (1.0 - emissive) + emissive) + specc;
                float fog = exp(-uFog.a * vZ);
                col = col * fog + uFog.rgb * (1.0 - fog);
                col = clamp(col, 0.0, 1.0);
                oColor = vec4(col, a);
                float g = emissive * (base.r + base.g + base.b) / 3.0 * fog;
                oGlow = vec4(g, g, g, a);
            }
            """;

    private static final String SHADOW_VS = """
            #version 150
            in vec3 aPos; in vec2 aUV; in vec3 aNormal; in vec4 aTint; in vec4 aMat; in vec2 aBlend;
            uniform vec3 uLPos; uniform vec3 uSR; uniform vec3 uSU; uniform vec3 uSF;
            out vec2 vUV; out float vE; out float vLZ;
            void main() {
                vec3 d = aPos - uLPos;
                float x = dot(d, uSR), y = dot(d, uSU), z = dot(d, uSF);
                const float T = 2.7474774;
                float zc = max(z, 0.06);
                gl_Position = vec4(x / T, y / T, (zc - 0.05) / 60.0 * zc * 0.0 + (zc / 60.0) * zc, zc);
                vUV = aUV; vE = aTint.a; vLZ = z;
            }
            """;

    private static final String SHADOW_FS = """
            #version 150
            in vec2 vUV; in float vE; in float vLZ;
            uniform sampler2D uTex;
            out vec4 oD;
            void main() {
                if (vE >= 0.99) discard;
                if (texture(uTex, vUV).a < 0.5) discard;
                oD = vec4(vLZ, 0.0, 0.0, 1.0);
            }
            """;

    private static final String BLOOM_EXTRACT_FS = """
            #version 150
            in vec2 vUV; uniform sampler2D uColor; uniform sampler2D uGlow; uniform float uThreshold; uniform vec2 uTexel;
            out vec4 o;
            void main() {
                vec3 acc = vec3(0.0);
                for (int j = 0; j < 4; j++) for (int i = 0; i < 4; i++) {
                    vec2 uv = vUV + (vec2(float(i), float(j)) - 1.5) * uTexel;
                    vec3 c = texture(uColor, uv).rgb;
                    float lum = dot(c, vec3(0.3, 0.59, 0.11));
                    float k = max(0.0, lum - uThreshold) + texture(uGlow, uv).r * 0.8;
                    acc += c * k;
                }
                o = vec4(acc / 16.0, 1.0);
            }
            """;

    private static final String BLUR_FS = """
            #version 150
            in vec2 vUV; uniform sampler2D uTex; uniform vec2 uDir; uniform int uRadius;
            out vec4 o;
            void main() {
                vec3 s = vec3(0.0); float n = 0.0;
                for (int d = -uRadius; d <= uRadius; d++) { s += texture(uTex, vUV + uDir * float(d)).rgb; n += 1.0; }
                o = vec4(s / n, 1.0);
            }
            """;

    private static final String BLOOM_ADD_FS = """
            #version 150
            in vec2 vUV; uniform sampler2D uColor; uniform sampler2D uBloom; uniform float uStrength;
            out vec4 o;
            void main() { o = vec4(texture(uColor, vUV).rgb + texture(uBloom, vUV).rgb * uStrength, 1.0); }
            """;

    private static final String SSAO_FS = """
            #version 150
            in vec2 vUV; uniform sampler2D uColor; uniform sampler2D uDepth; uniform float uStrength; uniform float uRadius; uniform vec2 uSize;
            out vec4 o;
            float lin(float d) { float n = 0.05, f = 80.0; return 2.0 * n * f / (f + n - (d * 2.0 - 1.0) * (f - n)); }
            void main() {
                vec3 c = texture(uColor, vUV).rgb;
                float d0 = texture(uDepth, vUV).r;
                if (d0 >= 0.99999) { o = vec4(c, 1.0); return; }
                float z = lin(d0);
                float rad = uRadius / (0.6 + z * 0.35);
                float occ = 0.0;
                vec2 dirs[8] = vec2[8](vec2(1,0), vec2(-1,0), vec2(0,1), vec2(0,-1), vec2(0.7,0.7), vec2(-0.7,0.7), vec2(0.7,-0.7), vec2(-0.7,-0.7));
                for (int i = 0; i < 8; i++) for (int s = 1; s <= 2; s++) {
                    vec2 uv = vUV + dirs[i] * rad * float(s) * 0.5 / uSize;
                    float zn = lin(texture(uDepth, uv).r);
                    float diff = z - zn;
                    if (diff > 0.04 && diff < 0.9) occ += min(1.0, diff * 2.2) / float(s);
                }
                float k = 1.0 - uStrength * min(1.0, occ / 6.0);
                o = vec4(c * k, 1.0);
            }
            """;

    private static final String RAYS_FS = """
            #version 150
            in vec2 vUV; uniform sampler2D uColor; uniform sampler2D uGlow; uniform sampler2D uDepth; uniform vec2 uLight; uniform float uStrength; uniform vec3 uTint;
            out vec4 o;
            void main() {
                vec3 c = texture(uColor, vUV).rgb;
                const int N = 40;
                vec2 d = (uLight - vUV) / float(N) * 0.9;
                vec2 p = vUV; float decay = 1.0; float acc = 0.0;
                for (int s = 0; s < N; s++) {
                    p += d;
                    if (p.x < 0.0 || p.y < 0.0 || p.x > 1.0 || p.y > 1.0) break;
                    vec3 sc = texture(uColor, p).rgb;
                    float lum = dot(sc, vec3(0.3, 0.59, 0.11));
                    float sky = texture(uDepth, p).r >= 0.99999 ? 1.0 : 0.0;
                    float m = sky > 0.5 ? lum : max(0.0, lum - 0.55) + texture(uGlow, p).r;
                    acc += m * decay;
                    decay *= 0.95;
                }
                acc = acc / float(N) * uStrength;
                o = vec4(c + acc * uTint, 1.0);
            }
            """;

    private static final String DOF_FS = """
            #version 150
            in vec2 vUV; uniform sampler2D uColor; uniform sampler2D uDepth; uniform float uFocus; uniform float uRange; uniform float uMaxRadius; uniform vec2 uTexel;
            out vec4 o;
            float lin(float d) { float n = 0.05, f = 80.0; return 2.0 * n * f / (f + n - (d * 2.0 - 1.0) * (f - n)); }
            void main() {
                float d = texture(uDepth, vUV).r;
                float coc = d >= 0.99999 ? 1.0 : min(1.0, abs(lin(d) - uFocus) / uRange);
                float rad = coc * uMaxRadius;
                if (rad < 0.5) { o = vec4(texture(uColor, vUV).rgb, 1.0); return; }
                vec3 s = vec3(0.0); float n = 0.0;
                for (int i = 0; i < 24; i++) {
                    float a = float(i) * 2.399963;
                    float r = sqrt((float(i) + 0.5) / 24.0) * rad;
                    s += texture(uColor, vUV + vec2(cos(a), sin(a)) * r * uTexel).rgb; n += 1.0;
                }
                o = vec4(s / n, 1.0);
            }
            """;

    private static final String GRADE_FS = """
            #version 150
            in vec2 vUV; uniform sampler2D uColor; uniform float uContrast; uniform float uSat; uniform vec3 uShadowTint; uniform vec3 uHighTint; uniform float uGain;
            out vec4 o;
            vec3 filmic(vec3 x) { return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0); }
            void main() {
                vec3 c = clamp(texture(uColor, vUV).rgb, 0.0, 1.0);
                float lum = dot(c, vec3(0.3, 0.59, 0.11));
                c = vec3(lum) + (c - vec3(lum)) * uSat;
                float sh = 1.0 - min(1.0, lum * 2.2), hi = max(0.0, lum * 2.0 - 1.0);
                c += uShadowTint * sh + uHighTint * hi;
                c = (c - 0.22) * uContrast + 0.22;
                c = filmic(max(c, 0.0));
                o = vec4(clamp(c * uGain, 0.0, 1.0), 1.0);
            }
            """;

    private static final String VHS_FS = """
            #version 150
            in vec2 vUV; uniform sampler2D uColor; uniform sampler2D uOverlay; uniform float uHasOverlay;
            uniform float uSlip; uniform float uHeavy; uniform float uFlash; uniform float uSeed; uniform float uBand; uniform vec2 uSize; uniform vec3 uTear; uniform float uGlitch;
            out vec4 o;
            float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233)) + uSeed * 91.7) * 43758.5453); }
            vec3 sampleAt(vec2 uv) {
                vec3 c = texture(uColor, uv).rgb;
                if (uHasOverlay > 0.5) { vec4 ov = texture(uOverlay, vec2(uv.x, 1.0 - uv.y)); c = mix(c, ov.rgb, ov.a); }
                return c;
            }
            void main() {
                vec2 uv = vUV;
                if (uTear.x >= 0.0 && uv.y > uTear.x && uv.y < uTear.x + uTear.y) uv.x = fract(uv.x - uTear.z);
                if (uGlitch > 0.3) {                                                         // a stutter tears whole rows sideways
                    float row = floor(uv.y * 40.0);
                    if (hash(vec2(row, 3.0)) < uGlitch * 0.35) uv.x = fract(uv.x + (hash(vec2(row, 5.0)) - 0.5) * 0.12 * uGlitch);
                }
                float sl = uSlip / uSize.x;
                float r = sampleAt(vec2(uv.x - sl, uv.y)).r;
                vec3 mid = sampleAt(uv);
                float b = sampleAt(vec2(uv.x + sl, uv.y)).b;
                vec3 c = vec3(r, mid.g, b);
                float n = (hash(uv * uSize) - 0.5) * (16.0 / 255.0);
                float row = floor(uv.y * 405.0);
                float k = mod(row, 2.0) < 0.5 ? 1.0 : 0.84;
                vec2 dxy = (uv - 0.5) * 2.0;
                float vig = 1.0 - (0.38 + 0.55 * uHeavy) * dot(dxy, dxy);
                c = (c + n) * k * vig + uFlash;
                float bandY = 1.0 - uv.y;
                if (bandY > uBand && bandY < uBand + 10.0 / 405.0) c += 16.0 / 255.0;
                o = vec4(clamp(c, 0.0, 1.0), 1.0);
            }
            """;

    private static final String SPRITE_VS = """
            #version 150
            in vec2 aPos; in vec2 aUV; out vec2 vUV;
            void main() { gl_Position = vec4(aPos, 0.0, 1.0); vUV = aUV; }
            """;

    private static final String SPRITE_FS = """
            #version 150
            in vec2 vUV; uniform sampler2D uTex; uniform float uGain; uniform vec3 uTint; out vec4 o;
            void main() { vec4 t = texture(uTex, vUV); o = vec4(t.rgb * uTint * t.a * uGain, 1.0); }
            """;

    private static final String COPY_FS = """
            #version 150
            in vec2 vUV; uniform sampler2D uTex; out vec4 o;
            void main() { o = vec4(texture(uTex, vUV).rgb, 1.0); }
            """;
}
