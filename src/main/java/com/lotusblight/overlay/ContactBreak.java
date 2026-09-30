package com.lotusblight.overlay;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.DoubleConsumer;

/**
 * Song two, from 1:17: the whole screen tears and asks whether you're going to die, then an eye opens
 * at the side and the Glitcher tries to cut the contact off - the eye comes apart in two and the halves
 * rush back together on the beat; the space bar has to be pressed just as they meet, or the health bar pays. Everything runs off the song
 * clock, so it can be joined mid-way.
 */
final class ContactBreak {
    static final double GLITCH_FROM = 77_000;      // 1:17 - the screen tears and asks
    static final double START = 79_000;            // 1:19 - the song halts for the lesson, then goes on and the eye opens
    static final double TUTORIAL_LEN = 20_000;     // the lesson: a scene of its own, with Honcho's track, song paused
    static final double END = 133_500;             // the eye lets go and the torn backdrop melts into the finale film
    static final double WALL_FROM = 115_000;       // the mess turns into a wall of "error" and a plea
    static final double LABELS_AT = 81_000;
    static final double LESSON_END = START + 3_500; // a moment of grace after the song comes back

    private static final double WINDOW = 140;      // ms either side of the beat that count as on time
    private static final double APPROACH = 900;    // how long the ring takes to close
    private static final double MISS_DAMAGE = 3;
    private static final double SPAM_DAMAGE = 1;   // pressing when nothing is due

    private static final String JAPANESE = "お前は死ぬのか？　そうなのか？";
    private static final String ENGLISH = "You're going to die?  Yes?";

    private final double[] beats;
    private final DoubleConsumer hurt;
    private final Runnable onHit;
    private int next;
    private boolean[] judged;
    private byte[] status;                          // 0 waiting, 1 caught, 2 missed
    private boolean started;
    private double resultAt = -1e9;
    private boolean resultHit;

    ContactBreak(double[] beats, DoubleConsumer hurt, Runnable onHit) {
        this.beats = beats;
        this.hurt = hurt;
        this.onHit = onHit;
        this.judged = new boolean[beats.length];
        this.status = new byte[beats.length];
    }

    /**
     * The moments the eye has to close in the song: every press of the space bar recorded while playing along
     * with it, taken as they were.
     */
    static double[] beatTimes() {
        return new double[]{
                80031, 80311, 80591, 80851, 81141, 81451, 81751, 82041, 82321, 82631, 82941, 83161,
                83301, 83421, 83521, 83651, 83771, 83881, 83971, 84111, 84421, 84721, 85021, 85331,
                85551, 85861, 85991, 86161, 86261, 86381, 86571, 86851, 87141, 87421, 87741, 87871,
                88071, 88381, 88551, 88691, 88991, 89231, 89501, 89801, 90111, 90351, 90481, 90621,
                90741, 90851, 90961, 91101, 91291, 91611, 91921, 92231, 92521, 92641, 92771, 92911,
                93051, 93171, 93281, 93411, 93521, 93641, 93801, 94071, 94361, 94651, 94931, 95071,
                95201, 95321, 95471, 95601, 95741, 95861, 95991, 96091, 96401, 96711, 97021, 97301,
                98521, 98841, 99131, 99431, 99721, 100061, 100351, 100621, 100911, 101231, 101531, 101831,
                102111, 102301, 102621, 102771, 103321, 103611, 103941, 104261, 104551, 104841, 105151, 105431,
                105731, 106011, 106311, 106611, 106911, 107231, 107531, 107851, 108141, 108751, 109431, 109751,
                110071, 110211, 110521, 111141, 111881, 112171, 112471, 112721, 112991, 113701, 114151, 114581,
                114901, 115031, 115331, 115891, 116521, 116671, 116831, 116971, 117281, 117431, 117731, 117991,
                118271, 118531, 118711, 118841, 118951, 119051, 119171, 119261, 119361, 119511, 119671, 119801,
                119951, 120101, 120371, 120681, 120971, 121101, 121431, 121791, 121921, 122031, 122141, 122261,
                122351, 122481, 122791, 123101, 123381, 123521, 123861, 124241, 124371, 124481, 124591, 124711,
                124851, 125161, 125501, 125791, 125961, 127311, 127401, 127581, 127681, 127911, 127991, 128201,
                128281, 128451, 128561, 128641, 128821, 128971, 129091, 129221, 129341, 129491, 129611, 129971,
                130301, 130581, 130701, 130831, 131131, 131301, 131421, 131551, 131651, 131771, 131871, 131991,
                132351};
    }

