package com.lotusblight.overlay;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

/**
 * Runs the first song's rhythm game (PROMISED FUTURE) in a black full-screen window for playing and
 * tuning: circles over a dark backdrop, driven by the music clock, left-click to hit, Esc to quit. The
 * scene behind the circles will be added later; for now it is a plain backdrop.
 *
 * Usage: java com.lotusblight.overlay.RhythmMain [--audio file.wav]
 */
public final class RhythmMain {
    private static final String MAP = "/assets/lotusblight/overlay/map_1.osu";
    private static final int FPS = 60;
    private static final int SONG1_HP = 48;

    private RhythmMain() {}

    public static void main(String[] args) throws Exception {
        String audio = null;
        double startAt = 0;
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--audio")) audio = args[i + 1];
            if (args[i].equals("--at")) startAt = Double.parseDouble(args[i + 1]);
        }
        if (GraphicsEnvironment.isHeadless()) { System.err.println("No screen."); System.exit(2); }

        OsuMap map = OsuMap.load(MAP);
        RhythmGame game = new RhythmGame(map, SONG1_HP);
        int[] skin = loadSkin();
        GraphicsConfiguration gc0 = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        Hazards hazards = new Hazards(skin, gc0.getBounds().height);

        Clip clip = null;
        final long startNano = System.nanoTime();
        final double base = startAt;
        if (audio != null) {
            try (AudioInputStream in = AudioSystem.getAudioInputStream(new File(audio))) {
                clip = AudioSystem.getClip();
                clip.open(in);
                clip.setMicrosecondPosition((long) (startAt * 1_000_000));
                clip.start();
            } catch (Exception e) { System.err.println("no audio: " + e); clip = null; }
        }
        final Clip music = clip;
        if (startAt > 0) game.skipTo(startAt * 1000);

        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        Rectangle screen = gc.getBounds();
        JFrame frame = new JFrame("Lotus Blight");
        frame.setUndecorated(true);
        frame.setBounds(screen);
        frame.setBackground(Color.BLACK);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { if (e.getKeyCode() == KeyEvent.VK_ESCAPE) System.exit(0); }
        });

        final boolean[] over = {false};
        JComponent canvas = new JComponent() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics;
                int w = getWidth(), h = getHeight();
                double t = music != null ? music.getMicrosecondPosition() / 1000.0
                        : base * 1000 + (System.nanoTime() - startNano) / 1_000_000.0;
                g.setColor(new Color(0x120820));
                g.fillRect(0, 0, w, h);

                game.update(t);
                game.render(g, w, h, t);
                hazards.render(g, w, h, t);

                if (!game.alive() || game.finished(t)) {
                    over[0] = true;
                    g.setColor(new Color(0, 0, 0, 160));
                    g.fillRect(0, 0, w, h);
                    g.setColor(game.alive() ? new Color(0xB060FF) : new Color(0xFF4060));
                    g.setFont(g.getFont().deriveFont(Font.BOLD, (float) (h * 0.09)));
                    String s = game.alive() ? "ПРОЙДЕНО" : "ПОРАЖЕНИЕ";
                    int sw = g.getFontMetrics().stringWidth(s);
                    g.drawString(s, (w - sw) / 2, h / 2);
                }
            }
        };
        canvas.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (over[0] || e.getButton() != MouseEvent.BUTTON1) return;
                double t = music != null ? music.getMicrosecondPosition() / 1000.0
                        : base * 1000 + (System.nanoTime() - startNano) / 1_000_000.0;
                game.click(e.getX(), e.getY(), t, canvas.getWidth(), canvas.getHeight());
            }
        });
        frame.setContentPane(canvas);
        frame.setVisible(true);
        frame.requestFocus();
        new Timer(1000 / FPS, e -> frame.repaint()).start();
    }

    private static int[] loadSkin() throws Exception {
        try (java.io.InputStream in = RhythmMain.class.getResourceAsStream(
                "/assets/lotusblight/textures/overlay/glitcher.png")) {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
            return img.getRGB(0, 0, 64, 64, null, 0, 64);
        }
    }
}
