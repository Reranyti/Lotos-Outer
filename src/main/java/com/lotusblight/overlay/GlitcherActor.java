package com.lotusblight.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

/**
 * The Glitcher during the first song: he walks, throws the desktop icons at the player, gets angry,
 * stomps - and at that the screen goes dark, the eyes open and he rises to the middle of them.
 * Everything is a function of the song clock only, so it needs no state and survives jumping in time.
 */
final class GlitcherActor {
    static final double THROW_FROM_MS = 40_000;
    static final double THROW_EVERY_MS = 3_500;
    static final double THROW_TO_MS = 73_500;
    static final double ANGER_MS = 70_000;         // he starts to seethe
    static final double GATHER_MS = 74_000;        // and comes to the middle to stand there
    static final double STOMP_MS = 80_000;         // the foot comes down
    static final double BLACKOUT_MS = 80_300;      // and everything on the screen is gone
    static final double LIFT_MS = 80_500;          // he rises to the middle of the eyes
    static final double RETURN_MS = 104_000;       // and it all lets go again
    static final double KNOCK_FROM = 12_000;       // he stops and knocks on the glass
    static final double WAVE_FROM = 14_800;
    static final double KNOCK_TO = 17_000;
    static final double COLLECT_FROM = 24_000;     // and goes to the left edge to pick up icons
    static final double COLLECT_TO = 37_000;
    static final double[] GRABS = {27_500, 31_000, 34_500};
    static final double STARE_FROM = 112_000;      // after the eyes: he stops and looks at you
    static final double STARE_TO = 118_000;
    static final double DISSOLVE_FROM = 122_500;   // and at the end of the song comes apart
    static final double DISSOLVE_MS = 3_500;

    private static final double LEG_ROLL = -0.9;
    private static final double SS = 1.5;          // drawn this many times larger, then scaled down smooth
    private static final int RIGHT_ARM = SkinModel.Part.RIGHT_ARM.ordinal();
    private static final int LEFT_ARM = SkinModel.Part.LEFT_ARM.ordinal();
    private static final int RIGHT_LEG = SkinModel.Part.RIGHT_LEG.ordinal();
    private static final int LEFT_LEG = SkinModel.Part.LEFT_LEG.ordinal();
    private static final int BODY = SkinModel.Part.BODY.ordinal();
    private static final int HEAD = SkinModel.Part.HEAD.ordinal();

    private final SkinModel model = new SkinModel();
    private final int[] skin;
    private final double unit;                     // screen pixels per skin pixel at scale 1
    private final SoftRenderer renderer;
    private final SoftRenderer.Pose pose = new SoftRenderer.Pose();
    private final double ox, oy;

    GlitcherActor(int[] skin, int screenH) {
        this.skin = skin;
        this.unit = screenH * 0.40 / 32.0;
        double u = unit * SS;
        this.renderer = new SoftRenderer((int) (52 * u), (int) (46 * u));
        this.ox = renderer.width / 2.0;
        this.oy = renderer.height - 3 * u;
    }

    static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** 0 up to the stomp, 1 from the blackout on until the eyes let go. */
    static double blackout(double ms) {
        return ms >= BLACKOUT_MS ? 1 - smooth((ms - RETURN_MS) / 1500.0) : 0;
    }

    /** How angry he is, 0..1: builds from 1:10, gone the moment the foot is down. */
    static double anger(double ms) {
        return ms >= STOMP_MS ? 0 : smooth((ms - ANGER_MS) / 8000.0);
    }

    private static double pull(double x, double anchor, double weight) {
        return x + (anchor - x) * weight;
    }

    private static double pathX(double ms, int w) {
        return w * (0.5 + 0.42 * Math.sin(ms / 1000.0 * 0.55));
    }

    private static double bell(double ms, double from, double len) {
        double f = (ms - from) / len;
        return f <= 0 || f >= 1 ? 0 : Math.sin(Math.PI * f);
    }

