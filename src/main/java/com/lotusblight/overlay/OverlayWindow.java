package com.lotusblight.overlay;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A borderless, see-through window over the whole main screen. The desktop stays visible underneath;
 * the figure walks along the top of the taskbar, turns its head after the mouse, glitches out, and
 * pulls icons off the desktop to throw them around - only their pictures, the real ones never move.
 * The window covers the screen with a barely-there tint so every click lands on it instead of on the
 * real desktop, and Esc, Alt+F4 or the time limit always close it.
 */
final class OverlayWindow {
    private enum State { ARRIVE, WALK, STARE, VANISH, FETCH, REACH, HOLD, THROW }

    private static final int FPS = 60;
    // Alpha 1 of 255: invisible, but it makes Windows send the clicks to us rather than through us.
    private static final Color CATCH_INPUT = new Color(0, 0, 0, 1);
    private static final int ARM = SkinModel.Part.RIGHT_ARM.ordinal();
    // The right hand, in model space: the bottom of the arm.
    private static final double[] HAND = {-5.5, 12.5, 0};

    /** An icon picture out in the open: pulled, held, flying or lying on the taskbar. */
    private static final class Thrown {
        final DesktopSnapshot.Icon icon;
        double x, y, vx, vy, angle, spin;
        boolean held = true;
        boolean resting;

        Thrown(DesktopSnapshot.Icon icon) {
            this.icon = icon;
            this.x = icon.bounds.getCenterX();
            this.y = icon.bounds.getCenterY();
        }
    }

    private final JFrame frame = new JFrame("Lotus Blight");
    private final Rectangle screen;
    private final int floorY;
    private final long closeAtMillis;
    private final Runnable onClose;
    private final DesktopSnapshot desktop;

    private final SkinModel model = new SkinModel();
    private final int[] skin;
    private final SoftRenderer renderer;
    private final SoftRenderer.Pose pose = new SoftRenderer.Pose();
    private final GlitchFx glitch = new GlitchFx();
    private final Random random = new Random();
    private final double scale;
    private final double gravity;
    private final List<Thrown> thrown = new ArrayList<>();

    private State state = State.ARRIVE;
    private int stateTicks;
    private double x;
    private double targetX;
    private double walkPhase;
    private double facing;
    private double glitchLevel = 1;
    private int burstTicks;
    private boolean closed;
    private DesktopSnapshot.Icon fetching;
    private Thrown holding;

