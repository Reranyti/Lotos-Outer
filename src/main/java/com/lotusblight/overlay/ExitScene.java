package com.lotusblight.overlay;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Everything the exit shows before the fight, as one timed sequence over the desktop: the error dialog,
 * which breaks apart; the Glitcher climbing out of the hole it leaves; the three Architect windows he
 * puts up one after another; the fourth one in the middle that fails with a second error; his line; and
 * the stomp that sends the desktop icons falling away. All of it is pictures - nothing on the real
 * desktop is touched. It advances in ticks of 1/FPS s and draws into any Graphics2D, so it can be
 * rendered off-screen as well.
 */
final class ExitScene {
    enum Stage { ERROR, SHATTER, EMERGE, WINDOWS, FOURTH, ERROR2, TYPE, SILENCE, STOMP, DONE }

    static final int FPS = 60;
    /** Measured and drawn with the same face, so a width worked out is the width painted. */
    static final Font BASE_FONT = new Font(Font.DIALOG, Font.PLAIN, 12);

    private static final String TITLE = "Ошибка";
    private static final String MISSING_IMAGE = "Изображение не найдено";
    private static final String LINE = "Me=Deleted?";
    private static final String[] OK = {"OK"};
    // Alpha 1 of 255: invisible, but it makes Windows send the clicks to us rather than through us.
    private static final Color CATCH_INPUT = new Color(0, 0, 0, 1);
    private static final int PURPLE = 0x7300FF;
    private static final int PURPLE_LIGHT = 0xB25CFF;

    // Stage lengths, seconds.
    private static final double ERROR_WAIT = 10, SHATTER_LEN = 1.4, CRACK = 0.25, EMERGE_LEN = 2.2,
            WINDOW_LEN = 2.6, FOURTH_LEN = 1.2, ERROR2_WAIT = 3.0, TYPE_CHAR = 0.12, TYPE_HOLD = 1.0,
            SILENCE_LEN = 2.2, STOMP_LEN = 2.8, LIFT = 0.4, IMPACT = 0.46;

    /** The three windows he puts up, in order, and the word each shows once its light is on. */
    private static final ArchitectScene.Architect[] ORDER = {
            ArchitectScene.Architect.GOLD, ArchitectScene.Architect.RED, ArchitectScene.Architect.BLUE};
    private static final String[] WORDS = {"Liar", "Victim", "Dangerous"};
    /** Window centres as shares of the screen: left, right, top. The middle is kept for the fourth. */
    private static final double[][] PLACES = {{0.20, 0.30}, {0.80, 0.30}, {0.50, 0.16}};
    private static final double[] MIDDLE = {0.50, 0.42};

    private final int w, h, floorY;
    private final int[] skin;
    private final DesktopSnapshot desktop;
    private final Runnable beep;
    private final Random random = new Random();

    // The first error and its pieces.
    private final String errorBody;
    private final Rectangle dialog;
    private FakeWindows.Hits hits;
    private Point grab;
    private final List<Shard> shards = new ArrayList<>();
    private BufferedImage broken;
    private final List<double[][]> cracks = new ArrayList<>();
    private double riftX, riftY;

    // The second error.
    private final Rectangle dialog2;
    private FakeWindows.Hits hits2;

    // The Architect windows.
    private final Rectangle[] windows = new Rectangle[3];
    private final Rectangle middle;
    private final ArchitectScene architects;
    private final int barH;

    // The Glitcher.
    private final Actor actor;
    private final SoftRenderer renderer;
    private final GlitchFx glitch = new GlitchFx();
    private final double scale;

    // The stomp.
    private final List<Falling> falling = new ArrayList<>();
    private int burstTicks;

    private Stage stage = Stage.ERROR;
    private double t;       // seconds into the current stage
    private double clock;   // seconds since the start

    private static final class Shard {
        final int sx, sy, sw, sh;
        double x, y, vx, vy, angle, spin;

        Shard(int sx, int sy, int sw, int sh) {
            this.sx = sx;
            this.sy = sy;
            this.sw = sw;
            this.sh = sh;
        }
    }