    /**
     * The lesson's own moments, on the scene's clock (79000 = its start): three shown, then a few to try. They
     * sit on hits in Honcho's track.
     */
    static double[] tutorialBeats() {
        double[] rel = {7065, 8316, 9366, 11688, 12800, 14013, 15311, 16794, 18062};
        double[] out = new double[rel.length];
        for (int i = 0; i < rel.length; i++) out[i] = START + rel[i];
        return out;
    }

    private boolean tutorialMode;

    /** This instance is the lesson: Honcho talks, there is no damage and no "your turn". */
    void setTutorial() { tutorialMode = true; }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** 0..1: how hard the screen is tearing. Quick in, quick out, over the two seconds before the eye. */
    static double glitchAmount(double ms) {
        if (ms >= 55_000 && ms < GLITCH_FROM) {
            // The build-up: from 0:55 the picture starts to twitch, more often and harder as 1:17 nears.
            double grow = smooth((ms - 55_000) / 22_000.0);
            return new Random((long) (ms / 90)).nextDouble() < 0.06 + 0.34 * grow ? 0.12 + 0.4 * grow : 0;
        }
        if (ms < GLITCH_FROM || ms >= START) return 0;
        double f = (ms - GLITCH_FROM) / (START - GLITCH_FROM);
        return Math.min(1, Math.min(f * 5, (1 - f) * 8));
    }

    /** How much the backdrop has turned into the wall of "error" (0..1). */
    static double wallAmount(double ms) {
        return smooth((ms - WALL_FROM) / 2500.0) * (1 - smooth((ms - END) / 1500.0));
    }

    /** How far the health bars have unfolded, 0..1, and how visible their names are. */
    static double unfold(double ms) {
        return smooth((ms - START) / 2000.0);
    }

    static double labelAlpha(double ms) {
        return smooth((ms - LABELS_AT) / 700.0);
    }

    /** How far either side of a beat a press still counts: less when the next beat is close by. */
    private double windowOf(int i) {
        double before = i > 0 ? beats[i] - beats[i - 1] : 1e9;
        double after = i + 1 < beats.length ? beats[i + 1] - beats[i] : 1e9;
        return Math.max(35, Math.min(WINDOW, 0.5 * Math.min(before, after)));
    }

    /** Judges the beats that have gone by. Call each frame. */
    void update(double ms) {
        if (!started) {
            started = true;                                     // joined late: skip what's gone, don't punish
            while (next < beats.length && beats[next] < ms - windowOf(next)) judged[next++] = true;
        }
        while (next < beats.length && (judged[next] || ms > beats[next] + windowOf(next))) {
            if (!judged[next]) {
                judged[next] = true;
                status[next] = 2;
                if (beats[next] >= LESSON_END) hurt.accept(MISS_DAMAGE);
                resultAt = ms;
                resultHit = false;
            }
            next++;
        }
    }

    void press(double ms) {
        if (ms < START || ms > END) return;
        int best = -1;
        double bestGap = 1e9;
        for (int i = next; i < beats.length && i < next + 6; i++) {
            if (judged[i]) continue;
            double gap = Math.abs(ms - beats[i]);
            if (gap <= windowOf(i) && gap < bestGap) { best = i; bestGap = gap; }
        }
        if (best >= 0) {
            judged[best] = true;
            status[best] = 1;
            resultAt = ms;
            resultHit = true;
            onHit.run();
        } else {
            if (ms >= LESSON_END) hurt.accept(SPAM_DAMAGE);
            resultAt = ms;
            resultHit = false;
        }
    }

    /** The question over the torn screen. Drawn into the picture that then gets torn. */
    void renderText(Graphics2D g, int w, int h, double ms) {
        if (ms < GLITCH_FROM || ms >= START + 300) return;
        double f = (ms - GLITCH_FROM) / (START + 300 - GLITCH_FROM);
        double alpha = Math.min(1, Math.min(f * 6, (1 - f) * 5));
        Random flick = new Random((long) (ms / 70));
        if (flick.nextInt(6) == 0) alpha *= 0.3;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        drawGhosted(g, JAPANESE, w, h * 0.44, h * 0.09, alpha);
        drawGhosted(g, ENGLISH, w, h * 0.55, h * 0.05, alpha);
    }

    private static void drawGhosted(Graphics2D g, String text, int w, double y, double size, double alpha) {
        g.setFont(new Font(Font.DIALOG, Font.BOLD, (int) size));
        int tw = g.getFontMetrics().stringWidth(text);
        int x = (w - tw) / 2;
        int a = (int) (255 * Math.max(0, Math.min(1, alpha)));
        g.setColor(new Color(255, 40, 80, a / 2));
        g.drawString(text, x - 6, (int) y + 2);
        g.setColor(new Color(40, 220, 255, a / 2));
        g.drawString(text, x + 6, (int) y - 2);
        g.setColor(new Color(255, 255, 255, a));
        g.drawString(text, x, (int) y);
    }