    OverlayWindow(int[] skin, int seconds, boolean icons, Runnable onClose) {
        this.skin = skin;
        this.onClose = onClose;
        this.closeAtMillis = seconds > 0 ? System.currentTimeMillis() + seconds * 1000L : Long.MAX_VALUE;

        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        this.screen = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        this.floorY = screen.height - insets.bottom;
        // Looked at before the window exists, so the picture is of the desktop and not of us.
        this.desktop = icons ? DesktopSnapshot.capture(screen, floorY) : new DesktopSnapshot();

        // About a third of the screen tall; the canvas leaves room for arms and a full turn.
        this.scale = screen.height * 0.34 / 32.0;
        this.gravity = screen.height * 0.0009;
        this.renderer = new SoftRenderer((int) (22 * scale), (int) (36 * scale));
        this.x = screen.width * (0.2 + random.nextDouble() * 0.6);
        this.targetX = x;

        frame.setUndecorated(true);
        frame.setAlwaysOnTop(true);
        frame.setBackground(new Color(0, 0, 0, 0));
        frame.setBounds(screen);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                close();
            }
        });
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) close();
            }
        });
        frame.setContentPane(new Canvas());
    }

    void show() {
        frame.setVisible(true);
        frame.toFront();
        frame.requestFocus();
        new Timer(1000 / FPS, e -> {
            if (closed) {
                ((Timer) e.getSource()).stop();
                return;
            }
            if (System.currentTimeMillis() >= closeAtMillis) {
                close();
                return;
            }
            tick();
            frame.repaint();
        }).start();
    }

    private void close() {
        if (closed) return;
        closed = true;
        frame.dispose();
        onClose.run();
    }

    private void tick() {
        stateTicks++;
        switch (state) {
            case ARRIVE -> {
                // Comes in as noise and settles.
                glitchLevel = Math.max(0.35, 1 - stateTicks / 45.0);
                if (stateTicks > 60) pickNext();
            }
            case WALK, FETCH -> {
                double dir = Math.signum(targetX - x);
                double speed = scale * 0.9;
                x += dir * speed;
                walkPhase += 0.22;
                facing = approach(facing, dir * Math.PI / 2, 0.2);
                glitchLevel = 0.3;
                if (Math.abs(targetX - x) <= speed) {
                    x = targetX;
                    if (state == State.FETCH) {
                        enter(State.REACH);
                    } else {
                        pickNext();
                    }
                }
            }
            case STARE -> {
                settle();
                glitchLevel = 0.25;
                if (stateTicks > 120 + random.nextInt(60)) pickNext();
            }
            case VANISH -> {
                glitchLevel = Math.min(1, stateTicks / 12.0);
                if (stateTicks == 18) {
                    x = screen.width * (0.1 + random.nextDouble() * 0.8);
                    targetX = x;
                }
                if (stateTicks > 18) {
                    state = State.ARRIVE;
                    stateTicks = 15;
                }
            }
            case REACH -> {
                // Arm up, and the icon is torn off the desktop and dragged down into the hand.
                settle();
                glitchLevel = 0.45;
                if (stateTicks == 1) {
                    fetching.taken = true;
                    holding = new Thrown(fetching);
                    thrown.add(holding);
                    burstTicks = 10;
                }
                if (stateTicks > 40) enter(State.HOLD);
            }
            case HOLD -> {
                settle();
                glitchLevel = 0.3;
                if (stateTicks > 20 + random.nextInt(20)) enter(State.THROW);
            }
            case THROW -> {
                glitchLevel = 0.4;
                if (stateTicks == 8 && holding != null) {
                    double side = random.nextBoolean() ? 1 : -1;
                    holding.held = false;
                    holding.vx = side * screen.width * (0.006 + random.nextDouble() * 0.01);
                    holding.vy = -screen.height * (0.018 + random.nextDouble() * 0.012);
                    holding.spin = side * (0.1 + random.nextDouble() * 0.25);
                    holding = null;
                }
                if (stateTicks > 20) pickNext();
            }
        }
        // Short random fits on top of whatever it's doing.
        if (burstTicks > 0) {
            burstTicks--;
        } else if (random.nextInt(FPS) == 0) {
            burstTicks = 6 + random.nextInt(18);
        }
        updatePose();
        moveThrown();
    }

    private void enter(State next) {
        state = next;
        stateTicks = 0;
    }

    /** Stops walking and turns to face the screen. */
    private void settle() {
        walkPhase = approach(walkPhase % (2 * Math.PI), 0, 0.3);
        facing = approach(facing, 0, 0.15);
    }

    private void pickNext() {
        int roll = random.nextInt(10);
        DesktopSnapshot.Icon icon = roll < 4 ? nextIcon() : null;
        if (icon != null) {
            fetching = icon;
            targetX = clamp(icon.bounds.getCenterX(), screen.width * 0.06, screen.width * 0.94);
            enter(State.FETCH);
        } else if (roll < 7) {
            targetX = screen.width * (0.08 + random.nextDouble() * 0.84);
            enter(State.WALK);
        } else if (roll < 9) {
            enter(State.STARE);
        } else {
            enter(State.VANISH);
        }
    }

    private DesktopSnapshot.Icon nextIcon() {
        List<DesktopSnapshot.Icon> left = new ArrayList<>();
        for (DesktopSnapshot.Icon icon : desktop.icons) {
            if (!icon.taken) left.add(icon);
        }
        return left.isEmpty() ? null : left.get(random.nextInt(left.size()));
    }

    private void updatePose() {
        pose.reset();
        pose.yaw = facing;
        boolean walking = state == State.WALK || state == State.FETCH;
        double swing = walking ? Math.sin(walkPhase) * 0.7 : Math.sin(walkPhase) * 0.1;
        pose.partPitch[SkinModel.Part.RIGHT_LEG.ordinal()] = swing;
        pose.partPitch[SkinModel.Part.LEFT_LEG.ordinal()] = -swing;
        pose.partPitch[ARM] = -swing * 0.8;
        pose.partPitch[SkinModel.Part.LEFT_ARM.ordinal()] = swing * 0.8;
        // Arms hang slightly out from the body.
        pose.partRoll[ARM] = -0.06;
        pose.partRoll[SkinModel.Part.LEFT_ARM.ordinal()] = 0.06;

        switch (state) {
            case REACH, HOLD -> {
                // Right arm straight up at the icon.
                double t = Math.min(1, stateTicks / 10.0);
                pose.partPitch[ARM] = -2.9 * (state == State.HOLD ? 1 : t);
                pose.partRoll[ARM] = -0.25;
            }
            case THROW -> {
                // Overarm swing: from up high down towards the screen, letting go on tick 8.
                double t = Math.min(1, stateTicks / 10.0);
                pose.partPitch[ARM] = -2.9 + 2.3 * t;
                pose.partRoll[ARM] = -0.25;
            }
            default -> {}
        }

        // The head follows the mouse, within what a neck allows.
        int head = SkinModel.Part.HEAD.ordinal();
        PointerInfo pointer = MouseInfo.getPointerInfo();
        if (pointer != null) {
            Point m = pointer.getLocation();
            double headX = screen.x + x;
            double headY = screen.y + floorY - 28 * scale;
            double lookYaw = Math.atan2(m.x - headX, screen.height * 0.6) - facing;
            double lookPitch = Math.atan2(m.y - headY, screen.height * 0.6);
            pose.partYaw[head] = clamp(lookYaw, -1.1, 1.1);
            pose.partPitch[head] = clamp(lookPitch, -0.6, 0.6);
        }

        if (burstTicks > 0) {
            // During a fit the head snaps to odd angles and parts slip out of place.
            pose.partRoll[head] = (random.nextDouble() - 0.5) * 1.2;
            for (int i = 0; i < 6; i++) {
                if (random.nextInt(3) == 0) {
                    pose.partOffset[i][0] = (random.nextDouble() - 0.5) * 3;
                    pose.partOffset[i][1] = (random.nextDouble() - 0.5) * 2;
                }
            }
        }
    }

    /** Where the right hand is on screen for the current pose. */
    private double[] handOnScreen() {
        double[] p = SoftRenderer.transform(HAND, ARM, pose);
        return new double[]{x + p[0] * scale, floorY - p[1] * scale};
    }

    private void moveThrown() {
        double[] hand = handOnScreen();
        for (Thrown t : thrown) {
            if (t.held) {
                // Dragged towards the hand, twitching on the way.
                t.x = approach(t.x, hand[0], 0.12) + (random.nextDouble() - 0.5) * scale * 0.6;
                t.y = approach(t.y, hand[1], 0.12) + (random.nextDouble() - 0.5) * scale * 0.6;
                t.angle = approach(t.angle, 0, 0.2);
                continue;
            }
            if (t.resting) continue;
            t.vy += gravity;
            t.x += t.vx;
            t.y += t.vy;
            t.angle += t.spin;
            double halfW = t.icon.bounds.width / 2.0, halfH = t.icon.bounds.height / 2.0;
            if (t.x < halfW || t.x > screen.width - halfW) {
                t.x = clamp(t.x, halfW, screen.width - halfW);
                t.vx = -t.vx * 0.6;
                t.spin = -t.spin * 0.7;
            }
            if (t.y < halfH) {
                t.y = halfH;
                t.vy = -t.vy * 0.5;
            }
            if (t.y > floorY - halfH) {
                t.y = floorY - halfH;
                t.vy = -t.vy * 0.45;
                t.vx *= 0.75;
                t.spin *= 0.6;
                if (Math.abs(t.vy) < gravity * 4) {
                    // Settled on the taskbar, tipped over at whatever angle it came down.
                    t.vy = 0;
                    t.vx = 0;
                    t.spin = 0;
                    t.resting = true;
                }
            }
        }
    }

    private static double approach(double value, double target, double rate) {
        return value + (target - value) * rate;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private final class Canvas extends JComponent {
        Canvas() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setComposite(AlphaComposite.Src);
            g.setColor(CATCH_INPUT);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setComposite(AlphaComposite.SrcOver);

            // Bare wallpaper where icons were taken from.
            for (DesktopSnapshot.Icon icon : desktop.icons) {
                if (icon.taken) g.drawImage(icon.cover, icon.bounds.x, icon.bounds.y, null);
            }

            renderer.clear();
            double originX = renderer.width / 2.0;
            double originY = renderer.height - 2 * scale;
            renderer.draw(model, skin, pose, scale, originX, originY);
            double level = burstTicks > 0 ? Math.max(glitchLevel, 0.7) : glitchLevel;
            glitch.apply(renderer.pixels, renderer.width, renderer.height, level);

            int drawX = (int) (x - originX);
            int drawY = (int) (floorY - originY);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(renderer.image, drawX, drawY, null);

            for (Thrown t : thrown) drawThrown(g, t);

            if (burstTicks > 0) {
                // Torn lines across the whole screen while it fits.
                for (int i = 0; i < 6; i++) {
                    int ly = random.nextInt(getHeight());
                    g.setColor(new Color(0x73, 0x00, 0xFF, 60 + random.nextInt(120)));
                    g.fillRect(0, ly, getWidth(), 1 + random.nextInt(3));
                }
            }
            g.dispose();
        }

        private void drawThrown(Graphics2D g, Thrown t) {
            int w = t.icon.bounds.width, h = t.icon.bounds.height;
            if (t.held || random.nextInt(40) == 0) {
                // Purple ghosts trailing it while it's being dragged, and the odd flicker later.
                Graphics2D ghost = (Graphics2D) g.create();
                ghost.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
                int jitter = (int) (scale * 0.8);
                ghost.drawImage(t.icon.image, (int) t.x - w / 2 - jitter, (int) t.y - h / 2, null);
                ghost.drawImage(t.icon.image, (int) t.x - w / 2 + jitter, (int) t.y - h / 2 + 1, null);
                ghost.dispose();
            }
            AffineTransform old = g.getTransform();
            g.translate(t.x, t.y);
            g.rotate(t.angle);
            g.drawImage(t.icon.image, -w / 2, -h / 2, null);
            g.setTransform(old);
        }
    }
}