    /** A desktop icon's picture on its way down and out. */
    private static final class Falling {
        final DesktopSnapshot.Icon icon;
        final double delay;
        double x, y, vx, vy, angle, spin;

        Falling(DesktopSnapshot.Icon icon, double delay) {
            this.icon = icon;
            this.delay = delay;
            this.x = icon.bounds.getCenterX();
            this.y = icon.bounds.getCenterY();
        }
    }

    ExitScene(int w, int h, int floorY, int[] skin, DesktopSnapshot desktop, String errorBody, Runnable beep) {
        this.w = w;
        this.h = h;
        this.floorY = floorY;
        this.skin = skin;
        this.desktop = desktop;
        this.errorBody = errorBody;
        this.beep = beep;
        this.dialog = dialogFor(errorBody, w, h, w / 2, h / 2);
        this.riftX = dialog.getCenterX();
        this.riftY = dialog.getCenterY();
        this.dialog2 = dialogFor(MISSING_IMAGE, w, h, (int) (w * MIDDLE[0]), (int) (h * MIDDLE[1]));

        int ww = (int) (w * 0.24), wh = (int) (ww * 0.62);
        for (int i = 0; i < 3; i++) {
            windows[i] = new Rectangle((int) (w * PLACES[i][0]) - ww / 2, (int) (h * PLACES[i][1]) - wh / 2, ww, wh);
        }
        middle = new Rectangle((int) (w * MIDDLE[0]) - ww / 2, (int) (h * MIDDLE[1]) - wh / 2, ww, wh);
        barH = (int) (wh * 0.22);
        architects = new ArchitectScene(skin, wh - barH);

        // About a third of the screen tall, like on the desktop walk.
        this.scale = h * 0.34 / 32.0;
        this.actor = new Actor(skin, false);
        this.renderer = new SoftRenderer((int) (22 * scale), (int) (36 * scale));
    }

    Stage stage() {
        return stage;
    }

    boolean done() {
        return stage == Stage.DONE;
    }

    /** Starts straight at a later stage, with everything before it already in place - for trying it out. */
    void jumpTo(Stage target) {
        if (target.ordinal() > Stage.SHATTER.ordinal()) broken = null;
        enter(target);
        if (target == Stage.SHATTER) startShatter(dialog.x + dialog.width / 2, dialog.y + dialog.height / 2);
        if (target == Stage.STOMP) startStomp();
    }

    // ---- input -----------------------------------------------------------------------------------

    /** A press at (x, y). Only the dialogs react; the rest of the time a press just lands on us. */
    void press(int x, int y) {
        if (stage == Stage.ERROR && hits != null) {
            if (hits.close().contains(x, y) || hits.buttons()[0].contains(x, y)) {
                startShatter(x, y);
                return;
            }
            Rectangle bar = new Rectangle(dialog.x, dialog.y, dialog.width, (int) (dialog.height * 0.22));
            grab = bar.contains(x, y) ? new Point(x - dialog.x, y - dialog.y) : null;
        } else if (stage == Stage.ERROR2 && hits2 != null) {
            if (hits2.close().contains(x, y) || hits2.buttons()[0].contains(x, y)) enter(Stage.TYPE);
        }
    }

    void drag(int x, int y) {
        if (stage == Stage.ERROR && grab != null) dialog.setLocation(x - grab.x, y - grab.y);
    }

    void release() {
        grab = null;
    }

    /** Whether clicks anywhere should land on the overlay. While the first error is up they pass through. */
    boolean catchesInput() {
        return stage != Stage.ERROR;
    }

    // ---- timeline --------------------------------------------------------------------------------