    /** Draws a picture torn into strips, its colours split, black bars cutting across it. */
    static void blit(Graphics2D g, BufferedImage buf, int w, int h, double gl, double ms) {
        Random rnd = new Random((long) (ms / 45));
        Graphics2D b = (Graphics2D) g.create();
        int shift = (int) (22 * gl);
        b.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        b.drawImage(buf, new RescaleOp(new float[]{1f, 0.1f, 0.2f, 1f}, new float[4], null), -shift, 0);
        b.drawImage(buf, new RescaleOp(new float[]{0.1f, 1f, 1f, 1f}, new float[4], null), shift, 0);
        b.setComposite(AlphaComposite.SrcOver);
        int strips = 22;
        int sh = h / strips + 1;
        for (int i = 0; i < strips; i++) {
            int y = i * sh;
            int dx = rnd.nextInt(3) == 0 ? (int) ((rnd.nextDouble() - 0.5) * 2 * w * 0.08 * gl) : 0;
            b.drawImage(buf, dx, y, dx + w, Math.min(h, y + sh), 0, y, w, Math.min(h, y + sh), null);
        }
        b.setColor(new Color(0, 0, 0, 210));
        for (int i = 0; i < 4; i++) {
            if (rnd.nextDouble() < gl) b.fillRect(0, rnd.nextInt(h), w, 3 + rnd.nextInt(16));
        }
        b.dispose();
    }

    // The torn-up screen behind everything, from 1:19
    private final GlitchBackdrop backdrop = new GlitchBackdrop();

    void renderBackdrop(Graphics2D g, int w, int h, double ms) {
        double a = smooth((ms - START) / 1500.0) * (1 - smooth((ms - END) / 1500.0));
        backdrop.render(g, w, h, ms, a);
    }

    // ------------------------------------------------------------------------------------------------
    // Two eyes in the dark that part and have to be brought together on the beat
    // ------------------------------------------------------------------------------------------------

    private static BufferedImage scleraSprite, irisSprite;

    private static synchronized void buildSprites() {
        if (scleraSprite != null) return;
        Random rnd = new Random(666);

        // The white of the eye: grey-white, dark towards the corners as on a ball, a few faint veins, grain.
        int sw = 480, sh = 300;
        BufferedImage sc = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sc.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new RadialGradientPaint(sw / 2f, sh / 2f, sw * 0.56f, new float[]{0f, 0.55f, 1f},
                new Color[]{new Color(226, 224, 218), new Color(184, 178, 172), new Color(58, 40, 42)}));
        g.fillRect(0, 0, sw, sh);
        for (int i = 0; i < 26; i++) {
            boolean left = i % 2 == 0;
            double x = left ? 6 : sw - 6, y = sh * (0.2 + 0.6 * rnd.nextDouble());
            double ang = (left ? 0 : Math.PI) + (rnd.nextDouble() - 0.5) * 1.0;
            vein(g, rnd, x, y, ang, 50 + rnd.nextInt(80), 2.0f, 40 + rnd.nextInt(60), 2);
        }
        for (int i = 0; i < 4200; i++) {
            int v = rnd.nextInt(256);
            g.setColor(new Color(v, v, v, 16 + rnd.nextInt(22)));
            g.fillRect(rnd.nextInt(sw), rnd.nextInt(sh), 1 + rnd.nextInt(2), 1);
        }
        g.dispose();
        scleraSprite = sc;

