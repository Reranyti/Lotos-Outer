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
import java.util.Random;

/**
 * A borderless, see-through window over the whole main screen. The desktop stays visible underneath;
 * the figure walks along the top of the taskbar, turns its head after the mouse and glitches out.
 * The window covers the screen with a barely-there tint so every click lands on it instead of on the
 * real desktop, and Esc, Alt+F4 or the time limit always close it.
 */
final class OverlayWindow {
    private enum State { ARRIVE, WALK, STARE, VANISH }

    private static final int FPS = 60;
    // Alpha 1 of 255: invisible, but it makes Windows send the clicks to us rather than through us.
    private static final Color CATCH_INPUT = new Color(0, 0, 0, 1);

    private final JFrame frame = new JFrame("Lotus Blight");
    private final Rectangle screen;
    private final int floorY;
    private final long closeAtMillis;
    private final Runnable onClose;

    private final SkinModel model = new SkinModel();
    private final int[] skin;
    private final SoftRenderer renderer;
    private final SoftRenderer.Pose pose = new SoftRenderer.Pose();
    private final GlitchFx glitch = new GlitchFx();
    private final Random random = new Random();
    private final double scale;

    private State state = State.ARRIVE;
    private int stateTicks;
    private double x;
    private double targetX;
    private double walkPhase;
    private double facing;
    private double glitchLevel = 1;
    private int burstTicks;
    private boolean closed;

    OverlayWindow(int[] skin, int seconds, Runnable onClose) {
        this.skin = skin;
        this.onClose = onClose;
        this.closeAtMillis = seconds > 0 ? System.currentTimeMillis() + seconds * 1000L : Long.MAX_VALUE;

        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        this.screen = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        this.floorY = screen.height - insets.bottom;

        // About a third of the screen tall; the canvas leaves room for arms and a full turn.
        this.scale = screen.height * 0.34 / 32.0;
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
            case WALK -> {
                double dir = Math.signum(targetX - x);
                double speed = scale * 0.9;
                x += dir * speed;
                walkPhase += 0.22;
                facing = approach(facing, dir * Math.PI / 2, 0.2);
                glitchLevel = 0.3;
                if (Math.abs(targetX - x) <= speed) {
                    x = targetX;
                    pickNext();
                }
            }
            case STARE -> {
                walkPhase = approach(walkPhase % (2 * Math.PI), 0, 0.3);
                facing = approach(facing, 0, 0.15);
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
        }
        // Short random fits on top of whatever it's doing.
        if (burstTicks > 0) {
            burstTicks--;
        } else if (random.nextInt(FPS) == 0) {
            burstTicks = 6 + random.nextInt(18);
        }
        updatePose();
    }

    private void pickNext() {
        stateTicks = 0;
        int roll = random.nextInt(10);
        if (roll < 5) {
            state = State.WALK;
            targetX = screen.width * (0.08 + random.nextDouble() * 0.84);
        } else if (roll < 8) {
            state = State.STARE;
        } else {
            state = State.VANISH;
        }
    }

    private void updatePose() {
        pose.reset();
        pose.yaw = facing;
        double swing = state == State.WALK ? Math.sin(walkPhase) * 0.7 : Math.sin(walkPhase) * 0.1;
        pose.partPitch[SkinModel.Part.RIGHT_LEG.ordinal()] = swing;
        pose.partPitch[SkinModel.Part.LEFT_LEG.ordinal()] = -swing;
        pose.partPitch[SkinModel.Part.RIGHT_ARM.ordinal()] = -swing * 0.8;
        pose.partPitch[SkinModel.Part.LEFT_ARM.ordinal()] = swing * 0.8;
        // Arms hang slightly out from the body.
        pose.partRoll[SkinModel.Part.RIGHT_ARM.ordinal()] = -0.06;
        pose.partRoll[SkinModel.Part.LEFT_ARM.ordinal()] = 0.06;

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

        boolean fit = burstTicks > 0;
        if (fit) {
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

            if (burstTicks > 0) {
                // A few torn lines across the whole screen while it fits.
                for (int i = 0; i < 6; i++) {
                    int ly = random.nextInt(getHeight());
                    g.setColor(new Color(0x73, 0x00, 0xFF, 60 + random.nextInt(120)));
                    g.fillRect(0, ly, getWidth(), 1 + random.nextInt(3));
                }
            }
            g.dispose();
        }
    }
}