    void tick() {
        double dt = 1.0 / FPS;
        t += dt;
        clock += dt;
        switch (stage) {
            case ERROR -> {
                if (t >= ERROR_WAIT) startShatter(dialog.x + dialog.width / 2, dialog.y + dialog.height / 2);
            }
            case SHATTER -> {
                if (t >= CRACK) moveShards();
                if (t >= SHATTER_LEN) {
                    broken = null;
                    shards.clear();
                    enter(Stage.EMERGE);
                }
            }
            case EMERGE -> {
                if (t >= EMERGE_LEN) enter(Stage.WINDOWS);
            }
            case WINDOWS -> {
                if (t >= WINDOW_LEN * 3) enter(Stage.FOURTH);
            }
            case FOURTH -> {
                if (t >= FOURTH_LEN) {
                    enter(Stage.ERROR2);
                    beep.run();
                    burstTicks = 12;
                }
            }
            case ERROR2 -> {
                if (t >= ERROR2_WAIT) enter(Stage.TYPE);
            }
            case TYPE -> {
                if (t >= LINE.length() * TYPE_CHAR + TYPE_HOLD) enter(Stage.SILENCE);
            }
            case SILENCE -> {
                if (t >= SILENCE_LEN) {
                    enter(Stage.STOMP);
                    startStomp();
                }
            }
            case STOMP -> {
                if (t >= IMPACT && t - dt < IMPACT) burstTicks = 14;
                if (t >= IMPACT) moveFalling(t - IMPACT);
                if (t >= STOMP_LEN) enter(Stage.DONE);
            }
            default -> {}
        }
        if (burstTicks > 0) burstTicks--;
        updatePose();
    }

    private void enter(Stage next) {
        stage = next;
        t = 0;
    }