        // The iris: grey-blue, a pale ring round the pupil, dark at the rim, fine fibres.
        int is = 256, c = is / 2;
        BufferedImage ir = new BufferedImage(is, is, BufferedImage.TYPE_INT_ARGB);
        g = ir.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new RadialGradientPaint(c, c, c - 1, new float[]{0f, 0.3f, 0.72f, 1f},
                new Color[]{new Color(150, 158, 170), new Color(96, 106, 122), new Color(60, 66, 80), new Color(14, 16, 22)}));
        g.fillOval(0, 0, is, is);
        for (int i = 0; i < 320; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r0 = c * (0.22 + 0.2 * rnd.nextDouble()), r1 = c * (0.6 + 0.36 * rnd.nextDouble());
            boolean dark = rnd.nextInt(3) == 0;
            g.setStroke(new BasicStroke(0.7f + rnd.nextFloat() * 1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(dark ? new Color(0, 0, 0, 60 + rnd.nextInt(70)) : new Color(255, 255, 255, 20 + rnd.nextInt(50)));
            double bend = (rnd.nextDouble() - 0.5) * 0.1;
            g.draw(new java.awt.geom.Line2D.Double(c + Math.cos(a) * r0, c + Math.sin(a) * r0,
                    c + Math.cos(a + bend) * r1, c + Math.sin(a + bend) * r1));
        }
        g.setColor(new Color(4, 5, 8, 235));
        g.setStroke(new BasicStroke(c * 0.11f));
        g.drawOval(2, 2, is - 4, is - 4);
        g.dispose();
        irisSprite = ir;
    }

    private static void vein(Graphics2D g, Random rnd, double x, double y, double ang, double len, float wd, int alpha, int depth) {
        double px = x, py = y;
        int steps = Math.max(2, (int) (len / 9));
        for (int i = 0; i < steps; i++) {
            ang += (rnd.nextDouble() - 0.5) * 0.7;
            double nx = px + Math.cos(ang) * 9, ny = py + Math.sin(ang) * 9;
            g.setStroke(new BasicStroke(Math.max(0.6f, wd * (1 - i / (float) steps)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(150, 60, 64, Math.max(0, Math.min(255, alpha))));
            g.draw(new java.awt.geom.Line2D.Double(px, py, nx, ny));
            if (depth > 0 && rnd.nextInt(6) == 0) {
                vein(g, rnd, nx, ny, ang + (rnd.nextBoolean() ? 0.75 : -0.75), len * 0.5, wd * 0.6f, alpha * 3 / 4, depth - 1);
            }
            px = nx;
            py = ny;
        }
    }

    /**
     * How far apart the halves are, 0 (together) .. 1. After a beat they part - a lot if the next one is far
     * off, only a twitch if it is right behind - and rush together so as to meet exactly on the next beat.
     */
    private double apart(double ms) {
        if (next >= beats.length) return 0;
        double nextBeat = beats[next];
        double prevBeat = next > 0 ? beats[next - 1] : nextBeat - 1500;
        double interval = nextBeat - prevBeat;
        if (interval <= 0 || ms >= nextBeat) return 0;
        double u = Math.max(0, (ms - prevBeat) / interval);
        double amount = Math.min(1, interval / 600.0);
        double out = smooth(u / 0.3);
        double back = u < 0.45 ? 0 : Math.pow((u - 0.45) / 0.55, 2.0);
        return amount * out * (1 - back);
    }

    /** 0..1 progress towards the coming beat (only shown when it is far enough off to follow). */
    private double progress(double ms) {
        if (next >= beats.length) return 0;
        double nextBeat = beats[next];
        double prevBeat = next > 0 ? beats[next - 1] : nextBeat - 1500;
        double interval = nextBeat - prevBeat;
        if (interval < 500 || ms >= nextBeat) return 0;
        return Math.max(0, Math.min(1, (ms - prevBeat) / interval));
    }

    /** One big diamond eye that splits along its slit on the beat; it watches the cursor. */
    void renderEye(Graphics2D g, int w, int h, double ms) {
        double open = smooth((ms - START - 1500) / 1600.0) * (1 - smooth((ms - END) / 800.0));
        if (open <= 0) {
            // Honcho speaks first, before the eye has opened.
            if (tutorialMode && ms >= START && ms < START + TUTORIAL_LEN + 100) {
                Graphics2D hb = (Graphics2D) g.create();
                drawHoncho(hb, w, h, ms, 1.0);
                hb.dispose();
            }
            return;
        }
        buildSprites();
        double r = h * 0.19;                                   // half of the long diagonal
        double cx = w * 0.5, cy = h * 0.42;
        double t = ms / 1000.0;

        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double since = ms - resultAt;
        boolean fresh = since >= 0 && since < 420;
        Color line = fresh ? (resultHit ? new Color(90, 255, 170) : new Color(255, 70, 80)) : new Color(30, 176, 245);
        double p = progress(ms);
        double gapFrac = apart(ms);
        double ex = gapFrac * r * 1.05;
        double shake = fresh && !resultHit ? (1 - since / 420.0) * r * 0.05 : gapFrac * r * 0.012;
        Random jitter = new Random((long) (ms / 40));
        double tilt = 0.0555;                                  // the whole diamond leans a hair, as the bar was drawn

        drawTimerRing(b, cx, cy, r, r * 1.75, p, open);

        // A blink now and then: the diamond narrows to its slit and opens again.
        double idleBlink = Math.max(0, 1 - Math.abs(((t + 1.3) % 6.1) - 0.1) / 0.1);
        double blink = Math.max(idleBlink, fresh && resultHit ? Math.max(0, 1 - since / 150.0) : 0);
        double narrow = 1 - 0.85 * blink;

        double mx = cx, my = cy;
        try {
            java.awt.PointerInfo pi = java.awt.MouseInfo.getPointerInfo();
            if (pi != null) { mx = pi.getLocation().x; my = pi.getLocation().y; }
        } catch (RuntimeException ignored) { }
        double gaze = Math.max(-1, Math.min(1, (mx - cx) / (w * 0.35)));

        for (int side = -1; side <= 1; side += 2) {
            Graphics2D hb = (Graphics2D) b.create();
            hb.translate(cx, cy);
            hb.rotate(tilt);
            hb.translate(side * ex + (jitter.nextDouble() - 0.5) * shake, (jitter.nextDouble() - 0.5) * shake);
            hb.scale(narrow, 1);
            hb.clip(side < 0 ? new java.awt.geom.Rectangle2D.Double(-r * 2, -r * 2, r * 2, r * 4)
                    : new java.awt.geom.Rectangle2D.Double(0, -r * 2, r * 2, r * 4));
            drawDiamond(hb, r, open, gaze, line, ms, p, fresh && resultHit ? 1 - since / 420.0 : 0);
            hb.dispose();
        }

        drawTrack(b, w, h, ms, open);

        if (tutorialMode) drawHoncho(b, w, h, ms, 1.0);
        drawGo(b, w, h, ms, open);
        b.dispose();
    }

    private static BufferedImage honchoImage;

    private static synchronized BufferedImage honcho() {
        if (honchoImage == null) {
            try (java.io.InputStream in = ContactBreak.class.getResourceAsStream("/assets/lotusblight/overlay/honcho.png")) {
                if (in != null) honchoImage = javax.imageio.ImageIO.read(in);
            } catch (java.io.IOException ignored) { }
            if (honchoImage == null) {
                // No portrait supplied: his suit from the front of the skin texture stands in.
                try (java.io.InputStream in = ContactBreak.class.getResourceAsStream("/assets/lotusblight/textures/entity/honcho.png")) {
                    if (in != null) {
                        BufferedImage skin = javax.imageio.ImageIO.read(in);
                        honchoImage = skin.getSubimage(20, 20, 8, 12);
                    }
                } catch (java.io.IOException | RuntimeException ignored) { }
            }
        }
        return honchoImage;
    }

    private static boolean[][] glitchLayer;

    /** The pattern of his glitching layer: the hat layer of his skin (the scattered dark pixels). */
    private static synchronized boolean[][] glitchLayer() {
        if (glitchLayer == null) {
            glitchLayer = new boolean[8][8];
            boolean found = false;
            try (java.io.InputStream in = ContactBreak.class.getResourceAsStream("/assets/lotusblight/textures/entity/honcho.png")) {
                if (in != null) {
                    BufferedImage skin = javax.imageio.ImageIO.read(in);
                    for (int y = 0; y < 8; y++) {
                        for (int x = 0; x < 8; x++) {
                            if ((skin.getRGB(40 + x, 8 + y) >>> 24) > 10) { glitchLayer[y][x] = true; found = true; }
                        }
                    }
                }
            } catch (java.io.IOException | RuntimeException ignored) { }
            if (!found) {
                Random r = new Random(9);
                for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) glitchLayer[y][x] = r.nextInt(3) == 0;
            }
        }
        return glitchLayer;
    }

    /** His head: a black square with a glitching layer over it; the glitch is worse while he talks. */
    private void drawHonchoHead(Graphics2D b, double px, double py, double ps, double a, double ms, boolean talking) {
        java.awt.Shape oldClip = b.getClip();
        b.clip(new java.awt.geom.RoundRectangle2D.Double(px, py, ps, ps, 14, 14));
        b.setColor(new Color(34, 40, 76, (int) (255 * a)));
        b.fillRect((int) px, (int) py, (int) ps, (int) ps);
        double inset = ps * 0.12, hs = ps - inset * 2, hx = px + inset, hy = py + inset;
        Random rnd = new Random((long) (ms / (talking ? 70 : 140)));
        double tear = talking ? 1 : 0.35;
        // The black square, torn into strips that now and then slide sideways.
        int strips = 12;
        double sh = hs / strips;
        b.setColor(new Color(0, 0, 0, (int) (255 * a)));
        for (int i = 0; i < strips; i++) {
            double dx = rnd.nextDouble() < 0.22 * tear ? (rnd.nextDouble() - 0.5) * hs * 0.34 * tear : 0;
            b.fill(new java.awt.geom.Rectangle2D.Double(hx + dx, hy + i * sh, hs, sh + 1));
        }
        // Over it, the layer of scattered pixels a little larger than the head: black like the head, but with
        // cyan and magenta copies of it showing at its edges, all jumping about.
        boolean[][] layer = glitchLayer();
        double cell = hs / 8.0 * 1.14, ox = hx - hs * 0.07, oy = hy - hs * 0.07;
        int[][] tint = {{60, 230, 255}, {255, 60, 200}, {0, 0, 0}};
        for (int c = 0; c < 3; c++) {
            double spread = (talking ? 0.05 : 0.028) * ps;
            double jx = (rnd.nextDouble() - 0.5) * ps * (talking ? 0.07 : 0.03) + (c == 0 ? -spread : c == 1 ? spread : 0);
            double jy = (rnd.nextDouble() - 0.5) * ps * (talking ? 0.04 : 0.015);
            b.setColor(new Color(tint[c][0], tint[c][1], tint[c][2], (int) (255 * a * (c < 2 ? 0.8 : 1))));
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    if (layer[y][x]) b.fill(new java.awt.geom.Rectangle2D.Double(ox + x * cell + jx, oy + y * cell + jy, cell, cell));
                }
            }
        }
        // A few bright blocks flicking on and off over the square.
        int blocks = talking ? 5 : 2;
        for (int i = 0; i < blocks; i++) {
            double bw = hs * (0.04 + 0.08 * rnd.nextDouble()), bh = hs * (0.015 + 0.035 * rnd.nextDouble());
            b.setColor(new Color(rnd.nextBoolean() ? 60 : 255, 230, 255, (int) (150 * a)));
            b.fill(new java.awt.geom.Rectangle2D.Double(hx + rnd.nextDouble() * hs, hy + rnd.nextDouble() * hs, bw, bh));
        }
        b.setClip(oldClip);
    }

    /** What Honcho says, when: {from, to, text}. He types each line out, one replacing the last. */
    private static final Object[][] SPEECH = {
            {79_300.0, 81_600.0, "Привет…"},
            {81_700.0, 84_000.0, "Кажется, кое-кто вышел из-под контроля! Я помогу ;0"},
            {84_200.0, 89_100.0, "Смотри: глаз раскалывается. Когда метка дойдёт до белой линии, жми ПРОБЕЛ. Вот так!"},
            {90_200.0, 99_000.0, "Теперь сам. Попробуй!"},
    };

    /** The short lesson, in a dialogue window with Honcho, played out in silence: what to press, and a few tries. */
    private void drawHoncho(Graphics2D b, int w, int h, double ms, double open) {
        if (ms < START + 200 || ms >= START + TUTORIAL_LEN + 100) return;
        double a = Math.min(1, (ms - START - 200) / 350.0) * (1 - smooth((ms - (START + TUTORIAL_LEN - 300)) / 300.0)) * open;
        if (a <= 0) return;
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double wx = w * 0.14, wy = h * 0.685, ww = w * 0.72, wh = h * 0.17;
        // The window.
        b.setColor(new Color(10, 10, 22, (int) (232 * a)));
        b.fillRoundRect((int) wx, (int) wy, (int) ww, (int) wh, 26, 26);
        b.setColor(new Color(150, 170, 255, (int) (220 * a)));
        b.setStroke(new BasicStroke(3f));
        b.drawRoundRect((int) wx, (int) wy, (int) ww, (int) wh, 26, 26);
        b.setColor(new Color(60, 80, 190, (int) (230 * a)));
        b.fillRoundRect((int) wx + 3, (int) wy + 3, (int) ww - 6, (int) (h * 0.034), 22, 22);
        b.setFont(new Font(Font.DIALOG, Font.BOLD, (int) (h * 0.022)));
        b.setColor(new Color(255, 255, 255, (int) (255 * a)));
        b.drawString("Хончо", (int) (wx + 22), (int) (wy + h * 0.025));

        // The portrait.
        double ps = wh * 0.68, px = wx + 20, py = wy + h * 0.045;
        b.setColor(new Color(30, 30, 60, (int) (255 * a)));
        b.fillRoundRect((int) px, (int) py, (int) ps, (int) ps, 14, 14);

        // The line being said, typed out.
        boolean talking = false;
        String line = "";
        for (Object[] sp : SPEECH) {
            double from = (Double) sp[0], to = (Double) sp[1];
            if (ms >= from) {
                String text = (String) sp[2];
                int shown = (int) Math.min(text.length(), (ms - from) / 1000.0 * 34);
                line = text.substring(0, shown);
                talking = shown < text.length();
                if (ms > to && sp == SPEECH[SPEECH.length - 1]) line = text;
            }
        }
        drawHonchoHead(b, px, py, ps, a, ms, talking);
        b.setColor(new Color(150, 170, 255, (int) (200 * a)));
        b.setStroke(new BasicStroke(2f));
        b.drawRoundRect((int) px, (int) py, (int) ps, (int) ps, 14, 14);
        b.setFont(new Font(Font.DIALOG, Font.BOLD, (int) (h * 0.03)));
        b.setColor(new Color(255, 255, 255, (int) (250 * a)));
        int tx = (int) (px + ps + 26), maxW = (int) (wx + ww - 22 - h * 0.17 - tx);
        int ty = (int) (wy + h * 0.075);
        java.awt.FontMetrics fm = b.getFontMetrics();
        StringBuilder cur = new StringBuilder();
        for (String word : line.split(" ", -1)) {
            String tryLine = cur.length() == 0 ? word : cur + " " + word;
            if (fm.stringWidth(tryLine) > maxW && cur.length() > 0) {
                b.drawString(cur.toString(), tx, ty);
                ty += (int) (h * 0.04);
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(tryLine);
            }
        }
        b.drawString(cur.toString(), tx, ty);

        // The space bar, pressed by itself on the trial beats to show how.
        boolean pressed = false;
        for (int i = 0; i < 3 && i < beats.length; i++) if (Math.abs(ms - beats[i]) < 150) pressed = true;
        double kw = h * 0.15, kh = h * 0.05, kx = wx + ww - 22 - kw, ky = wy + wh - 22 - kh + (pressed ? 5 : 0);
        b.setColor(new Color(pressed ? 235 : 190, pressed ? 240 : 200, 255, (int) (255 * a)));
        b.fillRoundRect((int) kx, (int) ky, (int) kw, (int) kh, 12, 12);
        b.setColor(new Color(60, 70, 130, (int) (255 * a)));
        b.setStroke(new BasicStroke(2f));
        b.drawRoundRect((int) kx, (int) ky, (int) kw, (int) kh, 12, 12);
        b.setFont(new Font(Font.DIALOG, Font.BOLD, (int) (h * 0.022)));
        int kwText = b.getFontMetrics().stringWidth("ПРОБЕЛ");
        b.setColor(new Color(20, 25, 60, (int) (255 * a)));
        b.drawString("ПРОБЕЛ", (int) (kx + (kw - kwText) / 2), (int) (ky + kh * 0.66));
    }

    /** "Your turn" flashed as the track starts, for a beat. */
    private void drawGo(Graphics2D b, int w, int h, double ms, double open) {
        if (tutorialMode || ms < START || ms >= START + 2200) return;
        double a = Math.max(0, 1 - (ms - START) / 2200.0) * open;
        b.setFont(new Font(Font.DIALOG, Font.BOLD, (int) (h * 0.06)));
        String s = "ТВОЯ ОЧЕРЕДЬ";
        int tw = b.getFontMetrics().stringWidth(s);
        b.setColor(new Color(0, 0, 0, (int) (170 * a)));
        b.fillRoundRect((w - tw) / 2 - 30, (int) (h * 0.79) - (int) (h * 0.058), tw + 60, (int) (h * 0.085), 22, 22);
        b.setColor(new Color(0, 0, 0, (int) (200 * a)));
        b.drawString(s, (w - tw) / 2 + 3, (int) (h * 0.79) + 3);
        b.setColor(new Color(255, 255, 255, (int) (255 * a)));
        b.drawString(s, (w - tw) / 2, (int) (h * 0.79));
    }

    /** The strip under the eye: every beat rides in from the right and the space bar goes when it's on the line. */
    private void drawTrack(Graphics2D b, int w, int h, double ms, double open) {
        double y = h * 0.9, x0 = w * 0.1, x1 = w * 0.9, hitX = w * 0.3, ahead = 2000, behind = 450;
        double half = h * 0.036;
        // The strip itself.
        b.setColor(new Color(0, 0, 0, (int) (205 * open)));
        b.fillRoundRect((int) x0, (int) (y - half), (int) (x1 - x0), (int) (half * 2), 18, 18);
        b.setColor(new Color(255, 255, 255, (int) (70 * open)));
        b.setStroke(new BasicStroke(2f));
        b.drawRoundRect((int) x0, (int) (y - half), (int) (x1 - x0), (int) (half * 2), 18, 18);

        // A glow on the line while a beat can be caught.
        boolean live = false;
        for (int i = Math.max(0, next - 2); i < beats.length && i < next + 4; i++) {
            if (!judged[i] && Math.abs(ms - beats[i]) <= windowOf(i)) { live = true; break; }
        }
        if (live) {
            b.setColor(new Color(255, 255, 255, (int) (70 * open)));
            b.fillRect((int) (hitX - h * 0.018), (int) (y - half), (int) (h * 0.036), (int) (half * 2));
        }

        // The beats, from those just gone to those coming in two seconds.
        double size = h * 0.026;
        for (int i = Math.max(0, next - 10); i < beats.length; i++) {
            double dt = beats[i] - ms;
            if (dt > ahead) break;
            if (dt < -behind) continue;
            double x = hitX + dt / ahead * (x1 - hitX);
            if (x < x0) continue;
            double a = dt < 0 ? Math.max(0, 1 + dt / behind) : Math.min(1, (x1 - x) / (h * 0.06) + 0.25);
            Color c = status[i] == 1 ? new Color(120, 255, 190) : status[i] == 2 ? new Color(255, 70, 80)
                    : (i == next ? new Color(255, 255, 255) : new Color(30, 176, 245));
            double sz = size * (i == next && status[i] == 0 ? 1.25 : 1.0) * (status[i] == 1 && dt < 0 ? 1 + (-dt / behind) * 1.2 : 1);
            java.awt.geom.Path2D.Double d = new java.awt.geom.Path2D.Double();
            d.moveTo(x - sz * 0.75, y);
            d.lineTo(x, y - sz * 1.15);
            d.lineTo(x + sz * 0.75, y);
            d.lineTo(x, y + sz * 1.15);
            d.closePath();
            b.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (235 * a * open)));
            b.fill(d);
            b.setColor(new Color(255, 255, 255, (int) (200 * a * open)));
            b.setStroke(new BasicStroke(1.5f));
            b.draw(d);
        }

        // The line.
        b.setStroke(new BasicStroke(3f));
        b.setColor(new Color(255, 255, 255, (int) (240 * open)));
        b.draw(new java.awt.geom.Line2D.Double(hitX, y - half * 1.25, hitX, y + half * 1.25));
    }

    /** A thin ring round the eye with an arc that fills up and closes exactly on the beat. */
    private static void drawTimerRing(Graphics2D b, double cx, double cy, double r, double rr, double p, double open) {
        b.setStroke(new BasicStroke((float) (r * 0.02)));
        b.setColor(new Color(255, 255, 255, (int) (50 * open)));
        b.draw(new java.awt.geom.Ellipse2D.Double(cx - rr, cy - rr, rr * 2, rr * 2));
        if (p > 0) {
            b.setStroke(new BasicStroke((float) (r * 0.05), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            b.setColor(new Color(255, 255, 255, (int) (215 * open)));
            b.draw(new java.awt.geom.Arc2D.Double(cx - rr, cy - rr, rr * 2, rr * 2, 90, -360 * p, java.awt.geom.Arc2D.OPEN));
        }
        b.setColor(new Color(255, 255, 255, (int) (200 * open)));
        java.awt.geom.Path2D.Double tri = new java.awt.geom.Path2D.Double();
        tri.moveTo(cx, cy - rr - r * 0.02);
        tri.lineTo(cx - r * 0.07, cy - rr - r * 0.16);
        tri.lineTo(cx + r * 0.07, cy - rr - r * 0.16);
        tri.closePath();
        b.fill(tri);
    }

    /**
     * The diamond, centred on 0,0 (the caller cuts it down the middle), as it was drawn: straight sides, a
     * touch taller than wide, and down the vertical diagonal one thick bar, leaning a little.
     */
    private static void drawDiamond(Graphics2D b, double r, double open, double gaze, Color line, double ms, double p, double flash) {
        double hx = r, hy = r * 1.08 * open;
        if (hy < 1) return;
        java.awt.geom.Path2D.Double d = new java.awt.geom.Path2D.Double();
        d.moveTo(-hx, 0);
        d.lineTo(0, -hy);
        d.lineTo(hx, 0);
        d.lineTo(0, hy);
        d.closePath();

        // The bar: narrow at the corners, fullest just below the middle, tipped so the top leans right.
        double top = 0, bottom = 0;
        java.awt.geom.Path2D.Double bar = new java.awt.geom.Path2D.Double();
        bar.moveTo(top - r * 0.032, -hy);
        bar.lineTo(top + r * 0.032, -hy);
        bar.lineTo(top * 0.15 + r * 0.09, -hy * 0.1);
        bar.lineTo(bottom + r * 0.06, hy);
        bar.lineTo(bottom - r * 0.06, hy);
        bar.lineTo(top * 0.15 - r * 0.09, -hy * 0.1);
        bar.closePath();
        b.setColor(line);
        b.fill(bar);
        if (flash > 0) {
            b.setColor(new Color(255, 255, 255, (int) (200 * flash)));
            b.fill(bar);
        }

        b.setStroke(new BasicStroke((float) (r * 0.03), BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER));
        b.setColor(line);
        b.draw(d);
    }
}