    /**
     * @param hoverFootY where his feet are when he floats in the middle of the field
     * @param hoverScale how big he is then (the field can draw away and take him with it)
     * @param spots where the desktop icons sit, so he can walk up and take them (may be empty)
     */
    void render(Graphics2D g, int w, int h, double ms, double hoverFootY, double hoverScale,
                List<BufferedImage> icons, List<java.awt.Rectangle> spots) {
        double t = ms / 1000.0;
        double anger = anger(ms);
        double cx = w * 0.5;

        double pathX = pathX(ms, w);
        double heading = Math.cos(t * 0.55) >= 0 ? 1 : -1;
        double x = pathX;
        x = pull(x, -w * 0.09, 1 - smooth(ms / 4500.0));                      // walking in from the left edge
        double knock = smooth((ms - KNOCK_FROM + 500) / 700.0) * (1 - smooth((ms - KNOCK_TO) / 700.0));
        x = pull(x, pathX(KNOCK_FROM, w), knock);                              // stopped to knock on the glass
        double collect = smooth((ms - COLLECT_FROM) / 3000.0) * (1 - smooth((ms - COLLECT_TO) / 3000.0));
        x = pull(x, w * 0.075, collect);                                       // at the left edge, taking icons
        if (collect > 0.3) heading = -1;
        double gather = smooth((ms - GATHER_MS) / 3000.0) * (1 - smooth((ms - RETURN_MS) / 3000.0));
        x = pull(x, cx, gather);                                               // in the middle before the stomp
        double stare = smooth((ms - STARE_FROM) / 2000.0) * (1 - smooth((ms - STARE_TO) / 1500.0));
        x = pull(x, pathX(STARE_FROM, w), stare);                              // looking out at you
        double toEnd = smooth((ms - STARE_TO) / 3000.0);
        x = pull(x, cx, toEnd);                                                // and to the middle, to fall apart
        double standing = smooth((ms - GATHER_MS - 2000) / 1000.0) * (1 - smooth((ms - RETURN_MS - 1000) / 1000.0));
        double atEnd = smooth((ms - STARE_TO - 2500) / 800.0);
        double idle = Math.max(Math.max(standing, knock), Math.max(Math.max(stare, atEnd), Math.pow(collect, 6)));
        double lev = smooth((ms - LIFT_MS) / 2700.0) * (1 - smooth((ms - RETURN_MS) / 1500.0));

        double calm = smooth((ms - RETURN_MS - 2000) / 2000.0);
        double freq = (2.4 + 2.2 * anger) * (1 - 0.4 * calm);
        double phase = t * freq * Math.PI;
        double walk = 1 - idle;
        double groundY = h * 0.72 + Math.abs(Math.sin(phase)) * h * 0.012 * walk;
        double footY = groundY + (hoverFootY - groundY) * lev;
        double scale = 1 + (hoverScale - 1) * lev;

        pose.reset();
        double facing = heading * 1.15 * (1 - idle);
        pose.yaw = facing;

        double sw = Math.sin(phase) * walk * (0.65 + 0.35 * anger);
        pose.partPitch[RIGHT_LEG] = sw;
        pose.partPitch[LEFT_LEG] = -sw;
        pose.partPitch[RIGHT_ARM] = -sw * 0.75;
        pose.partPitch[LEFT_ARM] = sw * 0.75;
        pose.partYaw[BODY] = Math.sin(phase) * 0.1 * walk;

        // Knocking on the glass, then a wave.
        double handX = x - unit * 5.5 * scale, handY = footY - unit * 17 * scale;
        if (knock > 0.02) {
            double pulse = 0;
            for (int i = 0; i < 3; i++) pulse = Math.max(pulse, Math.exp(-Math.pow((ms - (KNOCK_FROM + 500 + i * 800)) / 110.0, 2)));
            double wave = smooth((ms - WAVE_FROM) / 300.0) * (1 - smooth((ms - KNOCK_TO) / 500.0));
            pose.partPitch[RIGHT_ARM] = (-1.45 + 0.35 * pulse) * knock;
            pose.partPitch[LEFT_ARM] = 0;
            pose.partRoll[LEFT_ARM] = (2.5 + 0.35 * Math.sin(ms * 0.014)) * wave;
        }

        // Picking icons up off the edge: bend, reach, and up they come.
        double grab = 0;
        for (double gt : GRABS) grab = Math.max(grab, bell(ms, gt, 900));
        if (grab > 0) {
            pose.pitch = 0.45 * grab;
            pose.partPitch[RIGHT_ARM] = -1.3 * grab;
            pose.partPitch[HEAD] = 0.25 * grab;
        }

        // Throwing an icon: wind up, let go, follow through.
        double sinceThrow = throwProgress(ms);
        if (sinceThrow >= 0) {
            double windUp = smooth(sinceThrow / 500.0);
            double swing = smooth((sinceThrow - 600.0) / 140.0);
            double back = smooth((sinceThrow - 900.0) / 500.0);
            double arm = 2.5 * windUp * (1 - swing) + (-1.3) * swing * (1 - back);
            pose.partPitch[RIGHT_ARM] = arm;
        }

        // The stomp: arms up and out, one knee raised, held, then down hard.
        if (ms >= STOMP_MS - 1800 && ms < STOMP_MS + 250) {
            double up = smooth((ms - (STOMP_MS - 1800)) / 1300.0);
            double slam = smooth((ms - STOMP_MS) / 80.0);
            double raised = up * (1 - slam);
            pose.partPitch[RIGHT_LEG] = -0.55 * raised;
            pose.partRoll[RIGHT_LEG] = LEG_ROLL * raised;
            pose.partPitch[LEFT_LEG] = 0;
            pose.partRoll[RIGHT_ARM] = -0.7 * up;
            pose.partRoll[LEFT_ARM] = 0.7 * up;
            pose.partPitch[RIGHT_ARM] = -0.5 * up;
            pose.partPitch[LEFT_ARM] = -0.5 * up;
            pose.pitch = 0.12 * slam * (1 - smooth((ms - STOMP_MS - 80) / 170.0)) - 0.06 * up;
        }

        // Floating: arms out, legs hanging, turning slowly to look out at the eyes.
        if (lev > 0) {
            pose.yaw += (Math.sin(t * 0.6) * 0.35 - facing) * lev;
            pose.partRoll[RIGHT_ARM] = -0.95 * lev;
            pose.partRoll[LEFT_ARM] = 0.95 * lev;
            pose.partPitch[RIGHT_ARM] *= 1 - lev;
            pose.partPitch[LEFT_ARM] *= 1 - lev;
            pose.partPitch[RIGHT_LEG] = Math.sin(t * 1.3) * 0.12 * lev + sw * (1 - lev);
            pose.partPitch[LEFT_LEG] = -Math.sin(t * 1.3 + 0.7) * 0.12 * lev - sw * (1 - lev);
            pose.partRoll[RIGHT_LEG] = -0.1 * lev;
            pose.partRoll[LEFT_LEG] = 0.1 * lev;
            pose.partPitch[HEAD] = -0.15 * lev;
            footY += Math.sin(t * 1.4) * h * 0.012 * lev;
        }

        // Standing and looking at the player, head a little to one side.
        if (stare > 0) {
            pose.partRoll[HEAD] = 0.22 * stare;
            pose.partPitch[HEAD] = -0.1 * stare;
        }

        // A tremor while he seethes, and a jolt at the stomp.
        double shakeX = 0, shakeY = 0;
        if (anger > 0) {
            Random jitter = new Random((long) (ms / 40));
            shakeX = (jitter.nextDouble() - 0.5) * 7 * anger;
            shakeY = (jitter.nextDouble() - 0.5) * 4 * anger;
        }
        if (ms >= STOMP_MS && ms < STOMP_MS + 600) {
            double f = 1 - (ms - STOMP_MS) / 600.0;
            Random jitter = new Random((long) (ms / 30));
            shakeX += (jitter.nextDouble() - 0.5) * 26 * f;
            shakeY += (jitter.nextDouble() - 0.5) * 20 * f;
        }

        renderer.clear();
        renderer.draw(model, skin, pose, unit * SS, ox, oy);

        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double px = x + shakeX, py = footY + shakeY;
        if (lev < 1) {
            double sr = unit * 9 * scale;
            b.setPaint(new RadialGradientPaint((float) px, (float) (groundY + unit * 0.6), (float) sr, new float[]{0f, 1f},
                    new Color[]{new Color(0, 0, 0, (int) (90 * (1 - lev))), new Color(0, 0, 0, 0)}));
            b.fill(new java.awt.geom.Ellipse2D.Double(px - sr, groundY + unit * 0.6 - sr * 0.28, sr * 2, sr * 0.56));
        }
        AffineTransform base = b.getTransform();
        b.translate(px, py);
        b.scale(scale / SS, scale / SS);
        double dissolve = Math.max(0, Math.min(1, (ms - DISSOLVE_FROM) / DISSOLVE_MS));
        double burst = glitchBurst(ms, Math.max(anger, stare), lev);
        if (dissolve > 0) {
            drawDissolving(b, dissolve);
        } else if (burst > 0) {
            // Now and then he tears: strips of him slide sideways.
            Random rnd = new Random((long) (ms / 60));
            int strips = 18;
            int sh = renderer.height / strips + 1;
            for (int i = 0; i < strips; i++) {
                int y = i * sh;
                int dx = (int) ((rnd.nextDouble() - 0.5) * 2 * unit * SS * 3.5 * burst);
                b.drawImage(renderer.image, (int) -ox + dx, (int) -oy + y, (int) -ox + dx + renderer.width, (int) -oy + y + sh,
                        0, y, renderer.width, Math.min(renderer.height, y + sh), null);
            }
        } else {
            // After the eyes he leaves faint copies of himself behind.
            if (ms > RETURN_MS + 1500 && lev < 0.2) {
                for (int i = 2; i >= 1; i--) {
                    b.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.2f / i));
                    b.drawImage(renderer.image, (int) -ox - (int) (heading * unit * SS * 4 * i), (int) -oy, null);
                }
                b.setComposite(java.awt.AlphaComposite.SrcOver);
            }
            b.drawImage(renderer.image, (int) -ox, (int) -oy, null);
        }
        b.setTransform(base);
        b.dispose();

        drawKnockRipples(g, ms, handX, handY);
        drawCarried(g, w, h, ms, x, footY, scale, heading, icons, spots);
        drawThrown(g, w, h, ms, x, groundY, scale, icons);
    }

    /** Thin rings spreading over the "glass" where his hand knocks. */
    private void drawKnockRipples(Graphics2D g, double ms, double hx, double hy) {
        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int i = 0; i < 3; i++) {
            double age = ms - (KNOCK_FROM + 500 + i * 800);
            if (age < 0 || age > 700) continue;
            double f = age / 700.0;
            double r = unit * 3 + f * unit * 14;
            b.setStroke(new BasicStroke((float) (3 * (1 - f) + 1)));
            b.setColor(new Color(255, 255, 255, (int) (200 * (1 - f))));
            b.draw(new java.awt.geom.Ellipse2D.Double(hx - r, hy - r, r * 2, r * 2));
        }
        b.dispose();
    }

    /** The icons he takes: they fly to his hand off the edge, then ride in a little stack over his head. */
    private void drawCarried(Graphics2D g, int w, int h, double ms, double x, double footY, double scale,
                             double heading, List<BufferedImage> icons, List<java.awt.Rectangle> spots) {
        if (icons == null || icons.isEmpty()) return;
        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double handX = x + heading * unit * 5 * scale, handY = footY - unit * 8 * scale;
        int grabbed = 0;
        for (int k = 0; k < GRABS.length; k++) {
            int pick = (k * 7 + 1) % icons.size();
            double f = (ms - (GRABS[k] + 250)) / 550.0;
            if (f >= 0) GameMain.takeDesktopIcon(pick);
            if (f > 0 && f < 1) {
                java.awt.Rectangle r = spots != null && pick < spots.size() ? spots.get(pick) : null;
                double sx = r != null ? r.getCenterX() : w * 0.03, sy = r != null ? r.getCenterY() : h * (0.2 + 0.2 * k);
                double e = f * f * (3 - 2 * f);
                double ix = sx + (handX - sx) * e, iy = sy + (handY - sy) * e - Math.sin(Math.PI * f) * h * 0.06;
                drawIcon(b, icons.get(pick), ix, iy, h * 0.075 * (1 - 0.3 * e), f * 6);
            }
            if (ms >= GRABS[k] + 800) grabbed++;
        }
        int thrown = ms < THROW_FROM_MS + 640 ? 0 : (int) Math.min(10, (ms - THROW_FROM_MS - 640) / THROW_EVERY_MS + 1);
        int carried = Math.max(0, Math.min(3, grabbed - thrown));
        for (int i = 0; i < carried; i++) {
            int pick = (i * 7 + 1) % icons.size();
            drawIcon(b, icons.get(pick), x + (i - (carried - 1) / 2.0) * h * 0.02, footY - unit * 37 * scale - i * h * 0.03,
                    h * 0.06, (i - 1) * 0.15);
        }
        b.dispose();
    }

    private static void drawIcon(Graphics2D b, BufferedImage icon, double x, double y, double size, double rot) {
        double sc = size / Math.max(icon.getWidth(), icon.getHeight());
        AffineTransform old = b.getTransform();
        b.translate(x, y);
        b.rotate(rot);
        b.scale(sc, sc);
        b.drawImage(icon, -icon.getWidth() / 2, -icon.getHeight() / 2, null);
        b.setTransform(old);
    }

    /** He comes apart into squares that peel off in turn, drift up and away, and fade. */
    private void drawDissolving(Graphics2D b, double d) {
        int tile = 22;
        Random rnd = new Random(2607);
        for (int ty = 0; ty < renderer.height; ty += tile) {
            for (int tx = 0; tx < renderer.width; tx += tile) {
                double delay = rnd.nextDouble(), vx = (rnd.nextDouble() - 0.5), vy = rnd.nextDouble();
                int mx = Math.min(renderer.width - 1, tx + tile / 2), my = Math.min(renderer.height - 1, ty + tile / 2);
                if ((renderer.image.getRGB(mx, my) >>> 24) == 0) continue;
                double local = Math.max(0, Math.min(1, (d - delay * 0.55) / 0.45));
                double ox2 = vx * 700 * local * SS, oy2 = (-vy * 500 * local + 900 * local * local) * SS * 0.4;
                float alpha = (float) Math.max(0, 1 - local * local);
                if (alpha <= 0) continue;
                b.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
                int tw = Math.min(tile, renderer.width - tx), th = Math.min(tile, renderer.height - ty);
                b.drawImage(renderer.image, (int) (-ox + tx + ox2), (int) (-oy + ty + oy2), (int) (-ox + tx + ox2 + tw), (int) (-oy + ty + oy2 + th),
                        tx, ty, tx + tw, ty + th, null);
            }
        }
        b.setComposite(java.awt.AlphaComposite.SrcOver);
    }

    private double glitchBurst(double ms, double anger, double lev) {
        double rate = 2.8 - 1.4 * Math.max(anger, lev);
        double inCycle = (ms / 1000.0) % rate;
        double len = 0.12 + 0.1 * Math.max(anger, lev);
        return inCycle < len ? 1 : 0;
    }

    /** Milliseconds since the current throw began, or -1 when he isn't throwing. */
    private static double throwProgress(double ms) {
        if (ms < THROW_FROM_MS || ms > THROW_TO_MS + 2000) return -1;
        double since = (ms - THROW_FROM_MS) % THROW_EVERY_MS;
        return since < 1500 ? since : -1;
    }

    /** The icons in flight: from his hand to somewhere in the playfield, tumbling, staying a moment. */
    private void drawThrown(Graphics2D g, int w, int h, double ms, double gx, double groundY, double scale, List<BufferedImage> icons) {
        if (icons == null || icons.isEmpty() || ms < THROW_FROM_MS) return;
        int last = (int) Math.min((THROW_TO_MS - THROW_FROM_MS) / THROW_EVERY_MS, (ms - THROW_FROM_MS) / THROW_EVERY_MS);
        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for (int k = Math.max(0, last - 2); k <= last; k++) {
            double release = THROW_FROM_MS + k * THROW_EVERY_MS + 640;
            double age = ms - release;
            if (age < 0 || age > 3400) continue;
            Random rnd = new Random(k * 7919L + 13);
            BufferedImage icon = icons.get(rnd.nextInt(icons.size()));
            double tx = w * (0.15 + 0.7 * rnd.nextDouble());
            double ty = h * (0.22 + 0.45 * rnd.nextDouble());
            double sx = throwerX(release, w);
            double sy = groundY - unit * 22;
            double f = Math.min(1, age / 1500.0);
            double ease = 1 - (1 - f) * (1 - f);
            double x = sx + (tx - sx) * ease;
            double y = sy + (ty - sy) * ease - Math.sin(Math.PI * f) * h * 0.22;
            double grow = 0.55 + 0.75 * ease;
            double alpha = age < 2600 ? 1 : 1 - (age - 2600) / 800.0;
            double size = h * 0.11 * grow;
            double sc = size / Math.max(icon.getWidth(), icon.getHeight());
            AffineTransform old = b.getTransform();
            b.translate(x, y);
            b.rotate(age / 1000.0 * (rnd.nextBoolean() ? 5 : -5) * (1 - f * 0.8));
            b.scale(sc, sc);
            b.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) Math.max(0, alpha) * 0.3f));
            b.setColor(Color.BLACK);
            b.fillRoundRect(-icon.getWidth() / 2 + 8, -icon.getHeight() / 2 + 10, icon.getWidth(), icon.getHeight(), 8, 8);
            b.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) Math.max(0, alpha)));
            b.drawImage(icon, -icon.getWidth() / 2, -icon.getHeight() / 2, null);
            b.setTransform(old);
        }
        b.dispose();
    }

    /** Where he stood when a given throw let go. */
    private static double throwerX(double ms, int w) {
        return w * (0.5 + 0.42 * Math.sin(ms / 1000.0 * 0.55));
    }

    /** The shockwave from the stomp, the cracks, the flash: everything drawn over the top. */
    void renderEffects(Graphics2D g, int w, int h, double ms, double groundY) {
        double a = ms - STOMP_MS;
        if (a >= 0 && a < 900) {
            Graphics2D b = (Graphics2D) g.create();
            b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double cx = w * 0.5;
            double fy = h * 0.72 + unit * 0.6;
            double r = a / 900.0;
            double ring = w * 0.65 * (1 - (1 - r) * (1 - r));
            b.setStroke(new BasicStroke((float) (12 * (1 - r) + 2)));
            b.setColor(new Color(255, 255, 255, (int) (200 * (1 - r))));
            b.draw(new java.awt.geom.Ellipse2D.Double(cx - ring, fy - ring * 0.22, ring * 2, ring * 0.44));
            // Cracks in the screen running out from under his foot.
            Random rnd = new Random(80);
            b.setStroke(new BasicStroke(2.5f));
            int alpha = (int) (255 * Math.min(1, (1 - r) * 1.4));
            b.setColor(new Color(255, 255, 255, Math.max(0, alpha)));
            for (int i = 0; i < 16; i++) {
                double ang = -Math.PI + rnd.nextDouble() * Math.PI;
                double px = cx, py = fy;
                double reach = Math.min(1, a / 260.0) * w * (0.25 + 0.4 * rnd.nextDouble());
                double walked = 0;
                while (walked < reach) {
                    double seg = 24 + rnd.nextDouble() * 30;
                    ang += (rnd.nextDouble() - 0.5) * 0.7;
                    double nx = px + Math.cos(ang) * seg, ny = py + Math.sin(ang) * seg * 0.85;
                    b.draw(new java.awt.geom.Line2D.Double(px, py, nx, ny));
                    px = nx; py = ny; walked += seg;
                }
            }
            b.dispose();
        }
        // White flash at the moment it all goes.
        double f = ms - BLACKOUT_MS;
        if (f >= 0 && f < 500) {
            Graphics2D b = (Graphics2D) g.create();
            b.setColor(new Color(255, 255, 255, (int) (255 * (1 - f / 500.0))));
            b.fillRect(0, 0, w, h);
            b.dispose();
        }
    }

    /** A red creeping in at the edges as he gets angrier. */
    void renderAnger(Graphics2D g, int w, int h, double ms) {
        double anger = anger(ms);
        if (anger <= 0) return;
        double pulse = 0.65 + 0.35 * Math.sin(ms / 1000.0 * (4 + 6 * anger));
        int alpha = (int) (150 * anger * pulse);
        double r = Math.hypot(w, h) / 2;
        Graphics2D b = (Graphics2D) g.create();
        b.setPaint(new RadialGradientPaint((float) (w / 2.0), (float) (h / 2.0), (float) r, new float[]{0.45f, 1f},
                new Color[]{new Color(180, 0, 20, 0), new Color(180, 0, 20, Math.max(0, alpha))}));
        b.fillRect(0, 0, w, h);
        b.dispose();
    }

    /** Plain stand-in icons for when the desktop icons couldn't be read. */
    static List<BufferedImage> fallbackIcons() {
        BufferedImage[] list = new BufferedImage[4];
        for (int i = 0; i < list.length; i++) {
            BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            switch (i) {
                case 0 -> {                                   // a folder
                    g.setColor(new Color(0xF2C14E));
                    g.fillRoundRect(4, 14, 56, 40, 6, 6);
                    g.fillRoundRect(4, 8, 24, 12, 4, 4);
                    g.setColor(new Color(0xFFDD7A));
                    g.fillRoundRect(4, 20, 56, 34, 6, 6);
                }
                case 1 -> {                                   // a document
                    g.setColor(Color.WHITE);
                    g.fillRect(12, 4, 40, 56);
                    g.setColor(new Color(0x8AA4C8));
                    for (int l = 0; l < 6; l++) g.fillRect(18, 14 + l * 7, 28, 3);
                }
                case 2 -> {                                   // a program window
                    g.setColor(new Color(0x2F6FD6));
                    g.fillRoundRect(4, 8, 56, 48, 6, 6);
                    g.setColor(new Color(0xEAF2FF));
                    g.fillRect(8, 20, 48, 32);
                }
                default -> {                                  // a picture
                    g.setColor(new Color(0xEAF2FF));
                    g.fillRect(6, 10, 52, 44);
                    g.setColor(new Color(0x4CAF50));
                    g.fillPolygon(new int[]{6, 26, 40, 58, 58, 6}, new int[]{54, 28, 42, 22, 54, 54}, 6);
                    g.setColor(new Color(0xFFC107));
                    g.fillOval(42, 14, 10, 10);
                }
            }
            g.dispose();
            list[i] = img;
        }
        return List.of(list);
    }
}