    private void startShatter(int x, int y) {
        enter(Stage.SHATTER);
        riftX = dialog.getCenterX();
        riftY = dialog.getCenterY();
        // The dialog as it looks, to break into pieces.
        broken = new BufferedImage(dialog.width + 1, dialog.height + 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = broken.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(BASE_FONT);
        FakeWindows.drawDialog(g, 0, 0, dialog.width, dialog.height, TITLE, errorBody, OK);
        g.dispose();

        // Cracks running out from where it was hit.
        cracks.clear();
        double hx = x - dialog.x, hy = y - dialog.y;
        for (int i = 0; i < 7; i++) {
            double a = i * Math.PI * 2 / 7 + random.nextDouble() * 0.5;
            double[][] line = new double[5][];
            double px = hx, py = hy;
            for (int k = 0; k < 5; k++) {
                line[k] = new double[]{px, py};
                double len = dialog.height * (0.15 + random.nextDouble() * 0.25);
                a += (random.nextDouble() - 0.5) * 0.7;
                px += Math.cos(a) * len;
                py += Math.sin(a) * len * 0.6;
            }
            cracks.add(line);
        }

        // The pieces, flying out from the hit.
        shards.clear();
        int cols = 9, rows = 3;
        int cw = (int) Math.ceil(broken.getWidth() / (double) cols), rh = (int) Math.ceil(broken.getHeight() / (double) rows);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int sx = c * cw, sy = r * rh;
                Shard s = new Shard(sx, sy, Math.min(cw, broken.getWidth() - sx), Math.min(rh, broken.getHeight() - sy));
                s.x = dialog.x + sx + s.sw / 2.0;
                s.y = dialog.y + sy + s.sh / 2.0;
                double dx = s.x - x, dy = s.y - y, d = Math.max(1, Math.hypot(dx, dy));
                double speed = h * (0.006 + random.nextDouble() * 0.008);
                s.vx = dx / d * speed;
                s.vy = dy / d * speed - h * 0.006;
                s.spin = (random.nextDouble() - 0.5) * 0.3;
                shards.add(s);
            }
        }
        burstTicks = 16;
    }

    private void moveShards() {
        for (Shard s : shards) {
            s.vy += h * 0.0009;
            s.x += s.vx;
            s.y += s.vy;
            s.angle += s.spin;
        }
    }

    private void startStomp() {
        falling.clear();
        double stompX = w / 2.0;
        for (DesktopSnapshot.Icon icon : desktop.icons) {
            // A ripple out from his foot: the nearest icons go first.
            double delay = Math.abs(icon.bounds.getCenterX() - stompX) / w * 0.5 + random.nextDouble() * 0.08;
            Falling f = new Falling(icon, delay);
            f.vx = (random.nextDouble() - 0.5) * w * 0.004;
            f.vy = -h * (0.004 + random.nextDouble() * 0.008);
            f.spin = (random.nextDouble() - 0.5) * 0.3;
            falling.add(f);
        }
    }

    private void moveFalling(double since) {
        for (Falling f : falling) {
            if (since < f.delay) continue;
            f.icon.taken = true;
            f.vy += h * 0.0009;
            f.x += f.vx;
            f.y += f.vy;
            f.angle += f.spin;
        }
    }

    // ---- the Glitcher's body -------------------------------------------------------------------

    private void updatePose() {
        Actor a = actor;
        Pose15 p = a.begin();
        Rig15.Joint torso = Rig15.Joint.UPPER_TORSO, head = Rig15.Joint.HEAD;
        Rig15.Limb rArm = Rig15.Limb.R_ARM, lArm = Rig15.Limb.L_ARM;
        double breath = Math.sin(clock * 1.5);
        // Standing front-on with the feet planted, the hips a little low; he breathes, the arms hang loose.
        p.reach(Rig15.Limb.R_LEG, -2.4, 3, 0, 1).reach(Rig15.Limb.L_LEG, 2.4, 3, 0, 1);
        p.rootPos[1] = -0.7 - 0.25 * breath;
        double tx = 2 + breath, ty = 0, tz = 0, hx = -1, hy = 0, hz = 0;
        double aR = 2 * breath, aL = 2 * breath;
        double rollR = -4, rollL = 4, flexR = -9, flexL = -9;
        switch (stage) {
            case EMERGE -> {
                // Pulling himself out of the tear with both hands, then letting go.
                double reach = 1 - smooth(t / EMERGE_LEN);
                a.reach(rArm, -3.4, 26, 9, reach).reach(lArm, 3.4, 26, 9, reach);
                a.hands(Actor.Hand.GRIP, Actor.Hand.GRIP);
                a.fistR = a.fistL = 0.5 * reach;
                tx += 14 * reach;
                hx += 8 * reach;
            }
            case WINDOWS -> {
                // Each window is thrown up with an arm: a pointing finger for the first two, an open hand for the third.
                int i = Math.min(2, (int) (t / WINDOW_LEN));
                double lt = t - i * WINDOW_LEN;
                double up = lt < 1.2 ? smooth(lt / 0.3) : 1 - smooth((lt - 1.2) / 0.4);
                if (i == 1) {
                    a.reach(lArm, 6.5, 29, 5, up);
                    a.shapeL = Actor.Hand.POINT;
                } else {
                    double[] tgt = i == 0 ? new double[]{-6.5, 29, 5} : new double[]{-4.5, 32, 1};
                    a.reach(rArm, tgt[0], tgt[1], tgt[2], up);
                    a.shapeR = i == 0 ? Actor.Hand.POINT : Actor.Hand.OPEN;
                    if (i == 2) a.spread = 1;
                }
                hy = i == 0 ? -29 : i == 1 ? 29 : 0;
                hx -= 26;
                tx += 3 * up;
            }
            case FOURTH -> {
                double up = smooth(t / 0.3);
                a.reach(rArm, -4.6, 30, 3, up).reach(lArm, 4.6, 30, 3, up);
                a.shapeR = a.shapeL = Actor.Hand.OPEN;
                a.spread = 1;
                hx -= 17;
            }
            case ERROR2 -> {
                double down = smooth(t / 0.6);
                a.reach(rArm, -4.6, 30, 3, 1 - down).reach(lArm, 4.6, 30, 3, 1 - down);
                a.shapeR = a.shapeL = Actor.Hand.OPEN;
                hz = 17 * down;
            }
            case TYPE -> {
                // Both hands out in front of him, the fingers running over keys that are not there.
                a.reach(rArm, -2.8, 21, 9.4, 1).reach(lArm, 2.8, 21, 9.0, 1);
                a.shapeR = a.shapeL = Actor.Hand.CLAW;
                a.fistR = 0.22 + 0.22 * Math.sin(clock * 30);
                a.fistL = 0.22 + 0.22 * Math.sin(clock * 30 + 2.4);
                hx += 12;
                tx += 6;
            }
            case SILENCE -> hx += 26 * smooth(t / 1.2);
            case STOMP -> {
                hx += 26;
                // The leg comes up out to the side - seen from the front a forward kick barely shows.
                double lift = t < LIFT ? smooth(t / LIFT) : t < IMPACT ? 1 - (t - LIFT) / (IMPACT - LIFT) : 0;
                if (lift > 0) {
                    a.reach(Rig15.Limb.R_LEG, -7.5, 3 + 7 * lift, 1.5 * lift, lift);
                    a.pole(Rig15.Limb.R_LEG, -0.6, 0, 1);
                    p.ik[Rig15.Limb.R_LEG.ordinal()].roll = 28 * lift;
                }
                rollR -= 30 * lift;
                rollL += 17 * lift;
                a.fistR = a.fistL = 0.9 * lift;
            }
            default -> { }
        }
        if (burstTicks > 0) {                                               // a tear: the head twitches, parts jump
            hz += (random.nextDouble() - 0.5) * 57;
            for (Rig15.Joint j : new Rig15.Joint[]{Rig15.Joint.HEAD, Rig15.Joint.UPPER_TORSO, Rig15.Joint.R_UPPER_ARM, Rig15.Joint.L_UPPER_ARM, Rig15.Joint.R_UPPER_LEG, Rig15.Joint.L_UPPER_LEG}) {
                if (random.nextInt(3) == 0) {
                    p.offset[j.ordinal()][0] = (random.nextDouble() - 0.5) * 3;
                }
            }
        }
        p.turn(torso, tx, ty, tz).turn(head, hx, hy, hz);
        p.turn(Rig15.Joint.R_UPPER_ARM, aR, 0, rollR).turn(Rig15.Joint.L_UPPER_ARM, aL, 0, rollL);
        p.turn(Rig15.Joint.R_LOWER_ARM, flexR, 0, 0).turn(Rig15.Joint.L_LOWER_ARM, flexL, 0, 0);
    }

    private double glitchLevel() {
        double level = switch (stage) {
            case EMERGE -> 1 - 0.65 * smooth(t / EMERGE_LEN);
            case FOURTH -> 0.5;
            case ERROR2 -> 0.45;
            case SILENCE -> 0.05;
            case STOMP -> 0.2;
            default -> 0.25;
        };
        return burstTicks > 0 ? Math.max(level, 0.8) : level;
    }

    // ---- drawing ---------------------------------------------------------------------------------

    void render(Graphics2D g) {
        g.setComposite(AlphaComposite.Src);
        g.setColor(catchesInput() ? CATCH_INPUT : new Color(0, 0, 0, 0));
        g.fillRect(0, 0, w, h);
        g.setComposite(AlphaComposite.SrcOver);
        g.setFont(BASE_FONT);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Bare wallpaper where the icons fell from - steady, even while the rest shakes.
        for (DesktopSnapshot.Icon icon : desktop.icons) {
            if (icon.taken) g.drawImage(icon.cover, icon.bounds.x, icon.bounds.y, null);
        }

        Graphics2D s = (Graphics2D) g.create();
        if (stage == Stage.STOMP && t >= IMPACT && t < IMPACT + 0.6) {
            double k = h * 0.012 * (1 - (t - IMPACT) / 0.6);
            s.translate((random.nextDouble() - 0.5) * 2 * k, (random.nextDouble() - 0.5) * 2 * k);
        }

        if (stage == Stage.SHATTER || stage == Stage.EMERGE) drawRift(s);
        if (stage.ordinal() >= Stage.WINDOWS.ordinal()) {
            int shown = stage == Stage.WINDOWS ? Math.min(3, (int) (t / WINDOW_LEN) + 1) : 3;
            for (int i = 0; i < shown; i++) {
                double lt = stage == Stage.WINDOWS ? t - i * WINDOW_LEN : WINDOW_LEN;
                drawArchitectWindow(s, i, lt);
            }
        }
        if (stage == Stage.FOURTH || (stage == Stage.ERROR2 && t < 0.35 && random.nextBoolean())) drawFourth(s);
        if (stage.ordinal() >= Stage.EMERGE.ordinal() && stage != Stage.DONE) drawGlitcher(s);
        if (stage.ordinal() >= Stage.TYPE.ordinal() && stage != Stage.DONE) drawLine(s);
        if (stage == Stage.STOMP) drawFalling(s);

        switch (stage) {
            case ERROR -> hits = FakeWindows.drawDialog(s, dialog.x, dialog.y, dialog.width, dialog.height, TITLE, errorBody, OK);
            case SHATTER -> drawShatter(s);
            case ERROR2 -> hits2 = FakeWindows.drawDialog(s, dialog2.x, dialog2.y, dialog2.width, dialog2.height, TITLE, MISSING_IMAGE, OK);
            default -> {}
        }
        s.dispose();

        if (burstTicks > 0) {
            // Torn purple lines across the whole screen.
            for (int i = 0; i < 6; i++) {
                g.setColor(new Color(0x73, 0x00, 0xFF, 60 + random.nextInt(120)));
                g.fillRect(0, random.nextInt(h), w, 1 + random.nextInt(3));
            }
        }
    }

    private void drawShatter(Graphics2D g) {
        if (broken == null) return;
        if (t < CRACK) {
            // Still in one piece for a moment: shaking, cracks spreading from the hit.
            int jx = random.nextInt(7) - 3, jy = random.nextInt(5) - 2;
            g.drawImage(broken, dialog.x + jx, dialog.y + jy, null);
            double grow = t / CRACK;
            g.setStroke(new BasicStroke(2f));
            g.setColor(new Color(255, 255, 255, 220));
            for (double[][] line : cracks) {
                int n = 1 + (int) Math.ceil(grow * (line.length - 1));
                for (int k = 1; k < n; k++) {
                    g.drawLine(dialog.x + jx + (int) line[k - 1][0], dialog.y + jy + (int) line[k - 1][1],
                            dialog.x + jx + (int) line[k][0], dialog.y + jy + (int) line[k][1]);
                }
            }
            return;
        }
        float alpha = (float) clamp(1 - (t - CRACK) / (SHATTER_LEN - CRACK), 0, 1);
        Graphics2D f = (Graphics2D) g.create();
        f.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        for (Shard sh : shards) {
            AffineTransform old = f.getTransform();
            f.translate(sh.x, sh.y);
            f.rotate(sh.angle);
            f.drawImage(broken, -sh.sw / 2, -sh.sh / 2, sh.sw - sh.sw / 2, sh.sh - sh.sh / 2,
                    sh.sx, sh.sy, sh.sx + sh.sw, sh.sy + sh.sh, null);
            f.setTransform(old);
        }
        f.dispose();
    }

    /** The tear left where the dialog was: opens as it breaks, closes once he is through. */
    private void drawRift(Graphics2D g) {
        double open;
        if (stage == Stage.SHATTER) open = smooth((t - CRACK) / 0.5);
        else open = 1 - smooth((t - (EMERGE_LEN - 0.5)) / 0.5);
        if (open <= 0) return;
        double rh = h * 0.30 * open, rw = w * 0.05 * open;
        float glowR = (float) Math.max(1, rh * 0.9);
        g.setPaint(new RadialGradientPaint((float) riftX, (float) riftY, glowR, new float[]{0f, 1f},
                new Color[]{new Color(0x73, 0x00, 0xFF, 150), new Color(0x73, 0x00, 0xFF, 0)}));
        g.fillOval((int) (riftX - glowR), (int) (riftY - glowR), (int) (glowR * 2), (int) (glowR * 2));
        // A jagged black slit with a bright edge.
        Path2D slit = new Path2D.Double();
        int steps = 14;
        for (int i = 0; i <= steps; i++) {
            double y = riftY - rh / 2 + rh * i / steps;
            double half = rw / 2 * Math.sin(Math.PI * i / steps) * (0.7 + random.nextDouble() * 0.6);
            if (i == 0) slit.moveTo(riftX + half, y); else slit.lineTo(riftX + half, y);
        }
        for (int i = steps; i >= 0; i--) {
            double y = riftY - rh / 2 + rh * i / steps;
            double half = rw / 2 * Math.sin(Math.PI * i / steps) * (0.7 + random.nextDouble() * 0.6);
            slit.lineTo(riftX - half, y);
        }
        slit.closePath();
        g.setColor(Color.BLACK);
        g.fill(slit);
        g.setColor(new Color(PURPLE_LIGHT));
        g.setStroke(new BasicStroke(3f));
        g.draw(slit);
    }

    private void drawArchitectWindow(Graphics2D g, int i, double lt) {
        Rectangle r = windows[i];
        Graphics2D wg = popIn(g, r, lt);
        FakeWindows.drawFrame(wg, r.x, r.y, r.width, r.height, "");
        int bw = r.width - 1, bh = r.height - barH - 1;
        Graphics2D body = (Graphics2D) wg.create(r.x + 1, r.y + barH, bw, bh);
        // The light comes on, then the word.
        double reveal = clamp((lt - 0.35) / 0.9, 0, 1);
        architects.render(body, bw, bh, ORDER[i], clock, reveal);
        double word = clamp((lt - 1.3) / 0.5, 0, 1);
        if (word > 0) drawWord(body, WORDS[i], new Color(ORDER[i].text), word, bw, bh);
        body.dispose();
        wg.dispose();
    }

    /** The fourth window: it comes up in the middle, but there's no picture to put in it. */
    private void drawFourth(Graphics2D g) {
        Rectangle r = middle;
        Graphics2D wg = popIn(g, r, stage == Stage.FOURTH ? t : 1);
        FakeWindows.drawFrame(wg, r.x, r.y, r.width, r.height, "");
        int bw = r.width - 1, bh = r.height - barH - 1;
        Graphics2D body = (Graphics2D) wg.create(r.x + 1, r.y + barH, bw, bh);
        body.setColor(Color.BLACK);
        body.fillRect(0, 0, bw, bh);
        float flicker = (float) (0.4 + random.nextDouble() * 0.6);
        body.setPaint(new RadialGradientPaint(bw / 2f, bh * 0.4f, bh * 0.9f, new float[]{0f, 1f},
                new Color[]{new Color(0xB2, 0x5C, 0xFF, (int) (110 * flicker)), new Color(0, 0, 0, 0)}));
        body.fillRect(0, 0, bw, bh);
        // Static where the picture should be.
        for (int k = 0; k < 90; k++) {
            int sz = 2 + random.nextInt(Math.max(1, bh / 12));
            body.setColor(new Color(random.nextBoolean() ? PURPLE : 0x160046));
            body.fillRect(random.nextInt(bw), random.nextInt(bh), sz * 2, sz);
        }
        body.dispose();
        wg.dispose();
    }

    /** A window popping up: grows in from small with a slight overshoot and a stutter. */
    private Graphics2D popIn(Graphics2D g, Rectangle r, double lt) {
        Graphics2D wg = (Graphics2D) g.create();
        double pop = clamp(lt / 0.35, 0, 1);
        if (pop < 1) {
            double k = 0.3 + 0.7 * backOut(pop);
            double cx = r.getCenterX(), cy = r.getCenterY();
            wg.translate(cx + (random.nextInt(9) - 4), cy);
            wg.scale(k, k);
            wg.translate(-cx, -cy);
        }
        return wg;
    }

    private void drawWord(Graphics2D g, String word, Color color, double alpha, int bw, int bh) {
        Graphics2D t2 = (Graphics2D) g.create();
        t2.setFont(BASE_FONT.deriveFont(Font.BOLD, bh * 0.16f));
        int sw = t2.getFontMetrics().stringWidth(word);
        int x = (bw - sw) / 2, y = (int) (bh * 0.9);
        t2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (alpha * 0.35)));
        t2.setColor(color);
        for (int dx = -2; dx <= 2; dx += 2) {
            for (int dy = -2; dy <= 2; dy += 2) t2.drawString(word, x + dx, y + dy);
        }
        t2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));
        t2.drawString(word, x, y);
        t2.dispose();
    }

    private void drawGlitcher(Graphics2D g) {
        double feetX = w / 2.0, feetY = floorY, k = 1;
        if (stage == Stage.EMERGE) {
            // Out of the tear and down to the taskbar, growing as he comes closer.
            double p = smooth(t / EMERGE_LEN);
            feetX = riftX + (w / 2.0 - riftX) * p;
            feetY = riftY + h * 0.06 + (floorY - riftY - h * 0.06) * p;
            k = 0.15 + 0.85 * p;
        }
        renderer.clear();
        double originX = renderer.width / 2.0, originY = renderer.height - 2 * scale;
        actor.draw(renderer, scale, originX, originY);
        glitch.apply(renderer.pixels, renderer.width, renderer.height, glitchLevel());
        Graphics2D gg = (Graphics2D) g.create();
        gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        gg.drawImage(renderer.image, (int) (feetX - originX * k), (int) (feetY - originY * k),
                (int) (renderer.width * k), (int) (renderer.height * k), null);
        gg.dispose();
    }

    /** "Me=Deleted?" in the empty middle, typed out a letter at a time. */
    private void drawLine(Graphics2D g) {
        int n = stage == Stage.TYPE ? Math.min(LINE.length(), (int) (t / TYPE_CHAR)) : LINE.length();
        String text = LINE.substring(0, n);
        boolean cursor = ((int) (clock * (stage == Stage.TYPE ? 4 : 2))) % 2 == 0;
        Graphics2D t2 = (Graphics2D) g.create();
        t2.setFont(new Font(Font.MONOSPACED, Font.BOLD, (int) (h * 0.075)));
        int full = t2.getFontMetrics().stringWidth(LINE + "_");
        int x = (int) (w * MIDDLE[0]) - full / 2, y = (int) (h * MIDDLE[1]) + t2.getFontMetrics().getAscent() / 3;
        String shown = text + (cursor ? "_" : "");
        t2.setColor(new Color(0x73, 0x00, 0xFF, 120));
        t2.drawString(shown, x - 3, y);
        t2.drawString(shown, x + 3, y);
        t2.setColor(Color.WHITE);
        t2.drawString(shown, x, y);
        t2.dispose();
    }

    private void drawFalling(Graphics2D g) {
        if (t < IMPACT) return;
        double since = t - IMPACT;
        for (Falling f : falling) {
            if (since < f.delay) continue;
            float alpha = (float) clamp(1 - (since - f.delay) / 1.4, 0, 1);
            if (alpha <= 0) continue;
            Graphics2D fg = (Graphics2D) g.create();
            fg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            fg.translate(f.x, f.y);
            fg.rotate(f.angle);
            int iw = f.icon.bounds.width, ih = f.icon.bounds.height;
            fg.drawImage(f.icon.image, -iw / 2, -ih / 2, null);
            fg.dispose();
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------

    /** A dialog wide enough that the longest line never wraps, centred on (cx, cy), shrunk to fit. */
    static Rectangle dialogFor(String body, int screenW, int screenH, int cx, int cy) {
        Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        g.setFont(BASE_FONT);
        int dh = (int) (screenH * 0.15);
        int dw = dh;
        for (int attempt = 0; attempt < 8; attempt++) {
            g.setFont(BASE_FONT.deriveFont(Font.PLAIN, dh * 0.11f));
            int longest = 0;
            for (String line : body.split("\n")) longest = Math.max(longest, g.getFontMetrics().stringWidth(line));
            // FakeWindows wraps the body at 72% of the width.
            dw = Math.max(dh * 3, (int) Math.ceil(longest / 0.72) + 2);
            if (dw <= screenW * 0.9) break;
            dh = (int) (dh * screenW * 0.9 / dw);
        }
        g.dispose();
        return new Rectangle(cx - dw / 2, cy - dh / 2, dw, dh);
    }

    private static double smooth(double x) {
        x = clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static double backOut(double x) {
        double c = 1.70158;
        double u = x - 1;
        return 1 + (c + 1) * u * u * u + c * u * u;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
