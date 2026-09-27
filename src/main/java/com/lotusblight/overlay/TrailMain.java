package com.lotusblight.overlay;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.awt.image.BufferedImage;

/**
 * Plays the walking-loop scene on its own, in a plain black full-screen window, so it can be watched and
 * tuned. It draws only itself - no desktop capture, no windows touched - and Esc closes it.
 *
 * Usage: java -cp lotusblight.jar com.lotusblight.overlay.TrailMain [--seconds N]
 */
public final class TrailMain {
    private static final String SKIN = "/assets/lotusblight/textures/overlay/glitcher.png";
    private static final int FPS = 60;

    private TrailMain() {}

    public static void main(String[] args) throws Exception {
        int seconds = 30;
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--seconds")) seconds = Integer.parseInt(args[i + 1]);
        }
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("No screen to draw on.");
            System.exit(2);
        }
        int[] skin = loadSkin();
        long end = System.currentTimeMillis() + seconds * 1000L;

        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        Rectangle screen = gc.getBounds();
        TrailScene scene = new TrailScene(skin, screen.height);

        JFrame frame = new JFrame("Lotus Blight");
        frame.setUndecorated(true);
        frame.setBounds(screen);
        frame.setBackground(Color.BLACK);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) System.exit(0);
            }
        });
        long start = System.currentTimeMillis();
        JComponent canvas = new JComponent() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                double time = (System.currentTimeMillis() - start) / 1000.0;
                // Loop the singling-out panel in and out so both halves of the scene are visible.
                double cycle = time % 12.0;
                double panel = cycle < 6 ? 0 : Math.min(1, (cycle - 6) / 1.5) * Math.min(1, (12 - cycle) / 1.5);
                scene.render(g, getWidth(), getHeight(), time, panel);
            }
        };
        frame.setContentPane(canvas);
        frame.setVisible(true);
        frame.requestFocus();
        new Timer(1000 / FPS, e -> {
            if (System.currentTimeMillis() >= end) System.exit(0);
            frame.repaint();
        }).start();
    }

    private static int[] loadSkin() throws IOException {
        try (InputStream in = TrailMain.class.getResourceAsStream(SKIN)) {
            if (in == null) throw new IOException("missing " + SKIN);
            BufferedImage image = ImageIO.read(in);
            return image.getRGB(0, 0, 64, 64, null, 0, 64);
        }
    }
}
