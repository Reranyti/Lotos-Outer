package com.lotusblight.overlay;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
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
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;

/**
 * Plays the whole finale beat live, in a black full-screen window, synced to the track: the walking-row
 * loop, then the piercing, then the four Architect screens in turn. For watching and tuning only; Esc
 * closes it. Times are seconds into the track (matching the real cues around 2:15-2:51).
 */
public final class SceneMain {
    private static final String SKIN = "/assets/lotusblight/textures/overlay/glitcher.png";
    private static final int FPS = 60;

    // Track-time cues (seconds), from the user's storyboard.
    private static final double ARCH_START = 136;     // 2:16 - three architect screens (blue, gold, red)
    private static final double PIERCE = 144;         // 2:24 - the piercing cutscene
    private static final double ROW_START = 146;      // 2:26 - the pose-row cutscene, replayed 4x
    private static final double[] ROW_REPLAYS = {146, 151, 156, 160};
    private static final double PANEL_START = 164;    // 2:44 - the purple-panel part, one pose, no row
    private static final double[] PANEL_REPLAYS = {164, 169, 174, 179};   // 4 replays to the end (3:04)

    private SceneMain() {}

    public static void main(String[] args) throws Exception {
        int seconds = 40;
        String audio = null;
        double startAt = ROW_START;
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--seconds")) seconds = Integer.parseInt(args[i + 1]);
            if (args[i].equals("--audio")) audio = args[i + 1];
            if (args[i].equals("--at")) startAt = Double.parseDouble(args[i + 1]);
        }
        if (GraphicsEnvironment.isHeadless()) { System.err.println("No screen."); System.exit(2); }
        int[] skin = loadSkin();

        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        Rectangle screen = gc.getBounds();
        TrailScene row = new TrailScene(skin, screen.height);
        PiercingScene pierce = new PiercingScene(skin, screen.height);
        ArchitectScene arch = new ArchitectScene(skin, screen.height);
        ArchitectScene.Architect[] arcs = ArchitectScene.Architect.values();

        Clip clip = null;
        if (audio != null) {
            try (AudioInputStream in = AudioSystem.getAudioInputStream(new File(audio))) {
                clip = AudioSystem.getClip();
                clip.open(in);
                clip.setMicrosecondPosition((long) (startAt * 1_000_000));
                clip.start();
            } catch (Exception e) { System.err.println("no audio: " + e); clip = null; }
        }
        final Clip music = clip;
        final long start = System.currentTimeMillis();
        final double base = startAt;

        JFrame frame = new JFrame("Lotus Blight");
        frame.setUndecorated(true);
        frame.setBounds(screen);
        frame.setBackground(Color.BLACK);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { if (e.getKeyCode() == KeyEvent.VK_ESCAPE) System.exit(0); }
        });
        JComponent canvas = new JComponent() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics;
                double t = music != null ? music.getMicrosecondPosition() / 1_000_000.0
                        : base + (System.currentTimeMillis() - start) / 1000.0;
                int w = getWidth(), h = getHeight();
                if (t < ARCH_START) {
                    g.setColor(Color.BLACK);
                    g.fillRect(0, 0, w, h);
                } else if (t < PIERCE) {
                    // Three architect screens (blue, gold, red), the 4th is cut.
                    double each = (PIERCE - ARCH_START) / 3.0;
                    int idx = Math.min(2, (int) ((t - ARCH_START) / each));
                    double local = (t - ARCH_START) - idx * each;
                    arch.render(g, w, h, arcs[idx], t, Math.min(1, local / 0.5));
                } else if (t < ROW_START) {
                    pierce.render(g, w, h, t - PIERCE);
                } else if (t < PANEL_START) {
                    double startAtCue = ROW_REPLAYS[0];
                    for (double c : ROW_REPLAYS) if (t >= c) startAtCue = c;
                    row.render(g, w, h, t, startAtCue);
                } else {
                    double startAtCue = PANEL_REPLAYS[0];
                    int idx = 0;
                    for (int i = 0; i < PANEL_REPLAYS.length; i++) if (t >= PANEL_REPLAYS[i]) { startAtCue = PANEL_REPLAYS[i]; idx = i; }
                    row.renderPanelPart(g, w, h, t - startAtCue, idx);
                }
            }
        };
        frame.setContentPane(canvas);
        frame.setVisible(true);
        frame.requestFocus();
        long end = System.currentTimeMillis() + seconds * 1000L;
        new Timer(1000 / FPS, e -> { if (System.currentTimeMillis() >= end) System.exit(0); frame.repaint(); }).start();
    }

    private static int[] loadSkin() throws Exception {
        try (InputStream in = SceneMain.class.getResourceAsStream(SKIN)) {
            BufferedImage image = ImageIO.read(in);
            return image.getRGB(0, 0, 64, 64, null, 0, 64);
        }
    }
}
